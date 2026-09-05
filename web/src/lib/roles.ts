/**
 * The console's view of who may do what. Mirrors auth-service's Role enum; the
 * backend enforces all of this independently - these helpers only decide what to
 * bother rendering, never what is allowed.
 */
export type Role = "ADMIN" | "TECH" | "POC" | "HR" | "USER";

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: "Admin",
  TECH: "Admin (tech)",
  POC: "Admin (POC)",
  HR: "HR",
  USER: "Employee",
};

export const ROLE_BLURBS: Record<Role, string> = {
  ADMIN: "Every tenant, every action",
  TECH: "Full asset operations",
  POC: "Approves for their organisation",
  HR: "Offboarding collection",
  USER: "Their own gear, and requests",
};

const APPROVERS: Role[] = ["ADMIN", "TECH", "POC"];
const ASSET_OPERATORS: Role[] = ["ADMIN", "TECH"];

export function asRole(value: string | null | undefined): Role | null {
  return value && value in ROLE_LABELS ? (value as Role) : null;
}

/** An ordinary employee: their own gear and the event form, nothing else. */
export function isSelfServiceUser(role: string | null | undefined): boolean {
  return role === "USER";
}

/** May approve or deny an event sign-out request. */
export function canApprove(role: string | null | undefined): boolean {
  return APPROVERS.includes(role as Role);
}

/** May create/edit assets and hand gear out. */
export function canOperateAssets(role: string | null | undefined): boolean {
  return ASSET_OPERATORS.includes(role as Role);
}
