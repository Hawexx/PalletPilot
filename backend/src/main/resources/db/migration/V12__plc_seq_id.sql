-- =============================================================
-- V12: PLC SeqId Tracking + Command Queue Support
-- PalletPilot WCS — PLC/TwinCAT ICD v1.0 Completion
-- =============================================================

-- 1. Per-equipment PLC sequence ID tracker
-- Guarantees monotonically increasing SeqId across JVM restarts.
-- Row-locked (SELECT FOR UPDATE) during nextSeqId() for multi-pod safety.
CREATE TABLE IF NOT EXISTS equipment_plc_seq (
    equipment_id UUID PRIMARY KEY,
    last_seq_id  BIGINT NOT NULL DEFAULT 1,
    updated_at   TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Add queued_at timestamp if commands need queue-based execution
ALTER TABLE command_logs ADD COLUMN IF NOT EXISTS queued_at TIMESTAMPTZ;

-- Index for the PlcCommandWorker to efficiently pick QUEUED commands
CREATE INDEX IF NOT EXISTS idx_cmd_queued
    ON command_logs(status, queued_at)
    WHERE status = 'QUEUED';
