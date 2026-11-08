-- KaboofCasino schema 5: every Chip-Shop purchase (PLAN-2 C6, step 11). A log only, the items live in the player's
-- file. paid = the chips taken (value), change = the chips given back, owed = change that did not fit.
CREATE TABLE chipshop (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    player      TEXT NOT NULL,
    player_name TEXT NOT NULL,
    machine     TEXT NOT NULL,
    item        TEXT NOT NULL,
    quantity    INTEGER NOT NULL,
    unit_price  INTEGER NOT NULL,
    price       INTEGER NOT NULL,
    paid        TEXT NOT NULL,
    change      INTEGER NOT NULL DEFAULT 0,
    owed        INTEGER NOT NULL DEFAULT 0,
    at          INTEGER NOT NULL
);
CREATE INDEX chipshop_player ON chipshop (player, at);
CREATE INDEX chipshop_item ON chipshop (item, at);
