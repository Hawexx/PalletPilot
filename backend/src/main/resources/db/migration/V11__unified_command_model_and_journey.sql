-- =============================================================
-- V11: Unified Command Model + Orchestration Journey Steps
-- PalletPilot WCS — Hardware Integration Enhancement
-- =============================================================

-- 1. Enhance command_logs with unified command model fields
ALTER TABLE command_logs ADD COLUMN IF NOT EXISTS seq_id BIGINT;
ALTER TABLE command_logs ADD COLUMN IF NOT EXISTS command_type VARCHAR(50);
ALTER TABLE command_logs ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);
ALTER TABLE command_logs ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(50);
ALTER TABLE command_logs ADD COLUMN IF NOT EXISTS ack_at TIMESTAMPTZ;
ALTER TABLE command_logs ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
ALTER TABLE command_logs ADD COLUMN IF NOT EXISTS error_code INTEGER;
ALTER TABLE command_logs ADD COLUMN IF NOT EXISTS error_text VARCHAR(512);

-- Unique index on idempotency key to prevent duplicate execution
CREATE UNIQUE INDEX IF NOT EXISTS idx_cmd_idempotency
    ON command_logs(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Index for correlation ID lookups
CREATE INDEX IF NOT EXISTS idx_cmd_correlation
    ON command_logs(correlation_id)
    WHERE correlation_id IS NOT NULL;

-- 2. Orchestration journey steps table
CREATE TABLE IF NOT EXISTS journey_steps (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journey_id      UUID NOT NULL,
    step_index      INTEGER NOT NULL,
    command_type    VARCHAR(50) NOT NULL,
    equipment_id    UUID,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    command_id      VARCHAR(255),
    compensation_action VARCHAR(50),
    parameters      TEXT,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    error_details   TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT fk_journey_step_journey
        FOREIGN KEY (journey_id) REFERENCES workflow_instances(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_journey_step_journey
    ON journey_steps(journey_id, step_index);

CREATE INDEX IF NOT EXISTS idx_journey_step_status
    ON journey_steps(status)
    WHERE status IN ('PENDING', 'RUNNING');

-- 3. Enhance workflow_instances for orchestration
-- CRITICAL: V4 declared workflow_definition_id as NOT NULL,
-- but orchestration journeys use template-based steps without a definition FK.
-- Drop NOT NULL to allow null definition for journey-type instances.
ALTER TABLE workflow_instances ALTER COLUMN workflow_definition_id DROP NOT NULL;

-- Add columns that were previously managed by Hibernate ddl-auto=update
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS last_command_id VARCHAR(255);
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS pallet_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS journey_type VARCHAR(30);

CREATE INDEX IF NOT EXISTS idx_workflow_pallet
    ON workflow_instances(pallet_id)
    WHERE pallet_id IS NOT NULL;

