'use client';
import { useEffect, useState } from 'react';
import { Card, CardHeader, CardTitle, CardContent, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Table, TableHeader, TableRow, TableHead, TableBody, TableCell } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { PlusCircle, Power, RefreshCcw, Trash2, Settings, Plug, Network, Bot, Box, Cpu } from "lucide-react";
import { EquipmentApi } from "@/lib/api";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger, DialogFooter } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { toast } from "sonner";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

// Equipment type icons and metadata (Monochrome)
const EQUIPMENT_TYPES = {
    CONVEYOR: { label: 'Conveyor', icon: Box, protocol: 'MODBUS' },
    MOVU: { label: 'Movu AS/RS', icon: Network, protocol: 'REST' },
    KUKA: { label: 'KUKA Robot', icon: Bot, protocol: 'KRL' },
    CYKLOP_WRAPPER: { label: 'Cyklop Stretch Wrapper', icon: Box, protocol: 'MODBUS' },
    BECKHOFF_PLC: { label: 'Beckhoff TwinCAT (ADS)', icon: Cpu, protocol: 'ADS' },
    SIEMENS_PLC: { label: 'Siemens S7 PLC', icon: Cpu, protocol: 'S7' },
    AMR_ROBOT: { label: 'AMR Robot', icon: Bot, protocol: 'REST' },
    CUSTOM: { label: 'Custom Device', icon: Plug, protocol: 'REST' },
};

export default function EquipmentPage() {
    const [equipment, setEquipment] = useState<any[]>([]);
    const [loading, setLoading] = useState(true);
    const [isNewOpen, setIsNewOpen] = useState(false);
    const [editingId, setEditingId] = useState<string | null>(null);

    // New Equipment Form
    const [newName, setNewName] = useState('');
    const [newType, setNewType] = useState<keyof typeof EQUIPMENT_TYPES>('CONVEYOR');
    const [newIp, setNewIp] = useState('192.168.1.100');
    const [newPort, setNewPort] = useState(502);
    const [newProtocol, setNewProtocol] = useState('MODBUS');

    // Extra fields for specific protocols
    const [adsNetId, setAdsNetId] = useState('5.80.201.232.1.1');
    const [movuApiKey, setMovuApiKey] = useState('');
    const [kukaProject, setKukaProject] = useState('');

    const fetchEquipment = async () => {
        try {
            setLoading(true);
            const res = await EquipmentApi.getAll();
            setEquipment(res.data);
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchEquipment();
    }, []);

    useEffect(() => {
        // Auto-set protocol based on type ONLY if adding new, not editing
        if (!editingId) {
            const typeInfo = EQUIPMENT_TYPES[newType];
            if (typeInfo) {
                setNewProtocol(typeInfo.protocol);
                // Set default ports
                if (newType === 'BECKHOFF_PLC') setNewPort(851);
                else if (newType === 'SIEMENS_PLC') setNewPort(102);
                else if (newType === 'MOVU' || newType === 'AMR_ROBOT') setNewPort(443);
                else if (newType === 'KUKA') setNewPort(7000);
                else setNewPort(502);
            }
        }
    }, [newType, editingId]);

    const handleSave = async () => {
        try {
            const payload: any = {
                name: newName,
                type: newType,
                ipAddress: newIp,
                port: newPort,
                protocol: newProtocol,
                apiKey: movuApiKey, // Top-level apiKey
            };

            // Add extra config as JSON
            const extraConfig: any = {};
            if (newType === 'BECKHOFF_PLC') extraConfig.adsNetId = adsNetId;
            // if (newType === 'MOVU') extraConfig.apiKey = movuApiKey; // No longer needed in configJson
            if (newType === 'KUKA') extraConfig.project = kukaProject;
            if (Object.keys(extraConfig).length > 0) {
                payload.configJson = JSON.stringify(extraConfig);
            }

            if (editingId) {
                await EquipmentApi.update(editingId, payload);
                toast.success("Equipment updated");
            } else {
                await EquipmentApi.create(payload);
                toast.success("Equipment added");
            }

            setIsNewOpen(false);
            resetForm();
            fetchEquipment();
        } catch (err) {
            toast.error("Failed to save equipment");
        }
    };

    const handleEdit = (item: any) => {
        setEditingId(item.id);
        setNewName(item.name);
        setNewType(item.type);
        setNewIp(item.ipAddress || '');
        setNewPort(item.port || 0);
        setNewProtocol(item.protocol || 'REST');
        setMovuApiKey(item.apiKey || ''); // Read from top-level

        if (item.configJson) {
            try {
                const config = JSON.parse(item.configJson);
                if (config.adsNetId) setAdsNetId(config.adsNetId);
                // if (config.apiKey) setMovuApiKey(config.apiKey); // Backward compatibility?
                if (config.project) setKukaProject(config.project);
            } catch (e) { /* ignore */ }
        }
        setIsNewOpen(true);
    };

    const resetForm = () => {
        setEditingId(null);
        setNewName('');
        setNewType('CONVEYOR');
        setNewIp('192.168.1.100');
        setNewPort(502);
        setAdsNetId('5.80.201.232.1.1');
        setMovuApiKey('');
        setKukaProject('');
    };

    const handleDelete = async (id: string) => {
        if (!confirm("Delete equipment?")) return;
        try {
            await EquipmentApi.delete(id);
            toast.success("Equipment deleted");
            fetchEquipment();
        } catch (err) {
            toast.error("Failed to delete");
        }
    };

    const handleTestConnection = async (item: any) => {
        toast.info(`Testing connection to ${item.name}...`);
        try {
            const res = await EquipmentApi.testConnection(item.id);
            if (res.data.success) {
                toast.success(res.data.message);
            } else {
                toast.error(res.data.message);
            }
            fetchEquipment(); // Refresh status
        } catch (err) {
            toast.error("Connection test failed (Network Error)");
        }
    };

    const getTypeInfo = (type: string) => EQUIPMENT_TYPES[type as keyof typeof EQUIPMENT_TYPES] || EQUIPMENT_TYPES.CUSTOM;

    return (
        <div className="flex flex-col h-full gap-4 p-4">
            {/* Header */}
            <div className="flex items-center justify-between bg-white p-4 rounded-lg shadow-sm border border-gray-200">
                <div className="flex items-center gap-4">
                    <div className="p-2 bg-gray-100 rounded-md text-gray-700">
                        <Plug className="w-6 h-6" />
                    </div>
                    <div>
                        <h1 className="text-2xl font-bold font-headline text-gray-900">Equipment Management</h1>
                        <p className="text-sm text-gray-500">Configure and monitor hardware connections</p>
                    </div>
                </div>

                <Dialog open={isNewOpen} onOpenChange={(open) => {
                    setIsNewOpen(open);
                    if (!open) resetForm();
                }}>
                    <DialogTrigger asChild>
                        <Button className="bg-black hover:bg-gray-800 text-white">
                            <PlusCircle className="mr-2 h-4 w-4" />
                            Add Device
                        </Button>
                    </DialogTrigger>
                    <DialogContent className="sm:max-w-[600px]">
                        <DialogHeader><DialogTitle>{editingId ? 'Edit Equipment' : 'Add New Equipment'}</DialogTitle></DialogHeader>
                        <Tabs defaultValue="general" className="w-full">
                            <TabsList className="grid w-full grid-cols-2">
                                <TabsTrigger value="general">General</TabsTrigger>
                                <TabsTrigger value="connection">Connection</TabsTrigger>
                            </TabsList>

                            <TabsContent value="general" className="space-y-4 pt-4">
                                <div className="grid gap-2">
                                    <Label>Device Name</Label>
                                    <Input value={newName} onChange={e => setNewName(e.target.value)} placeholder="CONV-01" />
                                </div>
                                <div className="grid gap-2">
                                    <Label>Equipment Type</Label>
                                    <Select value={newType} onValueChange={(v) => setNewType(v as keyof typeof EQUIPMENT_TYPES)}>
                                        <SelectTrigger><SelectValue /></SelectTrigger>
                                        <SelectContent>
                                            {Object.entries(EQUIPMENT_TYPES).map(([key, val]) => (
                                                <SelectItem key={key} value={key}>
                                                    <span className="flex items-center gap-2">
                                                        <val.icon className="h-4 w-4 text-gray-600" />
                                                        {val.label}
                                                    </span>
                                                </SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                </div>
                            </TabsContent>

                            <TabsContent value="connection" className="space-y-4 pt-4">
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="grid gap-2">
                                        <Label>IP Address / Hostname</Label>
                                        <Input value={newIp} onChange={e => setNewIp(e.target.value)} />
                                    </div>
                                    <div className="grid gap-2">
                                        <Label>Port</Label>
                                        <Input type="number" value={newPort} onChange={e => setNewPort(Number(e.target.value))} />
                                    </div>
                                </div>
                                <div className="grid gap-2">
                                    <Label>Protocol</Label>
                                    <Select value={newProtocol} onValueChange={setNewProtocol}>
                                        <SelectTrigger><SelectValue /></SelectTrigger>
                                        <SelectContent>
                                            <SelectItem value="REST">REST API</SelectItem>
                                            <SelectItem value="MODBUS">Modbus TCP</SelectItem>
                                            <SelectItem value="ADS">Beckhoff ADS</SelectItem>
                                            <SelectItem value="S7">Siemens S7</SelectItem>
                                            <SelectItem value="KRL">KUKA KRL</SelectItem>
                                            <SelectItem value="OPC_UA">OPC-UA</SelectItem>
                                        </SelectContent>
                                    </Select>
                                </div>

                                {/* Protocol-specific fields (Monochrome) */}
                                {newType === 'BECKHOFF_PLC' && (
                                    <div className="grid gap-2 p-3 bg-gray-50 rounded border border-gray-200">
                                        <Label className="text-gray-900 font-medium">ADS Net ID</Label>
                                        <Input value={adsNetId} onChange={e => setAdsNetId(e.target.value)} placeholder="5.80.201.232.1.1" />
                                        <p className="text-xs text-gray-500">TwinCAT AMS Net ID (e.g., from TwinCAT System Manager)</p>
                                    </div>
                                )}

                                {newType === 'MOVU' && (
                                    <div className="grid gap-2 p-3 bg-gray-50 rounded border border-gray-200">
                                        <Label className="text-gray-900 font-medium">Movu API Key</Label>
                                        <Input type="password" value={movuApiKey} onChange={e => setMovuApiKey(e.target.value)} />
                                        <p className="text-xs text-gray-500">Obtain from Movu Atlas dashboard</p>
                                    </div>
                                )}

                                {newType === 'KUKA' && (
                                    <div className="grid gap-2 p-3 bg-gray-50 rounded border border-gray-200">
                                        <Label className="text-gray-900 font-medium">KUKA Project Name</Label>
                                        <Input value={kukaProject} onChange={e => setKukaProject(e.target.value)} placeholder="Palletizing_Cell_1" />
                                    </div>
                                )}
                            </TabsContent>
                        </Tabs>
                        <DialogFooter>
                            <Button variant="outline" onClick={() => setIsNewOpen(false)}>Cancel</Button>
                            <Button onClick={handleSave} className="bg-black hover:bg-gray-800 text-white">Save to Database</Button>
                        </DialogFooter>
                    </DialogContent>
                </Dialog>
            </div>

            {/* Equipment Table */}
            <Card className="flex-1 border-gray-200 shadow-sm">
                <CardContent className="p-0">
                    <Table>
                        <TableHeader>
                            <TableRow className="hover:bg-gray-50">
                                <TableHead className="font-semibold text-gray-700">Device</TableHead>
                                <TableHead className="font-semibold text-gray-700">Type</TableHead>
                                <TableHead className="font-semibold text-gray-700">Protocol</TableHead>
                                <TableHead className="font-semibold text-gray-700">Connection</TableHead>
                                <TableHead className="font-semibold text-gray-700">Status</TableHead>
                                <TableHead className="font-semibold text-gray-700">Actions</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {loading ? <TableRow><TableCell colSpan={6}>Loading...</TableCell></TableRow> :
                                equipment.map((item) => {
                                    const typeInfo = getTypeInfo(item.type);
                                    const TypeIcon = typeInfo.icon;
                                    return (
                                        <TableRow key={item.id} className="hover:bg-gray-50">
                                            <TableCell className="font-medium">
                                                <div className="flex items-center gap-2">
                                                    <TypeIcon className="h-5 w-5 text-gray-600" />
                                                    <div>
                                                        <div className="text-gray-900">{item.name}</div>
                                                        <div className="text-xs text-gray-400">{item.id?.substring(0, 8)}</div>
                                                    </div>
                                                </div>
                                            </TableCell>
                                            <TableCell>
                                                <Badge variant="outline" className="text-gray-600 border-gray-300">{typeInfo.label}</Badge>
                                            </TableCell>
                                            <TableCell className="font-mono text-xs text-gray-600">{item.protocol || 'N/A'}</TableCell>
                                            <TableCell className="font-mono text-xs text-gray-600">
                                                {item.ipAddress ? `${item.ipAddress}:${item.port}` : '-'}
                                            </TableCell>
                                            <TableCell>
                                                <Badge
                                                    variant="secondary"
                                                    className={
                                                        item.status === 'ONLINE' || item.status === 'IDLE' ? 'bg-black text-white hover:bg-black/90' :
                                                            item.status === 'BUSY' ? 'bg-gray-200 text-gray-800 hover:bg-gray-300' :
                                                                'bg-white text-gray-500 border border-gray-300'
                                                    }
                                                >
                                                    <Power className="h-3 w-3 mr-1" />
                                                    {item.status}
                                                </Badge>
                                            </TableCell>
                                            <TableCell>
                                                <div className="flex gap-1">
                                                    <Button variant="ghost" size="icon" title="Test Connection" onClick={() => handleTestConnection(item)}>
                                                        <RefreshCcw className="h-4 w-4 text-gray-500" />
                                                    </Button>
                                                    <Button variant="ghost" size="icon" title="Settings" onClick={() => handleEdit(item)}>
                                                        <Settings className="h-4 w-4 text-gray-500" />
                                                    </Button>
                                                    <Button variant="ghost" size="icon" className="text-gray-400 hover:text-red-500" onClick={() => handleDelete(item.id)}>
                                                        <Trash2 className="h-4 w-4" />
                                                    </Button>
                                                </div>
                                            </TableCell>
                                        </TableRow>
                                    );
                                })}
                            {!loading && equipment.length === 0 && (
                                <TableRow>
                                    <TableCell colSpan={6} className="text-center py-8 text-gray-500">
                                        <Plug className="h-12 w-12 mx-auto mb-2 opacity-20" />
                                        No equipment configured. Click "Add Device" to get started.
                                    </TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </CardContent>
            </Card>
        </div>
    );
}
