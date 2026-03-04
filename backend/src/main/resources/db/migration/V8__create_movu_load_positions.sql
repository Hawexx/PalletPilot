-- V8__create_movu_load_positions.sql
-- Tracks last known physical position of loads in the Movu warehouse.
-- MissionDropped actual distance is stored here and reused for subsequent retrievals.

CREATE TABLE IF NOT EXISTS movu_load_positions (
    load_id VARCHAR(100) PRIMARY KEY,
    level INTEGER NOT NULL,
    lane INTEGER NOT NULL,
    section INTEGER NOT NULL,
    distance_mm INTEGER NOT NULL,
    last_mission_id VARCHAR(64),
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
