-- KaboofCasino schema 2: where players logged out (block box of feet and head), so no machine is placed on them
-- while they are offline (they would log in inside barriers). Removed at the player's next join.
CREATE TABLE logouts (
    player TEXT PRIMARY KEY,
    world  TEXT NOT NULL,
    x0     INTEGER NOT NULL,
    y0     INTEGER NOT NULL,
    z0     INTEGER NOT NULL,
    x1     INTEGER NOT NULL,
    y1     INTEGER NOT NULL,
    z1     INTEGER NOT NULL,
    at     INTEGER NOT NULL
);
