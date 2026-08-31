import { dateTime } from "@/lib/format";
import type { AuditEvent } from "@/lib/types";

/** Read-only activity log for one entity. Rows are written in the same transaction as the change. */
export function AuditFeed({ events }: { events: AuditEvent[] }) {
  if (events.length === 0) {
    return (
      <p className="mt-3 text-sm text-muted-foreground">
        No recorded activity.
      </p>
    );
  }
  return (
    <ol className="mt-3 space-y-3 text-sm">
      {events.map((e) => (
        <li key={e.id} className="border-l-2 border-border pl-3">
          <p>
            <span className="font-medium">{e.actor}</span> {e.summary}
          </p>
          <p className="text-xs text-muted-foreground">
            {dateTime(e.at)} · <span className="font-mono">{e.action}</span>
          </p>
        </li>
      ))}
    </ol>
  );
}
