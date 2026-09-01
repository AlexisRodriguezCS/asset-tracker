// Mirrors the JSON the api-gateway returns. Kept to the fields the console uses.

export interface Client {
  id: number;
  name: string;
  slug: string;
  status: string;
}

export type AssetType =
  | "LAPTOP"
  | "TABLET"
  | "PHONE"
  | "MONITOR"
  | "DOCK"
  | "CHARGER"
  | "CABLE"
  | "PERIPHERAL"
  | "OTHER";

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
  type: AssetType;
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
  createdAt: string;
}

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
