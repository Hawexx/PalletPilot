CREATE TABLE storage_locations (
    id UUID PRIMARY KEY,
    zone VARCHAR(255) NOT NULL,
    aisle INTEGER,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    is_occupied BOOLEAN NOT NULL,
    stored_pallet_id UUID,
    max_height_mm INTEGER,
    max_weight_kg DOUBLE PRECISION
);

CREATE TABLE robots (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    battery_level INTEGER NOT NULL,
    state VARCHAR(50) NOT NULL,
    current_task_id UUID,
    assigned_charging_station_id UUID
);

CREATE TABLE charging_stations (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    is_occupied BOOLEAN NOT NULL,
    occupied_by_robot_id UUID
);

CREATE TABLE transport_tasks (
    id UUID PRIMARY KEY,
    pallet_id UUID NOT NULL,
    source_location_id UUID,
    target_location_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    assigned_robot_id UUID,
    created_at TIMESTAMP
);

-- Update Pallets table
-- current_location already exists in V1
ALTER TABLE pallets ADD COLUMN storage_location_id UUID;
ALTER TABLE pallets ADD COLUMN held_by_robot_id UUID;
