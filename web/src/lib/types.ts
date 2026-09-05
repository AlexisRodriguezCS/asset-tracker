// Mirrors the JSON the api-gateway returns. Kept to the fields the console uses.

export interface Client {
  id: number;
  name: string;
  slug: string;
  status: string;
}

/** A type name a client manages; {@link Asset.type} holds the name string. */
export interface AssetTypeDef {
  id: number;
  clientId: number;
  name: string;
}

export type AssetStatus =
  | "IN_STOCK"
  | "ASSIGNED"
  | "IN_REPAIR"
  | "BROKEN"
  | "PENDING_RECYCLE"
  | "RECYCLED"
  | "RETIRED"
  | "LOST";

export type AssetCondition = "NEW" | "GOOD" | "FAIR" | "POOR" | "DAMAGED";

export type HolderType = "PERSON" | "LOCATION" | "STOCKROOM";

export interface Asset {
  id: number;
  clientId: number;
  type: string;
  make: string | null;
  model: string | null;
  serialNumber: string;
  assetTag: string;
  status: AssetStatus;
  condition: AssetCondition | null;
  holderType: HolderType;
  holderId: number | null;
  purchaseDate: string | null;
  deployedOn: string | null;
  warrantyEndsOn: string | null;
  purchaseCostCents: number | null;
  notes: string | null;
  /** id of the unit this one replaced (retire-and-replace); null for an original. */
  supersedesAssetId: number | null;
  /** client's own spreadsheet columns we don't model, kept verbatim on import. */
  attributes: Record<string, string>;
  createdAt: string;
}

/** --- spreadsheet import --- */
export interface ColumnMapping {
  fields: Record<string, string>; // our field -> their column header
  attributeColumns: string[]; // their headers kept under attributes
}
export interface ImportProfile {
  id: number;
  name: string;
  mapping: ColumnMapping;
}
export interface ImportAnalyze {
  headers: string[];
  suggested: ColumnMapping;
  sampleRows: Record<string, string>[];
  profiles: ImportProfile[];
}
export interface ImportRowOutcome {
  line: number;
  values: Record<string, string>;
  action: "create" | "update" | "skip";
  errors: string[];
}
export interface ImportPreview {
  total: number;
  willCreate: number;
  willUpdate: number;
  invalid: number;
  rows: ImportRowOutcome[];
}
export interface ImportResult {
  created: number;
  updated: number;
  skipped: number;
  errors: ImportRowOutcome[];
}
export const IMPORT_FIELDS = [
  "assetTag",
  "type",
  "serialNumber",
  "make",
  "model",
  "condition",
  "purchaseDate",
  "warrantyEndsOn",
  "deployedOn",
  "notes",
] as const;

export type PersonStatus = "ACTIVE" | "OFFBOARDING" | "DEPARTED";

export interface Person {
  id: number;
  clientId: number;
  fullName: string;
  email: string;
  department: string | null;
  deskId: number | null;
  status: PersonStatus;
  createdAt: string;
}

export type LocationKind = "SITE" | "ROOM" | "DESK";

export interface Location {
  id: number;
  clientId: number;
  kind: LocationKind;
  label: string;
  building: string | null;
  floor: string | null;
  qrTag: string;
  createdAt: string;
}

export interface Assignment {
  id: number;
  clientId: number;
  assetId: number;
  holderType: "PERSON" | "LOCATION";
  holderId: number;
  checkedOutBy: string;
  checkedOutAt: string;
  returnedAt: string | null;
  returnedBy: string | null;
  open: boolean;
  note: string | null;
}

export interface OffboardingResult {
  personId: number;
  returned: number[];
  failed: number[];
}

export interface AuditEvent {
  id: number;
  clientId: number;
  actor: string;
  action: string;
  entityType: string;
  entityId: number;
  summary: string;
  detail: string | null;
  at: string;
}

export interface TokenResponse {
  token: string;
  tokenType: string;
  expiresInMs: number;
}

// --- event sign-out ---------------------------------------------------------

export type EventRequestStatus =
  "SUBMITTED" | "APPROVED" | "DENIED" | "FULFILLED" | "CLOSED";

export interface EventRequestLine {
  id: number;
  itemType: string;
  quantity: number;
  notes: string | null;
  fulfilledAssetIds: number[];
}

export interface EventRequest {
  id: number;
  clientId: number;
  eventName: string;
  eventDate: string;
  location: string | null;
  notes: string | null;
  requestedBy: string;
  requesterPersonId: number | null;
  status: EventRequestStatus;
  decidedBy: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  createdAt: string;
  lines: EventRequestLine[];
}

/**
 * The gear the sign-out form offers. These are asset *type* names, matching the
 * per-client type catalog, so a fulfilled line maps onto real inventory.
 */
export const EVENT_ITEMS = [
  { type: "Laptop", label: "Loaner laptop" },
  { type: "Charger", label: "Loaner charger" },
  { type: "Cable", label: "Cables" },
  { type: "TV", label: "TVs" },
  { type: "Speaker", label: "Speakers" },
] as const;

export const EVENT_STATUS_TONE: Record<EventRequestStatus, string> = {
  SUBMITTED: "warn",
  APPROVED: "primary",
  DENIED: "danger",
  FULFILLED: "success",
  CLOSED: "muted",
};
