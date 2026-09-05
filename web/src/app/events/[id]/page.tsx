import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { getEventRequest, listAssets } from "@/lib/api";
import { getSession } from "@/lib/session";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { EventStatusBadge } from "@/components/event-status-badge";
import { EventDecision } from "@/components/event-decision";
import { EventFulfil } from "@/components/event-fulfil";
import { canApprove, canOperateAssets } from "@/lib/roles";
import type { Asset } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function EventRequestPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const session = await getSession();
  if (!session) redirect(`/login?next=/events/${id}`);

  const request = await getEventRequest(id).catch(() => null);
  if (!request) notFound();

  const mayFulfil =
    canOperateAssets(session.role) && request.status === "APPROVED";

  // only load the stockroom when it is about to be shown
  const stock = mayFulfil
    ? await listAssets({
        clientId: request.clientId,
        status: "IN_STOCK",
      }).catch(() => [] as Asset[])
    : [];

  return (
    <div className="mx-auto max-w-3xl animate-fade-in-up space-y-6">
      <PageHeader
        title={request.eventName}
        subtitle={`${request.eventDate}${request.location ? ` · ${request.location}` : ""}`}
        action={<EventStatusBadge status={request.status} />}
      />

      <Card className="space-y-4 p-5">
        <h2 className="text-sm font-semibold">What was asked for</h2>
        <ul className="divide-y divide-border/70">
          {request.lines.map((line) => (
            <li key={line.id} className="flex justify-between gap-4 py-2.5">
              <span className="text-sm">
                {line.quantity} × {line.itemType}
                {line.notes && (
                  <span className="text-muted-foreground"> — {line.notes}</span>
                )}
              </span>
              <span className="text-right text-sm text-muted-foreground">
                {line.fulfilledAssetIds.length > 0
                  ? `${line.fulfilledAssetIds.length} handed out`
                  : "—"}
              </span>
            </li>
          ))}
        </ul>
        {request.notes && (
          <p className="border-t border-border/70 pt-3 text-sm text-muted-foreground">
            {request.notes}
          </p>
        )}
      </Card>

      <Card className="space-y-2 p-5 text-sm">
        <h2 className="mb-2 text-sm font-semibold">Trail</h2>
        <Row label="Requested by" value={request.requestedBy} />
        <Row
          label="Raised"
          value={new Date(request.createdAt).toLocaleString()}
        />
        {request.decidedBy && (
          <>
            <Row label="Decided by" value={request.decidedBy} />
            <Row
              label="Decided"
              value={
                request.decidedAt
                  ? new Date(request.decidedAt).toLocaleString()
                  : "—"
              }
            />
            {request.decisionNote && (
              <Row label="Note" value={request.decisionNote} />
            )}
          </>
        )}
      </Card>

      {canApprove(session.role) && request.status === "SUBMITTED" && (
        <Card className="space-y-3 p-5">
          <h2 className="text-sm font-semibold">Your decision</h2>
          <EventDecision id={request.id} />
        </Card>
      )}

      {mayFulfil && (
        <Card className="space-y-4 p-5">
          <div>
            <h2 className="text-sm font-semibold">Hand the gear out</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Pick the actual units. Each one is checked out to{" "}
              {request.requestedBy} the same way any other asset is.
            </p>
          </div>
          <EventFulfil id={request.id} lines={request.lines} stock={stock} />
        </Card>
      )}

      <Link
        href="/events"
        className="inline-block text-sm text-muted-foreground hover:text-foreground"
      >
        ← All requests
      </Link>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4">
      <span className="text-muted-foreground">{label}</span>
      <span className="text-right">{value}</span>
    </div>
  );
}
