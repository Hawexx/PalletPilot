import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { NodeType } from './types';
import {
    Box, Truck, Cpu, Archive, Package, Scan,
    RotateCw, MapPin, Layers, TrainTrack
} from 'lucide-react';

const TOOLS: { type: NodeType; label: string; icon: any; defaultWidth?: number; defaultHeight?: number }[] = [
    { type: 'CONVEYOR', label: 'Conveyor', icon: Box, defaultWidth: 120, defaultHeight: 60 },
    { type: 'MOVU', label: 'Movu Robot', icon: Truck, defaultWidth: 80, defaultHeight: 80 },
    { type: 'KUKA', label: 'Kuka Arm', icon: Cpu, defaultWidth: 80, defaultHeight: 80 },
    { type: 'RACK', label: 'Rack', icon: Archive, defaultWidth: 100, defaultHeight: 150 },
    { type: 'WRAPPER', label: 'Wrapper', icon: Package, defaultWidth: 80, defaultHeight: 80 },
    { type: 'SCANNER', label: 'Scanner', icon: Scan, defaultWidth: 60, defaultHeight: 60 },
    { type: 'TURNTABLE', label: 'Turntable', icon: RotateCw, defaultWidth: 80, defaultHeight: 80 },
    { type: 'ZONE', label: 'Zone', icon: MapPin, defaultWidth: 120, defaultHeight: 80 },
    { type: 'PALLET_BUFFER', label: 'Pallet Buffer', icon: Layers, defaultWidth: 100, defaultHeight: 100 },
    { type: 'TRANSFER_CAR', label: 'Transfer Car', icon: TrainTrack, defaultWidth: 100, defaultHeight: 60 },
];

export function Palette() {
    const handleDragStart = (e: React.DragEvent, type: NodeType, defaultWidth?: number, defaultHeight?: number) => {
        e.dataTransfer.setData('application/reactflow/type', type);
        e.dataTransfer.setData('application/reactflow/width', String(defaultWidth || 80));
        e.dataTransfer.setData('application/reactflow/height', String(defaultHeight || 80));
        e.dataTransfer.effectAllowed = 'move';
    };

    return (
        <Card className="w-64 h-full border-r rounded-none overflow-auto">
            <CardHeader className="py-3">
                <CardTitle className="text-lg">Palette</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-2 pb-4">
                {TOOLS.map((tool) => (
                    <div
                        key={tool.type}
                        draggable
                        onDragStart={(e) => handleDragStart(e, tool.type, tool.defaultWidth, tool.defaultHeight)}
                        className="flex items-center gap-3 p-3 border rounded-md cursor-move hover:bg-accent transition-colors bg-card"
                    >
                        <tool.icon className="w-5 h-5 text-muted-foreground" />
                        <span className="text-sm font-medium">{tool.label}</span>
                    </div>
                ))}
            </CardContent>
        </Card>
    );
}
