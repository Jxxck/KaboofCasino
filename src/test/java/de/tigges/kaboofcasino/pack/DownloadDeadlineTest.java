package de.tigges.kaboofcasino.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Pools and deadlines of game-port downloads with a simulated slow socket: the pure rules of {@link DownloadPolicy}
 * and the sniffer's accounting (slices only count once the "socket" took them) under a frozen netty clock.
 */
class DownloadDeadlineTest {

    private static PackFile pack;
    private static PackFile music;

    @BeforeAll
    static void load() {
        pack = PackFile.load(DownloadDeadlineTest.class.getClassLoader());
        music = PackFile.loadMusic(DownloadDeadlineTest.class.getClassLoader());
    }

    private static final long S = 1_000_000_000L;

    // ---- pure rules -------------------------------------------------------------------------------------------------

    @Test
    void defaultLimits() {
        DownloadPolicy p = DownloadPolicy.DEFAULT;
        assertEquals(new DownloadPolicy.Limits("casino", 16, 300 * S), p.limits(pack));
        assertEquals(new DownloadPolicy.Limits("music", 4, 180 * S), p.limits(music));
        assertEquals(20 * S, p.graceNanos());
        assertEquals(16 * 1024, p.minBytesPerSecond());
        assertEquals(15 * S, p.stallNanos());
        assertEquals(1000, p.checkMillis());
    }

    @Test
    void verdicts() {
        DownloadPolicy p = DownloadPolicy.DEFAULT;
        DownloadPolicy.Limits casino = p.limits(pack);
        DownloadPolicy.Limits songs = p.limits(music);
        assertEquals(DownloadPolicy.Verdict.OK, p.verdict(casino, 5 * S, 5 * S, 0), "slow start is fine");
        assertEquals(DownloadPolicy.Verdict.STALLED, p.verdict(casino, 15 * S, 15 * S, 0));
        assertEquals(DownloadPolicy.Verdict.OK, p.verdict(casino, 19 * S, 1 * S, 1000), "grace: no rate yet");
        assertEquals(DownloadPolicy.Verdict.TOO_SLOW, p.verdict(casino, 20 * S, 1 * S, 20 * 16 * 1024 - 1));
        assertEquals(DownloadPolicy.Verdict.OK, p.verdict(casino, 20 * S, 1 * S, 20 * 16 * 1024));
        assertEquals(DownloadPolicy.Verdict.OK, p.verdict(casino, 299 * S, 0, 5_000_000));
        assertEquals(DownloadPolicy.Verdict.OVERTIME, p.verdict(casino, 300 * S, 0, 4_700_000));
        assertEquals(DownloadPolicy.Verdict.OK, p.verdict(songs, 90 * S, 0, 20_000_000), "music may take longer");
        assertEquals(DownloadPolicy.Verdict.OVERTIME, p.verdict(songs, 180 * S, 0, 22_000_000));
        assertEquals(DownloadPolicy.Verdict.STALLED, p.verdict(songs, 60 * S, 16 * S, 20_000_000),
                "a fast start does not excuse a client that stopped reading");
    }

    @Test
    void poolsAreSeparateAndBounded() {
        DownloadSlots slots = new DownloadSlots(DownloadPolicy.DEFAULT, List.of(pack, music));
        DownloadSlots.Pool casino = slots.of(pack);
        DownloadSlots.Pool songs = slots.of(music);
        for (int i = 0; i < 4; i++) {
            assertTrue(songs.tryAcquire());
        }
        assertFalse(songs.tryAcquire());
        for (int i = 0; i < 16; i++) {
            assertTrue(casino.tryAcquire(), "music never takes a casino slot");
        }
        assertFalse(casino.tryAcquire());
        assertEquals("casino 16/16, music 4/4", slots.occupancy());
        casino.release();
        assertTrue(casino.tryAcquire());
        assertEquals(casino, slots.find(pack.path()));
        assertEquals(songs, slots.find(music.path()));
    }

    // ---- netty --------------------------------------------------------------------------------------------------------

    /**
     * Stands in for the socket of a slow client: holds what the sniffer writes and passes on only what the test
     * releases; unwritable while more than 128 KiB wait (like netty's high water mark).
     */
    private static final class SlowSocket extends ChannelOutboundHandlerAdapter {
        private final ArrayDeque<Object[]> queue = new ArrayDeque<>();
        private long queued;
        private ChannelHandlerContext ctx;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            queue.add(new Object[] {msg, promise});
            queued += ((ByteBuf) msg).readableBytes();
            update();
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            // the client decides
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            for (Object[] e : queue) {
                ReferenceCountUtil.release(e[0]);
            }
            queue.clear();
            super.close(ctx, promise);
        }

        /** Passes on {@code slices} body slices (and the small head before them). */
        void release(int slices) {
            while (slices > 0 && !queue.isEmpty()) {
                Object[] e = queue.poll();
                int n = ((ByteBuf) e[0]).readableBytes();
                queued -= n;
                if (n > 1024) {
                    slices--;
                }
                ctx.writeAndFlush(e[0], (ChannelPromise) e[1]);
                update(); // room again: the sniffer queues the next slices
            }
            update();
        }

        private void update() {
            boolean writable = queued <= 128 * 1024;
            if (ctx.channel().unsafe().outboundBuffer() != null
                    && ctx.channel().unsafe().outboundBuffer().getUserDefinedWritability(1) != writable) {
                ctx.channel().unsafe().outboundBuffer().setUserDefinedWritability(1, writable);
            }
        }
    }

    private record Setup(EmbeddedChannel ch, SlowSocket socket, GamePortPack host) {

        void second() {
            ch.advanceTimeBy(1, TimeUnit.SECONDS);
            ch.runScheduledPendingTasks();
            ch.runPendingTasks();
        }
    }

    private static Setup get(PackFile file) {
        GamePortPack host = new GamePortPack(List.of(pack, music), LoggerFactory.getLogger("test"));
        EmbeddedChannel ch = new EmbeddedChannel();
        ch.freezeTime();
        SlowSocket socket = new SlowSocket();
        ch.pipeline().addFirst(GamePortPack.HANDLER, new PackSniffer(host));
        ch.pipeline().addFirst("slow-socket", socket);
        ch.pipeline().fireChannelActive();
        ch.writeInbound(Unpooled.wrappedBuffer(("GET " + file.path() + " HTTP/1.1\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1)));
        return new Setup(ch, socket, host);
    }

    @Test
    void aClientThatReadsNothingIsCutAfter15Seconds() {
        Setup s = get(music);
        DownloadSlots.Pool pool = s.host().slots().of(music);
        assertEquals(1, pool.active());
        assertTrue(s.socket().queued <= 3 * 64 * 1024, "only ~128 KiB queued for a client that does not read");
        for (int i = 0; i < 14; i++) {
            s.second();
        }
        assertTrue(s.ch().isOpen());
        s.second();
        assertFalse(s.ch().isOpen(), "cut at 15 s");
        assertEquals(0, pool.active(), "slot free again");
        assertEquals(1, s.host().slots().cuts(DownloadPolicy.Verdict.STALLED));
    }

    @Test
    void aTricklingClientIsCutWhenTheGraceTimeEnds() {
        Setup s = get(pack);
        DownloadSlots.Pool pool = s.host().slots().of(pack);
        int second = 0;
        while (s.ch().isOpen() && second < 60) {
            second++;
            if (second % 8 == 0) {
                s.socket().release(1); // 64 KiB every 8 s = 8 KiB/s: below 16 KiB/s, but never stalled
            }
            s.second();
        }
        assertEquals(20, second, "cut at the first check after the 20 s grace");
        assertEquals(1, s.host().slots().cuts(DownloadPolicy.Verdict.TOO_SLOW));
        assertEquals(0, s.host().slots().cuts(DownloadPolicy.Verdict.STALLED));
        assertEquals(0, pool.active());
    }

    @Test
    void aSlowButSteadyMusicDownloadEndsAtTheHardCap() {
        Setup s = get(music);
        int second = 0;
        while (s.ch().isOpen() && second < 400) {
            second++;
            s.socket().release(1); // 64 KiB/s: fast enough, but 22 MB would take ~340 s
            s.second();
        }
        assertEquals(180, second);
        assertEquals(1, s.host().slots().cuts(DownloadPolicy.Verdict.OVERTIME));
        assertEquals(0, s.host().slots().of(music).active());
    }

    @Test
    void aSteadyDownloadFinishesAndFreesItsSlot() {
        Setup s = get(pack);
        int second = 0;
        while (s.ch().isOpen() && second < 90) {
            second++;
            s.socket().release(4); // 256 KiB/s: 3 MB in ~12 s
            s.second();
        }
        assertTrue(second < 20, "finished in " + second + " s");
        assertEquals(0, s.host().slots().of(pack).active());
        for (DownloadPolicy.Verdict v : DownloadPolicy.Verdict.values()) {
            assertEquals(0, s.host().slots().cuts(v), v.toString());
        }
    }

    @Test
    void aClientThatHangsUpFreesItsSlot() {
        Setup s = get(music);
        s.socket().release(3);
        s.second();
        s.ch().close();
        assertEquals(0, s.host().slots().of(music).active());
        for (DownloadPolicy.Verdict v : DownloadPolicy.Verdict.values()) {
            assertEquals(0, s.host().slots().cuts(v), v.toString());
        }
    }
}
