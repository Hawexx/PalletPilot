'use client';

import React, { useState, useRef, useEffect } from 'react';
import { NodeData, EdgeData, LayoutData, NodeType, FlowDirection } from './types';
import { Palette } from './Palette';
import { InfoPanel } from './InfoPanel';
import { WarehouseFlowApi } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Save, Loader2, ArrowRight, ArrowLeft, ArrowUp, ArrowDown, ArrowLeftRight, RotateCw } from 'lucide-react';
import { toast } from 'sonner';

// Color mapping for different node types
const NODE_COLORS: Record<NodeType, { bg: string; border: string; text: string }> = {
    CONVEYOR: { bg: 'bg-blue-50', border: 'border-blue-400', text: 'text-blue-700' },
    MOVU: { bg: 'bg-indigo-50', border: 'border-indigo-400', text: 'text-indigo-700' },
    KUKA: { bg: 'bg-red-50', border: 'border-red-400', text: 'text-red-700' },
    RACK: { bg: 'bg-cyan-50', border: 'border-cyan-400', text: 'text-cyan-700' },
    WRAPPER: { bg: 'bg-purple-50', border: 'border-purple-400', text: 'text-purple-700' },
    SCANNER: { bg: 'bg-gray-50', border: 'border-gray-400', text: 'text-gray-700' },
    TURNTABLE: { bg: 'bg-orange-50', border: 'border-orange-400', text: 'text-orange-700' },
    ZONE: { bg: 'bg-green-50', border: 'border-green-400', text: 'text-green-700' },
    PALLET_BUFFER: { bg: 'bg-violet-50', border: 'border-violet-400', text: 'text-violet-700' },
    TRANSFER_CAR: { bg: 'bg-amber-50', border: 'border-amber-400', text: 'text-amber-700' },
};

// Zone-specific colors
const ZONE_COLORS: Record<string, { bg: string; border: string }> = {
    INBOUND: { bg: 'bg-green-100', border: 'border-green-500' },
    OUTBOUND: { bg: 'bg-orange-100', border: 'border-orange-500' },
    STAGING: { bg: 'bg-blue-100', border: 'border-blue-500' },
    REJECT: { bg: 'bg-red-100', border: 'border-red-500' },
};

// Get direction arrow component
const getDirectionArrow = (direction?: FlowDirection) => {
    switch (direction) {
        case 'LEFT': return ArrowLeft;
        case 'RIGHT': return ArrowRight;
        case 'UP': return ArrowUp;
        case 'DOWN': return ArrowDown;
        case 'BIDIRECTIONAL': return ArrowLeftRight;
        default: return ArrowRight;
    }
};

export default function FlowEditor() {
    const [nodes, setNodes] = useState<NodeData[]>([]);
    const [edges, setEdges] = useState<EdgeData[]>([]);
    const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);

    // Interaction State
    const [draggingNodeId, setDraggingNodeId] = useState<string | null>(null);
    const [resizingNodeId, setResizingNodeId] = useState<string | null>(null);
    const [resizeHandle, setResizeHandle] = useState<string | null>(null);
    const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 });
    const [resizeStart, setResizeStart] = useState({ x: 0, y: 0, w: 0, h: 0, startX: 0, startY: 0 });

    const [edgeStartNodeId, setEdgeStartNodeId] = useState<string | null>(null);
    const [mousePos, setMousePos] = useState({ x: 0, y: 0 });

    const canvasRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        loadLayout();
    }, []);

    const loadLayout = async () => {
        try {
            const res = await WarehouseFlowApi.get();
            if (res.data && res.data.layoutJson) {
                const layout: LayoutData = JSON.parse(res.data.layoutJson);
                setNodes(layout.nodes || []);
                setEdges(layout.edges || []);
            }
        } catch (err) {
            console.error("Failed to load layout", err);
            toast.error("Failed to load layout");
        } finally {
            setIsLoading(false);
        }
    };

    const handleSave = async () => {
        setIsSaving(true);
        try {
            const layout: LayoutData = { nodes, edges };
            await WarehouseFlowApi.save(JSON.stringify(layout));
            toast.success("Layout saved successfully");
        } catch (err) {
            console.error("Failed to save", err);
            toast.error("Failed to save layout");
        } finally {
            setIsSaving(false);
        }
    };

    // ----- Drag & Drop New Nodes -----
    const handleDragOver = (e: React.DragEvent) => {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
    };

    const handleDrop = (e: React.DragEvent) => {
        e.preventDefault();
        const type = e.dataTransfer.getData('application/reactflow/type') as NodeType;
        const width = parseInt(e.dataTransfer.getData('application/reactflow/width')) || 80;
        const height = parseInt(e.dataTransfer.getData('application/reactflow/height')) || 80;
        if (!type || !canvasRef.current) return;

        const rect = canvasRef.current.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;

        const newNode: NodeData = {
            id: crypto.randomUUID(),
            type,
            x,
            y,
            width,
            height,
            label: type.replace('_', ' '),
            flowDirection: type === 'CONVEYOR' ? 'RIGHT' : undefined,
            rotation: 0,
            config: type === 'ZONE' ? { zoneType: 'INBOUND', throughput: 60 } : {}
        };

        setNodes((prev) => [...prev, newNode]);
    };

    // ----- Moving Existing Nodes -----
    const handleNodeMouseDown = (e: React.MouseEvent, id: string) => {
        e.stopPropagation();
        setSelectedNodeId(id);
        setDraggingNodeId(id);

        const node = nodes.find(n => n.id === id);
        if (node && canvasRef.current) {
            const rect = canvasRef.current.getBoundingClientRect();
            const mouseX = e.clientX - rect.left;
            const mouseY = e.clientY - rect.top;
            setDragOffset({ x: mouseX - node.x, y: mouseY - node.y });
        }
    };

    // ----- Resizing Nodes -----
    const handleResizeMouseDown = (e: React.MouseEvent, id: string, handle: string) => {
        e.stopPropagation();
        setResizingNodeId(id);
        setResizeHandle(handle);

        const node = nodes.find(n => n.id === id);
        if (node && canvasRef.current) {
            setResizeStart({
                x: node.x,
                y: node.y,
                w: node.width || 80,
                h: node.height || 80,
                startX: e.clientX,
                startY: e.clientY
            });
        }
    };

    // ----- Creating Edges -----
    const handlePortMouseDown = (e: React.MouseEvent, nodeId: string) => {
        e.stopPropagation();
        setEdgeStartNodeId(nodeId);
    };

    const handleCanvasMouseMove = (e: React.MouseEvent) => {
        if (!canvasRef.current) return;
        const rect = canvasRef.current.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        setMousePos({ x, y });

        if (draggingNodeId) {
            setNodes((prev) => prev.map(n => {
                if (n.id === draggingNodeId) {
                    return { ...n, x: x - dragOffset.x, y: y - dragOffset.y };
                }
                return n;
            }));
        }

        if (resizingNodeId && resizeHandle) {
            const deltaX = e.clientX - resizeStart.startX;
            const deltaY = e.clientY - resizeStart.startY;

            setNodes((prev) => prev.map(n => {
                if (n.id === resizingNodeId) {
                    let newX = resizeStart.x;
                    let newY = resizeStart.y;
                    let newW = resizeStart.w;
                    let newH = resizeStart.h;

                    if (resizeHandle.includes('e')) newW = Math.max(40, resizeStart.w + deltaX);
                    if (resizeHandle.includes('s')) newH = Math.max(40, resizeStart.h + deltaY);
                    if (resizeHandle.includes('w')) {
                        const maxDelta = resizeStart.w - 40;
                        const actualDelta = Math.min(deltaX, maxDelta);
                        newX = resizeStart.x + actualDelta;
                        newW = resizeStart.w - actualDelta;
                    }
                    if (resizeHandle.includes('n')) {
                        const maxDelta = resizeStart.h - 40;
                        const actualDelta = Math.min(deltaY, maxDelta);
                        newY = resizeStart.y + actualDelta;
                        newH = resizeStart.h - actualDelta;
                    }

                    return { ...n, x: newX, y: newY, width: newW, height: newH };
                }
                return n;
            }));
        }
    };

    const handleCanvasMouseUp = () => {
        setDraggingNodeId(null);
        setEdgeStartNodeId(null);
        setResizingNodeId(null);
        setResizeHandle(null);
    };

    const handleNodeMouseUp = (e: React.MouseEvent, targetNodeId: string) => {
        e.stopPropagation();
        if (edgeStartNodeId && edgeStartNodeId !== targetNodeId) {
            const exists = edges.some(edge =>
                (edge.source === edgeStartNodeId && edge.target === targetNodeId) ||
                (edge.source === targetNodeId && edge.target === edgeStartNodeId)
            );

            if (!exists) {
                const newEdge: EdgeData = {
                    id: crypto.randomUUID(),
                    source: edgeStartNodeId,
                    target: targetNodeId
                };
                setEdges(prev => [...prev, newEdge]);
            }
        }
        setDraggingNodeId(null);
        setEdgeStartNodeId(null);
        setResizingNodeId(null);
    };

    // ----- CRUD -----
    const handleUpdateNode = (updated: NodeData) => {
        setNodes(prev => prev.map(n => n.id === updated.id ? updated : n));
    };

    const handleDeleteNode = (id: string) => {
        setNodes(prev => prev.filter(n => n.id !== id));
        setEdges(prev => prev.filter(e => e.source !== id && e.target !== id));
        setSelectedNodeId(null);
    };

    // ----- Rendering -----
    const renderEdges = () => {
        return (
            <svg className="absolute top-0 left-0 w-full h-full pointer-events-none overflow-visible">
                <defs>
                    <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
                        <polygon points="0 0, 10 3.5, 0 7" fill="#64748b" />
                    </marker>
                </defs>
                {edges.map(edge => {
                    const source = nodes.find(n => n.id === edge.source);
                    const target = nodes.find(n => n.id === edge.target);
                    if (!source || !target) return null;

                    const sx = source.x + (source.width || 80) / 2;
                    const sy = source.y + (source.height || 80) / 2;
                    const tx = target.x + (target.width || 80) / 2;
                    const ty = target.y + (target.height || 80) / 2;

                    return (
                        <line
                            key={edge.id}
                            x1={sx}
                            y1={sy}
                            x2={tx}
                            y2={ty}
                            stroke="#64748b"
                            strokeWidth="2"
                            markerEnd="url(#arrowhead)"
                        />
                    );
                })}
                {edgeStartNodeId && (() => {
                    const startNode = nodes.find(n => n.id === edgeStartNodeId);
                    if (!startNode) return null;
                    return (
                        <line
                            x1={startNode.x + (startNode.width || 80) / 2}
                            y1={startNode.y + (startNode.height || 80) / 2}
                            x2={mousePos.x}
                            y2={mousePos.y}
                            stroke="#94a3b8"
                            strokeWidth="2"
                            strokeDasharray="5,5"
                        />
                    );
                })()}
            </svg>
        );
    };

    const getNodeColors = (node: NodeData) => {
        if (node.type === 'ZONE' && node.config?.zoneType) {
            return ZONE_COLORS[node.config.zoneType] || NODE_COLORS.ZONE;
        }
        return NODE_COLORS[node.type] || NODE_COLORS.CONVEYOR;
    };

    const renderNodeContent = (node: NodeData) => {
        const DirectionIcon = getDirectionArrow(node.flowDirection);

        return (
            <>
                {/* Node Label */}
                <span className={`text-xs font-bold text-center p-1 truncate w-full pointer-events-none ${NODE_COLORS[node.type]?.text || 'text-gray-700'}`}>
                    {node.label}
                </span>

                {/* Conveyor Direction Arrow */}
                {node.type === 'CONVEYOR' && (
                    <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                        <DirectionIcon className="w-6 h-6 text-blue-500 opacity-60" />
                    </div>
                )}

                {/* Turntable Rotation Icon */}
                {node.type === 'TURNTABLE' && (
                    <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                        <RotateCw className="w-8 h-8 text-orange-500 opacity-40" />
                    </div>
                )}

                {/* Zone Throughput Badge */}
                {node.type === 'ZONE' && node.config?.throughput && (
                    <div className="absolute bottom-1 right-1 text-[10px] font-bold bg-white/80 px-1 rounded">
                        {node.config.throughput} pal/h
                    </div>
                )}

                {/* Rack Lane/Level Info */}
                {node.type === 'RACK' && (node.config?.laneCount || node.config?.levels) && (
                    <div className="absolute bottom-1 left-1 text-[10px] font-bold bg-white/80 px-1 rounded">
                        {node.config.laneCount || 0}L × {node.config.levels || 0}
                    </div>
                )}

                {/* Pallet Buffer Type */}
                {node.type === 'PALLET_BUFFER' && node.config?.palletType && (
                    <div className="absolute top-1 right-1 text-[10px] font-bold bg-white/80 px-1 rounded">
                        {node.config.palletType}
                    </div>
                )}

                {/* Connection Port */}
                <div
                    className="w-4 h-4 bg-gray-400 rounded-full hover:bg-primary cursor-crosshair mt-1 z-30"
                    onMouseDown={(e) => handlePortMouseDown(e, node.id)}
                />
            </>
        );
    };

    if (isLoading) {
        return <div className="flex items-center justify-center h-full"><Loader2 className="animate-spin" /></div>;
    }

    return (
        <div className="flex h-[calc(100vh-4rem)] flex-col">
            {/* Toolbar */}
            <div className="h-14 border-b flex items-center px-4 justify-between bg-card">
                <h2 className="font-semibold">Warehouse Flow Editor</h2>
                <div className="text-sm text-muted-foreground bg-accent/50 px-3 py-1 rounded-full border">
                    💡 Tip: Drag from the grey dot at the bottom to connect. Select to configure.
                </div>
                <Button onClick={handleSave} disabled={isSaving}>
                    {isSaving && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    <Save className="mr-2 h-4 w-4" />
                    Save Layout
                </Button>
            </div>

            <div className="flex flex-1 overflow-hidden">
                <Palette />

                {/* Canvas Area */}
                <div
                    className="flex-1 bg-stone-50 relative overflow-auto select-none"
                    ref={canvasRef}
                    onDragOver={handleDragOver}
                    onDrop={handleDrop}
                    onMouseMove={handleCanvasMouseMove}
                    onMouseUp={handleCanvasMouseUp}
                    onClick={() => setSelectedNodeId(null)}
                >
                    {/* Grid Background */}
                    <div className="absolute inset-0 pointer-events-none" style={{
                        backgroundImage: 'linear-gradient(to right, #e5e7eb 1px, transparent 1px), linear-gradient(to bottom, #e5e7eb 1px, transparent 1px)',
                        backgroundSize: '40px 40px',
                        minWidth: '2000px',
                        minHeight: '2000px'
                    }} />

                    {renderEdges()}

                    {nodes.map(node => {
                        const colors = getNodeColors(node);
                        return (
                            <div
                                key={node.id}
                                className={`absolute ${colors.bg} border-2 ${colors.border} rounded-md shadow-md flex flex-col items-center justify-center cursor-grab active:cursor-grabbing z-10 transition-all select-none group
                                ${selectedNodeId === node.id ? 'ring-2 ring-primary shadow-lg' : ''}`}
                                style={{
                                    left: node.x,
                                    top: node.y,
                                    width: node.width || 80,
                                    height: node.height || 80,
                                    transform: node.rotation ? `rotate(${node.rotation}deg)` : undefined
                                }}
                                onMouseDown={(e) => handleNodeMouseDown(e, node.id)}
                                onMouseUp={(e) => handleNodeMouseUp(e, node.id)}
                                onClick={(e) => { e.stopPropagation(); setSelectedNodeId(node.id); }}
                            >
                                {renderNodeContent(node)}

                                {/* Resize Handles - Only visible if selected */}
                                {selectedNodeId === node.id && (
                                    <>
                                        <div className="absolute -top-1.5 -left-1.5 w-3 h-3 bg-white border-2 border-primary rounded-full cursor-nw-resize z-20"
                                            onMouseDown={(e) => handleResizeMouseDown(e, node.id, 'nw')} />
                                        <div className="absolute -top-1.5 -right-1.5 w-3 h-3 bg-white border-2 border-primary rounded-full cursor-ne-resize z-20"
                                            onMouseDown={(e) => handleResizeMouseDown(e, node.id, 'ne')} />
                                        <div className="absolute -bottom-1.5 -left-1.5 w-3 h-3 bg-white border-2 border-primary rounded-full cursor-sw-resize z-20"
                                            onMouseDown={(e) => handleResizeMouseDown(e, node.id, 'sw')} />
                                        <div className="absolute -bottom-1.5 -right-1.5 w-3 h-3 bg-white border-2 border-primary rounded-full cursor-se-resize z-20"
                                            onMouseDown={(e) => handleResizeMouseDown(e, node.id, 'se')} />
                                    </>
                                )}
                            </div>
                        );
                    })}
                </div>

                <InfoPanel
                    selectedNode={selectedNodeId ? nodes.find(n => n.id === selectedNodeId) || null : null}
                    onUpdate={handleUpdateNode}
                    onDelete={handleDeleteNode}
                />
            </div>
        </div>
    );
}
