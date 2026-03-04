export type EquipmentType = 'MOVU' | 'KUKA' | 'CONVEYOR' | 'WRAPPER';

export interface WorkflowStep {
    stepId: string;
    equipmentType: EquipmentType;
    action: string;
    parameters: Record<string, any>;
    nextStepId?: string;
}

export interface WorkflowDefinition {
    id?: string;
    name: string;
    steps: WorkflowStep[];
    active: boolean;
}

export interface WorkflowInstance {
    id: string;
    definition: WorkflowDefinition;
    orderId?: string;
    currentStepId: string;
    status: 'PLANNED' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED';
    errorDetails?: string;
    createdAt: string;
}
