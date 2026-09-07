import { redirect } from "next/navigation";
import { getSession } from "@/lib/session";
import { canOperateAssets } from "@/lib/roles";
import { currentClientId } from "@/lib/client";
import { DeskForm } from "@/components/desk-form";
import { PageHeader } from "@/components/ui/page-header";

export const dynamic = "force-dynamic";

export default async function NewDeskPage() {
  const [session, clientId] = await Promise.all([
    getSession(),
    currentClientId(),
  ]);
  if (!session) redirect("/welcome?next=/desks/new");
  if (!canOperateAssets(session.role)) redirect("/desks");

  return (
    <div className="mx-auto max-w-3xl animate-fade-in-up space-y-6">
      <PageHeader
        title="Add a location"
        subtitle="A desk, room or site. Assets are checked out to these the same way they are to a person."
      />
      <DeskForm clientId={clientId} />
    </div>
  );
}
