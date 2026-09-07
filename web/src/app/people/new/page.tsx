import { redirect } from "next/navigation";
import { getSession } from "@/lib/session";
import { canOperateAssets } from "@/lib/roles";
import { currentClientId } from "@/lib/client";
import { listLocations } from "@/lib/api";
import { PersonForm } from "@/components/person-form";
import { PageHeader } from "@/components/ui/page-header";

export const dynamic = "force-dynamic";
export const metadata = { title: "Add a person" };

export default async function NewPersonPage() {
  const [session, clientId] = await Promise.all([
    getSession(),
    currentClientId(),
  ]);
  if (!session) redirect("/welcome?next=/people/new");
  // Adding an employee is an operator action; anyone else would only collect 403s
  if (!canOperateAssets(session.role)) redirect("/people");

  // Offered as a picker rather than a free-text id: a desk that does not exist
  // is a support ticket, and the list is bounded by the client's own desks.
  const desks = await listLocations(clientId, "DESK").catch(() => []);

  return (
    <div className="mx-auto max-w-3xl animate-fade-in-up space-y-6">
      <PageHeader
        title="Add a person"
        subtitle="An employee of this client. A desk is optional — people are hired before they are seated."
      />
      <PersonForm clientId={clientId} desks={desks} />
    </div>
  );
}
