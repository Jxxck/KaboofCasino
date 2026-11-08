-- KaboofCasino schema 4: the leaderboard (PLAN-2 C4, step 10). Chip rounds are read by status, stake item and the
-- time they were settled. Ore rounds (0.8.0 and older, stake_item = an ore) never match the chip filter.
CREATE INDEX rounds_board ON rounds (status, stake_item, settled_at);
-- Players who hid themselves (/casino bestenliste aus): a mirror of the PDC flag kaboofcasino:board_hidden for the
-- DB thread, which cannot read player files. The PDC is authoritative, joins repair the mirror.
CREATE TABLE board_hidden (
    player TEXT PRIMARY KEY,
    at     INTEGER NOT NULL
);
-- Weeks whose winners went to the server log (once per week, also across restarts).
CREATE TABLE board_weeks (
    week_start INTEGER PRIMARY KEY,
    logged_at  INTEGER NOT NULL
);
