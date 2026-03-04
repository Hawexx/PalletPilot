/**
 * @deprecated These Drizzle table definitions are NO LONGER USED.
 * All data access now routes through the backend API (Spring Boot / JPA).
 * Backend uses UUID primary keys and richer domain models.
 * This file is kept for reference only — do NOT use these tables directly.
 *
 * See: src/lib/api.ts for the API client
 * See: backend/src/main/java/com/palletpilot/wcs/domain/model/ for JPA entities
 */
import { pgTable, serial, text, integer, jsonb, timestamp, boolean } from "drizzle-orm/pg-core";

export const users = pgTable("users", {
    id: serial("id").primaryKey(),
    username: text("username").notNull().unique(),
    password: text("password").notNull(), // Intentionally storing plain text/simple hash per request 'admin/12345678' implies simple for now, but usually we hash.
    role: text("role").default("user"),
    createdAt: timestamp("created_at").defaultNow(),
});

export const orders = pgTable("orders", {
    id: serial("id").primaryKey(),
    orderId: text("order_id").notNull(),
    customer: text("customer").notNull(),
    status: text("status").notNull(),
    items: integer("items").notNull(),
    totalWeight: text("total_weight").notNull(), // Keeping as text to match UI "450kg", or parse it.
    priority: text("priority").notNull(),
    createdAt: timestamp("created_at").defaultNow(),
});
