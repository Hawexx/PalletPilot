'use client';

import { useState, useEffect } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { AlertCircle, Play, Pause, Square, Plus, Trash2, Settings, Activity } from 'lucide-react';
import { WorkflowDefinition, WorkflowInstance, WorkflowStep } from '@/types/workflow';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

export default function OrchestrationPage() {
    const [workflows, setWorkflows] = useState<WorkflowDefinition[]>([]);
    const [instances, setInstances] = useState<WorkflowInstance[]>([]);
    const [selectedWorkflow, setSelectedWorkflow] = useState<WorkflowDefinition | null>(null);

    const fetchWorkflows = async () => {
        try {
            const res = await fetch(`${API_BASE}/workflows/definitions`);
            if (res.ok) setWorkflows(await res.json());
        } catch (e) { console.error('Failed to fetch workflows:', e); }
    };

    const fetchInstances = async () => {
        try {
            const res = await fetch(`${API_BASE}/workflows/instances`);
            if (res.ok) setInstances(await res.json());
        } catch (e) { console.error('Failed to fetch instances:', e); }
    };

    useEffect(() => {
        fetchWorkflows();
        fetchInstances();
        const interval = setInterval(() => { fetchWorkflows(); fetchInstances(); }, 5000);
        return () => clearInterval(interval);
    }, []);

    return (
        <div className="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-900 dark:to-slate-800 p-6">
            {/* Header */}
            <div className="mb-8">
                <div className="flex items-center justify-between">
                    <div>
                        <h1 className="text-4xl font-bold bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent">
                            Workflow Orchestration
                        </h1>
                        <p className="text-slate-600 dark:text-slate-400 mt-2">
                            Design, execute, and monitor automated warehouse workflows
                        </p>
                    </div>
                    <Button
                        size="lg"
                        className="bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700"
                    >
                        <Plus className="mr-2 h-5 w-5" />
                        New Workflow
                    </Button>
                </div>
            </div>

            <Tabs defaultValue="designer" className="space-y-6">
                <TabsList className="grid w-full max-w-md grid-cols-3">
                    <TabsTrigger value="designer">Flow Designer</TabsTrigger>
                    <TabsTrigger value="monitor">Live Monitor</TabsTrigger>
                    <TabsTrigger value="library">Templates</TabsTrigger>
                </TabsList>

                {/* Flow Designer Tab */}
                <TabsContent value="designer" className="space-y-6">
                    <Card className="p-6 border-2 border-dashed border-slate-300">
                        <WorkflowDesigner />
                    </Card>
                </TabsContent>

                {/* Live Monitor Tab */}
                <TabsContent value="monitor" className="space-y-6">
                    <LiveMonitor instances={instances} />
                </TabsContent>

                {/* Templates Library Tab */}
                <TabsContent value="library" className="space-y-6">
                    <TemplateLibrary workflows={workflows} />
                </TabsContent>
            </Tabs>
        </div>
    );
}

function WorkflowDesigner() {
    const [steps, setSteps] = useState<WorkflowStep[]>([]);
    const [workflowName, setWorkflowName] = useState('');

    const addStep = () => {
        const newStep: WorkflowStep = {
            stepId: `step_${steps.length + 1}`,
            equipmentType: 'MOVU',
            action: 'PICK',
            parameters: {},
            nextStepId: undefined,
        };
        setSteps([...steps, newStep]);
    };

    const saveWorkflow = async () => {
        try {
            const response = await fetch(`${API_BASE}/workflows/definitions`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    name: workflowName || 'Untitled Workflow',
                    steps,
                    active: true,
                }),
            });

            if (response.ok) {
                alert('Workflow saved successfully!');
            }
        } catch (error) {
            console.error('Failed to save workflow', error);
        }
    };

    return (
        <div className="space-y-6">
            <div className="flex items-center space-x-4">
                <input
                    type="text"
                    placeholder="Workflow Name"
                    value={workflowName}
                    onChange={(e) => setWorkflowName(e.target.value)}
                    className="flex-1 px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500"
                />
                <Button onClick={saveWorkflow} className="bg-green-600 hover:bg-green-700">
                    Save Workflow
                </Button>
            </div>

            <div className="space-y-4">
                {steps.map((step, index) => (
                    <Card key={step.stepId} className="p-4 hover:shadow-lg transition-shadow">
                        <div className="flex items-center justify-between">
                            <div className="flex items-center space-x-4">
                                <div className="w-10 h-10 rounded-full bg-blue-100 dark:bg-blue-900 flex items-center justify-center font-bold text-blue-600 dark:text-blue-300">
                                    {index + 1}
                                </div>
                                <div>
                                    <p className="font-semibold">{step.action} on {step.equipmentType}</p>
                                    <p className="text-sm text-slate-500">Step ID: {step.stepId}</p>
                                </div>
                            </div>
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setSteps(steps.filter((_, i) => i !== index))}
                            >
                                <Trash2 className="h-4 w-4 text-red-500" />
                            </Button>
                        </div>
                    </Card>
                ))}
            </div>

            <Button onClick={addStep} variant="outline" className="w-full border-dashed border-2">
                <Plus className="mr-2 h-4 w-4" />
                Add Step
            </Button>
        </div>
    );
}

function LiveMonitor({ instances }: { instances: WorkflowInstance[] }) {
    return (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {instances.length === 0 ? (
                <Card className="col-span-full p-12 text-center">
                    <Activity className="h-12 w-12 mx-auto text-slate-400 mb-4" />
                    <p className="text-slate-500">No active workflows</p>
                </Card>
            ) : (
                instances.map((instance) => (
                    <Card key={instance.id} className="p-6 hover:shadow-xl transition-all">
                        <div className="flex items-center justify-between mb-4">
                            <h3 className="font-semibold">{instance.definition?.name || 'Workflow'}</h3>
                            <Badge variant={instance.status === 'RUNNING' ? 'default' : 'secondary'}>
                                {instance.status}
                            </Badge>
                        </div>
                        <div className="space-y-2 text-sm">
                            <p className="text-slate-600">Current Step: {instance.currentStepId || 'N/A'}</p>
                            <div className="flex space-x-2 mt-4">
                                <Button size="sm" variant="outline">
                                    <Play className="h-3 w-3" />
                                </Button>
                                <Button size="sm" variant="outline">
                                    <Pause className="h-3 w-3" />
                                </Button>
                                <Button size="sm" variant="outline">
                                    <Square className="h-3 w-3" />
                                </Button>
                            </div>
                        </div>
                    </Card>
                ))
            )}
        </div>
    );
}

function TemplateLibrary({ workflows }: { workflows: WorkflowDefinition[] }) {
    return (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            <Card className="p-6 border-2 border-dashed border-blue-300 hover:border-blue-500 cursor-pointer transition-all">
                <Plus className="h-8 w-8 mx-auto text-blue-500 mb-2" />
                <p className="text-center text-sm font-medium">Create New Template</p>
            </Card>

            {workflows.map((workflow) => (
                <Card key={workflow.id} className="p-6 hover:shadow-xl transition-all cursor-pointer">
                    <h3 className="font-semibold mb-2">{workflow.name}</h3>
                    <p className="text-sm text-slate-500 mb-4">{workflow.steps.length} steps</p>
                    <Badge variant={workflow.active ? 'default' : 'secondary'}>
                        {workflow.active ? 'Active' : 'Inactive'}
                    </Badge>
                </Card>
            ))}
        </div>
    );
}
