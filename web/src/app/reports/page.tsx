import { redirect } from "next/navigation";
import { getSession } from "@/lib/session";
import { currentClientId } from "@/lib/client";
import { listAssets, listPeople, clientActivity } from "@/lib/api";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { StatStrip } from "@/components/ui/stat";
import { label } from "@/lib/format";
import type { Asset } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function ReportsPage() {
  const [session, clientId] = await Promise.all([
    getSession(),
    currentClientId(),
  ]);
  if (!session) redirect("/login");

  const [assets, people, audit] = await Promise.all([
    listAssets({ clientId }),
    listPeople(clientId),
    clientActivity(clientId).catch(() => []),
  ]);

  const personById = new Map(people.map((p) => [p.id, p]));
  const assetById = new Map(assets.map((a) => [a.id, a]));

  const tally = <T,>(items: T[], key: (t: T) => string): [string, number][] => {
    const m = new Map<string, number>();
    for (const it of items) {
      const k = key(it);
      m.set(k, (m.get(k) ?? 0) + 1);
    }
    return [...m.entries()].sort((a, b) => b[1] - a[1]);
  };

  const deptOf = (a: Asset): string => {
    if (a.holderType === "LOCATION") return "On a desk";
    if (a.holderType !== "PERSON" || a.holderId == null)
      return "Stockroom / unassigned";
    return personById.get(a.holderId)?.department ?? "Unknown dept";
  };

  const byType = tally(assets, (a) => label(a.type));
  const byStatus = tally(assets, (a) => label(a.status));
  const byCondition = tally(assets, (a) =>
    a.condition ? label(a.condition) : "Unrated",
  );
  const byDept = tally(assets, deptOf);

  // a "slot" is one tag + type; more than one row on it means it has been refilled
  const slot = new Map<string, Asset[]>();
  for (const a of assets) {
    const k = `${a.assetTag}||${a.type}`;
    slot.set(k, [...(slot.get(k) ?? []), a]);
  }
  const replacements = [...slot.entries()]
    .map(([k, rows]) => {
      const [tag, type] = k.split("||");
      return { tag, type: label(type), n: rows.length - 1 };
    })
    .filter((r) => r.n > 0)
    .sort((a, b) => b.n - a.n);
  const totalReplacements = replacements.reduce((s, r) => s + r.n, 0);

  const events = (action: string) =>
    audit.filter((e) => e.action === action).length;
  const lifecycle: [string, number][] = [
    ["Checked out", events("ASSET_ASSIGNED")],
    ["Returned", events("ASSET_RETURNED")],
    ["Sent for repair", events("ASSET_STATUS_IN_REPAIR")],
    ["Marked broken", events("ASSET_STATUS_BROKEN")],
    ["Marked lost", events("ASSET_STATUS_LOST")],
    ["Retired", events("ASSET_STATUS_RETIRED")],
    ["Recycled", events("ASSET_STATUS_RECYCLED")],
  ];

  const incidents = audit
    .filter(
      (e) =>
        e.action === "ASSET_STATUS_BROKEN" || e.action === "ASSET_STATUS_LOST",
    )
    .map((e) => assetById.get(e.entityId))
    .filter((a): a is Asset => Boolean(a));
  const incidentsByDept = tally(incidents, deptOf);
  const incidentsByType = tally(incidents, (a) => label(a.type));

  const totalCost = assets
    .filter((a) => ["ASSIGNED", "IN_STOCK", "IN_REPAIR"].includes(a.status))
    .reduce((s, a) => s + (a.purchaseCostCents ?? 0), 0);

  return (
    <div className="animate-fade-in-up space-y-6">
      <PageHeader
        title="Reports"
        subtitle="Everything below is for the current client, computed live from the fleet and its audit trail"
      />

      <StatStrip
        stats={[
          { label: "Assets", value: assets.length },
          { label: "People", value: people.length },
          {
            label: "Replacements",
            value: totalReplacements,
            tone: "warn",
          },
          {
            label: "Break / loss events",
            value: incidents.length,
            tone: "danger",
          },
          {
            label: "Fleet value (live)",
            value: `$${Math.round(totalCost / 100).toLocaleString()}`,
            tone: "primary",
          },
        ]}
      />

      <div className="grid gap-4 md:grid-cols-2">
        <Breakdown title="By type" rows={byType} />
        <Breakdown title="By status" rows={byStatus} />
        <Breakdown title="By condition" rows={byCondition} />
        <Breakdown title="By department (who holds it)" rows={byDept} />
        <Breakdown title="Lifecycle events (audit trail)" rows={lifecycle} />
        <Breakdown
          title="Break / loss by department"
          rows={incidentsByDept}
          empty="No breakage or loss recorded."
        />
        <Breakdown
          title="Break / loss by asset type"
          rows={incidentsByType}
          empty="No breakage or loss recorded."
        />

        <Card>
          <h2 className="text-sm font-semibold">
            Most-replaced slots ({totalReplacements} replacements)
          </h2>
          <p className="text-xs text-muted-foreground">
            A tag that has carried more than one unit of a type — chargers and
            cables lead here.
          </p>
          {replacements.length === 0 ? (
            <p className="mt-4 text-sm text-muted-foreground">
              Nothing has been replaced yet.
            </p>
          ) : (
            <ul className="mt-3 divide-y divide-border text-sm">
              {replacements.slice(0, 8).map((r) => (
                <li
                  key={`${r.tag}-${r.type}`}
                  className="flex items-center justify-between py-2"
                >
                  <span>
                    <span className="font-mono text-xs text-primary">
                      {r.tag}
                    </span>{" "}
                    · {r.type}
                  </span>
                  <span className="tabular-nums text-muted-foreground">
                    ×{r.n}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  );
}

function Breakdown({
  title,
  rows,
  empty = "Nothing to show.",
}: {
  title: string;
  rows: [string, number][];
  empty?: string;
}) {
  const max = Math.max(1, ...rows.map(([, n]) => n));
  const nonZero = rows.filter(([, n]) => n > 0);
  return (
    <Card>
      <h2 className="text-sm font-semibold">{title}</h2>
      {nonZero.length === 0 ? (
        <p className="mt-4 text-sm text-muted-foreground">{empty}</p>
      ) : (
        <ul className="mt-3 space-y-2 text-sm">
          {nonZero.slice(0, 10).map(([name, n]) => (
            <li key={name} className="flex items-center gap-3">
              <span className="w-48 shrink-0 truncate text-muted-foreground">
                {name}
              </span>
              <span className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
                <span
                  className="bg-gradient-primary block h-full rounded-full"
                  style={{ width: `${(n / max) * 100}%` }}
                />
              </span>
              <span className="w-8 shrink-0 text-right tabular-nums">{n}</span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
