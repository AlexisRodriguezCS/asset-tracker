import "server-only";
import { cookies } from "next/headers";

const COOKIE = "att_client";

/** The client (tenant) the console is currently scoped to. Defaults to 1 (Acme). */
export async function currentClientId(): Promise<number> {
  const raw = (await cookies()).get(COOKIE)?.value;
  const n = raw ? Number(raw) : NaN;
  return Number.isFinite(n) && n > 0 ? n : 1;
}

export const CLIENT_COOKIE = COOKIE;
