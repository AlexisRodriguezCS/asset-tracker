import { cn } from "@/lib/cn";
import { label } from "@/lib/format";

type Tone = "neutral" | "success" | "danger" | "info" | "warn";

/*
 * A state is a dot and a word.
 *
 * These used to be filled pills with a ring. In a table that put a lozenge of
 * colour in every status cell of every row, which outweighed the asset tag - the
 * thing you are actually looking for - and turned a scannable column into a
 * stack of buttons. The dot carries the same information in a fraction of the
 * ink, and the text stays legible because it is text rather than a label inside
 * a chip. Outside a table the same component reads as a status line, which is
 * what it is.
 */
const DOTS: Record<Tone, string> = {
  neutral: "bg-muted-foreground",
  success: "bg-success",
  danger: "bg-destructive",
  warn: "bg-warning",
  info: "bg-primary",
};

const LABELS: Record<Tone, string> = {
  neutral: "text-muted-foreground",
  success: "text-foreground",
  danger: "text-destructive",
  warn: "text-warning",
  info: "text-foreground",
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
        "inline-flex items-center gap-1.5 whitespace-nowrap text-xs font-medium",
        LABELS[tone],
        className,
      )}
    >
      <span
        aria-hidden="true"
        className={cn("h-1.5 w-1.5 shrink-0 rounded-full", DOTS[tone])}
      />
      {children}
    </span>
  );
}

const ASSET_TONE: Record<string, Tone> = {
  IN_STOCK: "success",
  ASSIGNED: "neutral",
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
