-- V1__init_schema.sql

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Orders Table
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    wms_order_id VARCHAR(255) UNIQUE NOT NULL,
    status VARCHAR(50) NOT NULL, -- CREATED, PLANNED, COMPLETED, etc.
    priority INTEGER DEFAULT 5,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Order Items Table
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID REFERENCES orders(id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL,
    width_mm INTEGER NOT NULL,
    length_mm INTEGER NOT NULL,
    height_mm INTEGER NOT NULL,
    weight_kg DOUBLE PRECISION NOT NULL,
    quantity INTEGER NOT NULL
);

-- 3. Pallets Table
CREATE TABLE pallets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID REFERENCES orders(id),
    type VARCHAR(50) NOT NULL DEFAULT 'EURO',
    process_state VARCHAR(50) NOT NULL, -- CREATED, PLANNED, PICKING, etc.
    stability_score DOUBLE PRECISION,
    current_location VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Pallet Layers Table
CREATE TABLE pallet_layers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    pallet_id UUID REFERENCES pallets(id) ON DELETE CASCADE,
    layer_index INTEGER NOT NULL, -- 0 is bottom
    z_height_mm INTEGER NOT NULL
);

-- 5. Boxes Table (Individual boxes on a pallet)
CREATE TABLE boxes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    pallet_layer_id UUID REFERENCES pallet_layers(id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL,
    x_pos_mm INTEGER NOT NULL,
    y_pos_mm INTEGER NOT NULL,
    rotation_degrees INTEGER DEFAULT 0
);

-- 6. Equipment Table
CREATE TABLE equipment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) UNIQUE NOT NULL,
    type VARCHAR(50) NOT NULL, -- MOVU, KUKA, CONVEYOR, WRAPPER
    ip_address VARCHAR(50),
    port INTEGER,
    protocol VARCHAR(50), -- REST, ADS, TCP
    status VARCHAR(50), -- IDLE, BUSY, ERROR, OFFLINE
    last_heartbeat TIMESTAMP WITH TIME ZONE
);

-- 7. Alerts Table
CREATE TABLE alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    equipment_id UUID REFERENCES equipment(id),
    message TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL, -- INFO, WARNING, CRITICAL
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved BOOLEAN DEFAULT FALSE
);
