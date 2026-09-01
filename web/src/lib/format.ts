export function money(cents: number | null | undefined): string {
  if (cents == null) return "—";
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(cents / 100);
}

export function dateOnly(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-US", { dateStyle: "medium" });
}

export function dateTime(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

/** ENUM_VALUE -> "Enum value" */
export function label(value: string): string {
  const s = value.replace(/_/g, " ").toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** Storage vs in use vs gone - derived from status, not a stored field. */
const DISPOSITION: Record<string, string> = {
  IN_STOCK: "In storage",
  ASSIGNED: "In use",
  IN_REPAIR: "At repair",
  BROKEN: "Broken",
  PENDING_RECYCLE: "Awaiting recycle",
  RECYCLED: "Recycled",
  RETIRED: "Retired",
  LOST: "Lost",
};

export function disposition(status: string): string {
  return DISPOSITION[status] ?? label(status);
}

/** true when an ISO date is in the past (used for warranty expiry). */
export function isPast(iso: string | null): boolean {
  if (!iso) return false;
  return new Date(iso).getTime() < Date.now();
}
