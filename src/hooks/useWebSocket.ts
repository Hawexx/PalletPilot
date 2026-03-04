'use client';

import { useEffect, useRef, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = (process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api')
    .replace('/api', '') + '/ws';

interface WebSocketEnvelope<T = unknown> {
    type: string;
    timestamp: string;
    data: T;
}

/**
 * React hook for subscribing to a STOMP WebSocket topic.
 *
 * Features:
 * - Auto-connect with SockJS fallback
 * - Auto-reconnect on disconnect (max 5 attempts, exponential backoff)
 * - Clean unsubscribe + disconnect on unmount
 * - SSR-safe (client-side only)
 *
 * @param topic   STOMP destination (e.g. '/topic/dashboard')
 * @param onMessage  Callback when a message arrives
 * @param enabled  Whether to connect (default: true)
 *
 * @example
 * useWebSocket('/topic/alerts', (data) => {
 *   setAlerts(prev => [data.data, ...prev]);
 * });
 */
export function useWebSocket<T = unknown>(
    topic: string,
    onMessage: (envelope: WebSocketEnvelope<T>) => void,
    enabled: boolean = true,
) {
    const clientRef = useRef<Client | null>(null);
    const onMessageRef = useRef(onMessage);
    const reconnectAttempts = useRef(0);
    const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    // Keep callback ref fresh without re-triggering effect
    useEffect(() => {
        onMessageRef.current = onMessage;
    }, [onMessage]);

    const connect = useCallback(() => {
        if (typeof window === 'undefined') return; // SSR guard

        const client = new Client({
            webSocketFactory: () => new SockJS(WS_URL) as WebSocket,
            reconnectDelay: 0, // We handle reconnect manually
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,

            onConnect: () => {
                reconnectAttempts.current = 0;
                console.log(`[WS] Connected, subscribing to ${topic}`);

                client.subscribe(topic, (message: IMessage) => {
                    try {
                        const envelope: WebSocketEnvelope<T> = JSON.parse(message.body);
                        onMessageRef.current(envelope);
                    } catch {
                        console.warn('[WS] Failed to parse message:', message.body);
                    }
                });
            },

            onStompError: (frame) => {
                console.error('[WS] STOMP error:', frame.headers['message']);
            },

            onWebSocketClose: () => {
                const maxAttempts = 5;
                if (reconnectAttempts.current < maxAttempts) {
                    const delay = Math.min(1000 * Math.pow(2, reconnectAttempts.current), 30000);
                    reconnectAttempts.current++;
                    console.log(`[WS] Disconnected. Reconnecting in ${delay}ms (attempt ${reconnectAttempts.current}/${maxAttempts})`);
                    reconnectTimerRef.current = setTimeout(() => {
                        reconnectTimerRef.current = null;
                        if (clientRef.current) {
                            clientRef.current.activate();
                        }
                    }, delay);
                } else {
                    console.warn('[WS] Max reconnect attempts reached. Falling back to polling.');
                }
            },
        });

        clientRef.current = client;
        client.activate();
    }, [topic]);

    useEffect(() => {
        if (!enabled) return;

        connect();

        return () => {
            // Clear any pending reconnect timer
            if (reconnectTimerRef.current) {
                clearTimeout(reconnectTimerRef.current);
                reconnectTimerRef.current = null;
            }
            if (clientRef.current?.active) {
                clientRef.current.deactivate();
                clientRef.current = null;
            }
        };
    }, [connect, enabled]);

    return {
        isConnected: clientRef.current?.active ?? false,
    };
}
