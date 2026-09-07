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
  imei: string | null;
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
  "imei",
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

export const EVENT_STATUS_TONE: Record<EventRequestStatus, string> = {
  SUBMITTED: "warn",
  APPROVED: "primary",
  DENIED: "danger",
  FULFILLED: "success",
  CLOSED: "muted",
};

/** One page of the catalog, from `GET /api/assets/paged`. */
export interface PagedAssets {
  items: Asset[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

/** Counts from `GET /api/assets/stats`, aggregated in the database. */
export interface AssetStats {
  total: number;
  byStatus: Record<string, number>;
  byType: Record<string, number>;
  byCondition: Record<string, number>;
  outOfWarranty: number;
  warrantyExpiringSoon: number;
}

/** One "needs attention" bucket from `GET /api/assets/attention`. */
export interface AttentionBucket {
  key: string;
  total: number;
  sample: Asset[];
}

export interface AssetAttention {
  buckets: AttentionBucket[];
}

/** Count plus example assets for one type, from `GET /api/assets/types/usage`. */
export interface TypeUsage {
  type: string;
  total: number;
  sample: Asset[];
}

/** One tag + type slot and how many units it has burned through. */
export interface ReplacedSlot {
  assetTag: string;
  type: string;
  count: number;
}

/**
 * Where a set of assets sits. Desks and the stockroom are named outright; people
 * arrive as holder ids, because the department behind an id belongs to
 * people-service and the console joins it against the people list it has.
 */
export interface Holdings {
  onDesk: number;
  unassigned: number;
  byPerson: Record<string, number>;
}

/** Everything the reports page draws, from `GET /api/assets/reports`. */
export interface AssetReport {
  total: number;
  byType: Record<string, number>;
  byStatus: Record<string, number>;
  byCondition: Record<string, number>;
  unrated: number;
  outOfWarranty: number;
  warrantyExpiringSoon: number;
  inWarranty: number;
  fleetValueCents: number;
  replacements: number;
  topReplacedSlots: ReplacedSlot[];
  holdings: Holdings;
  lifecycle: Record<string, number>;
  incidents: {
    total: number;
    byType: Record<string, number>;
    holdings: Holdings;
  };
}

/**
 * One line of the event sign-out menu: what the client owns for events, what is
 * already spoken for on the chosen day, and what is therefore left.
 *
 * All three travel rather than just the remainder — "none left today" and "they
 * do not own any" are different sentences to put in front of a requester.
 */
export interface EventEquipmentItem {
  id: number;
  itemType: string;
  owned: number;
  committed: number;
  available: number;
}
