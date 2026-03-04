-- V2__create_workflow_tables.sql

-- 1. Workflow Definitions Table
CREATE TABLE workflow_definitions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) UNIQUE NOT NULL,
    steps TEXT NOT NULL, -- JSON Content
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Workflow Instances Table
CREATE TABLE workflow_instances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_definition_id UUID NOT NULL REFERENCES workflow_definitions(id),
    order_id UUID, -- Can refer to orders(id), strictly optional
    current_step_id VARCHAR(100),
    status VARCHAR(50) NOT NULL, -- RUNNING, PAUSED, COMPLETED, FAILED
    error_details TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for finding active instances quickly
CREATE INDEX idx_workflow_instances_status ON workflow_instances(status);
