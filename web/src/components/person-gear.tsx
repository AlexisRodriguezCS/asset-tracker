"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { AssetStatusBadge } from "@/components/ui/badge";
import { label } from "@/lib/format";
import type { Asset } from "@/lib/types";

/**
 * Everything about what this person holds, on the page about this person.
 *
 * Handing one laptop over used to mean opening the asset, finding the person in a dropdown and
 * checking it out; taking one back meant opening the asset again. The only thing the person page
 * offered was "collect all", which is the offboarding sweep - far too big a hammer for swapping a
 * charger. Both directions now happen here, against the same endpoints as before.
 *
 * The two actions are gated separately because the services are: handing gear out is an operator's
 * job, taking it back is anyone who collects, which includes HR.
 */
export function PersonGear({
  clientId,
  personId,
  held,
  inStock,
  canGive,
  canTakeBack,
}: {
  clientId: number;
  personId: number;
  held: Asset[];
  /** What is in the stockroom right now, to hand out. */
  inStock: Asset[];
  canGive: boolean;
  canTakeBack: boolean;
}) {
  const router = useRouter();
  const [busyId, setBusyId] = useState<number | null>(null);
  const [giving, setGiving] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function post(path: string, body?: unknown) {
    setError(null);
    const res = await fetch(`/api/bff/${path}`, {
      method: "POST",
      ...(body
        ? {
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
          }
        : {}),
    });
    if (!res.ok) {
      const problem = await res.json().catch(() => null);
      setError(problem?.message ?? "That did not work.");
      return false;
    }
    router.refresh();
    return true;
  }

  async function give() {
    if (!giving) return;
    setBusyId(-1);
    const ok = await post("assignments", {
      clientId,
      assetId: Number(giving),
      holderType: "PERSON",
      holderId: personId,
    });
    setBusyId(null);
    if (ok) setGiving("");
  }

  async function takeBack(assetId: number) {
    setBusyId(assetId);
    await post(`assignments/return?assetId=${assetId}&clientId=${clientId}`);
    setBusyId(null);
  }

  return (
    <div className="space-y-4">
      {canGive && (
        <div className="flex flex-wrap items-center gap-2">
          <label className="sr-only" htmlFor="give">
            Gear to hand over
          </label>
          <select
            id="give"
            value={giving}
            disabled={busyId !== null || inStock.length === 0}
            onChange={(e) => setGiving(e.target.value)}
            className="h-9 min-w-[16rem] rounded-md border border-border bg-background px-3 text-sm outline-none focus-visible:border-primary"
          >
            <option value="">
              {inStock.length === 0
                ? "Nothing in the stockroom"
                : "Hand over something from stock…"}
            </option>
            {inStock.map((a) => (
              <option key={a.id} value={a.id}>
                {a.assetTag} ·{" "}
                {[a.make, a.model].filter(Boolean).join(" ") || label(a.type)}
              </option>
            ))}
          </select>
          <Button
            size="sm"
            disabled={!giving || busyId !== null}
            onClick={give}
          >
            {busyId === -1 ? "Handing over…" : "Hand over"}
          </Button>
        </div>
      )}

      {held.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          This person holds no assets.
        </p>
      ) : (
        <ul className="divide-y divide-border">
          {held.map((a) => (
            <li
              key={a.id}
              className="flex items-center justify-between gap-3 py-2.5"
            >
              <div className="min-w-0">
                <Link
                  href={`/assets/${a.id}`}
                  className="font-medium text-primary hover:underline"
                >
                  {[a.make, a.model].filter(Boolean).join(" ") || label(a.type)}
                </Link>
                <p className="font-mono text-xs text-muted-foreground">
                  {a.assetTag} · {label(a.type)}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <AssetStatusBadge status={a.status} />
                {canTakeBack && (
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={busyId !== null}
                    onClick={() => takeBack(a.id)}
                  >
                    {busyId === a.id ? "Returning…" : "Return"}
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
