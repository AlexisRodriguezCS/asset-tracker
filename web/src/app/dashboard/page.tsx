import Link from "next/link";
import {
  listAssets,
  listPeople,
  listLocations,
  clientActivity,
} from "@/lib/api";
import { getSession } from "@/lib/session";
import { currentClientId } from "@/lib/client";
import { PageHeader } from "@/components/ui/page-header";
import { StatStrip } from "@/components/ui/stat";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { AuditFeed } from "@/components/audit-feed";
import { isPast, withinDays } from "@/lib/format";
import type { Asset, AssetStatus } from "@/lib/types";

export const dynamic = "force-dynamic";

const WARRANTY_SOON_DAYS = 60;
const IN_SERVICE: AssetStatus[] = [
  "IN_STOCK",
  "ASSIGNED",
  "IN_REPAIR",
  "BROKEN",
];
const RECENT_ROWS = 8;
const PREVIEW_ROWS = 5;

export default async function DashboardPage() {
  const [session, clientId] = await Promise.all([
    getSession(),
    currentClientId(),
  ]);

  const [assets, people, desks, activity] = await Promise.all([
    listAssets({ clientId }).catch(() => [] as Asset[]),
    listPeople(clientId).catch(() => []),
    listLocations(clientId, "DESK").catch(() => []),
    clientActivity(clientId).catch(() => []),
  ]);

  const is = (...ss: AssetStatus[]) =>
    assets.filter((a) => ss.includes(a.status));
  const heldByDesk = new Set(
    assets.filter((a) => a.holderType === "LOCATION").map((a) => a.holderId),
  );

  const repair = is("IN_REPAIR", "BROKEN");
  const pendingRecycle = is("PENDING_RECYCLE");
  const outOfWarranty = assets.filter(
    (a) => IN_SERVICE.includes(a.status) && isPast(a.warrantyEndsOn),
  );
  const expiringSoon = assets.filter(
    (a) =>
      IN_SERVICE.includes(a.status) &&
      withinDays(a.warrantyEndsOn, WARRANTY_SOON_DAYS),
  );

  const offboardingWithGear = people
    .filter((p) => p.status === "OFFBOARDING")
    .map((p) => ({
      person: p,
      held: assets.filter(
        (a) => a.holderType === "PERSON" && a.holderId === p.id,
      ).length,
    }))
    .filter((r) => r.held > 0);

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
          { label: "Assets", value: assets.length, href: "/" },
          {
            label: "In use",
            value: is("ASSIGNED").length,
            tone: "primary",
            href: "/?status=ASSIGNED",
          },
          {
            label: "Available",
            value: is("IN_STOCK").length,
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
            count={repair.length}
            href="/?status=IN_REPAIR"
            rows={repair.slice(0, PREVIEW_ROWS).map((a) => ({
              key: a.id,
              href: `/assets/${a.id}`,
              left: a.assetTag,
              right: label(a),
            }))}
          />
          <AttentionCard
            title="Out of warranty"
            tone="danger"
            count={outOfWarranty.length}
            href="/?warranty=expired"
            rows={outOfWarranty.slice(0, PREVIEW_ROWS).map((a) => ({
              key: a.id,
              href: `/assets/${a.id}`,
              left: a.assetTag,
              right: label(a),
            }))}
          />
          <AttentionCard
            title="Warranty expiring soon"
            tone="warn"
            count={expiringSoon.length}
            href="/?warranty=soon"
            rows={expiringSoon.slice(0, PREVIEW_ROWS).map((a) => ({
              key: a.id,
              href: `/assets/${a.id}`,
              left: a.assetTag,
              right: label(a),
            }))}
          />
          <AttentionCard
            title="Pending recycle"
            tone="danger"
            count={pendingRecycle.length}
            href="/?status=PENDING_RECYCLE"
            rows={pendingRecycle.slice(0, PREVIEW_ROWS).map((a) => ({
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
  warn: "text-amber-500 dark:text-amber-400",
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
