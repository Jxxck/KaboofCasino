-- KaboofCasino schema 1. Never authoritative for items: money lives in the player PDC (server.md 7).
CREATE TABLE rounds (
    id            TEXT PRIMARY KEY,
    player        TEXT NOT NULL,
    player_name   TEXT NOT NULL,
    machine       TEXT NOT NULL,
    game          TEXT NOT NULL,
    stake_item    TEXT NOT NULL,
    stake_count   INTEGER NOT NULL,
    payout_item   TEXT,
    payout_count  INTEGER NOT NULL DEFAULT 0,
    detail        TEXT NOT NULL,
    created_at    INTEGER NOT NULL,
    settled_at    INTEGER,
    reason        TEXT,
    status        TEXT NOT NULL CHECK (status IN ('OPEN', 'SETTLED', 'VOID_CRASH'))
);
CREATE INDEX rounds_player ON rounds (player, created_at);
CREATE INDEX rounds_open ON rounds (status, created_at);

CREATE TABLE machines (
    id          TEXT PRIMARY KEY,
    type        TEXT NOT NULL,
    number      INTEGER NOT NULL,
    world       TEXT NOT NULL,
    x           INTEGER NOT NULL,
    y           INTEGER NOT NULL,
    z           INTEGER NOT NULL,
    facing      TEXT NOT NULL,
    placer      TEXT NOT NULL,
    placed_at   INTEGER NOT NULL,
    state       TEXT NOT NULL CHECK (state IN ('PLACED', 'GONE')),
    removed_at  INTEGER,
    removed_why TEXT
);
CREATE INDEX machines_type ON machines (type, number);
