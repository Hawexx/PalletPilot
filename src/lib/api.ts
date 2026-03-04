import axios from 'axios';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

export const api = axios.create({
    baseURL: API_BASE_URL,
    timeout: 15000, // 15s — prevents UI freeze if backend is unresponsive
    headers: {
        'Content-Type': 'application/json',
    },
});

// Response interceptor for global error handling
api.interceptors.response.use(
    (response: any) => response,
    (error: any) => {
        // Extract the most useful error message:
        // 1. Backend ApiErrorResponse.message (our standard format)
        // 2. Backend response data as string
        // 3. Axios error message (e.g., "Network Error", "timeout of 15000ms exceeded")
        const data = error.response?.data;
        const errorMessage = data?.message
            || (typeof data === 'string' && data)
            || error.message
            || 'Unknown error';

        console.error('API Error:', errorMessage, error.response?.status ? `(${error.response.status})` : '');
        return Promise.reject(error);
    }
);

export const OrderApi = {
    getAll: () => api.get('/orders'),
    getById: (id: string) => api.get(`/orders/${id}`),
    create: (data: any) => api.post('/orders', data),
    update: (id: string, data: any) => api.put(`/orders/${id}`, data),
    delete: (id: string) => api.delete(`/orders/${id}`),
    wmsIngest: (data: any) => api.post('/wms/ingest', data),
    getItems: (id: string) => api.get(`/orders/${id}/items`),
};

export const PalletApi = {
    getAll: () => api.get('/pallets'),
    plan: (orderId: string) => api.post<any[]>('/pallets/plan', { orderId }), // Returns array of pallets
    delete: (id: string) => api.delete(`/pallets/${id}`),
};

export const EquipmentApi = {
    getAll: () => api.get('/equipment'),
    getById: (id: string) => api.get(`/equipment/${id}`),
    create: (data: any) => api.post('/equipment', data),
    update: (id: string, data: any) => api.put(`/equipment/${id}`, data),
    delete: (id: string) => api.delete(`/equipment/${id}`),
    testConnection: (id: string) => api.post(`/equipment/${id}/test`),
};

export const WarehouseFlowApi = {
    get: () => api.get('/warehouse-flow'),
    save: (json: string) => api.put('/warehouse-flow', json, { headers: { 'Content-Type': 'text/plain' } }),
};

export const AlertApi = {
    getAll: () => api.get('/alerts'),
    delete: (id: string) => api.delete(`/alerts/${id}`),
    deleteAll: () => api.delete('/alerts'),
};

export const ConfigApi = {
    getAll: () => api.get<SystemConfig[]>('/config'),
    update: (key: string, value: string) => api.post(`/config/${key}`, { value }),

    // Legacy mapping (helper methods for UI components)
    // These now just call the generic update
    saveHardware: (type: string, data: any) => {
        // This is complex because we need to map flat keys to JSON or individual keys
        // For MVP of migration, we might need a specific endpoint or handle it in UI
        // Let's assume the UI calls 'update' for specific keys for now.
        return Promise.resolve();
    },
};

export interface SystemConfig {
    key: string;
    value: string;
    category: string;
    description: string;
}

export const WarehouseViewApi = {
    get: () => api.get('/warehouse-view'),
};

// Flow execution API - sends commands to PLCs based on flow layout
export const FlowApi = {
    // Get all conveyors from the flow layout
    getConveyors: () => api.get('/flow/conveyors'),

    // Start a conveyor by node ID
    startConveyor: (nodeId: string, direction?: string) =>
        api.post(`/flow/conveyors/${nodeId}/start`, { direction }),

    // Stop a conveyor by node ID
    stopConveyor: (nodeId: string) =>
        api.post(`/flow/conveyors/${nodeId}/stop`),

    // Execute full inbound flow for a pallet
    executeInbound: (palletId: string) =>
        api.post('/flow/execute/inbound', { palletId }),

    // Execute flow from a specific node type
    executeFromNode: (nodeType: string, palletId?: string) =>
        api.post(`/flow/execute/${nodeType}`, { palletId }),
};

export const DashboardApi = {
    getStats: () => api.get('/dashboard/stats'),
};

export const SystemStateApi = {
    get: () => api.get('/system/state'),
    set: (newState: string, userId: string, reason: string) =>
        api.post('/system/state', {}, {
            params: { newState, userId, reason },
        }),
};



