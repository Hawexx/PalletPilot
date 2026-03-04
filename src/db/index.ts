/**
 * @deprecated Direct database access from frontend is no longer used.
 * All data flows through the backend REST API (see src/lib/api.ts).
 * This module is kept for backward compatibility only.
 */
import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import * as dotenv from "dotenv";

dotenv.config();

if (!process.env.DATABASE_URL) {
    console.warn("[DRIZZLE] DATABASE_URL is not set — direct DB access is disabled (this is expected, use backend API instead)");
}

const client = process.env.DATABASE_URL ? postgres(process.env.DATABASE_URL) : null;
export const db = client ? drizzle(client) : null as any;
