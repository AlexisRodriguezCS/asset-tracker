import Link from "next/link";
import { currentClientId } from "@/lib/client";
import { listAssets, listCategories } from "@/lib/api";
import { getSession } from "@/lib/session";
import { AssetStatusBadge, ConditionBadge } from "@/components/ui/badge";
import { StatStrip } from "@/components/ui/stat";
import { PageHeader, TableCard } from "@/components/ui/page-header";
import { Button } from "@/components/ui/button";
import { dateOnly, disposition, isPast, label } from "@/lib/format";
import type { AssetStatus, AssetType } from "@/lib/types";

const TYPES: AssetType[] = [
  "LAPTOP",
  "TABLET",
  "PHONE",
  "MONITOR",
  "DOCK",
  "CHARGER",
  "CABLE",
  "HOTSPOT",
  "PERIPHERAL",
  "OTHER",
];

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
    category?: string;
  }>;
}) {
  const [clientId, sp, session] = await Promise.all([
    currentClientId(),
    searchParams,
    getSession(),
  ]);

  // the unfiltered set drives the stat strip; the table uses the active filter
  const [all, filtered, categories] = await Promise.all([
    listAssets({ clientId }),
    listAssets({
      clientId,
      type: sp.type,
      status: sp.status,
      category: sp.category,
    }),
    listCategories(clientId).catch(() => []),
  ]);

  const term = (sp.q ?? "").trim().toLowerCase();
  const assets = term
    ? filtered.filter((a) =>
        [a.assetTag, a.serialNumber, a.make, a.model, a.category]
          .filter(Boolean)
          .some((v) => (v as string).toLowerCase().includes(term)),
      )
    : filtered;

  const count = (...ss: AssetStatus[]) =>
    all.filter((a) => ss.includes(a.status)).length;
  const link = (p: Record<string, string>) => {
    const merged = { ...(term ? { q: sp.q as string } : {}), ...p };
    const s = new URLSearchParams(merged).toString();
    return s ? `/?${s}` : "/";
  };
  // filters that survive when you click a chip in another row
  const keepType: Record<string, string> = sp.type ? { type: sp.type } : {};
  const keepStatus: Record<string, string> = sp.status
    ? { status: sp.status }
    : {};
  const keepCat: Record<string, string> = sp.category
    ? { category: sp.category }
    : {};

  return (
    <div className="animate-fade-in-up space-y-6">
      <PageHeader
        title="Assets"
        subtitle={
          term
            ? `${assets.length} of ${all.length} match "${sp.q}"`
            : `${all.length} tracked for this client`
        }
        action={
          session ? (
            <Link href="/assets/new">
              <Button size="sm">Add asset</Button>
            </Link>
          ) : (
            <Link
              href="/login"
              className="text-sm text-muted-foreground hover:text-foreground"
            >
              Sign in to check assets out →
            </Link>
          )
        }
      />

      <StatStrip
        stats={[
          {
            label: "Total",
            value: all.length,
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

      <div className="space-y-2">
        <ChipRow heading="Type">
          <Chip href={link({ ...keepStatus, ...keepCat })} active={!sp.type}>
            All
          </Chip>
          {TYPES.map((t) => (
            <Chip
              key={t}
              href={link({ type: t, ...keepStatus, ...keepCat })}
              active={sp.type === t}
            >
              {label(t)}
            </Chip>
          ))}
        </ChipRow>
        <ChipRow heading="Status">
          <Chip href={link({ ...keepType, ...keepCat })} active={!sp.status}>
            Any
          </Chip>
          {STATUSES.map((s) => (
            <Chip
              key={s}
              href={link({ status: s, ...keepType, ...keepCat })}
              active={sp.status === s}
            >
              {label(s)}
            </Chip>
          ))}
        </ChipRow>
        {categories.length > 0 && (
          <ChipRow heading="Category">
            <Chip
              href={link({ ...keepType, ...keepStatus })}
              active={!sp.category}
            >
              All
            </Chip>
            {categories.map((c) => (
              <Chip
                key={c}
                href={link({ category: c, ...keepType, ...keepStatus })}
                active={sp.category === c}
              >
                {c}
              </Chip>
            ))}
          </ChipRow>
        )}
      </div>

      <TableCard>
        <thead className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
          <tr>
            <th className="px-4 py-3 font-medium">Tag</th>
            <th className="px-4 py-3 font-medium">Type</th>
            <th className="px-4 py-3 font-medium">Make / model</th>
            <th className="px-4 py-3 font-medium">Condition</th>
            <th className="px-4 py-3 font-medium">Status</th>
            <th className="px-4 py-3 font-medium">Location</th>
            <th className="px-4 py-3 font-medium">Holder</th>
            <th className="px-4 py-3 font-medium">Warranty</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border/70">
          {assets.map((a) => (
            <tr key={a.id} className="transition-colors hover:bg-accent/40">
              <td className="px-4 py-2.5">
                <Link
                  href={`/assets/${a.id}`}
                  className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs text-primary hover:bg-accent"
                >
                  {a.assetTag}
                </Link>
              </td>
              <td className="px-4 py-2.5">{label(a.type)}</td>
              <td className="px-4 py-2.5">
                {[a.make, a.model].filter(Boolean).join(" ") || (
                  <span className="text-muted-foreground">—</span>
                )}
                {a.category && (
                  <span className="block text-xs text-muted-foreground">
                    {a.category}
                  </span>
                )}
              </td>
              <td className="px-4 py-2.5">
                <ConditionBadge condition={a.condition} />
              </td>
              <td className="px-4 py-2.5">
                <AssetStatusBadge status={a.status} />
              </td>
              <td className="px-4 py-2.5 text-muted-foreground">
                {disposition(a.status)}
              </td>
              <td className="px-4 py-2.5 text-muted-foreground">
                {a.holderType === "STOCKROOM"
                  ? "Stockroom"
                  : `${label(a.holderType)} #${a.holderId}`}
              </td>
              <td className="px-4 py-2.5 text-muted-foreground">
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
                colSpan={8}
                className="px-4 py-12 text-center text-muted-foreground"
              >
                No assets match this filter.
              </td>
            </tr>
          )}
        </tbody>
      </TableCard>
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
    <div className="flex flex-wrap items-center gap-2">
      <span className="w-12 text-xs font-medium uppercase tracking-wide text-muted-foreground">
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
