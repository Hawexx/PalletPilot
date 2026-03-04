'use client';

import { useState, useEffect } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Badge } from '@/components/ui/badge';
import { Switch } from '@/components/ui/switch';
import { Settings, Activity, Save } from 'lucide-react';
import { ConfigApi, SystemConfig } from '@/lib/api';
import { toast } from 'sonner';

export default function SettingsPage() {
    const [configs, setConfigs] = useState<SystemConfig[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetchConfigs();
    }, []);

    const fetchConfigs = async () => {
        try {
            const res = await ConfigApi.getAll();
            setConfigs(res.data);
        } catch (error) {
            console.error('Failed to fetch config', error);
            toast.error("Failed to load settings");
        } finally {
            setLoading(false);
        }
    };

    const updateConfig = async (key: string, value: string) => {
        try {
            // Optimistic update
            setConfigs(prev => prev.map(c => c.key === key ? { ...c, value } : c));
            await ConfigApi.update(key, value);
            toast.success("Saved");
        } catch (error) {
            toast.error("Failed to save");
            fetchConfigs(); // Revert
        }
    };

    const groupConfigs = () => {
        const groups: Record<string, SystemConfig[]> = {};
        configs.forEach(c => {
            const cat = c.category || 'OTHER';
            if (!groups[cat]) groups[cat] = [];
            groups[cat].push(c);
        });
        return groups;
    };

    if (loading) {
        return <div className="flex items-center justify-center h-full">
            <Activity className="h-8 w-8 animate-spin text-gray-900" />
        </div>;
    }

    const groups = groupConfigs();
    const categories = Object.keys(groups);

    return (
        <div className="flex flex-col gap-6 p-6">
            <div>
                <h1 className="text-3xl font-bold tracking-tight text-gray-900">System Configuration</h1>
                <p className="text-muted-foreground mt-1">
                    Manage system-wide settings dynamically. Changes take effect immediately.
                </p>
            </div>

            <Tabs defaultValue={categories[0]} className="space-y-6">
                <TabsList className="bg-gray-100 text-gray-500 w-full justify-start overflow-x-auto">
                    {categories.map(cat => (
                        <TabsTrigger key={cat} value={cat} className="data-[state=active]:bg-white data-[state=active]:text-gray-900 shadow-none">
                            {cat}
                        </TabsTrigger>
                    ))}
                </TabsList>

                {categories.map(cat => (
                    <TabsContent key={cat} value={cat} className="mt-0 space-y-4">
                        <Card className="p-6 border-gray-200">
                            <h2 className="text-xl font-semibold text-gray-900 mb-4">{cat} Settings</h2>
                            <div className="space-y-6">
                                {groups[cat].map(cfg => (
                                    <div key={cfg.key} className="grid gap-2">
                                        <div className="flex items-center justify-between">
                                            <Label htmlFor={cfg.key} className="font-medium text-gray-700">
                                                {cfg.key}
                                            </Label>
                                            {cfg.description && (
                                                <span className="text-xs text-gray-400">{cfg.description}</span>
                                            )}
                                        </div>

                                        {cfg.value === 'true' || cfg.value === 'false' ? (
                                            <div className="flex items-center space-x-2">
                                                <Switch
                                                    id={cfg.key}
                                                    checked={cfg.value === 'true'}
                                                    onCheckedChange={(checked) => updateConfig(cfg.key, String(checked))}
                                                />
                                                <span className="text-sm text-gray-500">{cfg.value === 'true' ? 'Enabled' : 'Disabled'}</span>
                                            </div>
                                        ) : (
                                            <div className="flex gap-2">
                                                <Input
                                                    id={cfg.key}
                                                    defaultValue={cfg.value}
                                                    onBlur={(e) => {
                                                        if (e.target.value !== cfg.value) {
                                                            updateConfig(cfg.key, e.target.value);
                                                        }
                                                    }}
                                                />
                                            </div>
                                        )}
                                    </div>
                                ))}
                            </div>
                        </Card>
                    </TabsContent>
                ))}
            </Tabs>
        </div>
    );
}
