import { cn } from "@/lib/cn";
import type { EventRequestStatus } from "@/lib/types";

const STYLES: Record<EventRequestStatus, string> = {
  SUBMITTED: "border-warning/30 bg-warning/10 text-warning",
  APPROVED: "border-primary/30 bg-primary/10 text-primary",
  DENIED: "border-destructive/30 bg-destructive/10 text-destructive",
  FULFILLED: "border-success/30 bg-success/10 text-success",
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
