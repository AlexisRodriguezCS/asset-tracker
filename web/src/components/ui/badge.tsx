import { cn } from "@/lib/cn";
import { label } from "@/lib/format";

type Tone = "neutral" | "success" | "danger" | "info" | "warn";

const TONES: Record<Tone, string> = {
  neutral: "bg-muted text-muted-foreground ring-border",
  success: "bg-success/10 text-success ring-success/20",
  danger: "bg-destructive/10 text-destructive ring-destructive/20",
  warn: "bg-warning/10 text-warning ring-warning/20",
  info: "bg-accent text-primary ring-primary/20",
};

export function Badge({
  tone = "neutral",
  className,
  children,
}: {
  tone?: Tone;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium tracking-wide ring-1 ring-inset",
        TONES[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}

const ASSET_TONE: Record<string, Tone> = {
  IN_STOCK: "success",
  ASSIGNED: "info",
  IN_REPAIR: "warn",
  BROKEN: "danger",
  PENDING_RECYCLE: "warn",
  RECYCLED: "neutral",
  RETIRED: "neutral",
  LOST: "danger",
};
const PERSON_TONE: Record<string, Tone> = {
  ACTIVE: "success",
  OFFBOARDING: "warn",
  DEPARTED: "neutral",
};
/*
 * Condition is graded, not stated: it only earns attention when it is bad.
 *
 * NEW and GOOD used to be "success", which put a green pill in the condition
 * column right beside the green "In stock" pill in the status column - two
 * identical chips, one row apart, meaning entirely different things. On a
 * catalog where most units are good and in stock, that is two columns of the
 * same green and no hierarchy at all. Healthy conditions now read as quiet
 * text, so the colour left on the row belongs to status, and a FAIR or DAMAGED
 * unit is the thing that catches the eye.
 */
const CONDITION_TONE: Record<string, Tone> = {
  NEW: "neutral",
  GOOD: "neutral",
  FAIR: "warn",
  POOR: "warn",
  DAMAGED: "danger",
};

/** The healthy grades, drawn as plain text rather than a chip. */
const QUIET_CONDITIONS = new Set(["NEW", "GOOD"]);

export function AssetStatusBadge({ status }: { status: string }) {
  return <Badge tone={ASSET_TONE[status] ?? "neutral"}>{label(status)}</Badge>;
}
export function PersonStatusBadge({ status }: { status: string }) {
  return <Badge tone={PERSON_TONE[status] ?? "neutral"}>{label(status)}</Badge>;
}
export function ConditionBadge({ condition }: { condition: string | null }) {
  if (!condition) return <span className="text-muted-foreground">—</span>;
  if (QUIET_CONDITIONS.has(condition)) {
    return <span className="text-muted-foreground">{label(condition)}</span>;
  }
  return (
    <Badge tone={CONDITION_TONE[condition] ?? "neutral"}>
      {label(condition)}
    </Badge>
  );
}
