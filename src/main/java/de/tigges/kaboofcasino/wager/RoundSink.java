package de.tigges.kaboofcasino.wager;

import java.util.UUID;

/** Receives round events for the log/statistics (never authoritative). Called on MAIN; must not block. */
public interface RoundSink {

    void started(UUID player, String playerName, RoundRecord round);

    void settled(UUID player, RoundRecord round, SettleReason reason, long settledAt);

    /** A no-op sink for tests. */
    RoundSink NONE = new RoundSink() {
        @Override
        public void started(UUID player, String playerName, RoundRecord round) {
        }

        @Override
        public void settled(UUID player, RoundRecord round, SettleReason reason, long settledAt) {
        }
    };
}
