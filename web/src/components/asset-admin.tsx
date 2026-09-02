"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { AssetForm } from "@/components/asset-form";
import type { Asset } from "@/lib/types";

const RETIRE_AS = ["LOST", "BROKEN", "RETIRED", "RECYCLED"] as const;
const ACTIVE = ["IN_STOCK", "ASSIGNED", "IN_REPAIR"];

/**
 * Signed-in controls on the asset detail page: edit the descriptive fields, or
 * run retire-and-replace - mark this unit gone and open a pre-filled form for
 * its replacement, which keeps the same tag.
 */
export function AssetAdmin({
  asset,
  signedIn,
  types = [],
}: {
  asset: Asset;
  signedIn: boolean;
  types?: string[];
}) {
  const router = useRouter();
  const [mode, setMode] = useState<"none" | "edit" | "replace">("none");
  const [reason, setReason] = useState<string>("LOST");
  const [busy, setBusy] = useState(false);

  if (!signedIn) return null;

  async function replace() {
    setBusy(true);
    await fetch(`/api/bff/assets/${asset.id}/status`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status: reason }),
    });
    const params = new URLSearchParams({
      type: asset.type,
      tag: asset.assetTag,
      make: asset.make ?? "",
      model: asset.model ?? "",
      supersedes: String(asset.id),
    });
    if (asset.holderType !== "STOCKROOM" && asset.holderId != null) {
      params.set("reassignTo", `${asset.holderType}:${asset.holderId}`);
    }
    router.push(`/assets/new?${params.toString()}`);
  }

  return (
    <div className="mt-4 border-t border-border pt-4">
      {mode === "none" && (
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="outline" onClick={() => setMode("edit")}>
            Edit details
          </Button>
          {ACTIVE.includes(asset.status) && (
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setMode("replace")}
            >
              Retire &amp; replace
            </Button>
          )}
        </div>
      )}

      {mode === "edit" && (
        <div className="space-y-3">
          <p className="text-sm font-medium">Edit details</p>
          <AssetForm
            mode="edit"
            asset={asset}
            types={types}
            onDone={() => setMode("none")}
          />
          <Button size="sm" variant="ghost" onClick={() => setMode("none")}>
            Cancel
          </Button>
        </div>
      )}

      {mode === "replace" && (
        <div className="space-y-2">
          <div className="flex flex-wrap items-end gap-2">
            <label className="text-sm">
              <span className="mb-1 block text-xs font-medium uppercase tracking-wide text-muted-foreground">
                Mark this unit
              </span>
              <select
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                className="h-10 rounded-md border border-border bg-background px-3 text-sm"
              >
                {RETIRE_AS.map((s) => (
                  <option key={s} value={s}>
                    {s.charAt(0) + s.slice(1).toLowerCase()}
                  </option>
                ))}
              </select>
            </label>
            <Button size="sm" disabled={busy} onClick={replace}>
              {busy ? "…" : "Continue to replacement"}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setMode("none")}>
              Cancel
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            The replacement keeps tag{" "}
            <span className="font-mono">{asset.assetTag}</span>
            {asset.holderType !== "STOCKROOM"
              ? " and is checked straight back out to the current holder."
              : "."}
          </p>
        </div>
      )}
    </div>
  );
}
