/**
 * PalletPilot WCS — k6 Soak / Load Test
 *
 * Modes:
 *   CI (short):  k6 run --env MODE=ci soak.js
 *   Soak (long): k6 run --env MODE=soak soak.js
 *
 * Targets:
 *   BASE_URL:  default http://localhost:8080
 *
 * Prerequisites:
 *   - Backend running: ./gradlew bootRun
 *   - k6 installed: brew install k6
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ── Custom Metrics ──
const rateLimited = new Counter('rate_limited_429');
const webhookLatency = new Trend('webhook_latency_ms');
const erpLatency = new Trend('erp_latency_ms');

// ── Config ──
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MODE = __ENV.MODE || 'ci';

const SCENARIOS = {
    ci: {
        movu_webhook: { executor: 'constant-arrival-rate', rate: 10, timeUnit: '1s', duration: '5m', preAllocatedVUs: 20, maxVUs: 50 },
        erp_webhook: { executor: 'constant-arrival-rate', rate: 5, timeUnit: '1s', duration: '5m', preAllocatedVUs: 10, maxVUs: 30 },
        api_browse: { executor: 'constant-arrival-rate', rate: 5, timeUnit: '1s', duration: '5m', preAllocatedVUs: 10, maxVUs: 20 },
    },
    soak: {
        movu_webhook: { executor: 'constant-arrival-rate', rate: 30, timeUnit: '1s', duration: '4h', preAllocatedVUs: 60, maxVUs: 150 },
        erp_webhook: { executor: 'constant-arrival-rate', rate: 20, timeUnit: '1s', duration: '4h', preAllocatedVUs: 40, maxVUs: 100 },
        api_browse: { executor: 'constant-arrival-rate', rate: 10, timeUnit: '1s', duration: '4h', preAllocatedVUs: 20, maxVUs: 50 },
    },
};

export const options = {
    scenarios: SCENARIOS[MODE],
    thresholds: {
        'http_req_failed': ['rate<0.05'],          // <5% errors
        'http_req_duration': ['p(95)<1000'],         // p95 < 1s
        'rate_limited_429': ['count<100'],           // Not too many 429s in CI
        'webhook_latency_ms': ['p(99)<2000'],
        'erp_latency_ms': ['p(99)<2000'],
    },
};

// ── Helpers ──
function randomIp() {
    return `${Math.floor(Math.random() * 223) + 1}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`;
}

function randomMissionId() {
    return `MSN-${Math.floor(Math.random() * 100000)}`;
}

// ── Scenario: Movu Webhook ──
export function movu_webhook() {
    const missionId = randomMissionId();
    const hasError = Math.random() < 0.1; // 10% error injection

    const payload = JSON.stringify({
        missionId: missionId,
        status: hasError ? 'MissionError' : 'MissionCompleted',
        timestamp: new Date().toISOString(),
        ...(hasError && { errorMessage: 'Lane full — shuttle blocked at position 7' }),
    });

    const res = http.post(`${BASE_URL}/api/movu/callback`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });

    webhookLatency.add(res.timings.duration);

    if (res.status === 429) {
        rateLimited.add(1);
    }

    check(res, {
        'movu: status 200 or 429': (r) => r.status === 200 || r.status === 429,
    });

    sleep(0.01);
}

// ── Scenario: ERP Webhook ──
export function erp_webhook() {
    const payload = JSON.stringify({
        messageId: `ERP-${Date.now()}-${Math.floor(Math.random() * 10000)}`,
        messageType: 'ORDER_CREATE',
        erpOrderId: `ORD-${Math.floor(Math.random() * 50000)}`,
        payload: {
            sku: `SKU-${String.fromCharCode(65 + Math.floor(Math.random() * 26))}${Math.floor(Math.random() * 100)}`,
            quantity: Math.floor(Math.random() * 50) + 1,
            priority: Math.random() < 0.2 ? 'HIGH' : 'NORMAL',
        },
    });

    const res = http.post(`${BASE_URL}/api/erp/webhook`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });

    erpLatency.add(res.timings.duration);

    if (res.status === 429) {
        rateLimited.add(1);
    }

    check(res, {
        'erp: status 2xx or 429': (r) => (r.status >= 200 && r.status < 300) || r.status === 429,
    });

    sleep(0.02);
}

// ── Scenario: API Browse (dashboard simulation) ──
export function api_browse() {
    const endpoints = [
        '/api/orders',
        '/api/equipment',
        '/api/alerts',
        '/api/system/state',
        '/actuator/health',
        '/actuator/prometheus',
    ];

    const endpoint = endpoints[Math.floor(Math.random() * endpoints.length)];
    const res = http.get(`${BASE_URL}${endpoint}`);

    check(res, {
        'api: status 2xx': (r) => r.status >= 200 && r.status < 300,
    });

    sleep(0.1);
}
