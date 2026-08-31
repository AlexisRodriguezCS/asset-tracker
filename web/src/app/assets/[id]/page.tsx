import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import {
  getAsset,
  assignmentsForAsset,
  assetAudit,
  listPeople,
  GatewayError,
} from "@/lib/api";
import { getSession } from "@/lib/session";
import { currentClientId } from "@/lib/client";
import { AssetStatusBadge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { AssetActions } from "@/components/asset-actions";
import { AuditFeed } from "@/components/audit-feed";
import { dateOnly, dateTime, label, money } from "@/lib/format";

export default async function AssetDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  let asset;
  try {
    asset = await getAsset(id);
  } catch (e) {
    if (e instanceof GatewayError && e.status === 404) notFound();
    throw e;
  }

  const [session, clientId, history, activity] = await Promise.all([
    getSession(),
    currentClientId(),
    assignmentsForAsset(asset.id).catch(() => []),
    assetAudit(asset.clientId, asset.id).catch(() => []),
  ]);
  const people = session ? await listPeople(clientId).catch(() => []) : [];

  return (
    <div className="animate-fade-in-up">
      <Link
        href="/"
        className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft className="h-4 w-4" /> Assets
      </Link>

      <div className="mt-4 grid gap-6 md:grid-cols-3">
        <Card className="md:col-span-2">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="font-display text-2xl font-semibold tracking-tight">
                {[asset.make, asset.model].filter(Boolean).join(" ") ||
                  label(asset.type)}
              </h1>
              <p className="mt-1 font-mono text-xs text-muted-foreground">
                {asset.assetTag} · SN {asset.serialNumber}
              </p>
            </div>
            <AssetStatusBadge status={asset.status} />
          </div>

          <dl className="mt-6 grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
            <Field k="Type" v={label(asset.type)} />
            <Field
              k="Holder"
              v={
                asset.holderType === "STOCKROOM"
                  ? "Stockroom"
                  : `${label(asset.holderType)} #${asset.holderId}`
              }
            />
            <Field k="Purchased" v={dateOnly(asset.purchaseDate)} />
            <Field k="Cost" v={money(asset.purchaseCostCents)} />
            {asset.notes && (
              <div className="col-span-2">
                <Field k="Notes" v={asset.notes} />
              </div>
            )}
          </dl>

          <div className="mt-6 border-t border-border pt-6">
            <AssetActions
              asset={asset}
              people={people}
              signedIn={Boolean(session)}
            />
          </div>
        </Card>

        <Card>
          <h2 className="text-sm font-semibold">Custody history</h2>
          {history.length === 0 ? (
            <p className="mt-3 text-sm text-muted-foreground">
              Never assigned.
            </p>
          ) : (
            <ol className="mt-3 space-y-3 text-sm">
              {history.map((h) => (
                <li key={h.id} className="border-l-2 border-border pl-3">
                  <p className="font-medium">
                    {label(h.holderType)} #{h.holderId}
                    {h.open && (
                      <span className="ml-2 text-xs text-[hsl(var(--success))]">
                        current
                      </span>
                    )}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    out {dateTime(h.checkedOutAt)} by {h.checkedOutBy}
                  </p>
                  {h.returnedAt && (
                    <p className="text-xs text-muted-foreground">
                      back {dateTime(h.returnedAt)}
                    </p>
                  )}
                </li>
              ))}
            </ol>
          )}
        </Card>

        <Card className="md:col-span-3">
          <h2 className="text-sm font-semibold">Activity log</h2>
          <p className="text-xs text-muted-foreground">
            Who did what, recorded in the same transaction as the change.
          </p>
          <AuditFeed events={activity} />
        </Card>
      </div>
    </div>
  );
}

function Field({ k, v }: { k: string; v: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-muted-foreground">
        {k}
      </dt>
      <dd className="mt-0.5">{v}</dd>
    </div>
  );
}
