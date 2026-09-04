import { redirect } from "next/navigation";
import { getSession } from "@/lib/session";
import { currentClientId } from "@/lib/client";
import { PageHeader } from "@/components/ui/page-header";
import { ImportWizard } from "@/components/import-wizard";

export const dynamic = "force-dynamic";

export default async function ImportPage() {
  const [session, clientId] = await Promise.all([
    getSession(),
    currentClientId(),
  ]);
  if (!session) redirect("/login?next=/import");

  return (
    <div className="mx-auto max-w-3xl animate-fade-in-up space-y-6">
      <PageHeader
        title="Import from a spreadsheet"
        subtitle="Bring an existing asset list in. Map your columns once, save the mapping, and re-upload any time — matching rows update instead of duplicating."
      />
      <ImportWizard clientId={clientId} />
    </div>
  );
}
