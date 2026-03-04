'use client';
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Activity, Package, Bot, BarChart, Cpu, MemoryStick } from "lucide-react";
import { useEffect, useState, useCallback } from "react";
import { SystemControls } from "@/components/dashboard/SystemControls";
import { useWebSocket } from "@/hooks/useWebSocket";

export default function DashboardPage() {
    const [cpuUsage, setCpuUsage] = useState<number>(0);
    const [memoryUsage, setMemoryUsage] = useState<number>(0);
    const [errorMsg, setErrorMsg] = useState<string>("");
    const [stats, setStats] = useState({
        activeOrders: 0,
        activeMissions: 0,
        equipmentHealthPercentage: 100,
        onlineEquipment: 0,
        totalEquipment: 0,
        systemStatus: 'Unknown'
    });

    useEffect(() => {
        const fetchMetrics = async () => {
            try {
                const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
                const ACTUATOR_URL = API_URL.replace(/\/api\/?$/, '');

                // 1. Fetch Business Stats
                const statsRes = await fetch(`${API_URL}/dashboard/stats`);
                if (statsRes.ok) {
                    const stats = await statsRes.json();
                    setStats(stats);
                }

                // 2. Fetch CPU Usage
                const cpuRes = await fetch(`${ACTUATOR_URL}/actuator/metrics/system.cpu.usage`);
                if (cpuRes.ok) {
                    const cpuData = await cpuRes.json();
                    if (cpuData.measurements?.[0]) {
                        setCpuUsage(Math.round(cpuData.measurements[0].value * 100));
                    }
                }

                // 3. Fetch Memory Usage
                const memRes = await fetch(`${ACTUATOR_URL}/actuator/metrics/jvm.memory.used`);
                if (memRes.ok) {
                    const memData = await memRes.json();
                    if (memData.measurements?.[0]) {
                        setMemoryUsage(Math.round(memData.measurements[0].value / 1024 / 1024));
                    }
                }

                setErrorMsg("");
            } catch (e: any) {
                console.error("Metrics Fetch Error:", e);
                // Don't show error on screen for minor metric failures, just log it
            }
        };

        fetchMetrics();
        // Fallback polling (extended since WebSocket handles instant updates)
        const interval = setInterval(fetchMetrics, 30000);
        return () => clearInterval(interval);
    }, []);

    // Real-time WebSocket subscription for dashboard stats
    useWebSocket('/topic/dashboard', useCallback((envelope: any) => {
        if (envelope.data) {
            setStats(prev => ({ ...prev, ...envelope.data }));
        }
    }, []));

    return (
        <div className="space-y-6">
            <h1 className="text-3xl font-bold tracking-tight">WCS Dashboard</h1>

            <SystemControls />

            <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-4">
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium">System Status</CardTitle>
                        <Activity className="h-4 w-4 text-muted-foreground" />
                    </CardHeader>
                    <CardContent>
                        <div className={`text-2xl font-bold ${stats.systemStatus === 'Operational' ? 'text-green-600' :
                            stats.systemStatus === 'Degraded' ? 'text-yellow-600' : 'text-red-600'
                            }`}>
                            {stats.systemStatus}
                        </div>
                        <p className="text-xs text-muted-foreground">
                            {stats.systemStatus === 'Operational' ? 'All systems are online.' : 'Attention required.'}
                        </p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium">Active Orders</CardTitle>
                        <Package className="h-4 w-4 text-muted-foreground" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold">{stats.activeOrders}</div>
                        <p className="text-xs text-muted-foreground">Processing</p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium">CPU Usage</CardTitle>
                        <Cpu className="h-4 w-4 text-muted-foreground" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold">
                            {errorMsg ? <span className="text-red-500 text-xs">{errorMsg}</span> : `${cpuUsage}%`}
                        </div>
                        <p className="text-xs text-muted-foreground">System Load</p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium">Memory Usage</CardTitle>
                        <MemoryStick className="h-4 w-4 text-muted-foreground" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold">
                            {errorMsg ? <span className="text-red-500 text-xs">{errorMsg}</span> : `${memoryUsage} MB`}
                        </div>
                        <p className="text-xs text-muted-foreground">JVM Heap Used</p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium">Active Missions</CardTitle>
                        <Bot className="h-4 w-4 text-muted-foreground" />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold">{stats.activeMissions} Active</div>
                        <p className="text-xs text-muted-foreground">In Progress</p>
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium">Equipment Health</CardTitle>
                        <BarChart className="h-4 w-4 text-muted-foreground" />
                    </CardHeader>
                    <CardContent>
                        <div className={`text-2xl font-bold ${stats.equipmentHealthPercentage < 90 ? 'text-red-500' : 'text-green-600'}`}>
                            {stats.equipmentHealthPercentage}%
                        </div>
                        <p className="text-xs text-muted-foreground">{stats.onlineEquipment} / {stats.totalEquipment} Online</p>
                    </CardContent>
                </Card>
            </div>
            {/* Add more detailed components here */}
        </div>
    );
}
