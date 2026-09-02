import { redirect } from "next/navigation";
import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { getSession } from "@/lib/session";
import { currentClientId } from "@/lib/client";
import { listAssets, listAssetTypes } from "@/lib/api";
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

  const [types, assets] = await Promise.all([
    listAssetTypes(clientId),
    listAssets({ clientId }),
  ]);

  const usage: Record<
    string,
    {
      id: number;
      assetTag: string;
      make: string | null;
      model: string | null;
    }[]
  > = {};
  for (const a of assets) {
    (usage[a.type] ??= []).push({
      id: a.id,
      assetTag: a.assetTag,
      make: a.make,
      model: a.model,
    });
  }

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
        <TypeManager clientId={clientId} types={types} usage={usage} />
      </Card>
    </div>
  );
}
