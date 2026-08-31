import Link from "next/link";
import { currentClientId } from "@/lib/client";
import { listAssets } from "@/lib/api";
import { getSession } from "@/lib/session";
import { AssetStatusBadge } from "@/components/ui/badge";
import { label } from "@/lib/format";
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
const STATUSES: AssetStatus[] = [
  "IN_STOCK",
  "ASSIGNED",
  "IN_REPAIR",
  "RETIRED",
  "LOST",
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

  const assets = await listAssets({
    clientId,
    type: sp.type,
    status: sp.status,
  });

  const counts = STATUSES.map((s) => ({
    s,
    n: assets.filter((a) => a.status === s).length,
  }));

  return (
    <div className="animate-fade-in-up">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Assets</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {assets.length} shown{sp.type ? ` · ${label(sp.type)}` : ""}
            {sp.status ? ` · ${label(sp.status)}` : ""}
          </p>
        </div>
        {!session && (
          <Link
            href="/login"
            className="text-sm text-muted-foreground hover:text-foreground"
          >
            Sign in to check assets out →
          </Link>
        )}
      </div>

      <div className="mt-5 flex flex-wrap gap-2">
        <FilterLink label="All" active={!sp.type && !sp.status} params={{}} />
        {TYPES.map((t) => (
          <FilterLink
            key={t}
            label={label(t)}
            active={sp.type === t}
            params={{ type: t }}
          />
        ))}
        <span className="mx-1 w-px bg-border" />
        {counts
          .filter((c) => c.n > 0)
          .map((c) => (
            <FilterLink
              key={c.s}
              label={`${label(c.s)} ${c.n}`}
              active={sp.status === c.s}
              params={{ status: c.s }}
            />
          ))}
      </div>

      <div className="mt-6 overflow-x-auto rounded-lg border border-border bg-card/70 shadow-card backdrop-blur">
        <table className="w-full text-sm">
          <thead className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
            <tr>
              <th className="px-4 py-3">Tag</th>
              <th className="px-4 py-3">Type</th>
              <th className="px-4 py-3">Make / model</th>
              <th className="px-4 py-3">Serial</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Holder</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {assets.map((a) => (
              <tr key={a.id} className="hover:bg-muted/40">
                <td className="px-4 py-2.5 font-mono text-xs">
                  <Link
                    href={`/assets/${a.id}`}
                    className="text-primary hover:underline"
                  >
                    {a.assetTag}
                  </Link>
                </td>
                <td className="px-4 py-2.5">{label(a.type)}</td>
                <td className="px-4 py-2.5">
                  {[a.make, a.model].filter(Boolean).join(" ") || "—"}
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
              </tr>
            ))}
            {assets.length === 0 && (
              <tr>
                <td
                  colSpan={6}
                  className="px-4 py-10 text-center text-muted-foreground"
                >
                  No assets match.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function FilterLink({
  label: text,
  active,
  params,
}: {
  label: string;
  active: boolean;
  params: Record<string, string>;
}) {
  const qs = new URLSearchParams(params).toString();
  return (
    <Link
      href={qs ? `/?${qs}` : "/"}
      className={
        "rounded-full border px-3 py-1 text-xs font-medium transition-colors " +
        (active
          ? "border-primary/40 bg-accent text-primary"
          : "border-border text-muted-foreground hover:bg-muted")
      }
    >
      {text}
    </Link>
  );
}
