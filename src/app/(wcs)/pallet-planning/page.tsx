'use client';

import { useEffect, useState } from 'react';
import { Card, CardHeader, CardTitle, CardContent, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Badge } from "@/components/ui/badge";
import { OrderApi, PalletApi, ConfigApi } from "@/lib/api";
import { toast } from 'sonner';
import PalletVisualizer from "@/components/PalletVisualizer";
import { Cuboid, Cog, Zap, Package, Layers, Box } from 'lucide-react';

export default function PalletPlanningPage() {
    const [orders, setOrders] = useState<any[]>([]);
    const [selectedOrderId, setSelectedOrderId] = useState<string>('');
    const [palletPlans, setPalletPlans] = useState<any[]>([]); // Array of pallets
    const [selectedPalletIndex, setSelectedPalletIndex] = useState<number>(0);
    const [loading, setLoading] = useState(false);

    // Auto Mode - persisted to database
    const [autoMode, setAutoMode] = useState(false);
    const [autoModeLoading, setAutoModeLoading] = useState(true);

    // Load auto mode setting from database on mount
    useEffect(() => {
        const loadAutoMode = async () => {
            try {
                const res = await ConfigApi.getAll();
                const configs = res.data || [];
                const autoModeConfig = configs.find((c: any) => c.key === 'pallet.planning.auto.mode');
                if (autoModeConfig) {
                    setAutoMode(autoModeConfig.value === 'true');
                }
            } catch (err) {
                console.error('Failed to load auto mode setting:', err);
            } finally {
                setAutoModeLoading(false);
            }
        };
        loadAutoMode();
        OrderApi.getAll().then(res => setOrders(res.data)).catch(err => console.error(err));
    }, []);

    const handlePlan = async () => {
        if (!selectedOrderId) return;
        setLoading(true);
        setPalletPlans([]);
        setSelectedPalletIndex(0);
        try {
            const res = await PalletApi.plan(selectedOrderId);
            setPalletPlans(res.data); // Now an array
            toast.success(`Generated ${res.data.length} pallet(s) successfully`);
        } catch (err: any) {
            console.error(err);
            toast.error("Planning failed: " + (err.response?.data?.message || err.message));
        } finally {
            setLoading(false);
        }
    };

    const toggleAutoMode = async (enabled: boolean) => {
        if (enabled) {
            if (confirm("Enable Auto-Planning? The system will automatically plan pallets when new orders arrive.")) {
                try {
                    await ConfigApi.update('pallet.planning.auto.mode', 'true');
                    setAutoMode(true);
                    toast.success("Auto Mode Enabled - Polling for new orders every 5 seconds");
                } catch (err) {
                    console.error('Failed to save auto mode setting:', err);
                    toast.error("Failed to enable auto mode - check backend connection");
                }
            }
        } else {
            try {
                await ConfigApi.update('pallet.planning.auto.mode', 'false');
                setAutoMode(false);
                toast.info("Switched to Manual Mode");
            } catch (err) {
                console.error('Failed to save auto mode setting:', err);
                toast.error("Failed to disable auto mode - check backend connection");
            }
        }
    }

    // Auto-planning: poll for unplanned orders and plan them automatically
    useEffect(() => {
        if (!autoMode) return;

        const planUnplannedOrders = async () => {
            try {
                // Get fresh orders and pallets list
                const [ordersRes, palletsRes] = await Promise.all([
                    OrderApi.getAll(),
                    PalletApi.getAll()
                ]);
                const allOrders = ordersRes.data || [];
                const allPallets = palletsRes.data || [];

                // Get order IDs that already have pallets
                const ordersWithPallets = new Set(allPallets.map((p: any) => p.orderId));

                // Find orders that don't have pallets yet (CREATED, PENDING, RECEIVED statuses)
                const unplannedOrders = allOrders.filter((o: any) =>
                    (o.status === 'CREATED' || o.status === 'PENDING' || o.status === 'RECEIVED') &&
                    !ordersWithPallets.has(o.id)
                );

                if (unplannedOrders.length > 0) {
                    const orderToPlan = unplannedOrders[0];
                    toast.info(`Auto-planning order: ${orderToPlan.wmsOrderId || orderToPlan.id.substring(0, 8)}`);

                    try {
                        const res = await PalletApi.plan(orderToPlan.id);
                        setPalletPlans(res.data);
                        setSelectedOrderId(orderToPlan.id);
                        toast.success(`Auto-planned ${res.data.length} pallet(s) for order ${orderToPlan.wmsOrderId || orderToPlan.id.substring(0, 8)}`);

                        // Refresh orders list
                        OrderApi.getAll().then(res => setOrders(res.data)).catch(console.error);
                    } catch (err: any) {
                        console.error('Auto-planning failed:', err);
                        // Don't spam errors, just log
                    }
                }
            } catch (err) {
                console.error('Error checking for unplanned orders:', err);
            }
        };

        // Run immediately and then every 5 seconds
        planUnplannedOrders();
        const interval = setInterval(planUnplannedOrders, 5000);

        return () => clearInterval(interval);
    }, [autoMode]);

    // Summary stats across all pallets
    const totalBoxes = palletPlans.reduce((acc, p) => acc + (p.totalBoxCount || 0), 0);
    const totalLayers = palletPlans.reduce((acc, p) => acc + (p.layerCount || p.layers?.length || 0), 0);
    const totalWeight = palletPlans.reduce((acc, p) => acc + (p.totalWeightKg || 0), 0);
    const avgEfficiency = palletPlans.length > 0
        ? palletPlans.reduce((acc, p) => acc + (p.volumeEfficiency || 0), 0) / palletPlans.length
        : 0;

    // Current selected pallet
    const currentPallet = palletPlans[selectedPalletIndex];

    // Prepare data for visualizer — all pallets with absolute coords
    const palletsForVisualizer = palletPlans.map((pallet: any, idx: number) => {
        const boxes = (pallet.layers || []).flatMap((layer: any) =>
            (layer.boxes || []).map((box: any) => {
                // Resolve position: Jackson may serialize as xPosMm or xposMm
                const x = box.xPosMm ?? box.xposMm ?? box.xposmm ?? 0;
                const y = box.yPosMm ?? box.yposMm ?? box.yposmm ?? 0;
                const z = box.zPosMm ?? box.zposMm ?? box.zposmm ?? 0;

                // Resolve dimensions with explicit missing-data detection
                const width = box.widthMm ?? box.widthmm;
                const length = box.lengthMm ?? box.lengthmm;
                const height = box.heightMm ?? box.heightmm;
                const hasMissing = !width || !length || !height;

                if (hasMissing && process.env.NODE_ENV !== 'production') {
                    console.warn(
                        `[PalletPlan] Box missing dimensions — SKU: ${box.sku}`,
                        `width=${width}, length=${length}, height=${height}`,
                        '| Raw:', box
                    );
                }

                return {
                    sku: box.sku || 'UNKNOWN',
                    x, y, z,
                    width: width || 0,
                    length: length || 0,
                    height: height || 0,
                    rotation: box.rotationDegrees ?? 0,
                    _hasMissingDims: hasMissing,
                };
            })
        );
        return {
            id: pallet.palletDisplayId || pallet.id || `Pallet-${idx + 1}`,
            boxes
        };
    });

    return (
        <div className="flex flex-col h-full gap-4 p-4">
            {/* Header / Toolbar */}
            <div className="flex items-center justify-between bg-white p-4 rounded-lg shadow-sm border">
                <div className="flex items-center gap-4">
                    <div className="p-2 bg-blue-100 rounded-md text-blue-700">
                        <Cuboid className="w-6 h-6" />
                    </div>
                    <div>
                        <h1 className="text-2xl font-bold font-headline">Pallet Planning</h1>
                        <p className="text-sm text-gray-500">EPAL3 Standard (1000×1200mm) • Max 40 boxes/pallet</p>
                    </div>
                </div>

                <div className="flex items-center gap-4 bg-gray-50 p-2 rounded-md border">
                    <div className="flex items-center gap-2">
                        <span className={`text-sm font-bold ${autoMode ? 'text-gray-400' : 'text-blue-600'}`}>Manual</span>
                        <Switch
                            checked={autoMode}
                            onCheckedChange={toggleAutoMode}
                            disabled={autoModeLoading}
                        />
                        <span className={`text-sm font-bold ${autoMode ? 'text-green-600' : 'text-gray-400'}`}>
                            Auto <Zap className="w-3 h-3 inline" />
                        </span>
                        {autoModeLoading && <span className="text-xs text-gray-400">(loading...)</span>}
                    </div>
                </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 flex-1 min-h-0">
                {/* Control Panel */}
                <Card className="flex flex-col">
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <Cog className="w-4 h-4" /> Configuration
                        </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-6">
                        <div className="space-y-2">
                            <label className="text-sm font-medium">Select Order to Plan</label>
                            <Select onValueChange={setSelectedOrderId} value={selectedOrderId} disabled={autoMode}>
                                <SelectTrigger>
                                    <SelectValue placeholder="Choose an order..." />
                                </SelectTrigger>
                                <SelectContent>
                                    {orders.map(o => (
                                        <SelectItem key={o.id} value={o.id}>
                                            {o.wmsOrderId || o.id.substring(0, 8)} (P: {o.priority})
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                            {autoMode && <p className="text-xs text-amber-600">Auto mode enabled. Orders are planned automatically on arrival.</p>}
                        </div>

                        <Button
                            className="w-full"
                            size="lg"
                            onClick={handlePlan}
                            disabled={loading || !selectedOrderId || autoMode}
                        >
                            {loading ? "Optimizing..." : "Generate Plan"}
                        </Button>

                        {/* Summary Stats - All Pallets */}
                        {palletPlans.length > 0 && (
                            <div className="pt-6 border-t space-y-4 animate-in fade-in slide-in-from-bottom-4">
                                <h4 className="font-bold text-gray-700 flex items-center gap-2">
                                    <Package className="w-4 h-4" /> Order Summary
                                </h4>
                                <div className="grid grid-cols-2 gap-3">
                                    <div className="bg-gradient-to-br from-blue-50 to-blue-100 p-3 rounded-lg border border-blue-200 text-center">
                                        <div className="text-xs text-blue-600 uppercase font-semibold">Pallets</div>
                                        <div className="text-2xl font-bold text-blue-700">{palletPlans.length}</div>
                                    </div>
                                    <div className="bg-gradient-to-br from-green-50 to-green-100 p-3 rounded-lg border border-green-200 text-center">
                                        <div className="text-xs text-green-600 uppercase font-semibold">Total Boxes</div>
                                        <div className="text-2xl font-bold text-green-700">{totalBoxes}</div>
                                    </div>
                                    <div className="bg-gradient-to-br from-purple-50 to-purple-100 p-3 rounded-lg border border-purple-200 text-center">
                                        <div className="text-xs text-purple-600 uppercase font-semibold">Total Layers</div>
                                        <div className="text-2xl font-bold text-purple-700">{totalLayers}</div>
                                    </div>
                                    <div className="bg-gradient-to-br from-amber-50 to-amber-100 p-3 rounded-lg border border-amber-200 text-center">
                                        <div className="text-xs text-amber-600 uppercase font-semibold">Total Weight</div>
                                        <div className="text-2xl font-bold text-amber-700">{totalWeight.toFixed(1)} kg</div>
                                    </div>
                                </div>

                                {/* Pallet Selector */}
                                {palletPlans.length > 1 && (
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium">View Pallet Details</label>
                                        <Select
                                            value={selectedPalletIndex.toString()}
                                            onValueChange={(v) => setSelectedPalletIndex(parseInt(v))}
                                        >
                                            <SelectTrigger>
                                                <SelectValue />
                                            </SelectTrigger>
                                            <SelectContent>
                                                {palletPlans.map((p, idx) => (
                                                    <SelectItem key={idx} value={idx.toString()}>
                                                        {p.palletDisplayId || `Pallet-${idx + 1}`} ({p.totalBoxCount || 0} boxes)
                                                    </SelectItem>
                                                ))}
                                            </SelectContent>
                                        </Select>
                                    </div>
                                )}

                                {/* Selected Pallet Details */}
                                {currentPallet && (
                                    <div className="space-y-3 pt-4 border-t">
                                        <h4 className="font-bold text-gray-700 flex items-center gap-2">
                                            <Layers className="w-4 h-4" /> {currentPallet.palletDisplayId || 'Pallet'} Details
                                        </h4>
                                        <div className="grid grid-cols-2 gap-2 text-sm">
                                            <div className="bg-slate-50 p-2 rounded border">
                                                <div className="text-xs text-gray-500">Boxes</div>
                                                <div className="font-bold">{currentPallet.totalBoxCount || 0}</div>
                                            </div>
                                            <div className="bg-slate-50 p-2 rounded border">
                                                <div className="text-xs text-gray-500">Layers</div>
                                                <div className="font-bold">{currentPallet.layerCount || currentPallet.layers?.length || 0}</div>
                                            </div>
                                            <div className="bg-slate-50 p-2 rounded border">
                                                <div className="text-xs text-gray-500">Weight</div>
                                                <div className="font-bold">{currentPallet.totalWeightKg?.toFixed(1) || 0} kg</div>
                                            </div>
                                            <div className="bg-slate-50 p-2 rounded border">
                                                <div className="text-xs text-gray-500">Efficiency</div>
                                                <div className="font-bold">{currentPallet.volumeEfficiency?.toFixed(1) || 0}%</div>
                                            </div>
                                        </div>

                                        {/* SKU Breakdown per layer */}
                                        <div className="space-y-2">
                                            <div className="text-xs font-medium text-gray-500 uppercase">Layer Breakdown</div>
                                            {currentPallet.layers?.map((layer: any, lIdx: number) => {
                                                const skuCounts: Record<string, number> = {};
                                                layer.boxes?.forEach((b: any) => {
                                                    skuCounts[b.sku] = (skuCounts[b.sku] || 0) + 1;
                                                });
                                                return (
                                                    <div key={lIdx} className="bg-gray-50 p-2 rounded border text-xs">
                                                        <div className="font-semibold">Layer {lIdx + 1}</div>
                                                        <div className="flex flex-wrap gap-1 mt-1">
                                                            {Object.entries(skuCounts).map(([sku, count]) => (
                                                                <Badge key={sku} variant="secondary" className="text-xs">
                                                                    {sku}: {count}
                                                                </Badge>
                                                            ))}
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </div>
                                )}
                            </div>
                        )}

                        {palletPlans.length > 0 && (
                            <div className="pt-4 border-t flex items-center justify-between">
                                <div className="flex items-center text-green-600 gap-2">
                                    <span className="relative flex h-3 w-3">
                                        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                                        <span className="relative inline-flex rounded-full h-3 w-3 bg-green-500"></span>
                                    </span>
                                    <span className="text-sm font-medium">{palletPlans.length} Pallet(s) Saved to Database</span>
                                </div>
                            </div>
                        )}

                    </CardContent>
                </Card>

                {/* 3D Visualization Area */}
                <div className="lg:col-span-2 h-[600px] lg:h-auto bg-gray-900 rounded-lg shadow-inner overflow-hidden border border-gray-700 relative">
                    {palletsForVisualizer.length > 0 ? (
                        <PalletVisualizer pallets={palletsForVisualizer} />
                    ) : (
                        <div className="absolute inset-0 flex items-center justify-center text-gray-500 flex-col gap-2">
                            <Cuboid className="w-12 h-12 opacity-20" />
                            <p>Select an order and click 'Generate Plan' to view 3D model</p>
                            <p className="text-xs text-gray-600">EPAL3 Pallet • 10 boxes/layer • 4 layers max</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
