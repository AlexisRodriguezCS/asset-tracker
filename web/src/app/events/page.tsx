import Link from "next/link";
import { redirect } from "next/navigation";
import { CalendarDays, Plus } from "lucide-react";
import { listEventRequests } from "@/lib/api";
import { getSession } from "@/lib/session";
import { currentClientId } from "@/lib/client";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { EventStatusBadge } from "@/components/event-status-badge";
import { isSelfServiceUser } from "@/lib/roles";
import type { EventRequest } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function EventsPage() {
  const [session, clientId] = await Promise.all([
    getSession(),
    currentClientId(),
  ]);
  if (!session) redirect("/login?next=/events");

  const mine = isSelfServiceUser(session.role);
  const requests = await listEventRequests(clientId).catch(
    () => [] as EventRequest[],
  );

  const awaiting = requests.filter((r) => r.status === "SUBMITTED");

  return (
    <div className="animate-fade-in-up space-y-6">
      <PageHeader
        title={mine ? "My event requests" : "Event requests"}
        subtitle={
          mine
            ? "Gear you have asked to sign out, and where each request stands"
            : `${awaiting.length} waiting on a decision`
        }
        action={
          <Link href="/events/new">
            <Button size="sm">
              <Plus className="mr-1.5 h-4 w-4" />
              New request
            </Button>
          </Link>
        }
      />

      {requests.length === 0 ? (
        <Card className="p-10 text-center">
          <CalendarDays className="mx-auto h-8 w-8 text-muted-foreground" />
          <p className="mt-3 text-sm font-medium">No requests yet</p>
          <p className="mt-1 text-sm text-muted-foreground">
            {mine
              ? "When you need a loaner laptop, TVs or speakers for an event, ask here."
              : "Nothing has been requested for this client yet."}
          </p>
          <Link href="/events/new" className="mt-4 inline-block">
            <Button size="sm" variant="outline">
              Start a request
            </Button>
          </Link>
        </Card>
      ) : (
        <ul className="space-y-3">
          {requests.map((r) => (
            <li key={r.id}>
              <Link href={`/events/${r.id}`} className="block">
                <Card className="p-4 transition-colors hover:border-primary/50">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate font-medium">{r.eventName}</p>
                      <p className="mt-0.5 text-sm text-muted-foreground">
                        {r.eventDate}
                        {r.location && ` · ${r.location}`}
                        {!mine && ` · ${r.requestedBy}`}
                      </p>
                    </div>
                    <EventStatusBadge status={r.status} />
                  </div>
                  <p className="mt-3 text-sm text-muted-foreground">
                    {r.lines
                      .map((l) => `${l.quantity} × ${l.itemType}`)
                      .join(" · ")}
                  </p>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
