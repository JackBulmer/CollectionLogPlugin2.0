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

-- Container Item Tracking (for naturally generated containers)
CREATE TABLE IF NOT EXISTS container_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    world VARCHAR(64) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    slot INT NOT NULL,
    item_type VARCHAR(64) NOT NULL,
    item_amount INT NOT NULL,
    placer_uuid CHAR(36) NOT NULL,
    placed_at BIGINT NOT NULL,
    UNIQUE(world, x, y, z, slot)
);

CREATE INDEX IF NOT EXISTS idx_container_location ON container_items(world, x, y, z);
CREATE INDEX IF NOT EXISTS idx_container_placer ON container_items(placer_uuid);

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

-- Player collection progress tracking
CREATE TABLE IF NOT EXISTS player_collections (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid CHAR(36) NOT NULL,
    collection_id VARCHAR(128) NOT NULL,
    total_items INT NOT NULL,
    collected_items INT NOT NULL,
    completion_date BIGINT,
    last_updated BIGINT NOT NULL,
    UNIQUE(player_uuid, collection_id)
);

CREATE INDEX IF NOT EXISTS idx_player_collections_uuid ON player_collections(player_uuid);
CREATE INDEX IF NOT EXISTS idx_player_collections_id ON player_collections(collection_id);
CREATE INDEX IF NOT EXISTS idx_player_collections_completed ON player_collections(player_uuid, completion_date);

-- Individual collected items (for tracking which specific items player has)
CREATE TABLE IF NOT EXISTS collected_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid CHAR(36) NOT NULL,
    collection_id VARCHAR(128) NOT NULL,
    item_signature VARCHAR(256) NOT NULL,
    first_collected BIGINT NOT NULL,
    UNIQUE(player_uuid, collection_id, item_signature)
);

CREATE INDEX IF NOT EXISTS idx_collected_items_player ON collected_items(player_uuid);
CREATE INDEX IF NOT EXISTS idx_collected_items_collection ON collected_items(player_uuid, collection_id);

-- Collection completion tracking (for announcements, stats, leaderboards)
CREATE TABLE IF NOT EXISTS collection_completions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid CHAR(36) NOT NULL,
    collection_id VARCHAR(128) NOT NULL,
    completed_at BIGINT NOT NULL,
    completion_order INT NOT NULL,
    UNIQUE(player_uuid, collection_id)
);

CREATE INDEX IF NOT EXISTS idx_completions_player ON collection_completions(player_uuid);
CREATE INDEX IF NOT EXISTS idx_completions_time ON collection_completions(completed_at);
CREATE INDEX IF NOT EXISTS idx_completions_order ON collection_completions(completion_order);

-- Milestone perks unlocked by players
CREATE TABLE IF NOT EXISTS player_perks (
    player_uuid CHAR(36) NOT NULL,
    perk_id VARCHAR(64) NOT NULL,
    unlocked_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, perk_id)
);

CREATE INDEX IF NOT EXISTS idx_player_perks_uuid ON player_perks(player_uuid);

-- Milestones that have been completed by players
CREATE TABLE IF NOT EXISTS completed_milestones (
    player_uuid CHAR(36) NOT NULL,
    milestone_name VARCHAR(160) NOT NULL,
    completed_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, milestone_name)
);

CREATE INDEX IF NOT EXISTS idx_completed_milestones_uuid ON completed_milestones(player_uuid);

-- Unlocked player tags/suffixes
CREATE TABLE IF NOT EXISTS player_tags (
    player_uuid CHAR(36) NOT NULL,
    tag_id VARCHAR(64) NOT NULL,
    unlocked_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_player_tags_uuid ON player_tags(player_uuid);

-- Player selected prefix/suffix tags
CREATE TABLE IF NOT EXISTS player_tag_selections (
    player_uuid CHAR(36) PRIMARY KEY,
    prefix TEXT NOT NULL DEFAULT '',
    suffix TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_player_tag_selections_uuid ON player_tag_selections(player_uuid);

-- Perk toggle states (enabled/disabled for unlocked perks)
CREATE TABLE IF NOT EXISTS player_perk_toggles (
    player_uuid CHAR(36) NOT NULL,
    perk_id VARCHAR(64) NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (player_uuid, perk_id)
);

CREATE INDEX IF NOT EXISTS idx_perk_toggles_uuid ON player_perk_toggles(player_uuid);

-- Larger Ender Chest: Extra Row Storage
CREATE TABLE IF NOT EXISTS ender_chest_extra (
    player_uuid CHAR(36) NOT NULL,
    slot INT NOT NULL,
    item_type VARCHAR(64) NOT NULL,
    item_amount INT NOT NULL,
    item_data TEXT,
    PRIMARY KEY (player_uuid, slot)
);

CREATE INDEX IF NOT EXISTS idx_ender_extra_player ON ender_chest_extra(player_uuid);

-- Larger Ender Chest: Condensing Mode Tracking
CREATE TABLE IF NOT EXISTS ender_chest_condensing (
    player_uuid CHAR(36) PRIMARY KEY,
    marked_at BIGINT NOT NULL
);

-- Player glowing preferences (Glowing perk)
CREATE TABLE IF NOT EXISTS player_glowing (
    player_uuid VARCHAR(36) PRIMARY KEY,
    enabled INTEGER NOT NULL DEFAULT 0,
    color VARCHAR(16) NOT NULL DEFAULT 'WHITE'
);

-- Player teleport settings
CREATE TABLE IF NOT EXISTS player_teleport_settings (
    player_uuid CHAR(36) PRIMARY KEY,
    max_homes INT NOT NULL DEFAULT 5,
    tp_cost INT NOT NULL DEFAULT 5,
    inter_dimensional INTEGER NOT NULL DEFAULT 1,
    countdown_seconds INT NOT NULL DEFAULT 5
);
