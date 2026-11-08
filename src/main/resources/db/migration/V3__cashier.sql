-- KaboofCasino schema 3: every cashier transaction (PLAN-2 C3). EXCHANGE = ores in, chips out, CHANGE = chips in,
-- other chips out, BUY = chips in, a prize (and change) out. A log only: the items live in the player's file.
CREATE TABLE cashier (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    player      TEXT NOT NULL,
    player_name TEXT NOT NULL,
    kind        TEXT NOT NULL CHECK (kind IN ('EXCHANGE', 'CHANGE', 'BUY')),
    machine     TEXT NOT NULL,
    items_in    TEXT NOT NULL,
    items_out   TEXT NOT NULL,
    chip_value  INTEGER NOT NULL,
    owed        INTEGER NOT NULL DEFAULT 0,
    detail      TEXT NOT NULL DEFAULT '',
    at          INTEGER NOT NULL
);
CREATE INDEX cashier_player ON cashier (player, at);
CREATE INDEX cashier_kind ON cashier (kind, at);
