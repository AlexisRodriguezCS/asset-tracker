import { redirect } from "next/navigation";
import { getSession } from "@/lib/session";
import { currentClientId } from "@/lib/client";
import { PageHeader } from "@/components/ui/page-header";
import { EventRequestForm } from "@/components/event-request-form";

export const dynamic = "force-dynamic";

export default async function NewEventRequestPage() {
  const [session, clientId] = await Promise.all([
    getSession(),
    currentClientId(),
  ]);
  if (!session) redirect("/login?next=/events/new");

  return (
    <div className="mx-auto max-w-3xl animate-fade-in-up space-y-6">
      <PageHeader
        title="Event sign-out"
        subtitle="Ask for gear for an event — a tech or your POC reviews it before anything leaves the stockroom"
      />
      <EventRequestForm clientId={clientId} />
    </div>
  );
}
