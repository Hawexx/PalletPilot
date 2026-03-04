'use server';

// Mock data type for the rule-based algorithm output
export interface MixPalletAlgorithmPreviewOutput {
  palletComposition: string;
  stabilityScore: number;
  weightBalanceVisualization: string;
  productGroupingExplanation: string;
  ruleExplanation: { rule: string; explanation: string }[];
}

export interface MixPalletAlgorithmPreviewInput {
  orderRequirements: string;
  palletConstraints: string;
  algorithmGoals: string;
}

// A deterministic "algorithm" based on input length to provide some variability
function generateMockData(input: MixPalletAlgorithmPreviewInput): MixPalletAlgorithmPreviewOutput {
  const totalLength = input.orderRequirements.length + input.palletConstraints.length + input.algorithmGoals.length;
  const stability = 80 + (totalLength % 15); // Score between 80 and 94

  return {
    palletComposition: `Layer 1: 4 units of Product B at the base for weight.
Layer 2: 6 units of Product A, distributed evenly.
Layer 3: 6 units of Product A, completing the product group.
Layer 4: 10 units of Product C, lighter items on top.
Layer 5: 10 units of Product C, filling the remaining space.`,
    stabilityScore: stability,
    weightBalanceVisualization: `
+-----------------+
|      TOP        |
| C C C C C C C C |
| C C C C C C C C |
| A A A A A A A A |
| B B B B B B B B |
|      BASE       |
+-----------------+
Center of Gravity: X: 58cm, Y: 49cm (Offset: 2cm)`,
    productGroupingExplanation: "Products A and C were grouped into contiguous layers to improve picking efficiency and stability. Heavier Product B forms the foundation.",
    ruleExplanation: [
      { rule: "Heavier Box Placement", explanation: "Product B (8kg) was placed on Layer 1 as it is the heaviest item, creating a low and stable center of gravity." },
      { rule: "Identical Product Grouping", explanation: "All units of Product A and Product C were grouped into their own contiguous layers to maintain stability and simplify stacking." },
      { rule: "Weight Distribution", explanation: "The even placement of boxes on each layer keeps the center-of-gravity near the pallet's geometric center, preventing dangerous imbalances." },
      { rule: "Boundary Constraints", explanation: "All box placements were verified to be within the 120x100cm pallet boundaries, ensuring no overhang." }
    ]
  };
}

import { SignJWT } from 'jose';
import { cookies } from 'next/headers';
import { revalidatePath } from 'next/cache';

// ── Session secret: externalized via env var ──
// In production, set SESSION_SECRET to a strong random value (min 32 chars)
const SECRET_KEY = new TextEncoder().encode(
  process.env.SESSION_SECRET || 'dev-session-secret-change-in-prod'
);

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

export async function login(username: string, password: string): Promise<{ success: boolean; error?: string }> {
  try {
    // Call Backend to verify credentials
    const response = await fetch(`${API_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });

    if (!response.ok) {
      return { success: false, error: 'Invalid credentials' };
    }

    // Backend validated credentials → create Next.js session token
    const token = await new SignJWT({ username, role: 'admin' })
      .setProtectedHeader({ alg: 'HS256' })
      .setIssuedAt()
      .setExpirationTime('24h')
      .sign(SECRET_KEY);

    const cookieStore = await cookies();
    cookieStore.set('session', token, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'strict',
      maxAge: 60 * 60 * 24, // 24 hours
    });

    return { success: true };

  } catch (error) {
    console.error("Login verification failed:", error);
    return { success: false, error: 'System error during login' };
  }
}

export async function logout() {
  const cookieStore = await cookies();
  cookieStore.delete('session');
}

/**
 * Fetch all orders from the backend API.
 * Previously used Drizzle ORM for direct DB access — now routes through backend
 * to ensure consistent schema (UUID IDs, ProcessState enum, OrderItem relations).
 */
export async function getOrders() {
  try {
    const res = await fetch(`${API_URL}/orders`, { cache: 'no-store' });
    if (!res.ok) throw new Error(`API returned ${res.status}`);
    const data = await res.json();
    return { data, error: null };
  } catch (e) {
    console.error('Failed to fetch orders:', e);
    return { data: null, error: 'Failed to fetch orders' };
  }
}

/**
 * Create a new order via the backend API.
 * Backend handles validation, ERP integration, and proper schema enforcement.
 */
export async function createOrder(orderData: Record<string, unknown>) {
  try {
    const res = await fetch(`${API_URL}/orders`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(orderData),
    });
    if (!res.ok) throw new Error(`API returned ${res.status}`);
    revalidatePath('/orders');
    return { success: true };
  } catch (e) {
    console.error('Failed to create order:', e);
    return { success: false, error: 'Failed to create order' };
  }
}

// Keeping the algorithm preview as a calculation for now
export async function getMixPalletAlgorithmPreview(
  input: MixPalletAlgorithmPreviewInput
): Promise<{ data: MixPalletAlgorithmPreviewOutput | null; error: string | null }> {
  // Simulate network delay
  await new Promise(resolve => setTimeout(resolve, 500));

  try {
    const result = generateMockData(input);
    return { data: result, error: null };
  } catch (e) {
    console.error('Algorithm Error:', e);
    const errorMessage = e instanceof Error ? e.message : 'An unknown error occurred.';
    return { data: null, error: `Failed to generate preview: ${errorMessage}` };
  }
}

