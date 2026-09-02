import { redirect } from "next/navigation";
import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { getSession } from "@/lib/session";
import { currentClientId } from "@/lib/client";
import { listAssetTypes } from "@/lib/api";
import { AssetForm } from "@/components/asset-form";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";

export default async function NewAssetPage({
  searchParams,
}: {
  searchParams: Promise<{
    type?: string;
    tag?: string;
    make?: string;
    model?: string;
    reassignTo?: string;
    supersedes?: string;
  }>;
}) {
  const [session, clientId, sp] = await Promise.all([
    getSession(),
    currentClientId(),
    searchParams,
  ]);
  if (!session) redirect("/login");
  const types = (await listAssetTypes(clientId).catch(() => [])).map(
    (t) => t.name,
  );

  const replacing = Boolean(sp.tag);

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
          title={replacing ? "Add replacement" : "Add asset"}
          subtitle={
            replacing
              ? `New unit on tag ${sp.tag} for client ${clientId}`
              : `New asset for client ${clientId}`
          }
        />
      </div>
      <Card className="mt-6">
        <AssetForm
          mode="create"
          clientId={clientId}
          types={types}
          prefill={{
            type: sp.type,
            assetTag: sp.tag,
            make: sp.make,
            model: sp.model,
            reassignTo: sp.reassignTo,
            supersedes: sp.supersedes,
          }}
        />
      </Card>
    </div>
  );
}
