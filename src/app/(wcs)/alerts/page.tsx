'use client';
import { useEffect, useState } from 'react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader, CardTitle, CardContent, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { AlertTriangle, Info, ShieldAlert, Trash2, X } from "lucide-react";
import { AlertApi } from "@/lib/api";
import { useWebSocket } from "@/hooks/useWebSocket";
import { toast } from 'sonner';

export default function AlertsPage() {
  const [alerts, setAlerts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchAlerts = async () => {
    try {
      const res = await AlertApi.getAll();
      setAlerts(res.data);
    } catch (err) {
      console.error(err);
      // toast.error("Failed to load alerts"); // Optional
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAlerts();
    // Fallback polling (extended interval since WebSocket handles instant updates)
    const interval = setInterval(fetchAlerts, 30000);
    return () => clearInterval(interval);
  }, []);

  // Real-time WebSocket subscription
  useWebSocket('/topic/alerts', (envelope) => {
    // Prepend new alert to list without full refetch
    if (envelope.data) {
      setAlerts(prev => [envelope.data as any, ...prev]);
    }
  });

  const handleClearAll = async () => {
    if (!confirm("Clear all alerts?")) return;
    try {
      await AlertApi.deleteAll();
      fetchAlerts();
      toast.success("All alerts cleared");
    } catch (err) {
      toast.error("Failed to clear alerts");
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await AlertApi.delete(id);
      // Optimistic update
      setAlerts(prev => prev.filter(a => a.id !== id));
      toast.success("Alert deleted");
    } catch (err) {
      toast.error("Failed to delete alert");
      fetchAlerts();
    }
  };

  return (
    <Card className="h-full">
      <CardHeader className="flex flex-row items-center justify-between">
        <div>
          <CardTitle>Error & Alert Center</CardTitle>
          <CardDescription>Real-time alerts from Movu, Kuka, Conveyors, and WCS.</CardDescription>
        </div>
        <Button variant="destructive" size="sm" onClick={handleClearAll} disabled={alerts.length === 0}>
          <Trash2 className="h-4 w-4 mr-2" />
          Clear All
        </Button>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[120px]">Severity</TableHead>
              <TableHead>Message</TableHead>
              <TableHead className="w-[200px]">Time</TableHead>
              <TableHead className="w-[80px]">Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? <TableRow><TableCell colSpan={4}>Loading...</TableCell></TableRow> :
              alerts.length === 0 ? <TableRow><TableCell colSpan={4}>No alerts found.</TableCell></TableRow> :
                alerts.map((alert) => (
                  <TableRow key={alert.id}>
                    <TableCell>
                      <Badge variant={alert.severity === 'CRITICAL' ? 'destructive' : alert.severity === 'WARNING' ? 'secondary' : 'outline'}>
                        <div className="flex items-center gap-2">
                          {alert.severity === 'CRITICAL' && <AlertTriangle className="h-3 w-3" />}
                          {alert.severity === 'WARNING' && <ShieldAlert className="h-3 w-3" />}
                          {alert.severity === 'INFO' && <Info className="h-3 w-3" />}
                          {alert.severity}
                        </div>
                      </Badge>
                    </TableCell>
                    <TableCell className="font-medium">{alert.message}</TableCell>
                    <TableCell className="text-muted-foreground text-sm">{new Date(alert.createdAt).toLocaleString()}</TableCell>
                    <TableCell>
                      <Button variant="ghost" size="icon" className="h-8 w-8 text-red-500 hover:text-red-700 hover:bg-red-50" onClick={() => handleDelete(alert.id)}>
                        <X className="h-4 w-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
