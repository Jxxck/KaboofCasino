package de.tigges.kaboofcasino.pack;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * First handler of every game-port connection (see {@link GamePortPack}). It buffers the first bytes until
 * {@link HttpSniff} can tell HTTP from Minecraft:
 * <ul>
 *   <li>Minecraft (handshake VarInt, legacy ping 0xFE, anything else): remove this handler and replay the buffered
 *       bytes untouched to the next handler.</li>
 *   <li>HTTP: answer only {@code GET|HEAD /pack/<sha1>.zip} of a served pack (the casino pack or the music pack;
 *       200, application/zip, Content-Length, Connection: close), 404 for other paths, 405 for other methods, 503
 *       when the pack's own download pool is full (HEAD needs no slot); close after the write. The game pipeline
 *       behind never sees a byte; only its read-timeout handler is removed, the download has its own deadlines.</li>
 *   <li>A GET body goes out in {@link #CHUNK} slices while the channel is writable (at most ~128 KiB queued per
 *       connection) and is checked every second against {@link DownloadPolicy}: stalled, too slow or over the hard
 *       cap means a reset (SO_LINGER 0, so no kernel buffer is kept for a client that stopped reading) and the slot
 *       is free again.</li>
 *   <li>Nothing within {@link GamePortPack#SNIFF_TIMEOUT_MILLIS}: pass through (vanilla's timeout takes over); a
 *       started but unfinished HTTP request is answered if its request line is complete, else closed.</li>
 * </ul>
 * Runs on the channel's event loop only.
 */
final class PackSniffer extends ChannelInboundHandlerAdapter {

    private enum State {
        SNIFFING, PASSED, HTTP
    }

    /** Body slice per write. */
    static final int CHUNK = 64 * 1024;

    private final GamePortPack host;
    private byte[] buffer = new byte[256];
    private int length;
    private State state = State.SNIFFING;
    private ScheduledFuture<?> timeout;
    private Download download;

    PackSniffer(GamePortPack host) {
        this.host = host;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            schedule(ctx);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        schedule(ctx);
        super.channelActive(ctx);
    }

    private void schedule(ChannelHandlerContext ctx) {
        if (timeout == null && state == State.SNIFFING) {
            timeout = ctx.executor().schedule(() -> timedOut(ctx), GamePortPack.SNIFF_TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        switch (state) {
            case PASSED -> ctx.fireChannelRead(msg);
            case HTTP -> ReferenceCountUtil.release(msg);
            case SNIFFING -> sniff(ctx, msg);
        }
    }

    private void sniff(ChannelHandlerContext ctx, Object msg) {
        if (!host.enabled() || !(msg instanceof ByteBuf in)) {
            passThrough(ctx, msg, false);
            return;
        }
        int n = in.readableBytes();
        if (length + n > buffer.length) {
            buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, length + n));
        }
        in.getBytes(in.readerIndex(), buffer, length, n);
        length += n;
        in.release();
        switch (HttpSniff.classify(buffer, length)) {
            case WAIT -> {
                // keep buffering
            }
            case MINECRAFT -> passThrough(ctx, null, false);
            case HTTP -> serve(ctx, HttpSniff.requestLine(buffer, length));
            case HTTP_TOO_LARGE -> serve(ctx, null);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        if (state != State.HTTP) {
            ctx.fireChannelReadComplete();
        }
    }

    private void timedOut(ChannelHandlerContext ctx) {
        if (state != State.SNIFFING) {
            return;
        }
        if (host.enabled() && HttpSniff.looksLikeHttp(buffer, length)) {
            serve(ctx, HttpSniff.requestLine(buffer, length));
        } else {
            passThrough(ctx, null, true);
        }
    }

    /** Replays the buffered bytes (plus {@code extra}) to the game pipeline and leaves it for good. */
    private void passThrough(ChannelHandlerContext ctx, Object extra, boolean outsideRead) {
        state = State.PASSED;
        cancelTimeout();
        byte[] data = buffer;
        int n = length;
        buffer = null;
        length = 0;
        if (ctx.pipeline().context(this) != null) {
            ctx.pipeline().remove(this);
        }
        if (n > 0) {
            ctx.fireChannelRead(Unpooled.wrappedBuffer(data, 0, n));
        }
        if (extra != null) {
            ctx.fireChannelRead(extra);
        }
        if (outsideRead && (n > 0 || extra != null)) {
            ctx.fireChannelReadComplete();
        }
    }

    private void serve(ChannelHandlerContext ctx, HttpSniff.Request request) {
        state = State.HTTP;
        cancelTimeout();
        buffer = null;
        length = 0;
        if (ctx.pipeline().get("timeout") != null) {
            ctx.pipeline().remove("timeout");
        }
        if (request == null) {
            respond(ctx, HttpSniff.head(400, "Bad Request", null, 0, null), "?", 400);
            return;
        }
        String method = request.method();
        boolean head = method.equals("HEAD");
        if (!head && !method.equals("GET")) {
            respond(ctx, HttpSniff.head(405, "Method Not Allowed", null, 0, "Allow: GET, HEAD\r\n"), method, 405);
            return;
        }
        DownloadSlots.Pool pool = host.slots().find(request.path());
        if (pool == null) {
            respond(ctx, HttpSniff.head(404, "Not Found", null, 0, null), method, 404);
            return;
        }
        PackFile pack = pool.pack();
        byte[] headBytes = HttpSniff.head(200, "OK", "application/zip", pack.size(),
                "Cache-Control: public, max-age=31536000, immutable\r\n");
        if (head) {
            respond(ctx, headBytes, method, 200);
            return;
        }
        if (!pool.tryAcquire()) {
            respond(ctx, HttpSniff.head(503, "Service Unavailable", null, 0, "Retry-After: 5\r\n"), method, 503);
            return;
        }
        download = new Download(ctx, pool, headBytes);
        download.begin();
    }

    /** A short answer without a body; closes after the write. */
    private void respond(ChannelHandlerContext ctx, byte[] head, String method, int status) {
        ctx.writeAndFlush(Unpooled.wrappedBuffer(head)).addListener((ChannelFutureListener) f -> {
            host.answered(method, status);
            f.channel().close();
        });
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (state == State.HTTP) {
            if (download != null && ctx.channel().isWritable()) {
                download.pump();
            }
            return;
        }
        super.channelWritabilityChanged(ctx);
    }

    /** One GET body on its way out: slices while writable, deadlines from {@link DownloadPolicy}. */
    private final class Download {
        private final ChannelHandlerContext ctx;
        private final DownloadSlots.Pool pool;
        private final PackFile pack;
        private final byte[] body;
        private final byte[] head;
        private final DownloadPolicy policy;
        private final long number;
        private long startNanos;
        private long progressNanos;
        private int offset;
        private long sent;
        private boolean done;
        private boolean pumping;
        private boolean again;
        private ScheduledFuture<?> check;

        Download(ChannelHandlerContext ctx, DownloadSlots.Pool pool, byte[] head) {
            this.ctx = ctx;
            this.pool = pool;
            this.pack = pool.pack();
            this.body = pack.bytes();
            this.head = head;
            this.policy = host.slots().policy();
            this.number = host.slots().nextNumber();
        }

        private long now() {
            return ctx.executor().ticker().nanoTime();
        }

        void begin() {
            startNanos = now();
            progressNanos = startNanos;
            host.started(number, pack);
            ctx.write(Unpooled.wrappedBuffer(head));
            check = ctx.executor().scheduleAtFixedRate(this::check, policy.checkMillis(), policy.checkMillis(),
                    TimeUnit.MILLISECONDS);
            pump();
        }

        /**
         * Queues slices while the channel is writable; the rest follows on channelWritabilityChanged. A flush that
         * drains at once makes the channel writable again inside this call: that only marks another round instead of
         * recursing.
         */
        void pump() {
            if (done) {
                return;
            }
            if (pumping) {
                again = true;
                return;
            }
            pumping = true;
            try {
                do {
                    again = false;
                    while (!done && offset < body.length && ctx.channel().isWritable()) {
                        int n = Math.min(CHUNK, body.length - offset);
                        ByteBuf slice = Unpooled.wrappedBuffer(body, offset, n).asReadOnly();
                        offset += n;
                        boolean last = offset == body.length;
                        ctx.write(slice).addListener((ChannelFutureListener) f -> written(f, n, last));
                    }
                    if (!done) {
                        ctx.flush();
                    }
                } while (again && !done && offset < body.length);
            } finally {
                pumping = false;
            }
        }

        private void written(ChannelFuture f, int n, boolean last) {
            if (done) {
                return;
            }
            if (!f.isSuccess()) {
                end(null, "write failed: " + f.cause());
                return;
            }
            sent += n;
            progressNanos = now();
            if (last) {
                end(null, null);
            }
        }

        private void check() {
            if (done) {
                return;
            }
            long now = now();
            DownloadPolicy.Verdict v = policy.verdict(pool.limits(), now - startNanos, now - progressNanos, sent);
            if (v != DownloadPolicy.Verdict.OK) {
                host.slots().cut(v);
                end(v, v.text());
            }
        }

        /**
         * @param verdict the deadline that cut it, or null
         * @param why     null when complete, else why it ended early
         */
        void end(DownloadPolicy.Verdict verdict, String why) {
            if (done) {
                return;
            }
            done = true;
            if (check != null) {
                check.cancel(false);
            }
            pool.release();
            long millis = (now() - startNanos) / 1_000_000;
            if (why == null) {
                host.finished(number, pack, sent, millis);
                ctx.channel().close();
                return;
            }
            host.cut(number, pack, why, sent, millis);
            if (verdict != null && ctx.channel().isOpen()) {
                // a reset: the kernel drops what is still queued for a client that does not read
                ctx.channel().config().setOption(ChannelOption.SO_LINGER, 0);
            }
            ctx.channel().close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cancelTimeout();
        buffer = null;
        if (download != null) {
            download.end(null, "closed by the client");
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (state == State.HTTP) {
            ctx.close();
        } else {
            ctx.fireExceptionCaught(cause);
        }
    }

    private void cancelTimeout() {
        if (timeout != null) {
            timeout.cancel(false);
            timeout = null;
        }
    }
}
