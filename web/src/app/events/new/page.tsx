import { redirect } from "next/navigation";
import { getSession } from "@/lib/session";
import { currentClientId } from "@/lib/client";
import { eventEquipment } from "@/lib/api";
import { PageHeader } from "@/components/ui/page-header";
import { EventRequestForm } from "@/components/event-request-form";

export const dynamic = "force-dynamic";

export default async function NewEventRequestPage() {
  const [session, clientId] = await Promise.all([
    getSession(),
    currentClientId(),
  ]);
  if (!session) redirect("/welcome?next=/events/new");

  // The menu is the client's event equipment pool, not its asset catalogue. Those
  // are different questions: the catalogue is every type they track, the pool is
  // what they lend at events and how many. Offering the catalogue advertised gear
  // nobody had any of, and gave no way to say "we own two TVs".
  const items = await eventEquipment(clientId).catch(() => []);

  return (
    <div className="mx-auto max-w-3xl animate-fade-in-up space-y-6">
      <PageHeader
        title="Event sign-out"
        subtitle="Ask for gear for an event — a tech or your POC reviews it before anything leaves the stockroom"
      />
      <EventRequestForm clientId={clientId} initialItems={items} />
    </div>
  );
}
