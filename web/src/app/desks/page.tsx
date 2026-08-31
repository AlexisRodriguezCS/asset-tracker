import Link from "next/link";
import { MapPin } from "lucide-react";
import { currentClientId } from "@/lib/client";
import { listLocations, listAssets } from "@/lib/api";
import { StatStrip } from "@/components/ui/stat";
import { PageHeader } from "@/components/ui/page-header";
import { label } from "@/lib/format";
import type { Asset } from "@/lib/types";

export default async function DesksPage() {
  const clientId = await currentClientId();
  const [desks, assigned] = await Promise.all([
    listLocations(clientId, "DESK"),
    listAssets({ clientId, holderType: "LOCATION" }),
  ]);

  const byDesk = new Map<number, Asset[]>();
  for (const a of assigned) {
    if (a.holderId == null) continue;
    byDesk.set(a.holderId, [...(byDesk.get(a.holderId) ?? []), a]);
  }
  const occupied = desks.filter(
    (d) => (byDesk.get(d.id)?.length ?? 0) > 0,
  ).length;

  return (
    <div className="animate-fade-in-up space-y-6">
      <PageHeader
        title="Desks"
        subtitle="Each desk and what's on it — the view a QR scan at the desk would open"
      />

      <StatStrip
        stats={[
          { label: "Desks", value: desks.length },
          { label: "Occupied", value: occupied, tone: "primary" },
          { label: "Empty", value: desks.length - occupied, tone: "success" },
          { label: "Items placed", value: assigned.length },
        ]}
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {desks.map((d) => {
          const items = byDesk.get(d.id) ?? [];
          return (
            <div
              key={d.id}
              className="rounded-lg border border-border bg-card/70 p-4 shadow-card backdrop-blur transition-colors hover:border-primary/30"
            >
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-2">
                  <span className="grid h-8 w-8 place-items-center rounded-md bg-accent text-primary">
                    <MapPin className="h-4 w-4" />
                  </span>
                  <div>
                    <p className="font-medium leading-tight">{d.label}</p>
                    <p className="text-xs text-muted-foreground">
                      {[d.building, d.floor && `floor ${d.floor}`]
                        .filter(Boolean)
                        .join(" · ") || "—"}
                    </p>
                  </div>
                </div>
                <span className="font-mono text-[11px] text-muted-foreground">
                  {d.qrTag}
                </span>
              </div>

              {items.length === 0 ? (
                <p className="mt-4 text-sm text-muted-foreground">Empty</p>
              ) : (
                <ul className="mt-4 space-y-1.5 text-sm">
                  {items.map((a) => (
                    <li
                      key={a.id}
                      className="flex items-center justify-between"
                    >
                      <Link
                        href={`/assets/${a.id}`}
                        className="text-primary hover:underline"
                      >
                        {[a.make, a.model].filter(Boolean).join(" ") ||
                          label(a.type)}
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
        })}
      </div>
    </div>
  );
}
