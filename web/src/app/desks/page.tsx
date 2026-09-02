import Link from "next/link";
import { MapPin } from "lucide-react";
import { currentClientId } from "@/lib/client";
import { listLocations, listAssets } from "@/lib/api";
import { StatStrip } from "@/components/ui/stat";
import { PageHeader } from "@/components/ui/page-header";
import type { Asset, Location } from "@/lib/types";

export default async function DesksPage({
  searchParams,
}: {
  searchParams: Promise<{ building?: string; floor?: string }>;
}) {
  const [clientId, sp] = await Promise.all([currentClientId(), searchParams]);
  const [allDesks, assigned] = await Promise.all([
    listLocations(clientId, "DESK"),
    listAssets({ clientId, holderType: "LOCATION" }),
  ]);

  const byDesk = new Map<number, Asset[]>();
  for (const a of assigned) {
    if (a.holderId == null) continue;
    byDesk.set(a.holderId, [...(byDesk.get(a.holderId) ?? []), a]);
  }

  const buildings = uniq(allDesks.map((d) => d.building));
  const floorsForFilter = uniq(
    allDesks
      .filter((d) => !sp.building || d.building === sp.building)
      .map((d) => d.floor),
  );

  const desks = allDesks.filter(
    (d) =>
      (!sp.building || d.building === sp.building) &&
      (!sp.floor || d.floor === sp.floor),
  );
  const occupied = desks.filter((d) => (byDesk.get(d.id)?.length ?? 0) > 0);
  const itemsPlaced = desks.reduce(
    (n, d) => n + (byDesk.get(d.id)?.length ?? 0),
    0,
  );

  const link = (p: Record<string, string>) => {
    const q = new URLSearchParams(p).toString();
    return q ? `/desks?${q}` : "/desks";
  };

  // group the filtered desks building -> floor for a floor-plan style layout
  const groups = new Map<string, Map<string, Location[]>>();
  for (const d of desks) {
    const b = d.building ?? "Unassigned";
    const f = d.floor ?? "—";
    if (!groups.has(b)) groups.set(b, new Map());
    const floors = groups.get(b)!;
    floors.set(f, [...(floors.get(f) ?? []), d]);
  }

  return (
    <div className="animate-fade-in-up space-y-6">
      <PageHeader
        title="Desks"
        subtitle="Every desk and what's on it, laid out by building and floor — the view a QR scan at the desk would open"
      />

      <StatStrip
        stats={[
          { label: "Desks", value: desks.length },
          { label: "Occupied", value: occupied.length, tone: "primary" },
          {
            label: "Empty",
            value: desks.length - occupied.length,
            tone: "success",
          },
          { label: "Items placed", value: itemsPlaced },
        ]}
      />

      <div className="space-y-2">
        <ChipRow heading="Building">
          <Chip
            href={link(sp.floor ? { floor: sp.floor } : {})}
            active={!sp.building}
          >
            All
          </Chip>
          {buildings.map((b) => (
            <Chip
              key={b}
              href={link({ building: b })}
              active={sp.building === b}
            >
              {b}
            </Chip>
          ))}
        </ChipRow>
        <ChipRow heading="Floor">
          <Chip
            href={link(sp.building ? { building: sp.building } : {})}
            active={!sp.floor}
          >
            All
          </Chip>
          {floorsForFilter.map((f) => (
            <Chip
              key={f}
              href={link({
                floor: f,
                ...(sp.building ? { building: sp.building } : {}),
              })}
              active={sp.floor === f}
            >
              Floor {f}
            </Chip>
          ))}
        </ChipRow>
      </div>

      {desks.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          No desks match this filter.
        </p>
      ) : (
        [...groups.entries()].sort(byKey).map(([building, floors]) => (
          <section key={building} className="space-y-4">
            <h2 className="font-display text-lg font-semibold tracking-tight">
              {building}
              <span className="ml-2 text-sm font-normal text-muted-foreground">
                {[...floors.values()].reduce((n, ds) => n + ds.length, 0)} desks
              </span>
            </h2>
            {[...floors.entries()].sort(byKey).map(([floor, floorDesks]) => (
              <div key={floor}>
                <p className="mb-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Floor {floor}
                </p>
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  {floorDesks.map((d) => (
                    <DeskCard
                      key={d.id}
                      desk={d}
                      items={byDesk.get(d.id) ?? []}
                    />
                  ))}
                </div>
              </div>
            ))}
          </section>
        ))
      )}
    </div>
  );
}

function DeskCard({ desk, items }: { desk: Location; items: Asset[] }) {
  return (
    <div className="rounded-lg border border-border bg-card/70 p-4 shadow-card backdrop-blur transition-colors hover:border-primary/30">
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-2">
          <span
            className={
              "grid h-8 w-8 place-items-center rounded-md " +
              (items.length > 0
                ? "bg-gradient-primary text-primary-foreground"
                : "bg-accent text-primary")
            }
          >
            <MapPin className="h-4 w-4" />
          </span>
          <div>
            <p className="font-medium leading-tight">{desk.label}</p>
            <p className="text-xs text-muted-foreground">
              {[desk.building, desk.floor && `floor ${desk.floor}`]
                .filter(Boolean)
                .join(" · ") || "—"}
            </p>
          </div>
        </div>
        <span className="font-mono text-[11px] text-muted-foreground">
          {desk.qrTag}
        </span>
      </div>

      {items.length === 0 ? (
        <p className="mt-4 text-sm text-muted-foreground">Empty</p>
      ) : (
        <ul className="mt-4 space-y-1.5 text-sm">
          {items.map((a) => (
            <li key={a.id} className="flex items-center justify-between">
              <Link
                href={`/assets/${a.id}`}
                className="text-primary hover:underline"
              >
                {[a.make, a.model].filter(Boolean).join(" ") || a.type}
              </Link>
              <span className="font-mono text-[11px] text-muted-foreground">
                {a.assetTag}
              </span>
            </li>
          ))}
        </ul>
      )}
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

function uniq(values: (string | null)[]): string[] {
  return [...new Set(values.filter((v): v is string => Boolean(v)))].sort();
}

function byKey(a: [string, unknown], b: [string, unknown]): number {
  return a[0].localeCompare(b[0], undefined, { numeric: true });
}
