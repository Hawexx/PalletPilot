"use client";
import { useState, useEffect, useCallback } from "react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
    RefreshCw,
    BarChart3,
    AlertTriangle,
    ArrowRight,
    Loader2,
    Zap,
    CheckCircle2,
    Play,
    Trash2,
    Layers,
    Box,
} from "lucide-react";

const API_BASE = (process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api") + "/slotting";

// ── Types ───────────────────────────────────────────────
interface ZoneSummary {
    zoneClass: string;
    totalSlots: number;
    occupiedSlots: number;
    occupancyPercent: number;
}

interface SkuAnalytics {
    sku: string;
    totalOrders: number;
    totalQuantity: number;
    avgDailyQuantity: number;
    zoneClass: string;
    lastCalculatedAt: string;
    periodDays: number;
}

interface Recommendation {
    palletId: string;
    palletDisplayId: string;
    sku: string;
    currentZone: string;
    expectedZone: string;
    priorityScore: number;
    reason: string;
}

interface ZoneDefinition {
    id: string;
    zoneClass: string;
    priority: number;
    label: string;
    aisleMin: number | null;
    aisleMax: number | null;
    xMin: number | null;
    xMax: number | null;
    zMin: number | null;
    zMax: number | null;
    active: boolean;
}

// ── Zone Colors ─────────────────────────────────────────
const zoneColors: Record<string, { bg: string; border: string; text: string; bar: string }> = {
    A: { bg: "bg-emerald-500/10", border: "border-emerald-500/30", text: "text-emerald-400", bar: "bg-emerald-500" },
    B: { bg: "bg-blue-500/10", border: "border-blue-500/30", text: "text-blue-400", bar: "bg-blue-500" },
    C: { bg: "bg-amber-500/10", border: "border-amber-500/30", text: "text-amber-400", bar: "bg-amber-500" },
    D: { bg: "bg-red-500/10", border: "border-red-500/30", text: "text-red-400", bar: "bg-red-500" },
};

const zoneLabels: Record<string, string> = {
    A: "Fastest Access — Top 20% SKUs",
    B: "Fast Access — Next 30% SKUs",
    C: "Moderate Access — Next 30% SKUs",
    D: "Slowest Access — Bottom 20% SKUs",
};

export default function SlottingPage() {
    const [zones, setZones] = useState<ZoneSummary[]>([]);
    const [analytics, setAnalytics] = useState<SkuAnalytics[]>([]);
    const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
    const [zoneDefinitions, setZoneDefinitions] = useState<ZoneDefinition[]>([]);
    const [loading, setLoading] = useState(true);
    const [recalculating, setRecalculating] = useState(false);
    const [reslotting, setReslotting] = useState<string | null>(null);
    const [lastCalc, setLastCalc] = useState<string | null>(null);
    const [activeTab, setActiveTab] = useState<"analytics" | "recommendations" | "zones">("analytics");

    const fetchAll = useCallback(async () => {
        try {
            const [zRes, aRes, rRes, dRes] = await Promise.all([
                fetch(`${API_BASE}/zone-summary`),
                fetch(`${API_BASE}/analytics`),
                fetch(`${API_BASE}/recommendations`),
                fetch(`${API_BASE}/zones`),
            ]);
            if (zRes.ok) setZones(await zRes.json());
            if (aRes.ok) {
                const analyticsData = await aRes.json();
                setAnalytics(analyticsData);
                if (analyticsData.length > 0 && analyticsData[0].lastCalculatedAt) {
                    setLastCalc(new Date(analyticsData[0].lastCalculatedAt).toLocaleString("tr-TR"));
                }
            }
            if (rRes.ok) setRecommendations(await rRes.json());
            if (dRes.ok) setZoneDefinitions(await dRes.json());
        } catch (e) {
            console.error("Failed to load slotting data:", e);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchAll();
        const interval = setInterval(fetchAll, 10000);
        return () => clearInterval(interval);
    }, [fetchAll]);

    const recalculate = async () => {
        setRecalculating(true);
        try {
            await fetch(`${API_BASE}/recalculate?periodDays=30`, { method: "POST" });
            await fetch(`${API_BASE}/zones/apply`, { method: "POST" });
            await fetchAll();
        } catch (e) {
            console.error("Recalculation failed:", e);
        } finally {
            setRecalculating(false);
        }
    };

    const autoReslot = async (palletId: string, targetZone: string) => {
        setReslotting(palletId);
        try {
            await fetch(`${API_BASE}/reslot/auto`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ palletId, targetZone }),
            });
            setTimeout(fetchAll, 1500);
        } catch (e) {
            console.error("Auto-reslot failed:", e);
        } finally {
            setReslotting(null);
        }
    };

    if (loading) {
        return (
            <div className="flex h-[60vh] items-center justify-center">
                <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
        );
    }

    const totalSlots = zones.reduce((s, z) => s + z.totalSlots, 0);
    const totalOccupied = zones.reduce((s, z) => s + z.occupiedSlots, 0);

    return (
        <div className="space-y-6">
            {/* ── Header ────────────────────────── */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold tracking-tight">Slotting Optimization</h1>
                    <p className="text-muted-foreground mt-1">
                        ABC/ABCD zone classification · {totalSlots} slots · {totalOccupied} occupied
                        {lastCalc && <span className="ml-2 text-xs">• Last: {lastCalc}</span>}
                    </p>
                </div>
                <div className="flex items-center gap-2">
                    <Button onClick={recalculate} disabled={recalculating} variant="default" size="sm">
                        <Zap className={`h-4 w-4 mr-2 ${recalculating ? "animate-spin" : ""}`} />
                        {recalculating ? "Analyzing..." : "Recalculate ABC"}
                    </Button>
                    <Button onClick={fetchAll} variant="outline" size="sm">
                        <RefreshCw className="h-4 w-4" />
                    </Button>
                </div>
            </div>

            {/* ── Zone Heatmap ──────────────────── */}
            <div className="grid gap-4 md:grid-cols-4">
                {["A", "B", "C", "D"].map((zone) => {
                    const z = zones.find((v) => v.zoneClass === zone);
                    const c = zoneColors[zone] || zoneColors.C;
                    const total = z?.totalSlots ?? 0;
                    const occupied = z?.occupiedSlots ?? 0;
                    const pct = z?.occupancyPercent ?? 0;
                    return (
                        <Card key={zone} className={`${c.bg} ${c.border} border`}>
                            <CardHeader className="pb-2">
                                <div className="flex items-center justify-between">
                                    <CardTitle className={`text-lg ${c.text}`}>Zone {zone}</CardTitle>
                                    <Layers className={`w-4 h-4 ${c.text} opacity-60`} />
                                </div>
                                <CardDescription className="text-xs">{zoneLabels[zone]}</CardDescription>
                            </CardHeader>
                            <CardContent>
                                <div className="space-y-2">
                                    <div className="flex justify-between text-sm">
                                        <span>{occupied} / {total} slots</span>
                                        <span className={`font-mono font-bold ${pct > 85 ? "text-red-400" : pct > 60 ? "text-amber-400" : ""}`}>
                                            {pct.toFixed(0)}%
                                        </span>
                                    </div>
                                    <div className="h-2 rounded-full bg-muted overflow-hidden">
                                        <div
                                            className={`h-full rounded-full ${c.bar} transition-all duration-500`}
                                            style={{ width: `${Math.min(pct, 100)}%` }}
                                        />
                                    </div>
                                </div>
                            </CardContent>
                        </Card>
                    );
                })}
            </div>

            {/* ── Tabs ───────────────────────────── */}
            <div className="flex gap-1 rounded-lg border p-1 w-fit">
                {([
                    { key: "analytics" as const, label: `SKU Analytics (${analytics.length})`, icon: BarChart3 },
                    { key: "recommendations" as const, label: `Re-slot (${recommendations.length})`, icon: AlertTriangle },
                    { key: "zones" as const, label: `Zone Rules (${zoneDefinitions.length})`, icon: Box },
                ]).map((tab) => (
                    <Button
                        key={tab.key}
                        variant={activeTab === tab.key ? "secondary" : "ghost"}
                        size="sm"
                        onClick={() => setActiveTab(tab.key)}
                    >
                        <tab.icon className="h-4 w-4 mr-1.5" />
                        {tab.label}
                    </Button>
                ))}
            </div>

            {/* ── SKU Analytics Tab ──────────── */}
            {activeTab === "analytics" && (
                <Card>
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <BarChart3 className="h-5 w-5" />
                            SKU Demand Analytics
                        </CardTitle>
                        <CardDescription>
                            SKU frequency analysis from order history (last 30 days)
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                        {analytics.length === 0 ? (
                            <p className="text-muted-foreground text-center py-8">
                                No analytics data. Click &quot;Recalculate ABC&quot; to analyze order history.
                            </p>
                        ) : (
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="border-b text-left">
                                            <th className="py-2 px-3 font-medium">SKU</th>
                                            <th className="py-2 px-3 font-medium text-right">Orders</th>
                                            <th className="py-2 px-3 font-medium text-right">Total Qty</th>
                                            <th className="py-2 px-3 font-medium text-right">Avg/Day</th>
                                            <th className="py-2 px-3 font-medium text-center">Zone</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {analytics.map((a) => {
                                            const c = zoneColors[a.zoneClass] || zoneColors.C;
                                            return (
                                                <tr key={a.sku} className="border-b border-border/50 hover:bg-muted/50">
                                                    <td className="py-2 px-3 font-mono">{a.sku}</td>
                                                    <td className="py-2 px-3 text-right">{a.totalOrders}</td>
                                                    <td className="py-2 px-3 text-right font-bold">{a.totalQuantity.toLocaleString()}</td>
                                                    <td className="py-2 px-3 text-right">{a.avgDailyQuantity?.toFixed(1)}</td>
                                                    <td className="py-2 px-3 text-center">
                                                        <Badge variant="outline" className={`${c.bg} ${c.text} ${c.border}`}>
                                                            {a.zoneClass}
                                                        </Badge>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </CardContent>
                </Card>
            )}

            {/* ── Re-Slotting Recommendations Tab ─── */}
            {activeTab === "recommendations" && (
                <Card>
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <AlertTriangle className="h-5 w-5 text-amber-500" />
                            Re-Slotting Recommendations
                            {recommendations.length > 0 && (
                                <Badge variant="secondary">{recommendations.length}</Badge>
                            )}
                        </CardTitle>
                        <CardDescription>
                            Pallets in sub-optimal zones that should be moved for better performance
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                        {recommendations.length === 0 ? (
                            <div className="text-center py-8">
                                <CheckCircle2 className="w-10 h-10 text-emerald-400 mx-auto mb-3" />
                                <p className="text-muted-foreground">
                                    All pallets are in optimal zones. No re-slotting needed.
                                </p>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {recommendations.map((r) => {
                                    const fromC = zoneColors[r.currentZone] || zoneColors.C;
                                    const toC = zoneColors[r.expectedZone] || zoneColors.C;
                                    return (
                                        <div
                                            key={r.palletId}
                                            className="flex items-center justify-between rounded-lg border p-3 bg-muted/30"
                                        >
                                            <div className="flex-1 space-y-1">
                                                <div className="flex items-center gap-2">
                                                    <span className="font-mono font-bold">{r.palletDisplayId}</span>
                                                    <span className="text-xs text-muted-foreground">({r.sku})</span>
                                                </div>
                                                <div className="flex items-center gap-2 text-sm">
                                                    <Badge variant="outline" className={`${fromC.bg} ${fromC.text}`}>
                                                        Zone {r.currentZone}
                                                    </Badge>
                                                    <ArrowRight className="h-4 w-4 text-muted-foreground" />
                                                    <Badge variant="outline" className={`${toC.bg} ${toC.text}`}>
                                                        Zone {r.expectedZone}
                                                    </Badge>
                                                    <span className="text-xs text-muted-foreground ml-2">
                                                        Priority: {r.priorityScore.toFixed(0)}
                                                    </span>
                                                </div>
                                                {r.reason && (
                                                    <p className="text-xs text-muted-foreground truncate max-w-md">{r.reason}</p>
                                                )}
                                            </div>
                                            <Button
                                                variant="default"
                                                size="sm"
                                                className="shrink-0"
                                                disabled={reslotting === r.palletId}
                                                onClick={() => autoReslot(r.palletId, r.expectedZone)}
                                            >
                                                {reslotting === r.palletId ? (
                                                    <Loader2 className="h-4 w-4 mr-1.5 animate-spin" />
                                                ) : (
                                                    <Play className="h-4 w-4 mr-1.5" />
                                                )}
                                                {reslotting === r.palletId ? "Moving..." : "Auto Re-slot"}
                                            </Button>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </CardContent>
                </Card>
            )}

            {/* ── Zone Definitions Tab ────────────── */}
            {activeTab === "zones" && (
                <Card>
                    <CardHeader>
                        <div className="flex items-center justify-between">
                            <div>
                                <CardTitle className="flex items-center gap-2">
                                    <Box className="h-5 w-5" />
                                    Zone Definition Rules
                                </CardTitle>
                                <CardDescription>
                                    Configure how storage locations map to ABCD zones
                                </CardDescription>
                            </div>
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={async () => {
                                    await fetch(`${API_BASE}/zones/apply`, { method: "POST" });
                                    setTimeout(fetchAll, 500);
                                }}
                            >
                                <Zap className="h-4 w-4 mr-1.5" />
                                Apply Rules
                            </Button>
                        </div>
                    </CardHeader>
                    <CardContent>
                        {zoneDefinitions.length === 0 ? (
                            <p className="text-muted-foreground text-center py-8">
                                No zone rules defined. Using legacy z-level mapping (z=1→A, z=2→B, z=3→C, z=4→D).
                            </p>
                        ) : (
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="border-b text-left">
                                            <th className="py-2 px-3 font-medium">Zone</th>
                                            <th className="py-2 px-3 font-medium">Label</th>
                                            <th className="py-2 px-3 font-medium text-center">Priority</th>
                                            <th className="py-2 px-3 font-medium text-center">Z Level</th>
                                            <th className="py-2 px-3 font-medium text-center">Aisle</th>
                                            <th className="py-2 px-3 font-medium text-center">Bay (X)</th>
                                            <th className="py-2 px-3 font-medium text-center">Active</th>
                                            <th className="py-2 px-3 font-medium"></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {zoneDefinitions.map((def) => {
                                            const c = zoneColors[def.zoneClass] || zoneColors.C;
                                            return (
                                                <tr key={def.id} className="border-b border-border/50 hover:bg-muted/50">
                                                    <td className="py-2 px-3">
                                                        <Badge variant="outline" className={`${c.bg} ${c.text} ${c.border}`}>
                                                            Zone {def.zoneClass}
                                                        </Badge>
                                                    </td>
                                                    <td className="py-2 px-3 text-sm">{def.label}</td>
                                                    <td className="py-2 px-3 text-center font-mono">{def.priority}</td>
                                                    <td className="py-2 px-3 text-center font-mono">
                                                        {def.zMin != null ? `${def.zMin}–${def.zMax}` : "—"}
                                                    </td>
                                                    <td className="py-2 px-3 text-center font-mono text-muted-foreground">
                                                        {def.aisleMin != null ? `${def.aisleMin}–${def.aisleMax}` : "—"}
                                                    </td>
                                                    <td className="py-2 px-3 text-center font-mono text-muted-foreground">
                                                        {def.xMin != null ? `${def.xMin}–${def.xMax}` : "—"}
                                                    </td>
                                                    <td className="py-2 px-3 text-center">
                                                        <span
                                                            className={`inline-block w-2.5 h-2.5 rounded-full ${def.active ? "bg-emerald-400" : "bg-gray-600"
                                                                }`}
                                                        />
                                                    </td>
                                                    <td className="py-2 px-3 text-center">
                                                        <Button
                                                            variant="ghost"
                                                            size="sm"
                                                            className="h-8 w-8 p-0 text-muted-foreground hover:text-destructive"
                                                            onClick={async () => {
                                                                await fetch(`${API_BASE}/zones/${def.id}`, { method: "DELETE" });
                                                                fetchAll();
                                                            }}
                                                        >
                                                            <Trash2 className="h-4 w-4" />
                                                        </Button>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </CardContent>
                </Card>
            )}
        </div>
    );
}
