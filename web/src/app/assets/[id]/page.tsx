import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import {
  getAsset,
  assignmentsForAsset,
  assetAudit,
  listAssets,
  listAssetTypes,
  listLocations,
  listPeople,
  GatewayError,
} from "@/lib/api";
import { getSession } from "@/lib/session";
import { AssetStatusBadge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { AssetActions } from "@/components/asset-actions";
import { AssetAdmin } from "@/components/asset-admin";
import { AuditFeed } from "@/components/audit-feed";
import {
  dateOnly,
  dateTime,
  disposition,
  isPast,
  label,
  money,
} from "@/lib/format";

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

  const [session, history, activity, sameTag, people, desks] =
    await Promise.all([
      getSession(),
      assignmentsForAsset(asset.id).catch(() => []),
      assetAudit(asset.clientId, asset.id).catch(() => []),
      listAssets({ clientId: asset.clientId, tag: asset.assetTag }).catch(
        () => [],
      ),
      listPeople(asset.clientId).catch(() => []),
      listLocations(asset.clientId, "DESK").catch(() => []),
    ]);
  const types = session
    ? (await listAssetTypes(asset.clientId).catch(() => [])).map((t) => t.name)
    : [];
  const onTag = sameTag.filter((x) => x.id !== asset.id);
  const replaces =
    sameTag.find((x) => x.id === asset.supersedesAssetId) ?? null;
  const replacedBy =
    sameTag.find((x) => x.supersedesAssetId === asset.id) ?? null;
  const unitName = (x: (typeof sameTag)[number]) =>
    `${[x.make, x.model].filter(Boolean).join(" ") || x.type} · ${label(x.status)}`;

  const personById = new Map(people.map((p) => [p.id, p]));
  const deskById = new Map(desks.map((d) => [d.id, d]));
  // detail views have room, so qualify the holder: person -> email, desk -> building/floor
  const holderOf = (t: string, id: number | null) => {
    if (t === "STOCKROOM" || id == null) return "Stockroom";
    if (t === "PERSON") {
      const p = personById.get(id);
      if (!p) return `Person #${id}`;
      return p.email ? `${p.fullName} · ${p.email}` : p.fullName;
    }
    const d = deskById.get(id);
    if (!d) return `Desk #${id}`;
    const place = [d.building, d.floor && `floor ${d.floor}`]
      .filter(Boolean)
      .join(", ");
    return place ? `${d.label} · ${place}` : d.label;
  };

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
                  asset.type}
              </h1>
              <p className="mt-1 font-mono text-xs text-muted-foreground">
                {asset.assetTag} · SN {asset.serialNumber}
              </p>
            </div>
            <AssetStatusBadge status={asset.status} />
          </div>

          <dl className="mt-6 grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
            <Field k="Type" v={asset.type} />
            <Field
              k="Condition"
              v={asset.condition ? label(asset.condition) : "—"}
            />
            <Field k="Location" v={disposition(asset.status)} />
            <Field k="Holder" v={holderOf(asset.holderType, asset.holderId)} />
            <Field k="Purchased" v={dateOnly(asset.purchaseDate)} />
            <Field k="Deployed" v={dateOnly(asset.deployedOn)} />
            <Field
              k="Warranty ends"
              v={
                asset.warrantyEndsOn
                  ? dateOnly(asset.warrantyEndsOn) +
                    (isPast(asset.warrantyEndsOn) ? " · expired" : "")
                  : "—"
              }
            />
            <Field k="Cost" v={money(asset.purchaseCostCents)} />
            {replaces && (
              <Field
                k="Replaces"
                v={
                  <Link
                    href={`/assets/${replaces.id}`}
                    className="text-primary hover:underline"
                  >
                    {unitName(replaces)}
                  </Link>
                }
              />
            )}
            {replacedBy && (
              <Field
                k="Replaced by"
                v={
                  <Link
                    href={`/assets/${replacedBy.id}`}
                    className="text-primary hover:underline"
                  >
                    {unitName(replacedBy)}
                  </Link>
                }
              />
            )}
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
            <AssetAdmin
              asset={asset}
              signedIn={Boolean(session)}
              types={types}
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
                    {holderOf(h.holderType, h.holderId)}
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

        {onTag.length > 0 && (
          <Card className="md:col-span-3">
            <h2 className="text-sm font-semibold">
              On tag{" "}
              <span className="font-mono text-primary">{asset.assetTag}</span>
            </h2>
            <p className="text-xs text-muted-foreground">
              Accessories bundled under this tag, plus any unit it has been
              reassigned from.
            </p>
            <ul className="mt-3 divide-y divide-border text-sm">
              {onTag.map((x) => (
                <li
                  key={x.id}
                  className="flex flex-wrap items-center justify-between gap-2 py-2.5"
                >
                  <div>
                    <Link
                      href={`/assets/${x.id}`}
                      className="font-medium text-primary hover:underline"
                    >
                      {[x.make, x.model].filter(Boolean).join(" ") || x.type}
                    </Link>
                    <p className="font-mono text-xs text-muted-foreground">
                      {x.type} · SN {x.serialNumber}
                    </p>
                  </div>
                  <AssetStatusBadge status={x.status} />
                </li>
              ))}
            </ul>
          </Card>
        )}

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

function Field({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-muted-foreground">
        {k}
      </dt>
      <dd className="mt-0.5">{v}</dd>
    </div>
  );
}
