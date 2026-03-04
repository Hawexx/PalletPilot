"use client";

import { useEffect, useState, useCallback } from "react";
import { useWebSocket } from "@/hooks/useWebSocket";
import {
    Activity,
    AlertTriangle,
    CheckCircle2,
    Clock,
    Heart,
    RefreshCw,
    XCircle,
    Truck,
    Radio,
    Loader2,
    ChevronDown,
    ChevronRight,
} from "lucide-react";

// ─── Types ───────────────────────────────────────────────────────
interface MissionStats {
    missionId: string;
    status: string;
    equipmentId: string | null;
    sku: string | null;
    sourceLocation: string | null;
    targetLocation: string | null;
    createdAt: string;
    updatedAt: string;
}

interface PendingConfirmation {
    id: string;
    missionId: string;
    confirmationType: string;
    status: string;
    attemptCount: number;
    requestedAt: string;
    lastAttemptAt: string | null;
    reservedDistanceMm: number | null;
    errorMessage: string | null;
}

interface TimelineEvent {
    notificationType: string;
    vehicleId: string | null;
    loadId: string | null;
    eventTimestamp: string;
    processedAt: string;
}

interface HealthInfo {
    systemState: string;
    circuitState: string;
    timestamp: string;
}

const API_BASE = (process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api") + "/movu";

// ─── Helper Components ──────────────────────────────────────────

function StatusBadge({ status }: { status: string }) {
    const colors: Record<string, string> = {
        CREATED: "bg-gray-600 text-gray-100",
        ASSIGNED: "bg-blue-600 text-blue-100",
        ASSIGNING: "bg-blue-500 text-blue-100",
        PICKED: "bg-indigo-600 text-indigo-100",
        AWAITING_SOURCE_CONFIRM: "bg-amber-600 text-amber-100",
        AWAITING_DEST_CONFIRM: "bg-amber-600 text-amber-100",
        COMPLETED: "bg-emerald-600 text-emerald-100",
        FAILED: "bg-red-600 text-red-100",
        CANCELLED: "bg-gray-500 text-gray-100",
        ABORTED: "bg-red-500 text-red-100",
        REQUIRES_MANUAL_INTERVENTION: "bg-red-700 text-red-100",
        PENDING: "bg-yellow-600 text-yellow-100",
        IN_PROGRESS: "bg-blue-600 text-blue-100",
        SUCCEEDED: "bg-emerald-600 text-emerald-100",
        RUNNING: "bg-emerald-600 text-emerald-100",
        DEGRADED: "bg-amber-600 text-amber-100",
        STOPPED: "bg-red-600 text-red-100",
        CLOSED: "bg-emerald-600 text-emerald-100",
        OPEN: "bg-red-600 text-red-100",
        HALF_OPEN: "bg-amber-600 text-amber-100",
    };

    return (
        <span
            className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${colors[status] || "bg-gray-600 text-gray-100"
                }`}
        >
            {status.replace(/_/g, " ")}
        </span>
    );
}

function StatsCard({
    title,
    value,
    icon: Icon,
    color,
}: {
    title: string;
    value: string | number;
    icon: React.ElementType;
    color: string;
}) {
    const colorStyles: Record<string, { bg: string; border: string; label: string }> = {
        "bg-emerald-600": {
            bg: "bg-emerald-600",
            border: "border-emerald-700",
            label: "text-emerald-100",
        },
        "bg-amber-600": {
            bg: "bg-amber-600",
            border: "border-amber-700",
            label: "text-amber-100",
        },
        "bg-red-600": {
            bg: "bg-red-600",
            border: "border-red-700",
            label: "text-red-100",
        },
        "bg-blue-600": {
            bg: "bg-blue-600",
            border: "border-blue-700",
            label: "text-blue-100",
        },
        "bg-gray-600": {
            bg: "bg-gray-600",
            border: "border-gray-700",
            label: "text-gray-200",
        },
    };

    const style = colorStyles[color] || colorStyles["bg-gray-600"];

    return (
        <div className={`${style.bg} border ${style.border} rounded-xl p-5 backdrop-blur-sm`}>
            <div className="flex items-center justify-between">
                <div>
                    <p className={`text-sm font-medium mb-1 ${style.label}`}>{title}</p>
                    <p className="text-2xl font-bold text-white">{value}</p>
                </div>
                <div className={`p-3 rounded-lg ${color}`}>
                    <Icon className="w-6 h-6 text-white" />
                </div>
            </div>
        </div>
    );
}

function TimeAgo({ timestamp }: { timestamp: string }) {
    const seconds = Math.floor(
        (Date.now() - new Date(timestamp).getTime()) / 1000
    );
    if (seconds < 60) return <span>{seconds}s ago</span>;
    if (seconds < 3600) return <span>{Math.floor(seconds / 60)}m ago</span>;
    return <span>{Math.floor(seconds / 3600)}h ago</span>;
}

// ─── Main Page ──────────────────────────────────────────────────

export default function MovuOperationsPage() {
    const [activeMissions, setActiveMissions] = useState<MissionStats[]>([]);
    const [pendingConfirmations, setPendingConfirmations] = useState<
        PendingConfirmation[]
    >([]);
    const [health, setHealth] = useState<HealthInfo | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [expandedMission, setExpandedMission] = useState<string | null>(null);
    const [timeline, setTimeline] = useState<TimelineEvent[]>([]);
    const [timelineLoading, setTimelineLoading] = useState(false);
    const [lastRefresh, setLastRefresh] = useState(new Date());

    const fetchData = useCallback(async () => {
        try {
            const [missionsRes, pendingRes, healthRes] = await Promise.all([
                fetch(`${API_BASE}/missions/active`),
                fetch(`${API_BASE}/confirmations/pending`),
                fetch(`${API_BASE}/health`),
            ]);

            if (missionsRes.ok) setActiveMissions(await missionsRes.json());
            if (pendingRes.ok) setPendingConfirmations(await pendingRes.json());
            if (healthRes.ok) setHealth(await healthRes.json());

            setError(null);
            setLastRefresh(new Date());
        } catch (e) {
            setError("Backend bağlantısı kurulamadı");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchData();
        // Fallback polling (extended since WebSocket handles instant updates)
        const interval = setInterval(fetchData, 30000);
        return () => clearInterval(interval);
    }, [fetchData]);

    // Real-time WebSocket subscription — trigger refetch on Movu events
    useWebSocket('/topic/movu', useCallback(() => {
        fetchData();
    }, [fetchData]));

    const fetchTimeline = async (missionId: string) => {
        if (expandedMission === missionId) {
            setExpandedMission(null);
            return;
        }
        setTimelineLoading(true);
        setExpandedMission(missionId);
        try {
            const res = await fetch(`${API_BASE}/missions/${missionId}/timeline`);
            if (res.ok) setTimeline(await res.json());
            else setTimeline([]);
        } catch {
            setTimeline([]);
        } finally {
            setTimelineLoading(false);
        }
    };

    if (loading) {
        return (
            <div className="flex items-center justify-center h-96">
                <Loader2 className="w-8 h-8 animate-spin text-blue-500" />
                <span className="ml-3 text-gray-400">Yükleniyor...</span>
            </div>
        );
    }

    return (
        <div className="p-6 space-y-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-2xl font-bold text-white flex items-center gap-2">
                        <Radio className="w-7 h-7 text-blue-500" />
                        Movu Operations
                    </h1>
                    <p className="text-sm text-gray-400 mt-1">
                        Real-time shuttle monitoring, pending confirmations & health
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <span className="text-xs text-gray-500">
                        Last: {lastRefresh.toLocaleTimeString()}
                    </span>
                    <button
                        onClick={fetchData}
                        className="flex items-center gap-2 px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm text-gray-300 transition"
                    >
                        <RefreshCw className="w-4 h-4" />
                        Refresh
                    </button>
                </div>
            </div>

            {error && (
                <div className="bg-red-900/30 border border-red-700 rounded-xl p-4 flex items-center gap-3">
                    <AlertTriangle className="w-5 h-5 text-red-400" />
                    <span className="text-red-300">{error}</span>
                </div>
            )}

            {/* Health + Stats Cards */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <StatsCard
                    title="System State"
                    value={health?.systemState || "UNKNOWN"}
                    icon={Heart}
                    color={
                        health?.systemState === "RUNNING"
                            ? "bg-emerald-600"
                            : health?.systemState === "DEGRADED"
                                ? "bg-amber-600"
                                : "bg-red-600"
                    }
                />
                <StatsCard
                    title="Circuit Breaker"
                    value={health?.circuitState || "UNKNOWN"}
                    icon={Activity}
                    color={
                        health?.circuitState === "CLOSED"
                            ? "bg-emerald-600"
                            : health?.circuitState === "HALF_OPEN"
                                ? "bg-amber-600"
                                : "bg-red-600"
                    }
                />
                <StatsCard
                    title="Active Missions"
                    value={activeMissions.length}
                    icon={Truck}
                    color="bg-blue-600"
                />
                <StatsCard
                    title="Pending Confirms"
                    value={pendingConfirmations.length}
                    icon={Clock}
                    color={
                        pendingConfirmations.length > 0 ? "bg-amber-600" : "bg-gray-600"
                    }
                />
            </div>

            {/* Pending Confirmations */}
            {pendingConfirmations.length > 0 && (
                <div className="bg-gray-800/50 border border-amber-700/50 rounded-xl p-5">
                    <h2 className="text-lg font-semibold text-amber-400 flex items-center gap-2 mb-4">
                        <Clock className="w-5 h-5" />
                        Pending Confirmations ({pendingConfirmations.length})
                    </h2>
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="text-gray-400 border-b border-gray-700">
                                    <th className="text-left py-2 px-3">Mission</th>
                                    <th className="text-left py-2 px-3">Type</th>
                                    <th className="text-left py-2 px-3">Status</th>
                                    <th className="text-left py-2 px-3">Attempts</th>
                                    <th className="text-left py-2 px-3">Reserved Dist.</th>
                                    <th className="text-left py-2 px-3">Age</th>
                                    <th className="text-left py-2 px-3">Error</th>
                                </tr>
                            </thead>
                            <tbody>
                                {pendingConfirmations.map((pc) => (
                                    <tr
                                        key={pc.id}
                                        className="border-b border-gray-700/50 hover:bg-gray-700/30"
                                    >
                                        <td className="py-2 px-3 font-mono text-xs text-gray-300">
                                            {pc.missionId}
                                        </td>
                                        <td className="py-2 px-3">
                                            <span
                                                className={`text-xs px-2 py-0.5 rounded ${pc.confirmationType === "DESTINATION"
                                                    ? "bg-blue-900 text-blue-300"
                                                    : "bg-purple-900 text-purple-300"
                                                    }`}
                                            >
                                                {pc.confirmationType}
                                            </span>
                                        </td>
                                        <td className="py-2 px-3">
                                            <StatusBadge status={pc.status} />
                                        </td>
                                        <td className="py-2 px-3 text-gray-300">
                                            {pc.attemptCount}
                                        </td>
                                        <td className="py-2 px-3 text-gray-300">
                                            {pc.reservedDistanceMm
                                                ? `${pc.reservedDistanceMm}mm`
                                                : "—"}
                                        </td>
                                        <td className="py-2 px-3 text-gray-400">
                                            <TimeAgo timestamp={pc.requestedAt} />
                                        </td>
                                        <td className="py-2 px-3 text-red-400 text-xs max-w-[200px] truncate">
                                            {pc.errorMessage || "—"}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* Active Missions */}
            <div className="bg-gray-800/50 border border-gray-700 rounded-xl p-5">
                <h2 className="text-lg font-semibold text-white flex items-center gap-2 mb-4">
                    <Truck className="w-5 h-5 text-blue-400" />
                    Active Missions ({activeMissions.length})
                </h2>

                {activeMissions.length === 0 ? (
                    <div className="text-center py-8 text-gray-500">
                        <CheckCircle2 className="w-12 h-12 mx-auto mb-3 opacity-30" />
                        <p>No active missions — all missions completed</p>
                    </div>
                ) : (
                    <div className="space-y-2">
                        {activeMissions.map((m) => (
                            <div
                                key={m.missionId}
                                className="border border-gray-700/50 rounded-lg overflow-hidden"
                            >
                                <button
                                    onClick={() => fetchTimeline(m.missionId)}
                                    className="w-full flex items-center justify-between p-4 hover:bg-gray-700/30 transition text-left"
                                >
                                    <div className="flex items-center gap-4">
                                        {expandedMission === m.missionId ? (
                                            <ChevronDown className="w-4 h-4 text-gray-400" />
                                        ) : (
                                            <ChevronRight className="w-4 h-4 text-gray-400" />
                                        )}
                                        <span className="font-mono text-sm text-gray-300">
                                            {m.missionId}
                                        </span>
                                        <StatusBadge status={m.status} />
                                    </div>
                                    <div className="flex items-center gap-4 text-xs text-gray-400">
                                        {m.sku && <span>SKU: {m.sku}</span>}
                                        {m.sourceLocation && <span>From: {m.sourceLocation}</span>}
                                        {m.targetLocation && <span>To: {m.targetLocation}</span>}
                                        <TimeAgo timestamp={m.updatedAt || m.createdAt} />
                                    </div>
                                </button>

                                {/* Timeline (expanded) */}
                                {expandedMission === m.missionId && (
                                    <div className="border-t border-gray-700/50 p-4 bg-gray-900/50">
                                        <h4 className="text-sm font-medium text-gray-400 mb-3">
                                            Event Timeline
                                        </h4>
                                        {timelineLoading ? (
                                            <div className="flex items-center gap-2 text-gray-500">
                                                <Loader2 className="w-4 h-4 animate-spin" />
                                                Loading...
                                            </div>
                                        ) : timeline.length === 0 ? (
                                            <p className="text-sm text-gray-500">No events found</p>
                                        ) : (
                                            <div className="space-y-2">
                                                {timeline.map((evt, idx) => (
                                                    <div
                                                        key={idx}
                                                        className="flex items-center gap-3 text-sm"
                                                    >
                                                        <div className="w-2 h-2 rounded-full bg-blue-500 flex-shrink-0" />
                                                        <span className="text-gray-300 font-medium min-w-[260px]">
                                                            {evt.notificationType}
                                                        </span>
                                                        {evt.vehicleId && (
                                                            <span className="text-gray-500">
                                                                Vehicle: {evt.vehicleId}
                                                            </span>
                                                        )}
                                                        {evt.loadId && (
                                                            <span className="text-gray-500">
                                                                Load: {evt.loadId}
                                                            </span>
                                                        )}
                                                        <span className="text-gray-500 ml-auto">
                                                            {new Date(evt.eventTimestamp).toLocaleTimeString()}
                                                        </span>
                                                    </div>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
