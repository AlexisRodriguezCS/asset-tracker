import { redirect } from "next/navigation";
import { getSession } from "@/lib/session";
import { currentClientId } from "@/lib/client";
import { assetReport, listPeople } from "@/lib/api";
import { isSelfServiceUser } from "@/lib/roles";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { StatStrip } from "@/components/ui/stat";
import { label } from "@/lib/format";
import type { Holdings, Person } from "@/lib/types";

const WARRANTY_SOON_DAYS = 60;
const TOP_ROWS = 10;

export const dynamic = "force-dynamic";

/** The audit actions this page names, in the order it lists them. */
const LIFECYCLE: [string, string][] = [
  ["ASSET_ASSIGNED", "Checked out"],
  ["ASSET_RETURNED", "Returned"],
  ["ASSET_STATUS_IN_REPAIR", "Sent for repair"],
  ["ASSET_STATUS_BROKEN", "Marked broken"],
  ["ASSET_STATUS_LOST", "Marked lost"],
  ["ASSET_STATUS_RETIRED", "Retired"],
  ["ASSET_STATUS_RECYCLED", "Recycled"],
];

export default async function ReportsPage() {
  const [session, clientId] = await Promise.all([
    getSession(),
    currentClientId(),
  ]);
  if (!session) redirect("/welcome?next=/reports");
  // every number here is a tenant-wide total, which is the one thing an employee
  // must not see - the API refuses them too, this just avoids an error page
  if (isSelfServiceUser(session.role)) redirect("/");

  const [report, people] = await Promise.all([
    assetReport(clientId, WARRANTY_SOON_DAYS),
    listPeople(clientId),
  ]);

  const personById = new Map(people.map((p) => [p.id, p]));

  const rows = (
    counts: Record<string, number>,
    name: (key: string) => string = (key) => key,
  ): [string, number][] =>
    Object.entries(counts)
      .map(([key, n]): [string, number] => [name(key), n])
      .sort((a, b) => b[1] - a[1]);

  /**
   * The one rollup the API cannot finish: an asset knows the id of the person
   * holding it, but the department lives in people-service. The counts arrive
   * per holder - bounded by headcount, not catalog size - and are folded into
   * departments here, against the people list this page already loads.
   */
  const byDepartment = (holdings: Holdings): [string, number][] => {
    const totals = new Map<string, number>();
    for (const [personId, n] of Object.entries(holdings.byPerson)) {
      const dept = departmentOf(personById.get(Number(personId)));
      totals.set(dept, (totals.get(dept) ?? 0) + n);
    }
    if (holdings.onDesk > 0) totals.set("On a desk", holdings.onDesk);
    if (holdings.unassigned > 0) {
      totals.set("Stockroom / unassigned", holdings.unassigned);
    }
    return [...totals.entries()].sort((a, b) => b[1] - a[1]);
  };

  const conditionRows: [string, number][] = [
    ...rows(report.byCondition, label),
    ...(report.unrated > 0
      ? ([["Unrated", report.unrated]] as [string, number][])
      : []),
  ];

  const warrantyRows: [string, number][] = [
    ["Out of warranty", report.outOfWarranty],
    [`Expiring within ${WARRANTY_SOON_DAYS} days`, report.warrantyExpiringSoon],
    ["In warranty", report.inWarranty],
  ];

  const lifecycleRows: [string, number][] = LIFECYCLE.map(([action, name]) => [
    name,
    report.lifecycle[action] ?? 0,
  ]);

  return (
    <div className="animate-fade-in-up space-y-6">
      <PageHeader
        title="Reports"
        subtitle="Everything below is for the current client, computed live from the fleet and its audit trail"
      />

      <StatStrip
        stats={[
          { label: "Assets", value: report.total },
          { label: "People", value: people.length },
          {
            label: "Replacements",
            value: report.replacements,
            tone: "warn",
          },
          {
            label: "Break / loss events",
            value: report.incidents.total,
            tone: "danger",
          },
          {
            label: "Fleet value (live)",
            value: `$${Math.round(report.fleetValueCents / 100).toLocaleString()}`,
            tone: "primary",
          },
        ]}
      />

      <div className="grid gap-4 md:grid-cols-2">
        <Breakdown title="By type" rows={rows(report.byType)} />
        <Breakdown title="By status" rows={rows(report.byStatus, label)} />
        <Breakdown title="By condition" rows={conditionRows} />
        <Breakdown title="Warranty" rows={warrantyRows} />
        <Breakdown
          title="By department (who holds it)"
          rows={byDepartment(report.holdings)}
        />
        <Breakdown
          title="Lifecycle events (audit trail)"
          rows={lifecycleRows}
        />
        <Breakdown
          title="Break / loss by department"
          rows={byDepartment(report.incidents.holdings)}
          empty="No breakage or loss recorded."
        />
        <Breakdown
          title="Break / loss by asset type"
          rows={rows(report.incidents.byType)}
          empty="No breakage or loss recorded."
        />

        <Card>
          <h2 className="text-sm font-semibold">
            Most-replaced slots ({report.replacements} replacements)
          </h2>
          <p className="text-xs text-muted-foreground">
            A tag that has carried more than one unit of a type — chargers and
            cables lead here.
          </p>
          {report.topReplacedSlots.length === 0 ? (
            <p className="mt-4 text-sm text-muted-foreground">
              Nothing has been replaced yet.
            </p>
          ) : (
            <ul className="mt-3 divide-y divide-border text-sm">
              {report.topReplacedSlots.map((slot) => (
                <li
                  key={`${slot.assetTag}-${slot.type}`}
                  className="flex items-center justify-between py-2"
                >
                  <span>
                    <span className="font-mono text-xs text-primary">
                      {slot.assetTag}
                    </span>{" "}
                    · {slot.type}
                  </span>
                  <span className="tabular-nums text-muted-foreground">
                    ×{slot.count}
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

function departmentOf(person: Person | undefined): string {
  return person?.department ?? "Unknown dept";
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
          {nonZero.slice(0, TOP_ROWS).map(([name, n]) => (
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
