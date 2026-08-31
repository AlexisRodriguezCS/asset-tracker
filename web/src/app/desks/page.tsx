import Link from "next/link";
import { currentClientId } from "@/lib/client";
import { listLocations, listAssets } from "@/lib/api";
import { label } from "@/lib/format";

export default async function DesksPage() {
  const clientId = await currentClientId();
  const [desks, assigned] = await Promise.all([
    listLocations(clientId, "DESK"),
    listAssets({ clientId, holderType: "LOCATION" }),
  ]);

  const byDesk = new Map<number, typeof assigned>();
  for (const a of assigned) {
    if (a.holderId == null) continue;
    const list = byDesk.get(a.holderId) ?? [];
    list.push(a);
    byDesk.set(a.holderId, list);
  }

  return (
    <div className="animate-fade-in-up">
      <h1 className="text-3xl font-semibold tracking-tight">Desks</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        {desks.length} desks · what a future QR scan at a desk would show
      </p>

      <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {desks.map((d) => {
          const items = byDesk.get(d.id) ?? [];
          return (
            <div
              key={d.id}
              className="rounded-lg border border-border bg-card/70 p-4 shadow-card backdrop-blur"
            >
              <div className="flex items-center justify-between">
                <p className="font-medium">{d.label}</p>
                <span className="font-mono text-[11px] text-muted-foreground">
                  {d.qrTag}
                </span>
              </div>
              <p className="text-xs text-muted-foreground">
                {[d.building, d.floor && `floor ${d.floor}`]
                  .filter(Boolean)
                  .join(" · ") || "—"}
              </p>
              {items.length === 0 ? (
                <p className="mt-3 text-sm text-muted-foreground">Empty</p>
              ) : (
                <ul className="mt-3 space-y-1 text-sm">
                  {items.map((a) => (
                    <li key={a.id}>
                      <Link
                        href={`/assets/${a.id}`}
                        className="text-primary hover:underline"
                      >
                        {label(a.type)}
                      </Link>
                      <span className="ml-1 font-mono text-[11px] text-muted-foreground">
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
