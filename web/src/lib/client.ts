import "server-only";
import { cookies } from "next/headers";
import { getSession } from "@/lib/session";

const COOKIE = "att_client";
const DEFAULT_CLIENT = 1;

/**
 * The client (tenant) the console is currently scoped to.
 *
 * The cookie is a preference, not an authorization: it is validated against the
 * tenants the session may act on, and ignored when it names one they cannot.
 * Without that check a stale cookie left a user pinned to an organisation they
 * have no grant for, and every page rendered zeroes instead of saying why.
 */
export async function currentClientId(): Promise<number> {
  const raw = (await cookies()).get(COOKIE)?.value;
  const asked = raw ? Number(raw) : NaN;
  const wanted = Number.isFinite(asked) && asked > 0 ? asked : DEFAULT_CLIENT;

  const session = await getSession();
  if (!session || session.clientIds.length === 0) {
    return wanted;
  }
  return session.clientIds.includes(wanted) ? wanted : session.clientIds[0];
}

export const CLIENT_COOKIE = COOKIE;
