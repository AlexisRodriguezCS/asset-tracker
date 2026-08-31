import { cn } from "@/lib/cn";

type Tone = "neutral" | "success" | "danger" | "info" | "warn";

const TONES: Record<Tone, string> = {
  neutral: "bg-muted text-muted-foreground ring-border",
  success: "bg-success/10 text-success ring-success/20",
  danger: "bg-destructive/10 text-destructive ring-destructive/20",
  warn: "bg-amber-500/10 text-amber-600 ring-amber-500/20 dark:text-amber-400",
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
  RETIRED: "neutral",
  LOST: "danger",
};
const PERSON_TONE: Record<string, Tone> = {
  ACTIVE: "success",
  OFFBOARDING: "warn",
  DEPARTED: "neutral",
};

export function AssetStatusBadge({ status }: { status: string }) {
  return (
    <Badge tone={ASSET_TONE[status] ?? "neutral"}>
      {status.replace(/_/g, " ")}
    </Badge>
  );
}
export function PersonStatusBadge({ status }: { status: string }) {
  return <Badge tone={PERSON_TONE[status] ?? "neutral"}>{status}</Badge>;
}
