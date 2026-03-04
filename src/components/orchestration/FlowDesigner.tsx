'use client';

import React, { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Plus, Save, Trash2, ArrowRight } from 'lucide-react';
import { WorkflowDefinition, WorkflowStep, EquipmentType } from '@/types/workflow';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

const DEFAULT_STEPS: WorkflowStep[] = [
    { stepId: 'step-1', equipmentType: 'MOVU', action: 'PICK_PALLET', parameters: { location: 'A-01' }, nextStepId: 'step-2' }
];

export default function FlowDesigner() {
    const [flowName, setFlowName] = useState('New Workflow');
    const [steps, setSteps] = useState<WorkflowStep[]>(DEFAULT_STEPS);

    const addStep = () => {
        const newId = `step-${steps.length + 1}`;
        // Link previous step to this one
        if (steps.length > 0) {
            steps[steps.length - 1].nextStepId = newId;
        }

        setSteps([...steps, {
            stepId: newId,
            equipmentType: 'CONVEYOR',
            action: 'MOVE',
            parameters: {},
            nextStepId: undefined
        }]);
    };

    const updateStep = (index: number, field: keyof WorkflowStep, value: any) => {
        const newSteps = [...steps];
        (newSteps[index] as any)[field] = value;
        setSteps(newSteps);
    };

    const saveWorkflow = async () => {
        const definition: WorkflowDefinition = {
            name: flowName,
            steps: steps,
            active: true
        };

        try {
            const res = await fetch(`${API_BASE}/workflows/definitions`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(definition)
            });
            if (res.ok) alert('Workflow Saved!');
            else alert('Failed to save');
        } catch (e) {
            alert('Error saving workflow');
        }
    };

    return (
        <Card className="bg-neutral-900 border-neutral-800 text-neutral-100">
            <CardHeader>
                <CardTitle className="text-xl">Workflow Designer</CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
                <div className="flex gap-4">
                    <Input
                        value={flowName}
                        onChange={(e) => setFlowName(e.target.value)}
                        className="bg-neutral-800 border-neutral-700"
                        placeholder="Workflow Name"
                    />
                    <Button onClick={saveWorkflow}><Save className="mr-2 h-4 w-4" /> Save Template</Button>
                </div>

                <div className="space-y-4">
                    {steps.map((step, idx) => (
                        <div key={step.stepId} className="flex items-center gap-4 p-4 bg-neutral-800 rounded-lg border border-neutral-700">
                            <div className="w-8 h-8 flex items-center justify-center bg-blue-600 rounded-full font-bold">
                                {idx + 1}
                            </div>

                            <div className="grid grid-cols-3 gap-4 flex-1">
                                <Select
                                    value={step.equipmentType}
                                    onValueChange={(v) => updateStep(idx, 'equipmentType', v)}
                                >
                                    <SelectTrigger className="bg-neutral-700">
                                        <SelectValue placeholder="Equipment" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="MOVU">Movu Atlas</SelectItem>
                                        <SelectItem value="KUKA">Kuka Robot</SelectItem>
                                        <SelectItem value="CONVEYOR">Conveyor</SelectItem>
                                        <SelectItem value="WRAPPER">Cyklop Wrapper</SelectItem>
                                    </SelectContent>
                                </Select>

                                <Input
                                    value={step.action}
                                    onChange={(e) => updateStep(idx, 'action', e.target.value)}
                                    placeholder="Action (e.g. PICK)"
                                    className="bg-neutral-700"
                                />

                                <Input
                                    value={JSON.stringify(step.parameters)}
                                    onChange={(e) => {
                                        try {
                                            const p = JSON.parse(e.target.value);
                                            updateStep(idx, 'parameters', p);
                                        } catch (err) {
                                            // ignore partial json
                                        }
                                    }}
                                    placeholder='Params {"speed": 100}'
                                    className="bg-neutral-700 font-mono text-sm"
                                />
                            </div>

                            {idx < steps.length - 1 && <ArrowRight className="text-neutral-500" />}
                        </div>
                    ))}
                </div>

                <Button variant="outline" onClick={addStep} className="w-full border-dashed">
                    <Plus className="mr-2 h-4 w-4" /> Add Step
                </Button>
            </CardContent>
        </Card>
    );
}
