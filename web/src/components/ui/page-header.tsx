export function PageHeader({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="font-display text-3xl font-semibold tracking-tight">
          {title}
        </h1>
        {subtitle && (
          // named so a test can ask for the page's own summary line rather than for
          // any element whose prose happens to match it
          <p
            data-testid="page-subtitle"
            className="mt-1 text-sm text-muted-foreground"
          >
            {subtitle}
          </p>
        )}
      </div>
      {action}
    </div>
  );
}

/**
 * A shared table shell: rounded card, horizontal scroll on narrow screens,
 * hover highlight per row.
 *
 * The header is deliberately **not** sticky, though this comment claimed it was
 * for a while. Making it stick would mean dropping `overflow-x-auto`, because
 * setting either overflow axis makes the other a scroll container too, and a
 * sticky header then resolves against a box that cannot scroll - so it simply
 * never sticks. Keeping the horizontal scroll is worth more than a fixed header
 * on a table that is already paginated to 50 rows.
 */
export function TableCard({ children }: { children: React.ReactNode }) {
  return (
    <div className="mt-6 overflow-x-auto rounded-lg border border-border bg-card/70 shadow-card backdrop-blur">
      <table className="w-full text-sm">{children}</table>
    </div>
  );
}
