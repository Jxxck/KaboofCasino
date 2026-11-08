-- KaboofCasino schema 4: the leaderboard (PLAN-2 C4, step 10). A play is one bet commit of one player (a three-slot
-- wheel spin, a derby group entry with three lanes, one blackjack hand). Its rows share player, machine and created_at
-- (one commit, one clock reading), which is also what the play column says for new rows (player:machine:created).
-- The boards group by those three, so rows of 0.9.x without a play key group the same way. refund marks rows that
-- only gave the stake back (derby entries before the start, tables removed mid-hand), the boards skip them.
ALTER TABLE rounds ADD COLUMN play TEXT;
ALTER TABLE rounds ADD COLUMN refund INTEGER NOT NULL DEFAULT 0;
UPDATE rounds SET refund = 1 WHERE detail LIKE '%refund%';
-- Covering index of the board's one scan, in play order (player, machine, created_at), so the grouping needs no sort,
-- with everything a play needs. Ore rounds (0.8.0 and older) never match the chip filter.
CREATE INDEX rounds_board ON rounds (status, stake_item, refund, player, machine, created_at, settled_at, game,
    stake_count, payout_count);
-- Players who hid themselves (/casino bestenliste aus): a mirror of the PDC flag kaboofcasino:board_hidden for the
-- DB thread, which cannot read player files. Either one hides a player, only "an" clears both.
CREATE TABLE board_hidden (
    player TEXT PRIMARY KEY,
    at     INTEGER NOT NULL
);
-- Weeks whose winners went to the server log (once per week, also across restarts).
CREATE TABLE board_weeks (
    week_start INTEGER PRIMARY KEY,
    logged_at  INTEGER NOT NULL
);
