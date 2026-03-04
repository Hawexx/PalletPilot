"use client";
import { useState, useEffect, useCallback } from "react";
import { useWebSocket } from "@/hooks/useWebSocket";
import {
    ArrowDown,
    ArrowUp,
    RefreshCw,
    CheckCircle2,
    XCircle,
    Clock,
    AlertTriangle,
    Zap,
    Database,
    Send,
    Inbox,
    RotateCcw,
    Ban,
    Eye,
    ChevronDown,
    ChevronUp,
} from "lucide-react";

const API_BASE = (process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api") + "/erp";

interface Stats {
    inbound_received: number;
    inbound_processing: number;
    inbound_processed: number;
    inbound_failed: number;
    inbound_dlq: number;
    outbox_pending: number;
    outbox_sent: number;
    outbox_failed: number;
    outbox_dlq: number;
    logs_inbound_success: number;
    logs_outbound_success: number;
}

interface LogEntry {
    id: string;
    direction: string;
    eventType: string;
    erpOrderId: string;
    wcsOrderId: string;
    correlationId: string;
    status: string;
    errorMessage: string | null;
    createdAt: string;
    httpStatus: number | null;
}

interface InboundMessage {
    id: string;
    messageId: string;
    erpOrderId: string;
    eventType: string;
    status: string;
    errorMessage: string | null;
    attemptCount: number;
    receivedAt: string;
    processedAt: string | null;
}

export default function ErpIntegrationPage() {
    const [stats, setStats] = useState<Stats | null>(null);
    const [logs, setLogs] = useState<LogEntry[]>([]);
    const [failedMessages, setFailedMessages] = useState<InboundMessage[]>([]);
    const [dlqInbound, setDlqInbound] = useState<InboundMessage[]>([]);
    const [dlqOutbox, setDlqOutbox] = useState<any[]>([]);
    const [loading, setLoading] = useState(true);
    const [retrying, setRetrying] = useState<string | null>(null);
    const [expandedDlq, setExpandedDlq] = useState<string | null>(null);
    const [dlqDetail, setDlqDetail] = useState<any>(null);

    const fetchData = useCallback(async () => {
        try {
            const [statsRes, logsRes, failedRes, dlqInRes, dlqOutRes] = await Promise.all([
                fetch(`${API_BASE}/stats`),
                fetch(`${API_BASE}/logs?size=50`),
                fetch(`${API_BASE}/inbound?status=FAILED`),
                fetch(`${API_BASE}/inbound?status=DLQ`),
                fetch(`${API_BASE}/outbox?status=DLQ`),
            ]);

            if (statsRes.ok) setStats(await statsRes.json());
            if (logsRes.ok) {
                const data = await logsRes.json();
                setLogs(data.content || []);
            }
            if (failedRes.ok) setFailedMessages(await failedRes.json());
            if (dlqInRes.ok) setDlqInbound(await dlqInRes.json());
            if (dlqOutRes.ok) setDlqOutbox(await dlqOutRes.json());
        } catch (e) {
            console.error("Failed to fetch ERP data:", e);
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

    // Real-time WebSocket subscription — trigger refetch on ERP events
    useWebSocket('/topic/erp', useCallback(() => {
        fetchData();
    }, [fetchData]));

    const inspectDlq = async (type: "inbound" | "outbox", id: string) => {
        if (expandedDlq === id) {
            setExpandedDlq(null);
            setDlqDetail(null);
            return;
        }
        try {
            const res = await fetch(`${API_BASE}/${type}/${id}`);
            if (res.ok) {
                setDlqDetail(await res.json());
                setExpandedDlq(id);
            }
        } catch (e) {
            console.error("Failed to inspect DLQ:", e);
        }
    };

    const requeueOutbox = async (id: string) => {
        setRetrying(id);
        try {
            await fetch(`${API_BASE}/outbox/${id}/retry`, { method: "POST" });
            setTimeout(fetchData, 1000);
        } catch (e) {
            console.error("Requeue failed:", e);
        } finally {
            setRetrying(null);
        }
    };

    const retryMessage = async (id: string) => {
        setRetrying(id);
        try {
            await fetch(`${API_BASE}/inbound/${id}/retry`, { method: "POST" });
            setTimeout(fetchData, 1000);
        } catch (e) {
            console.error("Retry failed:", e);
        } finally {
            setRetrying(null);
        }
    };

    if (loading) {
        return (
            <div className="flex items-center justify-center h-64">
                <RefreshCw className="w-8 h-8 animate-spin text-blue-400" />
            </div>
        );
    }

    const statusColor = (status: string) => {
        switch (status) {
            case "PROCESSED":
            case "SENT":
            case "SUCCESS":
                return "text-emerald-400 bg-emerald-400/10";
            case "FAILED":
                return "text-red-400 bg-red-400/10";
            case "DLQ":
                return "text-orange-400 bg-orange-400/10";
            case "PROCESSING":
            case "PENDING":
                return "text-yellow-400 bg-yellow-400/10";
            case "RECEIVED":
                return "text-blue-400 bg-blue-400/10";
            case "CANCELLED":
                return "text-gray-400 bg-gray-500/10";
            case "CANCEL_REQUESTED":
                return "text-amber-400 bg-amber-400/10";
            default:
                return "text-gray-400 bg-gray-400/10";
        }
    };

    const directionIcon = (dir: string) =>
        dir === "INBOUND" ? (
            <Inbox className="w-4 h-4 text-blue-400" />
        ) : (
            <Send className="w-4 h-4 text-emerald-400" />
        );

    return (
        <div className="space-y-6 p-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-2xl font-bold text-white">ERP Integration</h1>
                    <p className="text-sm text-gray-400 mt-1">
                        SAP/ERP order pipeline monitoring, logs, and retry management
                    </p>
                </div>
                <button
                    onClick={fetchData}
                    className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors text-sm font-medium"
                >
                    <RefreshCw className="w-4 h-4" />
                    Refresh
                </button>
            </div>

            {/* Stats Cards */}
            {stats && (
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                    {/* Inbound */}
                    <StatsCard
                        label="Inbound Queued"
                        value={stats.inbound_received}
                        icon={<Clock className="w-5 h-5" />}
                        color="blue"
                    />
                    <StatsCard
                        label="Inbound Processed"
                        value={stats.inbound_processed}
                        icon={<CheckCircle2 className="w-5 h-5" />}
                        color="emerald"
                    />
                    <StatsCard
                        label="Inbound Failed"
                        value={stats.inbound_failed + stats.inbound_dlq}
                        icon={<XCircle className="w-5 h-5" />}
                        color={
                            stats.inbound_failed + stats.inbound_dlq > 0 ? "red" : "gray"
                        }
                        alert={stats.inbound_failed + stats.inbound_dlq > 0}
                    />
                    {/* Outbound */}
                    <StatsCard
                        label="Outbox Pending"
                        value={stats.outbox_pending}
                        icon={<Send className="w-5 h-5" />}
                        color="yellow"
                    />
                    <StatsCard
                        label="Outbox Sent"
                        value={stats.outbox_sent}
                        icon={<CheckCircle2 className="w-5 h-5" />}
                        color="emerald"
                    />
                    <StatsCard
                        label="Outbox Failed"
                        value={stats.outbox_failed + stats.outbox_dlq}
                        icon={<AlertTriangle className="w-5 h-5" />}
                        color={
                            stats.outbox_failed + stats.outbox_dlq > 0 ? "orange" : "gray"
                        }
                    />
                    <StatsCard
                        label="Total Inbound Logs"
                        value={stats.logs_inbound_success}
                        icon={<Database className="w-5 h-5" />}
                        color="blue"
                    />
                    <StatsCard
                        label="Total Outbound Logs"
                        value={stats.logs_outbound_success}
                        icon={<Zap className="w-5 h-5" />}
                        color="purple"
                    />
                </div>
            )}

            {/* Failed Messages */}
            {failedMessages.length > 0 && (
                <div className="bg-red-900/20 border border-red-500/30 rounded-xl p-5">
                    <h2 className="text-lg font-semibold text-red-300 flex items-center gap-2 mb-4">
                        <AlertTriangle className="w-5 h-5" />
                        Failed Messages ({failedMessages.length})
                    </h2>
                    <div className="space-y-3">
                        {failedMessages.map((msg) => (
                            <div
                                key={msg.id}
                                className="flex items-center justify-between bg-gray-900/60 rounded-lg p-4"
                            >
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-3">
                                        <span className="text-sm font-mono text-red-300">
                                            {msg.erpOrderId}
                                        </span>
                                        <span
                                            className={`text-xs px-2 py-0.5 rounded-full ${statusColor(msg.status)}`}
                                        >
                                            {msg.status}
                                        </span>
                                        <span className="text-xs text-gray-500">
                                            {msg.eventType}
                                        </span>
                                        <span className="text-xs text-gray-600">
                                            Attempts: {msg.attemptCount}
                                        </span>
                                    </div>
                                    {msg.errorMessage && (
                                        <p className="text-xs text-red-400/80 mt-1 truncate">
                                            {msg.errorMessage}
                                        </p>
                                    )}
                                </div>
                                <button
                                    onClick={() => retryMessage(msg.id)}
                                    disabled={retrying === msg.id}
                                    className="flex items-center gap-1.5 px-3 py-1.5 bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white rounded-lg text-xs font-medium transition-colors ml-4"
                                >
                                    <RotateCcw
                                        className={`w-3.5 h-3.5 ${retrying === msg.id ? "animate-spin" : ""}`}
                                    />
                                    Retry
                                </button>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* DLQ Inspection */}
            {(dlqInbound.length > 0 || dlqOutbox.length > 0) && (
                <div className="bg-orange-900/15 border border-orange-500/30 rounded-xl p-5">
                    <h2 className="text-lg font-semibold text-orange-300 flex items-center gap-2 mb-4">
                        <Ban className="w-5 h-5" />
                        Dead Letter Queue ({dlqInbound.length + dlqOutbox.length})
                    </h2>
                    <div className="space-y-3">
                        {dlqInbound.map((msg) => (
                            <div key={msg.id}>
                                <div className="flex items-center justify-between bg-gray-900/60 rounded-lg p-4">
                                    <div className="flex items-center gap-3 flex-1 min-w-0">
                                        <Inbox className="w-4 h-4 text-blue-400 shrink-0" />
                                        <span className="text-xs text-gray-500">INBOUND</span>
                                        <span className="text-sm font-mono text-orange-300">{msg.erpOrderId}</span>
                                        <span className={`text-xs px-2 py-0.5 rounded-full ${statusColor(msg.status)}`}>{msg.status}</span>
                                        <span className="text-xs text-gray-500">{msg.eventType}</span>
                                        <span className="text-xs text-gray-600">Attempts: {msg.attemptCount}</span>
                                    </div>
                                    <div className="flex items-center gap-2 ml-4">
                                        <button onClick={() => inspectDlq("inbound", msg.id)} className="flex items-center gap-1 px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-white rounded-lg text-xs font-medium transition-colors">
                                            {expandedDlq === msg.id ? <ChevronUp className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                                            {expandedDlq === msg.id ? "Close" : "Inspect"}
                                        </button>
                                        <button onClick={() => retryMessage(msg.id)} disabled={retrying === msg.id} className="flex items-center gap-1 px-3 py-1.5 bg-orange-600 hover:bg-orange-700 disabled:opacity-50 text-white rounded-lg text-xs font-medium transition-colors">
                                            <RotateCcw className={`w-3.5 h-3.5 ${retrying === msg.id ? "animate-spin" : ""}`} />
                                            Retry
                                        </button>
                                    </div>
                                </div>
                                {expandedDlq === msg.id && dlqDetail && (
                                    <pre className="mt-1 p-3 bg-gray-950 rounded-b-lg text-xs text-gray-400 overflow-x-auto max-h-48">{JSON.stringify(dlqDetail, null, 2)}</pre>
                                )}
                            </div>
                        ))}
                        {dlqOutbox.map((msg: any) => (
                            <div key={msg.id}>
                                <div className="flex items-center justify-between bg-gray-900/60 rounded-lg p-4">
                                    <div className="flex items-center gap-3 flex-1 min-w-0">
                                        <Send className="w-4 h-4 text-emerald-400 shrink-0" />
                                        <span className="text-xs text-gray-500">OUTBOX</span>
                                        <span className="text-sm font-mono text-orange-300">{msg.erpOrderId}</span>
                                        <span className={`text-xs px-2 py-0.5 rounded-full ${statusColor(msg.status)}`}>{msg.status}</span>
                                        <span className="text-xs text-gray-500">{msg.messageType}</span>
                                        <span className="text-xs text-gray-600">Attempts: {msg.attemptCount}</span>
                                    </div>
                                    <div className="flex items-center gap-2 ml-4">
                                        <button onClick={() => inspectDlq("outbox", msg.id)} className="flex items-center gap-1 px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-white rounded-lg text-xs font-medium transition-colors">
                                            {expandedDlq === msg.id ? <ChevronUp className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                                            {expandedDlq === msg.id ? "Close" : "Inspect"}
                                        </button>
                                        <button onClick={() => requeueOutbox(msg.id)} disabled={retrying === msg.id} className="flex items-center gap-1 px-3 py-1.5 bg-orange-600 hover:bg-orange-700 disabled:opacity-50 text-white rounded-lg text-xs font-medium transition-colors">
                                            <RotateCcw className={`w-3.5 h-3.5 ${retrying === msg.id ? "animate-spin" : ""}`} />
                                            Requeue
                                        </button>
                                    </div>
                                </div>
                                {expandedDlq === msg.id && dlqDetail && (
                                    <pre className="mt-1 p-3 bg-gray-950 rounded-b-lg text-xs text-gray-400 overflow-x-auto max-h-48">{JSON.stringify(dlqDetail, null, 2)}</pre>
                                )}
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Integration Logs */}
            <div className="bg-gray-900/50 border border-gray-700/50 rounded-xl overflow-hidden">
                <div className="px-5 py-4 border-b border-gray-700/50">
                    <h2 className="text-lg font-semibold text-white flex items-center gap-2">
                        <Database className="w-5 h-5 text-blue-400" />
                        Integration Logs
                    </h2>
                </div>
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="text-gray-400 text-left border-b border-gray-700/30">
                                <th className="px-5 py-3 font-medium">Direction</th>
                                <th className="px-5 py-3 font-medium">Event</th>
                                <th className="px-5 py-3 font-medium">ERP Order</th>
                                <th className="px-5 py-3 font-medium">Status</th>
                                <th className="px-5 py-3 font-medium">HTTP</th>
                                <th className="px-5 py-3 font-medium">Time</th>
                                <th className="px-5 py-3 font-medium">Error</th>
                            </tr>
                        </thead>
                        <tbody>
                            {logs.length === 0 ? (
                                <tr>
                                    <td colSpan={7} className="px-5 py-8 text-center text-gray-500">
                                        No integration logs yet
                                    </td>
                                </tr>
                            ) : (
                                logs.map((log) => (
                                    <tr
                                        key={log.id}
                                        className="border-b border-gray-700/20 hover:bg-gray-800/30"
                                    >
                                        <td className="px-5 py-3">
                                            <div className="flex items-center gap-2">
                                                {directionIcon(log.direction)}
                                                <span className="text-xs font-medium text-gray-300">
                                                    {log.direction}
                                                </span>
                                            </div>
                                        </td>
                                        <td className="px-5 py-3 text-gray-300 font-mono text-xs">
                                            {log.eventType}
                                        </td>
                                        <td className="px-5 py-3 text-white font-mono text-xs">
                                            {log.erpOrderId || "—"}
                                        </td>
                                        <td className="px-5 py-3">
                                            <span
                                                className={`text-xs px-2 py-0.5 rounded-full ${statusColor(log.status)}`}
                                            >
                                                {log.status}
                                            </span>
                                        </td>
                                        <td className="px-5 py-3 text-gray-400 text-xs">
                                            {log.httpStatus || "—"}
                                        </td>
                                        <td className="px-5 py-3 text-gray-500 text-xs whitespace-nowrap">
                                            {new Date(log.createdAt).toLocaleString("tr-TR")}
                                        </td>
                                        <td className="px-5 py-3 text-red-400/80 text-xs truncate max-w-[200px]">
                                            {log.errorMessage || "—"}
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}

function StatsCard({
    label,
    value,
    icon,
    color,
    alert,
}: {
    label: string;
    value: number;
    icon: React.ReactNode;
    color: string;
    alert?: boolean;
}) {
    const colorMap: Record<string, { card: string; label: string; icon: string }> = {
        blue: {
            card: "bg-blue-600 border-blue-700",
            label: "text-blue-100",
            icon: "text-blue-200",
        },
        emerald: {
            card: "bg-emerald-600 border-emerald-700",
            label: "text-emerald-100",
            icon: "text-emerald-200",
        },
        red: {
            card: "bg-red-600 border-red-700",
            label: "text-red-100",
            icon: "text-red-200",
        },
        yellow: {
            card: "bg-yellow-600 border-yellow-700",
            label: "text-yellow-100",
            icon: "text-yellow-200",
        },
        orange: {
            card: "bg-orange-600 border-orange-700",
            label: "text-orange-100",
            icon: "text-orange-200",
        },
        purple: {
            card: "bg-purple-600 border-purple-700",
            label: "text-purple-100",
            icon: "text-purple-200",
        },
        gray: {
            card: "bg-gray-600 border-gray-700",
            label: "text-gray-200",
            icon: "text-gray-300",
        },
    };

    const scheme = colorMap[color] || colorMap.gray;

    return (
        <div
            className={`${scheme.card} border rounded-xl p-4 ${alert ? "animate-pulse" : ""}`}
        >
            <div className="flex items-center justify-between">
                <div>
                    <p className={`text-xs font-medium mb-1 ${scheme.label}`}>{label}</p>
                    <p className="text-2xl font-bold text-white">{value}</p>
                </div>
                <div className={scheme.icon}>{icon}</div>
            </div>
        </div>
    );
}

