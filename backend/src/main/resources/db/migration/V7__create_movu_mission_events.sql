-- V7__create_movu_mission_events.sql
-- Append-only event log for Movu aSTC webhook notifications.
-- notification_id UNIQUE provides idempotency for duplicate webhook deliveries.

CREATE TABLE IF NOT EXISTS movu_mission_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    notification_id VARCHAR(100) NOT NULL UNIQUE,
    mission_id VARCHAR(64) NOT NULL,
    notification_type VARCHAR(100) NOT NULL,
    vehicle_id VARCHAR(20),
    load_id VARCHAR(100),
    payload_json TEXT,
    event_timestamp TIMESTAMP WITH TIME ZONE,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mme_mission_id ON movu_mission_events(mission_id);
CREATE INDEX IF NOT EXISTS idx_mme_notification_type ON movu_mission_events(notification_type);
