import Link from "next/link";
import { redirect } from "next/navigation";
import {
  assetAttention,
  assetStats,
  listAssets,
  listPeople,
  listLocations,
  clientActivity,
} from "@/lib/api";
import { getSession } from "@/lib/session";
import { currentClientId } from "@/lib/client";
import { isSelfServiceUser } from "@/lib/roles";
import { PageHeader } from "@/components/ui/page-header";
import { StatStrip } from "@/components/ui/stat";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { AuditFeed } from "@/components/audit-feed";
import type { Asset, AssetStatus } from "@/lib/types";

export const dynamic = "force-dynamic";
export const metadata = { title: "Dashboard" };

// Which statuses count as still-in-the-fleet used to be duplicated here; it now
// lives once in the backend (AssetStatus.IN_SERVICE), which is what decides the
// warranty buckets this page renders.
const WARRANTY_SOON_DAYS = 60;
const RECENT_ROWS = 8;
const PREVIEW_ROWS = 5;

export default async function DashboardPage() {
  const [session, clientId] = await Promise.all([
    getSession(),
    currentClientId(),
  ]);
  if (!session) redirect("/welcome?next=/dashboard");
  // The fleet dashboard is a staff view. An employee is scoped out of every number
  // on it, so it would render a wall of zeroes that says nothing about their gear.
  if (isSelfServiceUser(session.role)) redirect("/");

  // Counts and previews are aggregated in the database. Desk occupancy still
  // needs rows, but only the ones held by a location - bounded by desk count,
  // not by the size of the catalog.
  const [stats, attention, deskAssets, people, desks, activity] =
    await Promise.all([
      assetStats(clientId, WARRANTY_SOON_DAYS).catch(() => null),
      assetAttention(clientId, WARRANTY_SOON_DAYS).catch(() => null),
      listAssets({ clientId, holderType: "LOCATION" }).catch(
        () => [] as Asset[],
      ),
      listPeople(clientId).catch(() => []),
      listLocations(clientId, "DESK").catch(() => []),
      clientActivity(clientId).catch(() => []),
    ]);

  const bucket = (key: string) =>
    attention?.buckets.find((b) => b.key === key) ?? {
      key,
      total: 0,
      sample: [] as Asset[],
    };
  const statusCount = (...ss: AssetStatus[]) =>
    ss.reduce((n, s2) => n + (stats?.byStatus[s2] ?? 0), 0);

  const heldByDesk = new Set<number | null>();
  for (const a of deskAssets) heldByDesk.add(a.holderId);

  // Offboarding is normally a handful of people, so ask about each rather than
  // pulling every assignment to build a map that is mostly zeroes.
  const offboarding = people.filter((p) => p.status === "OFFBOARDING");
  const offboardingWithGear = (
    await Promise.all(
      offboarding.map(async (person) => ({
        person,
        held: (
          await listAssets({
            clientId,
            holderType: "PERSON",
            holderId: person.id,
          }).catch(() => [] as Asset[])
        ).length,
      })),
    )
  ).filter((r) => r.held > 0);

  const label = (a: Asset) =>
    [a.make, a.model].filter(Boolean).join(" ") || a.type;

  return (
    <div className="animate-fade-in-up space-y-6">
      <PageHeader
        title="Dashboard"
        subtitle="Where the fleet stands right now, and what needs a look"
        action={
          session ? (
            <Link href="/assets/new">
              <Button size="sm">Add asset</Button>
            </Link>
          ) : undefined
        }
      />

      <StatStrip
        stats={[
          { label: "Assets", value: stats?.total ?? 0, href: "/" },
          {
            label: "In use",
            value: statusCount("ASSIGNED"),
            tone: "primary",
            href: "/?status=ASSIGNED",
          },
          {
            label: "Available",
            value: statusCount("IN_STOCK"),
            tone: "success",
            href: "/?status=IN_STOCK",
          },
          { label: "People", value: people.length, href: "/people" },
          {
            label: "Desks occupied",
            value: `${desks.filter((d) => heldByDesk.has(d.id)).length}/${desks.length}`,
            href: "/desks",
          },
        ]}
      />

      <div>
        <h2 className="mb-3 font-display text-lg font-semibold tracking-tight">
          Needs attention
        </h2>
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          <AttentionCard
            title="In repair or broken"
            tone="warn"
            count={bucket("repair").total}
            href="/?status=IN_REPAIR"
            rows={bucket("repair")
              .sample.slice(0, PREVIEW_ROWS)
              .map((a) => ({
                key: a.id,
                href: `/assets/${a.id}`,
                left: a.assetTag,
                right: label(a),
              }))}
          />
          <AttentionCard
            title="Out of warranty"
            tone="danger"
            count={bucket("outOfWarranty").total}
            href="/?warranty=expired"
            rows={bucket("outOfWarranty")
              .sample.slice(0, PREVIEW_ROWS)
              .map((a) => ({
                key: a.id,
                href: `/assets/${a.id}`,
                left: a.assetTag,
                right: label(a),
              }))}
          />
          <AttentionCard
            title="Warranty expiring soon"
            tone="warn"
            count={bucket("warrantyExpiringSoon").total}
            href="/?warranty=soon"
            rows={bucket("warrantyExpiringSoon")
              .sample.slice(0, PREVIEW_ROWS)
              .map((a) => ({
                key: a.id,
                href: `/assets/${a.id}`,
                left: a.assetTag,
                right: label(a),
              }))}
          />
          <AttentionCard
            title="Pending recycle"
            tone="danger"
            count={bucket("pendingRecycle").total}
            href="/?status=PENDING_RECYCLE"
            rows={bucket("pendingRecycle")
              .sample.slice(0, PREVIEW_ROWS)
              .map((a) => ({
                key: a.id,
                href: `/assets/${a.id}`,
                left: a.assetTag,
                right: label(a),
              }))}
          />
          <AttentionCard
            title="Offboarding · gear not collected"
            tone="danger"
            count={offboardingWithGear.length}
            href="/people"
            rows={offboardingWithGear.slice(0, PREVIEW_ROWS).map((r) => ({
              key: r.person.id,
              href: `/people/${r.person.id}`,
              left: r.person.fullName,
              right: `${r.held} held`,
            }))}
          />
        </div>
      </div>

      <Card>
        <h2 className="text-sm font-semibold">Recent activity</h2>
        <AuditFeed events={activity.slice(0, RECENT_ROWS)} />
      </Card>
    </div>
  );
}

type Row = { key: number; href: string; left: string; right: string };

const TONE_TEXT = {
  warn: "text-warning",
  danger: "text-destructive",
} as const;

function AttentionCard({
  title,
  tone,
  count,
  href,
  rows,
}: {
  title: string;
  tone: keyof typeof TONE_TEXT;
  count: number;
  href: string;
  rows: Row[];
}) {
  return (
    <Card className="flex flex-col p-4">
      <div className="flex items-baseline justify-between">
        <h3 className="text-sm font-medium">{title}</h3>
        <span
          className={`font-display text-xl font-semibold tabular-nums ${count > 0 ? TONE_TEXT[tone] : "text-muted-foreground"}`}
        >
          {count}
        </span>
      </div>
      {count === 0 ? (
        <p className="mt-3 text-sm text-muted-foreground">All clear.</p>
      ) : (
        <>
          <ul className="mt-3 flex-1 divide-y divide-border/70 text-sm">
            {rows.map((r) => (
              <li
                key={r.key}
                className="flex items-center justify-between gap-3 py-1.5"
              >
                <Link
                  href={r.href}
                  className="truncate font-mono text-xs text-primary hover:underline"
                >
                  {r.left}
                </Link>
                <span className="truncate text-right text-muted-foreground">
                  {r.right}
                </span>
              </li>
            ))}
          </ul>
          <Link
            href={href}
            className="mt-3 text-xs text-muted-foreground hover:text-foreground"
          >
            {count > rows.length ? `View all ${count}` : "Open in list"} →
          </Link>
        </>
      )}
    </Card>
  );
}
