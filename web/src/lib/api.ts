import "server-only";
import { getSession } from "@/lib/session";
import type {
  Asset,
  Assignment,
  AuditEvent,
  Client,
  Location,
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
}

/**
 * The one place the server talks to the platform. GET on assets / people /
 * locations / assignments / clients is public; anything with {@code auth: true}
 * attaches the bearer token from the session cookie. Never called from the
 * browser.
 */
export async function gateway<T>(
  path: string,
  { method = "GET", body, auth = false }: CallOptions = {},
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
    cache: "no-store",
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

// --- reads (public) --------------------------------------------------------

export const listClients = () => gateway<Client[]>("/api/clients");

export const listAssets = (params: {
  clientId: number;
  type?: string;
  status?: string;
  holderType?: string;
  holderId?: number;
  tag?: string;
  category?: string;
}) => gateway<Asset[]>(`/api/assets${qs(params)}`);

export const listCategories = (clientId: number) =>
  gateway<string[]>(`/api/assets/categories${qs({ clientId })}`);

export const getAsset = (id: string | number) =>
  gateway<Asset>(`/api/assets/${id}`);

export const listPeople = (clientId: number, status?: string) =>
  gateway<Person[]>(`/api/people${qs({ clientId, status })}`);

export const getPerson = (id: string | number) =>
  gateway<Person>(`/api/people/${id}`);

export const listLocations = (clientId: number, kind?: string) =>
  gateway<Location[]>(`/api/locations${qs({ clientId, kind })}`);

export const assignmentsForAsset = (assetId: number) =>
  gateway<Assignment[]>(`/api/assignments${qs({ assetId })}`);

// --- audit trail (append-only, public read) ------------------------------

export const assetAudit = (clientId: number, assetId: number) =>
  gateway<AuditEvent[]>(`/api/assets/audit${qs({ clientId, assetId })}`);

export const personAudit = (clientId: number, personId: number) =>
  gateway<AuditEvent[]>(
    `/api/people/audit${qs({ clientId, entityId: personId })}`,
  );

export const clientActivity = (clientId: number) =>
  gateway<AuditEvent[]>(`/api/assets/audit${qs({ clientId })}`);
