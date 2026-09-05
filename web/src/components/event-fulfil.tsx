"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import type { Asset, EventRequestLine } from "@/lib/types";

/**
 * Hands the gear out: pick the actual assets going against each line, then every
 * one is checked out to the requester through the normal custody path.
 */
export function EventFulfil({
  id,
  lines,
  stock,
}: {
  id: number;
  lines: EventRequestLine[];
  stock: Asset[];
}) {
  const router = useRouter();
  const [picked, setPicked] = useState<Record<number, number[]>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function toggle(lineId: number, assetId: number) {
    setPicked((p) => {
      const current = p[lineId] ?? [];
      return {
        ...p,
        [lineId]: current.includes(assetId)
          ? current.filter((a) => a !== assetId)
          : [...current, assetId],
      };
    });
  }

  const chosen = Object.values(picked).flat().length;

  async function submit() {
    setBusy(true);
    setError(null);
    const res = await fetch(
      `/api/bff/assignments/event-requests/${id}/fulfil`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          lines: Object.entries(picked)
            .filter(([, ids]) => ids.length > 0)
            .map(([lineId, ids]) => ({
              lineId: Number(lineId),
              assetIds: ids,
            })),
        }),
      },
    );
    setBusy(false);
    if (!res.ok) {
      const problem = await res.json().catch(() => null);
      setError(problem?.message ?? "Could not hand the gear out.");
      return;
    }
    router.refresh();
  }

  return (
    <div className="space-y-4">
      {lines.map((line) => {
        const options = stock.filter((a) => a.type === line.itemType);
        const taken = picked[line.id] ?? [];
        return (
          <div key={line.id}>
            <p className="text-sm font-medium">
              {line.quantity} × {line.itemType}{" "}
              <span className="font-normal text-muted-foreground">
                — {taken.length} picked
              </span>
            </p>
            {options.length === 0 ? (
              <p className="mt-1 text-sm text-muted-foreground">
                Nothing of this type is in stock right now.
              </p>
            ) : (
              <div className="mt-2 flex flex-wrap gap-1.5">
                {options.map((a) => (
                  <button
                    key={a.id}
                    type="button"
                    onClick={() => toggle(line.id, a.id)}
                    className={
                      taken.includes(a.id)
                        ? "rounded-md border border-primary bg-primary/10 px-2 py-1 font-mono text-xs text-primary"
                        : "rounded-md border border-border px-2 py-1 font-mono text-xs text-muted-foreground hover:border-primary/50"
                    }
                  >
                    {a.assetTag}
                  </button>
                ))}
              </div>
            )}
          </div>
        );
      })}

      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      )}

      <Button size="sm" disabled={busy || chosen === 0} onClick={submit}>
        {busy
          ? "Handing out…"
          : `Hand out ${chosen} item${chosen === 1 ? "" : "s"}`}
      </Button>
    </div>
  );
}
