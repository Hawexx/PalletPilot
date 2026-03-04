-- V2__add_cog_and_state_metrics.sql

-- Add Center of Gravity and detailed metrics to Pallets
ALTER TABLE pallets 
ADD COLUMN cog_offset_x_mm DOUBLE PRECISION DEFAULT 0.0,
ADD COLUMN cog_offset_y_mm DOUBLE PRECISION DEFAULT 0.0,
ADD COLUMN total_weight_kg DOUBLE PRECISION DEFAULT 0.0;

-- Add metrics to Pallet Layers
ALTER TABLE pallet_layers
ADD COLUMN layer_weight_kg DOUBLE PRECISION DEFAULT 0.0,
ADD COLUMN layer_center_x_mm DOUBLE PRECISION DEFAULT 0.0,
ADD COLUMN layer_center_y_mm DOUBLE PRECISION DEFAULT 0.0;

-- Add Equipment State Machine Tracking
ALTER TABLE equipment
ADD COLUMN current_state VARCHAR(50) DEFAULT 'OFFLINE',
ADD COLUMN simulation_mode BOOLEAN DEFAULT FALSE;

-- Create Command Audit Log for Equipment
CREATE TABLE command_audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    equipment_id UUID REFERENCES equipment(id),
    command_type VARCHAR(100) NOT NULL,
    payload TEXT,
    sent_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) -- SENT, ACKNOWLEDGED, COMPLETED, FAILED
);

-- Warehouse Flow Enforcment Table
CREATE TABLE warehouse_flow_config (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_state VARCHAR(50) NOT NULL,
    target_state VARCHAR(50) NOT NULL,
    required_equipment_type VARCHAR(50),
    allow_simulation BOOLEAN DEFAULT TRUE,
    sequence_order INTEGER NOT NULL
);

-- Seed initial Warehouse Flow
INSERT INTO warehouse_flow_config 
(source_state, target_state, required_equipment_type, sequence_order) VALUES
('CREATED', 'PLANNED', NULL, 1),
('PLANNED', 'PICKING', 'MOVU', 2),
('PICKING', 'DEPALLETIZING', 'CONVEYOR', 3),
('DEPALLETIZING', 'REPALLETIZING', 'KUKA', 4),
('REPALLETIZING', 'WRAPPING', 'CONVEYOR', 5),
('WRAPPING', 'LOADING', 'MOVU', 6),
('LOADING', 'COMPLETED', NULL, 7);
