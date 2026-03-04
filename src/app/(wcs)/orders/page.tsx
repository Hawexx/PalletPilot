'use client';
import { useEffect, useState } from 'react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent, CardDescription } from "@/components/ui/card";
import { PlusCircle, Trash2, Eye, Save, X, Package } from "lucide-react";
import { OrderApi, PalletApi } from "@/lib/api";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger, DialogFooter } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";

// Types
interface OrderItem {
  id: string;
  sku: string;
  widthMm: number;
  lengthMm: number;
  heightMm: number;
  weightKg: number;
  quantity: number;
}

interface Order {
  id: string;
  wmsOrderId: string;
  status: string;
  priority: number;
  createdAt: string;
  items?: OrderItem[];
}

interface NewItem {
  sku: string;
  widthMm: number;
  lengthMm: number;
  heightMm: number;
  weightKg?: number | string; // Allow string for empty input
  quantity: number;
}

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [pallets, setPallets] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  // Create Dialog State
  const [isNewOrderOpen, setIsNewOrderOpen] = useState(false);
  const [newOrderId, setNewOrderId] = useState('');
  const [newPriority, setNewPriority] = useState(5);
  const [newItems, setNewItems] = useState<NewItem[]>([
    { sku: 'SKU-001', widthMm: 300, lengthMm: 200, heightMm: 100, weightKg: 1.5, quantity: 10 }
  ]);

  // Edit Dialog State
  const [isEditOrderOpen, setIsEditOrderOpen] = useState(false);
  const [editingOrder, setEditingOrder] = useState<Order | null>(null);
  const [editItems, setEditItems] = useState<NewItem[]>([]);

  const fetchData = async () => {
    try {
      setLoading(true);
      const [ordersRes, palletsRes] = await Promise.all([
        OrderApi.getAll(),
        PalletApi.getAll()
      ]);
      setOrders(ordersRes.data);
      setPallets(palletsRes.data);
    } catch (err: any) {
      console.error("Failed to fetch data:", err);
      toast.error("Failed to load data");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleCreateOrder = async () => {
    try {
      await OrderApi.create({
        wmsOrderId: newOrderId,
        priority: newPriority,
        items: newItems.map(i => ({
          ...i,
          weightKg: (i.weightKg === '' || i.weightKg === undefined || i.weightKg === null) ? null : Number(i.weightKg)
        }))
      });
      setIsNewOrderOpen(false);
      fetchData();
      setNewOrderId('');
      setNewItems([{ sku: 'SKU-001', widthMm: 300, lengthMm: 200, heightMm: 100, weightKg: 1.5, quantity: 10 }]);
      toast.success("Order Created");
    } catch (err: any) {
      const errorMessage = err.response?.data?.message || err.message || 'Failed to create order';
      toast.error(errorMessage);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure?')) return;
    try {
      await OrderApi.delete(id);
      fetchData();
      toast.success("Order Deleted");
    } catch (err) {
      toast.error('Failed to delete');
    }
  }

  const handleDeletePallet = async (palletId: string) => {
    if (!confirm("Delete this pallet plan?")) return;
    try {
      await PalletApi.delete(palletId);
      toast.success("Pallet plan deleted");
      fetchData();
    } catch (e) {
      toast.error("Failed to delete plan");
    }
  };

  const getPalletForOrder = (orderId: string) => {
    return pallets.find(p => p.orderId === orderId);
  }

  const openEditDialog = async (order: Order) => {
    try {
      // Fetch full details including items (though usually returned in list, ensuring we have them)
      const res = await OrderApi.getById(order.id); // Or check order.items if already present
      const fullOrder = res.data;
      // If items are nested in response (due to our Recent JSON fix they should be)

      setEditingOrder(fullOrder);

      // Map existing items to editable format
      if (fullOrder.items && fullOrder.items.length > 0) {
        setEditItems(fullOrder.items.map((i: any) => ({
          sku: i.sku,
          widthMm: i.widthMm,
          lengthMm: i.lengthMm,
          heightMm: i.heightMm,
          weightKg: i.weightKg,
          quantity: i.quantity
        })));
      } else {
        setEditItems([]);
      }

      setIsEditOrderOpen(true);
    } catch (e) {
      toast.error("Failed to load details");
    }
  };

  const handleUpdateOrder = async () => {
    if (!editingOrder) return;
    try {
      await OrderApi.update(editingOrder.id, {
        priority: editingOrder.priority,
        status: editingOrder.status,
        items: editItems
      });
      setIsEditOrderOpen(false);
      fetchData();
      toast.success("Order Updated");
    } catch (e) {
      console.error(e);
      toast.error("Failed to update order");
    }
  };

  const updateEditItem = (index: number, field: keyof NewItem, value: any) => {
    const items = [...editItems];
    // @ts-ignore
    items[index][field] = value;
    setEditItems(items);
  };

  return (
    <Card className="h-full">
      <CardHeader className="flex flex-row items-center justify-between">
        <div>
          <CardTitle>Order Management</CardTitle>
          <CardDescription>Real-time orders from WMS/Manual Entry.</CardDescription>
        </div>
        <Dialog open={isNewOrderOpen} onOpenChange={setIsNewOrderOpen}>
          <DialogTrigger asChild>
            <Button>
              <PlusCircle className="mr-2 h-4 w-4" />
              New Order
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-[800px]">
            <DialogHeader>
              <DialogTitle>Create Manual Order</DialogTitle>
            </DialogHeader>
            <div className="grid gap-4 py-4">
              <div className="grid grid-cols-4 items-center gap-4">
                <Label className="text-right">Order ID</Label>
                <Input value={newOrderId} onChange={e => setNewOrderId(e.target.value)} className="col-span-3" placeholder="ORD-MAN-001" />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label className="text-right">Priority</Label>
                <Input type="number" value={newPriority} onChange={e => setNewPriority(Number(e.target.value))} className="col-span-3" />
              </div>

              <div className="border p-4 rounded-md bg-slate-50 dark:bg-slate-900">
                <div className="flex justify-between items-center mb-2">
                  <h4 className="font-bold">Order Items</h4>
                  <Button variant="outline" size="sm" onClick={() => setNewItems([...newItems, { sku: '', widthMm: 300, lengthMm: 200, heightMm: 100, weightKg: 1, quantity: 1 }])}>
                    Add Item
                  </Button>
                </div>
                {newItems.map((item, idx) => (
                  <div key={idx} className="grid grid-cols-8 gap-2 items-end mb-2 border-b pb-2">
                    <div className="col-span-2">
                      <Label className="text-xs">SKU</Label>
                      <Input placeholder="SKU" value={item.sku} onChange={e => {
                        const items = [...newItems]; items[idx].sku = e.target.value; setNewItems(items);
                      }} />
                    </div>
                    <div>
                      <Label className="text-xs">Qty</Label>
                      <Input type="number" value={item.quantity} onChange={e => {
                        const items = [...newItems]; items[idx].quantity = Number(e.target.value); setNewItems(items);
                      }} />
                    </div>
                    <div>
                      <Label className="text-xs">X (L)</Label>
                      <Input type="number" value={item.lengthMm} onChange={e => {
                        const items = [...newItems]; items[idx].lengthMm = Number(e.target.value); setNewItems(items);
                      }} />
                    </div>
                    <div>
                      <Label className="text-xs">Y (W)</Label>
                      <Input type="number" value={item.widthMm} onChange={e => {
                        const items = [...newItems]; items[idx].widthMm = Number(e.target.value); setNewItems(items);
                      }} />
                    </div>
                    <div>
                      <Label className="text-xs">Z (H)</Label>
                      <Input type="number" value={item.heightMm} onChange={e => {
                        const items = [...newItems]; items[idx].heightMm = Number(e.target.value); setNewItems(items);
                      }} />
                    </div>
                    <div>
                      <Label className="text-xs">Weight (kg)</Label>
                      <Input type="number" step="0.1" placeholder="Optional" value={item.weightKg ?? ''} onChange={e => {
                        const items = [...newItems]; items[idx].weightKg = e.target.value; setNewItems(items);
                      }} />
                    </div>
                    <Button variant="ghost" size="icon" className="text-red-500" onClick={() => {
                      setNewItems(newItems.filter((_, i) => i !== idx));
                    }}><X className="h-4 w-4" /></Button>
                  </div>
                ))}
              </div>
              <Button onClick={handleCreateOrder}>Submit Order</Button>
            </div>
          </DialogContent>
        </Dialog>

        {/* EDIT DIALOG */}
        <Dialog open={isEditOrderOpen} onOpenChange={setIsEditOrderOpen}>
          <DialogContent className="sm:max-w-[800px]">
            <DialogHeader>
              <DialogTitle>Edit Order {editingOrder?.wmsOrderId || editingOrder?.id?.substring(0, 8)}</DialogTitle>
            </DialogHeader>
            <div className="grid gap-4 py-4">
              <div className="grid grid-cols-4 items-center gap-4">
                <Label className="text-right">Priority</Label>
                <Input type="number" value={editingOrder?.priority || 5} onChange={e => setEditingOrder(prev => prev ? { ...prev, priority: Number(e.target.value) } : null)} className="col-span-3" />
              </div>

              <div className="border p-4 rounded-md bg-slate-50 dark:bg-slate-900">
                <div className="flex justify-between items-center mb-2">
                  <h4 className="font-bold">Items Configuration</h4>
                  <Button variant="outline" size="sm" onClick={() => setEditItems([...editItems, { sku: '', widthMm: 300, lengthMm: 200, heightMm: 100, weightKg: 1, quantity: 1 }])}>
                    Add Item
                  </Button>
                </div>
                {editItems.map((item, idx) => (
                  <div key={idx} className="grid grid-cols-8 gap-2 items-end mb-2 border-b pb-2">
                    <div className="col-span-1">
                      <Label className="text-xs">SKU</Label>
                      <Input value={item.sku} onChange={e => updateEditItem(idx, 'sku', e.target.value)} />
                    </div>
                    <div className="col-span-1">
                      <Label className="text-xs">Qty</Label>
                      <Input type="number" value={item.quantity} onChange={e => updateEditItem(idx, 'quantity', Number(e.target.value))} />
                    </div>
                    <div>
                      <Label className="text-xs">Length (X)</Label>
                      <Input type="number" value={item.lengthMm} onChange={e => updateEditItem(idx, 'lengthMm', Number(e.target.value))} />
                    </div>
                    <div>
                      <Label className="text-xs">Width (Y)</Label>
                      <Input type="number" value={item.widthMm} onChange={e => updateEditItem(idx, 'widthMm', Number(e.target.value))} />
                    </div>
                    <div>
                      <Label className="text-xs">Height (Z)</Label>
                      <Input type="number" value={item.heightMm} onChange={e => updateEditItem(idx, 'heightMm', Number(e.target.value))} />
                    </div>
                    <div>
                      <Label className="text-xs">Weight (kg)</Label>
                      <Input type="number" step="0.1" placeholder="Optional" value={item.weightKg ?? ''} onChange={e => updateEditItem(idx, 'weightKg', e.target.value)} />
                    </div>
                    <Button variant="ghost" size="icon" className="text-red-500" onClick={() => {
                      setEditItems(editItems.filter((_, i) => i !== idx));
                    }}><X className="h-4 w-4" /></Button>
                  </div>
                ))}
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setIsEditOrderOpen(false)}>Cancel</Button>
              <Button onClick={handleUpdateOrder}><Save className="mr-2 h-4 w-4" /> Save Changes</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Order ID</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Pallet Plan</TableHead>
              <TableHead>Priority</TableHead>
              <TableHead>Created At</TableHead>
              <TableHead>Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? <TableRow><TableCell colSpan={6}>Loading...</TableCell></TableRow> :
              orders.map((order) => {
                const pallet = getPalletForOrder(order.id);
                return (
                  <TableRow key={order.id}>
                    <TableCell className="font-medium">{order.wmsOrderId || order.id || 'N/A'}</TableCell>
                    <TableCell>
                      <Badge
                        className={
                          order.status === 'COMPLETED' ? 'bg-gray-200 text-gray-800' :
                            order.status === 'CREATED' ? 'bg-blue-100 text-blue-800' :
                              'bg-yellow-100 text-yellow-800'
                        }
                      >
                        {order.status}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {pallet ? (
                        <div className="flex items-center gap-2">
                          <Badge variant="secondary" className="bg-green-100 text-green-800">
                            <Package className="w-3 h-3 mr-1" />
                            {pallet.volumeEfficiency ? `${pallet.volumeEfficiency.toFixed(0)}% Dense` : 'Planned'}
                          </Badge>
                          <Button variant="ghost" size="icon" className="h-6 w-6 text-red-500" title="Delete Plan" onClick={() => handleDeletePallet(pallet.id)}>
                            <X className="h-3 w-3" />
                          </Button>
                        </div>
                      ) : (
                        <span className="text-gray-400 text-xs">-</span>
                      )}
                    </TableCell>
                    <TableCell>{order.priority}</TableCell>
                    <TableCell>
                      {order.createdAt ? new Date(order.createdAt).toLocaleString() : '-'}
                    </TableCell>
                    <TableCell className="flex gap-2">
                      <Button variant="outline" size="sm" onClick={() => openEditDialog(order)}>
                        <Eye className="h-4 w-4" />
                      </Button>
                      <Button variant="destructive" size="sm" onClick={() => handleDelete(order.id)}>
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                )
              })}
            {!loading && orders.length === 0 && <TableRow><TableCell colSpan={6}>No orders found.</TableCell></TableRow>}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
