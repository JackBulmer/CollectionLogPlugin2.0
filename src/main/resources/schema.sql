-- Block Ownership Tracking (Anti-Cheat Core)
CREATE TABLE IF NOT EXISTS block_ownership (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    world VARCHAR(64) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    owner_uuid CHAR(36) NOT NULL,
    placed_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_location ON block_ownership(world, x, y, z);
CREATE INDEX IF NOT EXISTS idx_owner ON block_ownership(owner_uuid);
CREATE INDEX IF NOT EXISTS idx_placed_time ON block_ownership(placed_at);

-- Player Data
CREATE TABLE IF NOT EXISTS player_data (
    player_uuid CHAR(36) PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    data_json TEXT NOT NULL,
    last_updated BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_username ON player_data(username);
CREATE INDEX IF NOT EXISTS idx_last_updated ON player_data(last_updated);

-- Player Homes
CREATE TABLE IF NOT EXISTS player_homes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid CHAR(36) NOT NULL,
    home_name VARCHAR(32) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    icon_material VARCHAR(64),
    created_at BIGINT NOT NULL,
    UNIQUE(player_uuid, home_name)
);

CREATE INDEX IF NOT EXISTS idx_player_homes ON player_homes(player_uuid);

-- Player Stats (Leaderboard)
CREATE TABLE IF NOT EXISTS player_stats (
    player_uuid CHAR(36) PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    total_collected INT NOT NULL DEFAULT 0,
    last_updated BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_total_collected ON player_stats(total_collected DESC);
CREATE INDEX IF NOT EXISTS idx_stats_username ON player_stats(username);
