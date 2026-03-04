-- V10__create_remaining_tables.sql
-- Creates all tables that were previously managed by Hibernate ddl-auto=update.
-- This migration ensures these tables exist with proper constraints and indexes
-- for production deployment where ddl-auto should be set to 'validate'.
--
-- Tables covered:
--   erp_inbound_messages, erp_outbox_messages, erp_configs, erp_integration_logs,
--   movu_pending_confirmations, movu_lane_reservations, movu_tasks,
--   sku_analytics, zone_definitions

-- =============================================
-- ERP: Inbound Messages (raw ERP events)
-- =============================================
CREATE TABLE IF NOT EXISTS erp_inbound_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id VARCHAR(128) NOT NULL UNIQUE,
    erp_order_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    payload_json JSONB NOT NULL,
    payload_hash VARCHAR(64),
    correlation_id VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    error_message TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_erp_inbound_status ON erp_inbound_messages(status, next_retry_at);

-- =============================================
-- ERP: Outbox Messages (outbound confirmations)
-- =============================================
CREATE TABLE IF NOT EXISTS erp_outbox_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    erp_order_id VARCHAR(128) NOT NULL,
    wcs_order_id UUID NOT NULL,
    message_type VARCHAR(30) NOT NULL,
    target_url VARCHAR(512) NOT NULL,
    request_json JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error TEXT,
    response_json JSONB,
    http_status INTEGER,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_outbox_status_next ON erp_outbox_messages(status, next_attempt_at);

-- =============================================
-- ERP: Configuration (per-warehouse ERP settings)
-- =============================================
CREATE TABLE IF NOT EXISTS erp_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(100),
    integration_mode VARCHAR(20) NOT NULL DEFAULT 'POLLING',
    poll_url VARCHAR(512),
    poll_interval_seconds INTEGER DEFAULT 60,
    api_key_encrypted VARCHAR(512),
    webhook_secret_encrypted VARCHAR(512),
    ip_allowlist TEXT,
    rate_limit_per_minute INTEGER DEFAULT 60,
    callback_url VARCHAR(512),
    callback_api_key_encrypted VARCHAR(512),
    auto_confirm BOOLEAN DEFAULT false,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

-- =============================================
-- ERP: Integration Log (audit trail)
-- =============================================
CREATE TABLE IF NOT EXISTS erp_integration_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    direction VARCHAR(10) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    erp_order_id VARCHAR(128),
    wcs_order_id UUID,
    message_id VARCHAR(128),
    status VARCHAR(20) NOT NULL,
    request_payload JSONB,
    response_payload JSONB,
    error_message TEXT,
    http_status INTEGER,
    duration_ms BIGINT,
    correlation_id VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_erp_log_created ON erp_integration_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_erp_log_erp_order ON erp_integration_logs(erp_order_id);

-- =============================================
-- Movu: Pending Confirmations (late decision queue)
-- =============================================
CREATE TABLE IF NOT EXISTS movu_pending_confirmations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    equipment_id UUID NOT NULL,
    mission_id VARCHAR(64) NOT NULL,
    confirmation_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMPTZ,
    attempt_count INTEGER DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    reserved_distance_mm INTEGER,
    lane INTEGER,
    level INTEGER,
    section INTEGER,
    error_message TEXT,
    CONSTRAINT uk_pending_mission_type UNIQUE (mission_id, confirmation_type)
);

-- =============================================
-- Movu: Lane Reservations (collision prevention)
-- =============================================
CREATE TABLE IF NOT EXISTS movu_lane_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    equipment_id UUID NOT NULL,
    level INTEGER NOT NULL,
    lane INTEGER NOT NULL,
    section INTEGER NOT NULL,
    distance_mm INTEGER NOT NULL,
    mission_id VARCHAR(64) NOT NULL,
    confirmation_type VARCHAR(20),
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_lane_reservation UNIQUE (equipment_id, level, lane, section, distance_mm)
);
CREATE INDEX IF NOT EXISTS idx_lane_res_expires ON movu_lane_reservations(expires_at);

-- =============================================
-- Movu: Tasks (dock/undock for shuttle roaming)
-- =============================================
CREATE TABLE IF NOT EXISTS movu_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    equipment_id UUID NOT NULL,
    task_id VARCHAR(64) UNIQUE,
    vehicle_id INTEGER NOT NULL,
    task_type VARCHAR(10) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'NOT_ASSIGNED',
    source_level INTEGER,
    target_level INTEGER,
    lift_section INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    last_event_at TIMESTAMPTZ
);

-- =============================================
-- Slotting: SKU Analytics (demand classification)
-- =============================================
CREATE TABLE IF NOT EXISTS sku_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(128) NOT NULL UNIQUE,
    total_orders INTEGER NOT NULL DEFAULT 0,
    total_quantity INTEGER NOT NULL DEFAULT 0,
    avg_daily_quantity DOUBLE PRECISION NOT NULL DEFAULT 0,
    zone_class VARCHAR(1) NOT NULL DEFAULT 'C',
    last_calculated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    period_days INTEGER NOT NULL DEFAULT 30,
    classification_mode VARCHAR(32) NOT NULL DEFAULT 'CUMULATIVE_SHARE',
    rank_metric VARCHAR(32) NOT NULL DEFAULT 'TOTAL_QUANTITY'
);
CREATE INDEX IF NOT EXISTS idx_sku_analytics_zone_class ON sku_analytics(zone_class);

-- =============================================
-- Slotting: Zone Definitions (dynamic zone rules)
-- =============================================
CREATE TABLE IF NOT EXISTS zone_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_class VARCHAR(1) NOT NULL,
    priority INTEGER NOT NULL,
    label VARCHAR(100),
    aisle_min INTEGER,
    aisle_max INTEGER,
    x_min INTEGER,
    x_max INTEGER,
    y_min INTEGER,
    y_max INTEGER,
    z_min INTEGER,
    z_max INTEGER,
    zone_name_filter VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT true
);
