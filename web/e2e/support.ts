import path from "node:path";
import type { Page } from "@playwright/test";

/**
 * Shared helpers for the browser suite.
 *
 * Three rules these encode, all learned the hard way in this repo:
 *
 * 1. **Sign in once, not per test.** The gateway rate-limits `POST
 *    /api/auth/**` to ten a minute per IP. A login inside every test blew that
 *    budget and failed fifteen tests for a reason none of them were about.
 * 2. **A run must be able to follow the last one.** The API matrix booked gear
 *    on a fixed date, passed once, then collided with its own previous booking
 *    forever after. Event names and dates here are unique per run.
 * 3. **Never disturb the demo personas' data.** The REST-Assured suite ran an
 *    offboarding sweep on the seeded employee - who is also the console's
 *    employee persona - and quietly emptied her account on every `./gradlew
 *    build`. Nothing here offboards anybody.
 */

export const PERSONAS = ["ADMIN", "TECH", "POC", "HR", "USER"] as const;
export type Persona = (typeof PERSONAS)[number];

/**
 * Saved sessions live beside the specs; the setup project writes them.
 *
 * Resolved from the working directory rather than `import.meta.dirname`, which
 * Playwright's CommonJS transpilation does not provide - the whole suite failed
 * to load with "Cannot use 'import.meta' outside a module". Playwright always
 * runs from the directory holding its config, which is this package root.
 */
export const AUTH_DIR = path.join(process.cwd(), "e2e", ".auth");

export const statePath = (role: Persona) =>
  path.join(AUTH_DIR, `${role.toLowerCase()}.json`);

/** Distinct per run, so a second run does not collide with the first. */
export const RUN = Date.now().toString(36).slice(-6).toUpperCase();

/**
 * A date far enough out that it cannot clash with seed data, and unique to this
 * run so the event pool it consumes is its own.
 */
export function uniqueEventDate(offsetDays = 0): string {
  const base = Date.now() + 500 * 86_400_000 + (Date.now() % 700) * 86_400_000;
  return new Date(base + offsetDays * 86_400_000).toISOString().slice(0, 10);
}

/** The tenant the seeded personas all belong to. */
export const ACME = 1;

/** Reads the event equipment pool straight from the BFF, for arranging a test. */
export async function poolFor(
  page: Page,
  date: string,
): Promise<Record<string, number>> {
  const res = await page.request.get(
    `/api/bff/assignments/event-equipment?clientId=${ACME}&date=${date}`,
  );
  const items = (await res.json()) as { itemType: string; available: number }[];
  return Object.fromEntries(items.map((i) => [i.itemType, i.available]));
}
