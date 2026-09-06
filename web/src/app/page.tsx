import Link from "next/link";
import { redirect } from "next/navigation";
import { currentClientId } from "@/lib/client";
import {
  assetStats,
  listAssets,
  listAssetsPaged,
  listAssetTypes,
  listLocations,
  listPeople,
} from "@/lib/api";
import { getSession } from "@/lib/session";
import { canOperateAssets, isSelfServiceUser } from "@/lib/roles";
import { AssetStatusBadge, ConditionBadge } from "@/components/ui/badge";
import { StatStrip } from "@/components/ui/stat";
import { PageHeader, TableCard } from "@/components/ui/page-header";
import { Pagination } from "@/components/pagination";
import { Button } from "@/components/ui/button";
import { dateOnly, disposition, isPast, label, withinDays } from "@/lib/format";
import type { Asset, AssetStatus } from "@/lib/types";

/** Warranty counts as "expiring soon" this many days before it ends. */
const WARRANTY_SOON_DAYS = 60;

/**
 * Rows rendered per page. This table used to render every asset a client owns,
 * which cost ~2kB of HTML each - 2.3MB for a 1000-asset tenant, measured. The
 * cap is on what is *rendered*, so it holds on the search path too.
 */
const PAGE_SIZE = 50;

const STATUSES: AssetStatus[] = [
  "IN_STOCK",
  "ASSIGNED",
  "IN_REPAIR",
  "BROKEN",
  "PENDING_RECYCLE",
  "RECYCLED",
  "RETIRED",
  "LOST",
];

export default async function AssetsPage({
  searchParams,
}: {
  searchParams: Promise<{
    type?: string;
    status?: string;
    q?: string;
    warranty?: string;
    page?: string;
  }>;
}) {
  const [clientId, sp, session] = await Promise.all([
    currentClientId(),
    searchParams,
    getSession(),
  ]);
  // Reads need a token now, so without a session every fetch below 401s and the
  // page lands on the error boundary. Send them to sign in instead.
  if (!session) redirect("/welcome?next=/");
  // An employee sees only their own gear here, so the page says so - the nav
  // calls the same destination "My assets".
  const mine = isSelfServiceUser(session.role);

  const term = (sp.q ?? "").trim().toLowerCase();
  const pageIndex = Math.max(0, Number(sp.page ?? "0") || 0);

  // Free-text and warranty filtering happen here rather than in the database -
  // the search covers the holder's name, which asset-service does not know. Those
  // paths therefore still need the full set; plain browsing takes the paged
  // endpoint and never materialises more than PAGE_SIZE rows.
  const needsFullSet = Boolean(term) || Boolean(sp.warranty);

  const [stats, page, everything, types, people, desks] = await Promise.all([
    // counts come from the database; the strip no longer needs the catalog
    assetStats(clientId, WARRANTY_SOON_DAYS),
    needsFullSet
      ? null
      : listAssetsPaged({
          clientId,
          type: sp.type,
          status: sp.status,
          page: pageIndex,
          size: PAGE_SIZE,
        }),
    needsFullSet ? listAssets({ clientId }) : null,
    listAssetTypes(clientId).catch(() => []),
    listPeople(clientId).catch(() => []),
    listLocations(clientId, "DESK").catch(() => []),
  ]);

  const filtered = (everything ?? []).filter(
    (a) =>
      (!sp.type || a.type === sp.type) &&
      (!sp.status || a.status === sp.status),
  );

  const personName = new Map(people.map((p) => [p.id, p.fullName]));
  const deskName = new Map(desks.map((d) => [d.id, d.label]));
  const holderLabel = (a: Asset) => {
    if (a.holderType === "STOCKROOM" || a.holderId == null) return "Stockroom";
    if (a.holderType === "PERSON")
      return personName.get(a.holderId) ?? `Person #${a.holderId}`;
    return deskName.get(a.holderId) ?? `Location #${a.holderId}`;
  };

  const warrantyMatch = (a: Asset) => {
    if (sp.warranty === "expired") return isPast(a.warrantyEndsOn);
    if (sp.warranty === "soon")
      return withinDays(a.warrantyEndsOn, WARRANTY_SOON_DAYS);
    return true;
  };
  const matches = needsFullSet
    ? filtered
        .filter(warrantyMatch)
        .filter(
          (a) =>
            !term ||
            [
              a.assetTag,
              a.serialNumber,
              a.make,
              a.model,
              a.type,
              holderLabel(a),
            ]
              .filter(Boolean)
              .some((v) => (v as string).toLowerCase().includes(term)),
        )
    : [];

  // one shape for the table whichever path produced it
  const assets = needsFullSet
    ? matches.slice(pageIndex * PAGE_SIZE, (pageIndex + 1) * PAGE_SIZE)
    : (page?.items ?? []);
  const matchTotal = needsFullSet ? matches.length : (page?.total ?? 0);
  const totalPages = Math.max(1, Math.ceil(matchTotal / PAGE_SIZE));

  const count = (...ss: AssetStatus[]) =>
    ss.reduce((n, status) => n + (stats.byStatus[status] ?? 0), 0);
  const link = (p: Record<string, string>) => {
    const merged: Record<string, string> = {
      ...(term ? { q: sp.q as string } : {}),
      ...(sp.warranty ? { warranty: sp.warranty } : {}),
      ...p,
    };
    for (const k of Object.keys(merged)) if (!merged[k]) delete merged[k];
    const s = new URLSearchParams(merged).toString();
    return s ? `/?${s}` : "/";
  };

  // Paging keeps the active filters; changing a filter drops the page, because
  // "page 7 of the old filter" is never what you meant.
  const pageLink = (next: number) => {
    const merged: Record<string, string> = {
      ...(sp.type ? { type: sp.type } : {}),
      ...(sp.status ? { status: sp.status } : {}),
      ...(term ? { q: sp.q as string } : {}),
      ...(sp.warranty ? { warranty: sp.warranty } : {}),
      ...(next > 0 ? { page: String(next) } : {}),
    };
    const s = new URLSearchParams(merged).toString();
    return s ? `/?${s}` : "/";
  };

  // filters that survive when you click a chip in another row
  const keepType: Record<string, string> = sp.type ? { type: sp.type } : {};
  const keepStatus: Record<string, string> = sp.status
    ? { status: sp.status }
    : {};
  const outOfWarranty = stats.outOfWarranty;

  return (
    <div className="animate-fade-in-up space-y-6">
      <PageHeader
        title={mine ? "My assets" : "Assets"}
        subtitle={
          term
            ? `${matchTotal} of ${stats.total} match "${sp.q}"`
            : mine
              ? `${stats.total} assigned to you`
              : outOfWarranty > 0
                ? `${stats.total} tracked for this client · ${outOfWarranty} out of warranty`
                : `${stats.total} tracked for this client`
        }
        action={
          // creating and importing assets is a tech action; an employee would
          // only get a 403 from the buttons
          canOperateAssets(session.role) ? (
            <div className="flex gap-2">
              <Link href="/import">
                <Button size="sm" variant="outline">
                  Import
                </Button>
              </Link>
              <Link href="/assets/new">
                <Button size="sm">Add asset</Button>
              </Link>
            </div>
          ) : undefined
        }
      />

      {!mine && (
        <StatStrip
          stats={[
            {
              label: "Total",
              value: stats.total,
              href: "/",
              active: !sp.status && !sp.type,
            },
            {
              label: "In storage",
              value: count("IN_STOCK"),
              tone: "success",
              href: link({ status: "IN_STOCK" }),
              active: sp.status === "IN_STOCK",
            },
            {
              label: "In use",
              value: count("ASSIGNED"),
              tone: "primary",
              href: link({ status: "ASSIGNED" }),
              active: sp.status === "ASSIGNED",
            },
            {
              label: "Repair",
              value: count("IN_REPAIR", "BROKEN"),
              tone: "warn",
              href: link({ status: "IN_REPAIR" }),
              active: sp.status === "IN_REPAIR",
            },
            {
              label: "End of life",
              value: count("PENDING_RECYCLE", "RECYCLED", "RETIRED", "LOST"),
              tone: "danger",
              href: link({ status: "RETIRED" }),
              active: sp.status === "RETIRED",
            },
          ]}
        />
      )}

      {/*
        Someone with a handful of items does not need eleven type chips, nine
        statuses - most of which describe gear that has left the fleet - and a
        warranty filter. Staff triaging a catalog do.
      */}
      {!mine && (
        <div className="space-y-2">
          <ChipRow heading="Type">
            <Chip href={link({ ...keepStatus })} active={!sp.type}>
              All
            </Chip>
            {types.map((t) => (
              <Chip
                key={t.id}
                href={link({ type: t.name, ...keepStatus })}
                active={sp.type === t.name}
              >
                {t.name}
              </Chip>
            ))}
            {canOperateAssets(session.role) && (
              <Link
                href="/types"
                className="ml-1 text-xs text-muted-foreground hover:text-foreground"
              >
                Manage
              </Link>
            )}
          </ChipRow>
          <ChipRow heading="Status">
            <Chip href={link({ ...keepType })} active={!sp.status}>
              All
            </Chip>
            {STATUSES.map((s) => (
              <Chip
                key={s}
                href={link({ status: s, ...keepType })}
                active={sp.status === s}
              >
                {label(s)}
              </Chip>
            ))}
          </ChipRow>
          <ChipRow heading="Warranty">
            <Chip
              href={link({ warranty: "", ...keepType, ...keepStatus })}
              active={!sp.warranty}
            >
              All
            </Chip>
            <Chip
              href={link({ warranty: "expired", ...keepType, ...keepStatus })}
              active={sp.warranty === "expired"}
            >
              Expired
            </Chip>
            <Chip
              href={link({ warranty: "soon", ...keepType, ...keepStatus })}
              active={sp.warranty === "soon"}
            >
              Expiring soon
            </Chip>
          </ChipRow>
        </div>
      )}

      <TableCard>
        <thead className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
          <tr>
            <th className="px-4 py-3 font-medium">Tag</th>
            <th className="px-4 py-3 font-medium">Type</th>
            <th className="px-4 py-3 font-medium">Make / model</th>
            <th className="px-4 py-3 font-medium">Condition</th>
            <th className="px-4 py-3 font-medium">Status</th>
            {!mine && (
              <>
                <th className="px-4 py-3 font-medium">Location</th>
                <th className="px-4 py-3 font-medium">Holder</th>
              </>
            )}
            <th className="px-4 py-3 font-medium">Warranty</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border/70">
          {assets.map((a) => (
            <tr key={a.id} className="transition-colors hover:bg-accent/40">
              <td className="px-4 py-2">
                {/*
                  The detail page is a technician's view - custody history,
                  activity log, purchase and deploy dates. An employee's list
                  already shows everything about their own gear that concerns
                  them, so the tag is not a door out of it.
                */}
                {mine ? (
                  <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs text-muted-foreground">
                    {a.assetTag}
                  </span>
                ) : (
                  <Link
                    href={`/assets/${a.id}`}
                    className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs text-primary hover:bg-accent"
                  >
                    {a.assetTag}
                  </Link>
                )}
              </td>
              <td className="px-4 py-2">{a.type}</td>
              <td className="px-4 py-2">
                {[a.make, a.model].filter(Boolean).join(" ") || (
                  <span className="text-muted-foreground">—</span>
                )}
              </td>
              <td className="px-4 py-2">
                <ConditionBadge condition={a.condition} />
              </td>
              <td className="px-4 py-2">
                <AssetStatusBadge status={a.status} />
              </td>
              {!mine && (
                <>
                  <td className="px-4 py-2 text-muted-foreground">
                    {disposition(a.status)}
                  </td>
                  <td className="px-4 py-2">
                    {a.holderType === "PERSON" && a.holderId != null ? (
                      <Link
                        href={`/people/${a.holderId}`}
                        className="text-primary hover:underline"
                      >
                        {holderLabel(a)}
                      </Link>
                    ) : a.holderType === "LOCATION" && a.holderId != null ? (
                      <Link
                        href={`/desks#desk-${a.holderId}`}
                        className="text-primary hover:underline"
                      >
                        {holderLabel(a)}
                      </Link>
                    ) : (
                      <span className="text-muted-foreground">
                        {holderLabel(a)}
                      </span>
                    )}
                  </td>
                </>
              )}
              <td className="px-4 py-2 text-muted-foreground">
                {a.warrantyEndsOn ? (
                  <span
                    className={
                      isPast(a.warrantyEndsOn) ? "text-destructive" : ""
                    }
                  >
                    {dateOnly(a.warrantyEndsOn)}
                    {isPast(a.warrantyEndsOn) && " · expired"}
                  </span>
                ) : (
                  "—"
                )}
              </td>
            </tr>
          ))}
          {assets.length === 0 && (
            <tr>
              <td
                colSpan={mine ? 6 : 8}
                className="px-4 py-12 text-center text-muted-foreground"
              >
                {mine
                  ? "Nothing is assigned to you right now."
                  : "No assets match this filter."}
              </td>
            </tr>
          )}
        </tbody>
      </TableCard>

      <Pagination
        page={pageIndex}
        totalPages={totalPages}
        total={matchTotal}
        size={PAGE_SIZE}
        href={pageLink}
      />
    </div>
  );
}

function ChipRow({
  heading,
  children,
}: {
  heading: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1.5">
      <span className="w-20 shrink-0 text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {heading}
      </span>
      {children}
    </div>
  );
}

function Chip({
  href,
  active,
  children,
}: {
  href: string;
  active: boolean;
  children: React.ReactNode;
}) {
  return (
    <Link
      href={href}
      className={
        "rounded-full border px-3 py-1 text-xs font-medium transition-colors " +
        (active
          ? "bg-gradient-primary border-primary/50 text-primary-foreground"
          : "border-border text-muted-foreground hover:border-primary/30 hover:bg-muted")
      }
    >
      {children}
    </Link>
  );
}
