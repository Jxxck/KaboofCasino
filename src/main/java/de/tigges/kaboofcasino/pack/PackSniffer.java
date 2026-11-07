package de.tigges.kaboofcasino.pack;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
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
 *       200, application/zip, Content-Length, Connection: close), 404 for other paths, 405 for other methods, 503 when
 *       {@link GamePortPack#MAX_DOWNLOADS} are running; close after the write. The game pipeline behind never sees a byte; only its read-timeout handler is removed so a
 *       slow download is not cut after 30 s.</li>
 *   <li>Nothing within {@link GamePortPack#SNIFF_TIMEOUT_MILLIS}: pass through (vanilla's timeout takes over); a
 *       started but unfinished HTTP request is answered if its request line is complete, else closed.</li>
 * </ul>
 * Runs on the channel's event loop only.
 */
final class PackSniffer extends ChannelInboundHandlerAdapter {

    private enum State {
        SNIFFING, PASSED, HTTP
    }

    private final GamePortPack host;
    private byte[] buffer = new byte[256];
    private int length;
    private State state = State.SNIFFING;
    private ScheduledFuture<?> timeout;

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
        long start = System.nanoTime();
        if (request == null) {
            respond(ctx, HttpSniff.head(400, "Bad Request", null, 0, null), null, "?", 400, start, false);
            return;
        }
        String method = request.method();
        boolean head = method.equals("HEAD");
        if (!head && !method.equals("GET")) {
            respond(ctx, HttpSniff.head(405, "Method Not Allowed", null, 0, "Allow: GET, HEAD\r\n"), null, method,
                    405, start, false);
            return;
        }
        PackFile pack = host.find(request.path());
        if (pack == null) {
            respond(ctx, HttpSniff.head(404, "Not Found", null, 0, null), null, method, 404, start, false);
            return;
        }
        if (!host.tryAcquire()) {
            respond(ctx, HttpSniff.head(503, "Service Unavailable", null, 0, "Retry-After: 5\r\n"), null, method,
                    503, start, false);
            return;
        }
        byte[] zip = pack.bytes();
        byte[] headBytes = HttpSniff.head(200, "OK", "application/zip", zip.length,
                "Cache-Control: public, max-age=31536000, immutable\r\n");
        respond(ctx, headBytes, head ? null : zip, method, 200, start, true, pack.name());
    }

    private void respond(ChannelHandlerContext ctx, byte[] head, byte[] body, String method, int status, long start,
                         boolean acquired) {
        respond(ctx, head, body, method, status, start, acquired, "-");
    }

    private void respond(ChannelHandlerContext ctx, byte[] head, byte[] body, String method, int status, long start,
                         boolean acquired, String packName) {
        ctx.write(Unpooled.wrappedBuffer(head));
        ByteBuf payload = body == null ? Unpooled.EMPTY_BUFFER : Unpooled.wrappedBuffer(body).asReadOnly();
        ctx.writeAndFlush(payload).addListener((ChannelFutureListener) f -> {
            if (acquired) {
                host.release();
            }
            host.served(method, status, packName, body == null ? 0 : body.length,
                    (System.nanoTime() - start) / 1_000_000);
            f.channel().close();
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cancelTimeout();
        buffer = null;
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
