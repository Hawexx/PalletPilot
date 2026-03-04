
export type NodeType =
    | 'CONVEYOR'
    | 'MOVU'
    | 'KUKA'
    | 'RACK'
    | 'WRAPPER'
    | 'SCANNER'
    | 'TURNTABLE'
    | 'ZONE'
    | 'PALLET_BUFFER'
    | 'TRANSFER_CAR';

export type FlowDirection = 'LEFT' | 'RIGHT' | 'UP' | 'DOWN' | 'BIDIRECTIONAL';
export type ZoneType = 'INBOUND' | 'OUTBOUND' | 'STAGING' | 'REJECT';
export type PalletType = 'EURO' | 'IND' | 'BOTH';

export interface NodeConfig {
    // Conveyor config
    speed?: number;              // m/s
    // Zone config
    throughput?: number;         // pal/h
    zoneType?: ZoneType;
    // Robot config
    trigger?: string;
    // Equipment config
    ip?: string;
    port?: number;
    // Rack config
    laneCount?: number;
    levels?: number;
    // Pallet buffer config
    palletType?: PalletType;
    capacity?: number;
    // Turntable config
    rotationSpeed?: number;      // degrees/sec
}

export interface NodeData {
    id: string;
    type: NodeType;
    x: number;
    y: number;
    width?: number;
    height?: number;
    label?: string;
    rotation?: number;              // 0, 90, 180, 270 degrees
    flowDirection?: FlowDirection;  // For conveyors
    config?: NodeConfig;
}

export interface EdgeData {
    id: string;
    source: string;
    target: string;
    label?: string;
}

export interface LayoutData {
    nodes: NodeData[];
    edges: EdgeData[];
}
