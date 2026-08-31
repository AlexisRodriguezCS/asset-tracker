import Link from "next/link";
import { currentClientId } from "@/lib/client";
import { listAssets } from "@/lib/api";
import { getSession } from "@/lib/session";
import { AssetStatusBadge } from "@/components/ui/badge";
import { StatStrip } from "@/components/ui/stat";
import { PageHeader, TableCard } from "@/components/ui/page-header";
import { label, money } from "@/lib/format";
import type { AssetStatus, AssetType } from "@/lib/types";

const TYPES: AssetType[] = [
  "LAPTOP",
  "TABLET",
  "PHONE",
  "MONITOR",
  "DOCK",
  "CHARGER",
  "CABLE",
  "PERIPHERAL",
  "OTHER",
];

export default async function AssetsPage({
  searchParams,
}: {
  searchParams: Promise<{ type?: string; status?: string }>;
}) {
  const [clientId, sp, session] = await Promise.all([
    currentClientId(),
    searchParams,
    getSession(),
  ]);

  // the unfiltered set drives the stat strip; the table uses the active filter
  const [all, assets] = await Promise.all([
    listAssets({ clientId }),
    listAssets({ clientId, type: sp.type, status: sp.status }),
  ]);

  const count = (s: AssetStatus) => all.filter((a) => a.status === s).length;
  const link = (p: Record<string, string>) => {
    const q = new URLSearchParams(p).toString();
    return q ? `/?${q}` : "/";
  };

  return (
    <div className="animate-fade-in-up space-y-6">
      <PageHeader
        title="Assets"
        subtitle={`${all.length} tracked for this client`}
        action={
          !session && (
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
            label: "In stock",
            value: count("IN_STOCK"),
            tone: "success",
            href: link({ status: "IN_STOCK" }),
            active: sp.status === "IN_STOCK",
          },
          {
            label: "Assigned",
            value: count("ASSIGNED"),
            tone: "primary",
            href: link({ status: "ASSIGNED" }),
            active: sp.status === "ASSIGNED",
          },
          {
            label: "In repair",
            value: count("IN_REPAIR"),
            tone: "warn",
            href: link({ status: "IN_REPAIR" }),
            active: sp.status === "IN_REPAIR",
          },
          {
            label: "Retired / lost",
            value: count("RETIRED") + count("LOST"),
            tone: "danger",
            href: link({ status: "RETIRED" }),
            active: sp.status === "RETIRED",
          },
        ]}
      />

      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
          Type
        </span>
        <Chip href="/" active={!sp.type}>
          All
        </Chip>
        {TYPES.map((t) => (
          <Chip
            key={t}
            href={link({
              type: t,
              ...(sp.status ? { status: sp.status } : {}),
            })}
            active={sp.type === t}
          >
            {label(t)}
          </Chip>
        ))}
      </div>

      <TableCard>
        <thead className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
          <tr>
            <th className="px-4 py-3 font-medium">Tag</th>
            <th className="px-4 py-3 font-medium">Type</th>
            <th className="px-4 py-3 font-medium">Make / model</th>
            <th className="px-4 py-3 font-medium">Serial</th>
            <th className="px-4 py-3 font-medium">Status</th>
            <th className="px-4 py-3 font-medium">Holder</th>
            <th className="px-4 py-3 font-medium">Cost</th>
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
              </td>
              <td className="px-4 py-2.5 font-mono text-xs text-muted-foreground">
                {a.serialNumber}
              </td>
              <td className="px-4 py-2.5">
                <AssetStatusBadge status={a.status} />
              </td>
              <td className="px-4 py-2.5 text-muted-foreground">
                {a.holderType === "STOCKROOM"
                  ? "Stockroom"
                  : `${label(a.holderType)} #${a.holderId}`}
              </td>
              <td className="px-4 py-2.5 tabular-nums text-muted-foreground">
                {money(a.purchaseCostCents)}
              </td>
            </tr>
          ))}
          {assets.length === 0 && (
            <tr>
              <td
                colSpan={7}
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
