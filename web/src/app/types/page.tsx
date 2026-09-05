import { redirect } from "next/navigation";
import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { getSession } from "@/lib/session";
import { currentClientId } from "@/lib/client";
import { listAssetTypes, typeUsage } from "@/lib/api";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { TypeManager } from "@/components/type-manager";

export const dynamic = "force-dynamic";

export default async function TypesPage() {
  const [session, clientId] = await Promise.all([
    getSession(),
    currentClientId(),
  ]);
  if (!session) redirect("/login");

  const types = await listAssetTypes(clientId);

  // Count and a few examples per type, from the database. This page used to pull
  // the whole catalog and group it here just to say "12 assets" and list a
  // handful of tags in the delete confirmation.
  const rows = types.length
    ? await typeUsage(
        clientId,
        types.map((t) => t.name),
      ).catch(() => [])
    : [];

  const usage = Object.fromEntries(
    rows.map((r) => [
      r.type,
      r.sample.map((a) => ({
        id: a.id,
        assetTag: a.assetTag,
        make: a.make,
        model: a.model,
      })),
    ]),
  );
  const usageTotals = Object.fromEntries(rows.map((r) => [r.type, r.total]));

  return (
    <div className="max-w-2xl animate-fade-in-up">
      <Link
        href="/"
        className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft className="h-4 w-4" /> Assets
      </Link>
      <div className="mt-4">
        <PageHeader
          title="Asset types"
          subtitle="The kinds of thing this client tracks. Add your own; a type can't be removed while assets still use it unless you move them first."
        />
      </div>
      <Card className="mt-6">
        <TypeManager
          clientId={clientId}
          types={types}
          usage={usage}
          totals={usageTotals}
        />
      </Card>
    </div>
  );
}
