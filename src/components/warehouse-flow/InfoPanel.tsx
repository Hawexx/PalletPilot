import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { NodeData, NodeConfig, FlowDirection, ZoneType, PalletType } from './types';
import { ArrowLeft, ArrowRight, ArrowUp, ArrowDown, ArrowLeftRight } from 'lucide-react';

interface InfoPanelProps {
    selectedNode: NodeData | null;
    onUpdate: (node: NodeData) => void;
    onDelete: (id: string) => void;
}

const DIRECTION_OPTIONS: { value: FlowDirection; label: string; icon: any }[] = [
    { value: 'RIGHT', label: 'Right →', icon: ArrowRight },
    { value: 'LEFT', label: '← Left', icon: ArrowLeft },
    { value: 'UP', label: '↑ Up', icon: ArrowUp },
    { value: 'DOWN', label: '↓ Down', icon: ArrowDown },
    { value: 'BIDIRECTIONAL', label: '↔ Both', icon: ArrowLeftRight },
];

const ZONE_TYPE_OPTIONS: { value: ZoneType; label: string; color: string }[] = [
    { value: 'INBOUND', label: 'Inbound', color: 'bg-green-500' },
    { value: 'OUTBOUND', label: 'Outbound', color: 'bg-orange-500' },
    { value: 'STAGING', label: 'Staging', color: 'bg-blue-500' },
    { value: 'REJECT', label: 'Reject', color: 'bg-red-500' },
];

const PALLET_TYPE_OPTIONS: { value: PalletType; label: string }[] = [
    { value: 'EURO', label: 'Euro Pallet (1200x800)' },
    { value: 'IND', label: 'Industrial (1200x1000)' },
    { value: 'BOTH', label: 'Both Types' },
];

export function InfoPanel({ selectedNode, onUpdate, onDelete }: InfoPanelProps) {
    const [config, setConfig] = useState<NodeConfig>({});
    const [label, setLabel] = useState('');

    useEffect(() => {
        if (selectedNode) {
            setConfig(selectedNode.config || {});
            setLabel(selectedNode.label || selectedNode.type);
        }
    }, [selectedNode]);

    const handleConfigChange = (key: keyof NodeConfig, value: any) => {
        const newConfig = { ...config, [key]: value };
        setConfig(newConfig);
        if (selectedNode) {
            onUpdate({ ...selectedNode, config: newConfig });
        }
    };

    const handleLabelChange = (val: string) => {
        setLabel(val);
        if (selectedNode) {
            onUpdate({ ...selectedNode, label: val });
        }
    };

    const handleFlowDirectionChange = (direction: FlowDirection) => {
        if (selectedNode) {
            onUpdate({ ...selectedNode, flowDirection: direction });
        }
    };

    const handleRotationChange = (rotation: number) => {
        if (selectedNode) {
            onUpdate({ ...selectedNode, rotation });
        }
    };

    if (!selectedNode) {
        return (
            <Card className="w-80 h-full border-l rounded-none overflow-auto">
                <CardHeader>
                    <CardTitle className="text-lg">Properties</CardTitle>
                </CardHeader>
                <CardContent>
                    <p className="text-sm text-muted-foreground">Select a node to edit its properties.</p>
                </CardContent>
            </Card>
        );
    }

    return (
        <Card className="w-80 h-full border-l rounded-none overflow-auto">
            <CardHeader className="py-3">
                <CardTitle className="text-lg">Edit {selectedNode.type}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4 pb-6">
                {/* Basic Properties */}
                <div className="space-y-2">
                    <Label>Label</Label>
                    <Input value={label} onChange={(e) => handleLabelChange(e.target.value)} />
                </div>

                <div className="grid grid-cols-2 gap-2">
                    <div className="space-y-2">
                        <Label>Width</Label>
                        <Input
                            type="number"
                            min="40"
                            value={selectedNode.width || 80}
                            onChange={(e) => onUpdate({ ...selectedNode, width: Number(e.target.value) })}
                        />
                    </div>
                    <div className="space-y-2">
                        <Label>Height</Label>
                        <Input
                            type="number"
                            min="40"
                            value={selectedNode.height || 80}
                            onChange={(e) => onUpdate({ ...selectedNode, height: Number(e.target.value) })}
                        />
                    </div>
                </div>

                <Separator />

                {/* CONVEYOR Configuration */}
                {selectedNode.type === 'CONVEYOR' && (
                    <>
                        <h4 className="text-sm font-semibold text-blue-600">Conveyor Settings</h4>

                        <div className="space-y-2">
                            <Label>Flow Direction</Label>
                            <div className="grid grid-cols-5 gap-1">
                                {DIRECTION_OPTIONS.map((opt) => (
                                    <Button
                                        key={opt.value}
                                        variant={selectedNode.flowDirection === opt.value ? "default" : "outline"}
                                        size="sm"
                                        className="p-2"
                                        onClick={() => handleFlowDirectionChange(opt.value)}
                                        title={opt.label}
                                    >
                                        <opt.icon className="w-4 h-4" />
                                    </Button>
                                ))}
                            </div>
                        </div>

                        <div className="space-y-2">
                            <Label>Speed (m/s)</Label>
                            <Input
                                type="number"
                                step="0.1"
                                min="0"
                                value={config.speed || ''}
                                placeholder="e.g. 0.5"
                                onChange={(e) => handleConfigChange('speed', parseFloat(e.target.value) || 0)}
                            />
                        </div>

                        <div className="space-y-2">
                            <Label>Rotation</Label>
                            <Select
                                value={String(selectedNode.rotation || 0)}
                                onValueChange={(v) => handleRotationChange(Number(v))}
                            >
                                <SelectTrigger>
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="0">0° (Horizontal)</SelectItem>
                                    <SelectItem value="90">90° (Vertical)</SelectItem>
                                    <SelectItem value="180">180°</SelectItem>
                                    <SelectItem value="270">270°</SelectItem>
                                </SelectContent>
                            </Select>
                        </div>
                    </>
                )}

                {/* ZONE Configuration */}
                {selectedNode.type === 'ZONE' && (
                    <>
                        <h4 className="text-sm font-semibold text-green-600">Zone Settings</h4>

                        <div className="space-y-2">
                            <Label>Zone Type</Label>
                            <Select
                                value={config.zoneType || 'INBOUND'}
                                onValueChange={(v) => handleConfigChange('zoneType', v as ZoneType)}
                            >
                                <SelectTrigger>
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    {ZONE_TYPE_OPTIONS.map((opt) => (
                                        <SelectItem key={opt.value} value={opt.value}>
                                            <div className="flex items-center gap-2">
                                                <div className={`w-3 h-3 rounded-full ${opt.color}`} />
                                                {opt.label}
                                            </div>
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>

                        <div className="space-y-2">
                            <Label>Throughput (pal/h)</Label>
                            <Input
                                type="number"
                                min="0"
                                value={config.throughput || ''}
                                placeholder="e.g. 60"
                                onChange={(e) => handleConfigChange('throughput', parseInt(e.target.value) || 0)}
                            />
                        </div>
                    </>
                )}

                {/* PALLET BUFFER Configuration */}
                {selectedNode.type === 'PALLET_BUFFER' && (
                    <>
                        <h4 className="text-sm font-semibold text-purple-600">Pallet Buffer Settings</h4>

                        <div className="space-y-2">
                            <Label>Pallet Type</Label>
                            <Select
                                value={config.palletType || 'EURO'}
                                onValueChange={(v) => handleConfigChange('palletType', v as PalletType)}
                            >
                                <SelectTrigger>
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    {PALLET_TYPE_OPTIONS.map((opt) => (
                                        <SelectItem key={opt.value} value={opt.value}>
                                            {opt.label}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>

                        <div className="space-y-2">
                            <Label>Capacity (pallets)</Label>
                            <Input
                                type="number"
                                min="1"
                                value={config.capacity || ''}
                                placeholder="e.g. 20"
                                onChange={(e) => handleConfigChange('capacity', parseInt(e.target.value) || 0)}
                            />
                        </div>
                    </>
                )}

                {/* TURNTABLE Configuration */}
                {selectedNode.type === 'TURNTABLE' && (
                    <>
                        <h4 className="text-sm font-semibold text-orange-600">Turntable Settings</h4>

                        <div className="space-y-2">
                            <Label>Rotation Speed (°/sec)</Label>
                            <Input
                                type="number"
                                min="0"
                                value={config.rotationSpeed || ''}
                                placeholder="e.g. 90"
                                onChange={(e) => handleConfigChange('rotationSpeed', parseFloat(e.target.value) || 0)}
                            />
                        </div>
                    </>
                )}

                {/* RACK Configuration */}
                {selectedNode.type === 'RACK' && (
                    <>
                        <h4 className="text-sm font-semibold text-cyan-600">Rack Settings</h4>

                        <div className="grid grid-cols-2 gap-2">
                            <div className="space-y-2">
                                <Label>Lanes</Label>
                                <Input
                                    type="number"
                                    min="1"
                                    value={config.laneCount || ''}
                                    placeholder="e.g. 4"
                                    onChange={(e) => handleConfigChange('laneCount', parseInt(e.target.value) || 0)}
                                />
                            </div>
                            <div className="space-y-2">
                                <Label>Levels</Label>
                                <Input
                                    type="number"
                                    min="1"
                                    value={config.levels || ''}
                                    placeholder="e.g. 10"
                                    onChange={(e) => handleConfigChange('levels', parseInt(e.target.value) || 0)}
                                />
                            </div>
                        </div>
                    </>
                )}

                {/* MOVU Robot Configuration */}
                {selectedNode.type === 'MOVU' && (
                    <>
                        <h4 className="text-sm font-semibold text-indigo-600">Movu Robot Settings</h4>

                        <div className="space-y-2">
                            <Label>Trigger Event</Label>
                            <Input
                                placeholder="e.g. PALLET_READY"
                                value={config.trigger || ''}
                                onChange={(e) => handleConfigChange('trigger', e.target.value)}
                            />
                        </div>
                    </>
                )}

                {/* KUKA Configuration */}
                {selectedNode.type === 'KUKA' && (
                    <>
                        <h4 className="text-sm font-semibold text-red-600">KUKA Arm Settings</h4>

                        <div className="space-y-2">
                            <Label>Trigger Event</Label>
                            <Input
                                placeholder="e.g. BOX_ARRIVAL"
                                value={config.trigger || ''}
                                onChange={(e) => handleConfigChange('trigger', e.target.value)}
                            />
                        </div>
                    </>
                )}

                {/* TRANSFER_CAR Configuration */}
                {selectedNode.type === 'TRANSFER_CAR' && (
                    <>
                        <h4 className="text-sm font-semibold text-amber-600">Transfer Car Settings</h4>

                        <div className="space-y-2">
                            <Label>Speed (m/s)</Label>
                            <Input
                                type="number"
                                step="0.1"
                                min="0"
                                value={config.speed || ''}
                                placeholder="e.g. 1.5"
                                onChange={(e) => handleConfigChange('speed', parseFloat(e.target.value) || 0)}
                            />
                        </div>
                    </>
                )}

                {/* Common Network Configuration */}
                <Separator />
                <h4 className="text-sm font-semibold">Network (Optional)</h4>

                <div className="grid grid-cols-2 gap-2">
                    <div className="space-y-2">
                        <Label>IP Address</Label>
                        <Input
                            value={config.ip || ''}
                            placeholder="192.168.1.x"
                            onChange={(e) => handleConfigChange('ip', e.target.value)}
                        />
                    </div>
                    <div className="space-y-2">
                        <Label>Port</Label>
                        <Input
                            type="number"
                            value={config.port || ''}
                            placeholder="502"
                            onChange={(e) => handleConfigChange('port', parseInt(e.target.value) || 0)}
                        />
                    </div>
                </div>

                <div className="pt-4">
                    <Button variant="destructive" className="w-full" onClick={() => onDelete(selectedNode.id)}>
                        Delete Node
                    </Button>
                </div>
            </CardContent>
        </Card>
    );
}
