import { cn } from "@/lib/cn";
import type { EventRequestStatus } from "@/lib/types";

/* Dot and word, matching Badge - the two systems have drifted apart once already. */
const DOTS: Record<EventRequestStatus, string> = {
  SUBMITTED: "bg-warning",
  APPROVED: "bg-primary",
  DENIED: "bg-destructive",
  FULFILLED: "bg-success",
  CLOSED: "bg-muted-foreground",
};

const LABEL_TONE: Record<EventRequestStatus, string> = {
  SUBMITTED: "text-warning",
  APPROVED: "text-foreground",
  DENIED: "text-destructive",
  FULFILLED: "text-foreground",
  CLOSED: "text-muted-foreground",
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
        "inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap text-xs font-medium",
        LABEL_TONE[status],
      )}
    >
      <span
        aria-hidden="true"
        className={cn("h-1.5 w-1.5 shrink-0 rounded-full", DOTS[status])}
      />
      {LABELS[status]}
    </span>
  );
}
