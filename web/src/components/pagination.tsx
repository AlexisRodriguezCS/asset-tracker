import Link from "next/link";
import { cn } from "@/lib/cn";

/**
 * Prev / next with a page readout. Server-rendered links rather than client
 * state, so a page of the catalog is a real URL you can share or reload into.
 */
export function Pagination({
  page,
  totalPages,
  total,
  size,
  href,
}: {
  page: number;
  totalPages: number;
  total: number;
  size: number;
  /** Builds the URL for a given zero-based page, preserving the active filters. */
  href: (page: number) => string;
}) {
  if (total === 0) return null;
  const first = page * size + 1;
  const last = Math.min(total, (page + 1) * size);

  return (
    <div className="flex items-center justify-between gap-4 border-t border-border/70 px-4 py-3 text-sm">
      <p className="text-muted-foreground">
        <span className="tabular-nums">
          {first}–{last}
        </span>{" "}
        of <span className="tabular-nums">{total}</span>
      </p>
      {totalPages > 1 && (
        <div className="flex items-center gap-1">
          <PageLink href={href(page - 1)} disabled={page <= 0}>
            ← Prev
          </PageLink>
          <span className="px-2 text-xs tabular-nums text-muted-foreground">
            {page + 1} / {totalPages}
          </span>
          <PageLink href={href(page + 1)} disabled={page >= totalPages - 1}>
            Next →
          </PageLink>
        </div>
      )}
    </div>
  );
}

function PageLink({
  href,
  disabled,
  children,
}: {
  href: string;
  disabled: boolean;
  children: React.ReactNode;
}) {
  const base = "rounded-md border px-2.5 py-1 text-xs transition-colors";
  if (disabled) {
    return (
      <span
        aria-disabled
        className={cn(base, "border-border/60 text-muted-foreground/50")}
      >
        {children}
      </span>
    );
  }
  return (
    <Link
      href={href}
      className={cn(
        base,
        "border-border hover:border-primary hover:text-primary",
      )}
    >
      {children}
    </Link>
  );
}
