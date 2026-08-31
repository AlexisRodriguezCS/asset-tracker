import Link from "next/link";
import { cn } from "@/lib/cn";

type Stat = {
  label: string;
  value: number | string;
  href?: string;
  tone?: "default" | "primary" | "success" | "warn" | "danger";
  active?: boolean;
};

const TONE_RING: Record<NonNullable<Stat["tone"]>, string> = {
  default: "",
  primary: "text-primary",
  success: "text-[hsl(var(--success))]",
  warn: "text-amber-500 dark:text-amber-400",
  danger: "text-destructive",
};

export function StatStrip({ stats }: { stats: Stat[] }) {
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      {stats.map((s) => {
        const inner = (
          <>
            <div
              className={cn(
                "font-display text-2xl font-semibold tabular-nums",
                TONE_RING[s.tone ?? "default"],
              )}
            >
              {s.value}
            </div>
            <div className="mt-0.5 text-xs uppercase tracking-wide text-muted-foreground">
              {s.label}
            </div>
          </>
        );
        const base =
          "rounded-lg border bg-card/70 px-4 py-3 shadow-card backdrop-blur transition-colors";
        return s.href ? (
          <Link
            key={s.label}
            href={s.href}
            className={cn(
              base,
              s.active
                ? "border-primary/50 ring-1 ring-primary/30"
                : "border-border hover:border-primary/30",
            )}
          >
            {inner}
          </Link>
        ) : (
          <div key={s.label} className={cn(base, "border-border")}>
            {inner}
          </div>
        );
      })}
    </div>
  );
}
