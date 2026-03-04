'use client';

import { useEffect, useState } from 'react';
import { Card, CardHeader, CardTitle, CardContent, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { AlertCircle, Play, Power, RefreshCw, Octagon } from 'lucide-react';
import { SystemStateApi } from '@/lib/api';
import { toast } from 'sonner';

export function SystemControls() {
    const [status, setStatus] = useState<string>('UNKNOWN');
    const [isLoading, setIsLoading] = useState(false);

    const fetchState = async () => {
        try {
            const res = await SystemStateApi.get();
            // Assuming API returns string or { state: '...' }
            // Backend SystemStateController returns just the enum string usually, or a JSON wrapper.
            // Let's assume it returns the raw string based on previous backend code reading.
            // Actually, SystemStateController usually returns a JSON object or string.
            // Let's handle both.
            if (res.data && typeof res.data === 'object' && res.data.state) {
                setStatus(res.data.state);
            } else {
                setStatus(String(res.data));
            }
        } catch (e) {
            console.error("Failed to fetch system state", e);
            setStatus('OFFLINE'); // Assume offline if API fails
        }
    };

    useEffect(() => {
        fetchState();
        const interval = setInterval(fetchState, 3000);
        return () => clearInterval(interval);
    }, []);

    const handleStateChange = async (newState: string, reason: string) => {
        setIsLoading(true);
        try {
            // userId is hardcoded for MVP
            await SystemStateApi.set(newState, "admin-user", reason);
            toast.success(`System state changed to ${newState}`);
            await fetchState();
        } catch (e: any) {
            console.error("State Change Error", e);
            toast.error("Failed to change state: " + (e.response?.data?.message || e.message));
        } finally {
            setIsLoading(false);
        }
    };

    const isRunning = status === 'RUNNING';
    const isStopped = status === 'STOPPED' || status === 'EMERGENCY_STOP';
    const isError = status === 'ERROR';

    return (
        <Card className="border-l-4 border-l-primary">
            <CardHeader>
                <CardTitle className="flex items-center gap-2">
                    <Power className="h-5 w-5" />
                    System Controls
                </CardTitle>
                <CardDescription>
                    Manage the global state of the Warehouse Control System.
                </CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col sm:flex-row gap-4 items-center">

                {/* Status Indicator */}
                <div className="flex-1 w-full p-4 bg-muted rounded-lg flex items-center justify-between">
                    <span className="text-sm font-medium text-muted-foreground">Current State</span>
                    <span className={`text-xl font-bold px-3 py-1 rounded-full border ${status === 'RUNNING' ? 'bg-green-100 text-green-700 border-green-200' :
                            status === 'EMERGENCY_STOP' ? 'bg-red-100 text-red-700 border-red-200' :
                                status === 'ERROR' ? 'bg-orange-100 text-orange-700 border-orange-200' :
                                    'bg-gray-100 text-gray-700 border-gray-200'
                        }`}>
                        {status}
                    </span>
                </div>

                {/* Actions */}
                <div className="flex gap-2 w-full sm:w-auto">
                    {/* START BUTTON */}
                    {!isRunning && (
                        <Button
                            className="flex-1 sm:flex-none bg-green-600 hover:bg-green-700 text-white"
                            onClick={() => handleStateChange('RUNNING', 'User Manually Started System')}
                            disabled={isLoading || isError}
                        >
                            <Play className="mr-2 h-4 w-4" /> Start
                        </Button>
                    )}

                    {/* STOP BUTTON (Normal Stop) */}
                    {isRunning && (
                        <Button
                            variant="outline"
                            className="flex-1 sm:flex-none"
                            onClick={() => handleStateChange('STOPPED', 'User Manually Paused System')}
                            disabled={isLoading}
                        >
                            <Octagon className="mr-2 h-4 w-4" /> Pause
                        </Button>
                    )}

                    {/* EMERGENCY STOP - ALWAYS VISIBLE if not already in E-STOP */}
                    {status !== 'EMERGENCY_STOP' && (
                        <Button
                            variant="destructive"
                            className="flex-1 sm:flex-none"
                            onClick={() => handleStateChange('EMERGENCY_STOP', 'USER TRIGGERED E-STOP')}
                            disabled={isLoading}
                        >
                            <AlertCircle className="mr-2 h-4 w-4" /> E-STOP
                        </Button>
                    )}

                    {/* RESET BUTTON - Visible on Error or E-Stop */}
                    {(isError || status === 'EMERGENCY_STOP') && (
                        <Button
                            variant="secondary"
                            className="flex-1 sm:flex-none"
                            onClick={() => handleStateChange('STOPPED', 'User Reset System')}
                            disabled={isLoading}
                        >
                            <RefreshCw className="mr-2 h-4 w-4" /> Reset
                        </Button>
                    )}
                </div>
            </CardContent>
        </Card>
    );
}
