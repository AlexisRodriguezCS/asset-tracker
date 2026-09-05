import "server-only";
import { getSession } from "@/lib/session";
import type {
  Asset,
  AssetTypeDef,
  Assignment,
  AuditEvent,
  Client,
  Location,
  EventRequest,
  Person,
} from "@/lib/types";

const GATEWAY = process.env.GATEWAY_URL ?? "http://localhost:8080";

export class GatewayError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "GatewayError";
  }
}

interface CallOptions {
  method?: string;
  body?: unknown;
  auth?: boolean;
  /** seconds to cache a GET (default: no cache - the console shows live data). */
  revalidate?: number;
}

/**
 * The one place the server talks to the platform. GET on assets / people /
 * locations / assignments / clients is public; anything with {@code auth: true}
 * attaches the bearer token from the session cookie. Never called from the
 * browser.
 */
export async function gateway<T>(
  path: string,
  { method = "GET", body, auth = false, revalidate }: CallOptions = {},
): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";

  if (auth) {
    const session = await getSession();
    if (!session) throw new GatewayError(401, "NO_SESSION", "Not signed in");
    headers.Authorization = `Bearer ${session.token}`;
  }

  const res = await fetch(`${GATEWAY}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    ...(revalidate != null
      ? { next: { revalidate } }
      : { cache: "no-store" as const }),
  });

  if (!res.ok) {
    const problem = await res.json().catch(() => null);
    throw new GatewayError(
      res.status,
      problem?.code ?? "ERROR",
      problem?.message ?? res.statusText,
    );
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

const qs = (params: Record<string, string | number | undefined>) => {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== "") sp.set(k, String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : "";
};

// --- reads -----------------------------------------------------------------
//
// Every read carries the bearer token. Reads used to be public; that ended when
// ordinary employees became users of the console, because what comes back is now
// scoped to the caller's role and person.

// the tenant list changes rarely and is fetched on every navigation (layout) - cache it briefly
export const listClients = () =>
  gateway<Client[]>("/api/clients", { auth: true, revalidate: 300 });

export const listAssets = (params: {
  clientId: number;
  type?: string;
  status?: string;
  holderType?: string;
  holderId?: number;
  tag?: string;
}) => gateway<Asset[]>(`/api/assets${qs(params)}`, { auth: true });

export const listAssetTypes = (clientId: number) =>
  gateway<AssetTypeDef[]>(`/api/assets/types${qs({ clientId })}`, {
    auth: true,
  });

export const getAsset = (id: string | number) =>
  gateway<Asset>(`/api/assets/${id}`, { auth: true });

export const listPeople = (clientId: number, status?: string) =>
  gateway<Person[]>(`/api/people${qs({ clientId, status })}`, { auth: true });

export const getPerson = (id: string | number) =>
  gateway<Person>(`/api/people/${id}`, { auth: true });

export const listLocations = (clientId: number, kind?: string) =>
  gateway<Location[]>(`/api/locations${qs({ clientId, kind })}`, {
    auth: true,
  });

export const assignmentsForAsset = (assetId: number) =>
  gateway<Assignment[]>(`/api/assignments${qs({ assetId })}`, { auth: true });

// --- audit trail (append-only) ---------------------------------------------

export const assetAudit = (clientId: number, assetId: number) =>
  gateway<AuditEvent[]>(`/api/assets/audit${qs({ clientId, assetId })}`, {
    auth: true,
  });

export const personAudit = (clientId: number, personId: number) =>
  gateway<AuditEvent[]>(
    `/api/people/audit${qs({ clientId, entityId: personId })}`,
    { auth: true },
  );

export const clientActivity = (clientId: number) =>
  gateway<AuditEvent[]>(`/api/assets/audit${qs({ clientId })}`, { auth: true });

// --- event sign-out --------------------------------------------------------

// Scoped server-side by role: an employee gets back only the requests they raised.
export const listEventRequests = (clientId: number, status?: string) =>
  gateway<EventRequest[]>(
    `/api/assignments/event-requests${qs({ clientId, status })}`,
    { auth: true },
  );

export const getEventRequest = (id: string | number) =>
  gateway<EventRequest>(`/api/assignments/event-requests/${id}`, {
    auth: true,
  });
