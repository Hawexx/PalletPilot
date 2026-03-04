"use client";

import React, { useCallback, useRef, useState, useEffect } from 'react';
import {
    ReactFlow,
    ReactFlowProvider,
    addEdge,
    useNodesState,
    useEdgesState,
    Controls,
    Background,
    MiniMap,
    Connection,
    Edge,
    Node,
    ReactFlowInstance,
    Panel,
} from '@xyflow/react';

import '@xyflow/react/dist/style.css';
import Sidebar from './Sidebar';
import { Button } from '@/components/ui/button';
import { toast } from 'sonner';

interface WorkflowStep {
    stepId: string;
    equipmentType: string;
    action: string;
    parameters: Record<string, any>;
    nextStepId?: string;
}

const initialNodes: Node[] = [];
const initialEdges: Edge[] = [];

let id = 0;
const getId = () => `dndnode_${id++}`;

function WorkflowEditorContent() {
    const reactFlowWrapper = useRef<HTMLDivElement>(null);
    const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
    const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
    const [reactFlowInstance, setReactFlowInstance] = useState<ReactFlowInstance | null>(null);

    const onConnect = useCallback(
        (params: Connection) => setEdges((eds) => addEdge(params, eds)),
        [setEdges],
    );

    const onDragOver = useCallback((event: React.DragEvent) => {
        event.preventDefault();
        event.dataTransfer.dropEffect = 'move';
    }, []);

    const onDrop = useCallback(
        (event: React.DragEvent) => {
            event.preventDefault();

            if (!reactFlowWrapper.current || !reactFlowInstance) {
                return;
            }

            const type = event.dataTransfer.getData('application/reactflow');
            const label = event.dataTransfer.getData('application/reactflow-label');

            if (typeof type === 'undefined' || !type) {
                return;
            }

            const position = reactFlowInstance.screenToFlowPosition({
                x: event.clientX,
                y: event.clientY,
            });

            const newNode: Node = {
                id: getId(),
                type: 'default', // Using default for now, can be custom
                position,
                data: { label: `${label}` },
                style: {
                    border: '1px solid #777',
                    padding: 10,
                    borderRadius: 5,
                    background: 'white',
                    width: 150,
                }
            };

            setNodes((nds) => nds.concat(newNode));
        },
        [reactFlowInstance, setNodes],
    );

    const onSave = useCallback(async () => {
        if (reactFlowInstance) {
            const flow = reactFlowInstance.toObject();
            const nodes = flow.nodes;
            const edges = flow.edges;

            const steps: WorkflowStep[] = nodes.map(node => {
                // Find edge starting from this node
                const outboundEdge = edges.find(edge => edge.source === node.id);

                let eqType = 'CUSTOM';
                const label = node.data.label as string;

                if (label.includes('Conveyor')) eqType = 'CONVEYOR';
                else if (label.includes('Robot')) eqType = 'MOVU'; // Defaulting to Movu for 'Robot'
                else if (label.includes('Wrapper')) eqType = 'CYKLOP_WRAPPER';
                else if (label.includes('Palletizer')) eqType = 'KUKA';

                return {
                    stepId: node.id,
                    equipmentType: eqType,
                    action: 'MOVE', // Default action, needs refinement in node details
                    parameters: { x: node.position.x, y: node.position.y },
                    nextStepId: outboundEdge ? outboundEdge.target : undefined
                };
            });

            const workflowDefinition = {
                name: `Flow-${Date.now()}`,
                active: true,
                steps: steps
            };

            console.log('Validating Workflow:', workflowDefinition);

            try {
                const response = await fetch('/api/workflow/definitions', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(workflowDefinition)
                });

                if (response.ok) {
                    toast.success("Workflow saved successfully!");
                } else {
                    toast.error("Failed to save workflow");
                    console.error("Save error", await response.json());
                }
            } catch (error) {
                console.error("Network error", error);
                toast.error("Network error saving workflow");
            }
        }
    }, [reactFlowInstance]);

    return (
        <div className="flex h-screen w-full">
            <Sidebar />
            <div className="flex-grow h-full relative" ref={reactFlowWrapper}>
                <ReactFlow
                    nodes={nodes}
                    edges={edges}
                    onNodesChange={onNodesChange}
                    onEdgesChange={onEdgesChange}
                    onConnect={onConnect}
                    onInit={setReactFlowInstance}
                    onDrop={onDrop}
                    onDragOver={onDragOver}
                    fitView
                >
                    <Controls />
                    <MiniMap />
                    <Background gap={12} size={1} />
                    <Panel position="top-right" className="bg-white dark:bg-gray-800 p-2 rounded shadow-sm border border-gray-200 dark:border-gray-700">
                        <Button onClick={onSave} size="sm">Save Workflow</Button>
                    </Panel>
                </ReactFlow>
            </div>
        </div>
    );
}

export default function WorkflowEditor() {
    return (
        <ReactFlowProvider>
            <WorkflowEditorContent />
        </ReactFlowProvider>
    );
}
