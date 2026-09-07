import { redirect } from "next/navigation";
import { getSession } from "@/lib/session";
import { canOperateAssets } from "@/lib/roles";
import { currentClientId } from "@/lib/client";
import { listAssetTypes, typeUsage } from "@/lib/api";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { TypeManager } from "@/components/type-manager";

export const dynamic = "force-dynamic";
export const metadata = { title: "Asset types" };

/*
 * Same shell as every other top-level page. This was capped at max-w-2xl with a
 * back-link to Assets, which made a nav destination look like a sub-page and
 * left half the window empty beside it.
 */
export default async function TypesPage() {
  const [session, clientId] = await Promise.all([
    getSession(),
    currentClientId(),
  ]);
  if (!session) redirect("/welcome");
  // Everything on this page is a tech action; anyone else would only collect 403s.
  if (!canOperateAssets(session.role)) redirect("/");

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
    <div className="animate-fade-in-up space-y-6">
      <PageHeader
        title="Asset types"
        subtitle="The kinds of thing this client tracks. Add your own; a type can't be removed while assets still use it unless you move them first."
      />
      <Card>
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
