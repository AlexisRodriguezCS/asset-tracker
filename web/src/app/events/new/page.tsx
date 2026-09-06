import { redirect } from "next/navigation";
import { getSession } from "@/lib/session";
import { currentClientId } from "@/lib/client";
import { listAssetTypes } from "@/lib/api";
import { PageHeader } from "@/components/ui/page-header";
import { EventRequestForm } from "@/components/event-request-form";

export const dynamic = "force-dynamic";

export default async function NewEventRequestPage() {
  const [session, clientId] = await Promise.all([
    getSession(),
    currentClientId(),
  ]);
  if (!session) redirect("/welcome?next=/events/new");

  // Offer what this client actually tracks. The list used to be hardcoded, so it
  // could advertise gear the client has no type for - a request nobody could ever
  // fulfil, because the tech's picker would find nothing of that type.
  const types = await listAssetTypes(clientId).catch(() => []);

  return (
    <div className="mx-auto max-w-3xl animate-fade-in-up space-y-6">
      <PageHeader
        title="Event sign-out"
        subtitle="Ask for gear for an event — a tech or your POC reviews it before anything leaves the stockroom"
      />
      <EventRequestForm clientId={clientId} types={types.map((t) => t.name)} />
    </div>
  );
}
