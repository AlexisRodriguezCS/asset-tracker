import { cn } from "@/lib/cn";
import type { EventRequestStatus } from "@/lib/types";

const STYLES: Record<EventRequestStatus, string> = {
  SUBMITTED:
    "border-amber-500/30 bg-amber-500/10 text-amber-600 dark:text-amber-400",
  APPROVED: "border-primary/30 bg-primary/10 text-primary",
  DENIED: "border-destructive/30 bg-destructive/10 text-destructive",
  FULFILLED:
    "border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400",
  CLOSED: "border-border bg-muted text-muted-foreground",
};

const LABELS: Record<EventRequestStatus, string> = {
  SUBMITTED: "Waiting on a decision",
  APPROVED: "Approved",
  DENIED: "Denied",
  FULFILLED: "Handed out",
  CLOSED: "Closed",
};

export function EventStatusBadge({ status }: { status: EventRequestStatus }) {
  return (
    <span
      className={cn(
        "shrink-0 rounded-full border px-2.5 py-0.5 text-xs font-medium",
        STYLES[status],
      )}
    >
      {LABELS[status]}
    </span>
  );
}
