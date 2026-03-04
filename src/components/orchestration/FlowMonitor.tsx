'use client';

import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { WorkflowInstance } from '@/types/workflow';

// Mock data for display until connected
const MOCK_INSTANCES: WorkflowInstance[] = [
    {
        id: 'inst-1',
        definition: { name: 'Standard Outbound', steps: [], active: true },
        currentStepId: 'step-2',
        status: 'RUNNING',
        createdAt: new Date().toISOString()
    }
];

export default function FlowMonitor() {
    const [instances, setInstances] = useState<WorkflowInstance[]>(MOCK_INSTANCES);

    // Poll for updates (WebSocket implementation would go here)
    useEffect(() => {
        // placeholder for fetch
    }, []);

    return (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {instances.map(inst => (
                <Card key={inst.id} className="bg-neutral-900 border-neutral-800 text-neutral-100">
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium">
                            {inst.definition.name}
                        </CardTitle>
                        <Badge variant={inst.status === 'RUNNING' ? 'default' : 'secondary'}>
                            {inst.status}
                        </Badge>
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold mb-2">Step: {inst.currentStepId}</div>
                        <p className="text-xs text-neutral-400">
                            ID: {inst.id.substring(0, 8)}
                        </p>
                        {inst.errorDetails && (
                            <div className="mt-2 text-red-400 text-sm bg-red-900/20 p-2 rounded">
                                {inst.errorDetails}
                            </div>
                        )}
                    </CardContent>
                </Card>
            ))}
        </div>
    );
}
