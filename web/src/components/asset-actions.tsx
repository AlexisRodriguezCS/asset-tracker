"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { Asset, Person } from "@/lib/types";

/** Check-out / return controls on the asset detail page. Requires a signed-in user. */
export function AssetActions({
  asset,
  people,
  signedIn,
}: {
  asset: Asset;
  people: Person[];
  signedIn: boolean;
}) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [personId, setPersonId] = useState<number | "">(people[0]?.id ?? "");
  const [deskId, setDeskId] = useState<string>("");

  if (!signedIn) {
    return (
      <p className="text-sm text-muted-foreground">
        <a href="/login" className="text-primary hover:underline">
          Sign in
        </a>{" "}
        to check this asset out.
      </p>
    );
  }

  async function call(path: string, body?: unknown) {
    setBusy(true);
    setError(null);
    const res = await fetch(`/api/bff/${path}`, {
      method: "POST",
      headers: body ? { "Content-Type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
    setBusy(false);
    if (res.ok) {
      router.refresh();
      return;
    }
    if (res.status === 409) setError("That asset is already assigned.");
    else if (res.status === 422) setError("That asset is retired or lost.");
    else {
      const b = await res.json().catch(() => null);
      setError(b?.message ?? "Action failed.");
    }
  }

  const assigned = asset.status === "ASSIGNED";

  return (
    <div className="space-y-3">
      {assigned ? (
        <Button
          disabled={busy}
          onClick={() => call(`assignments/return?assetId=${asset.id}`)}
        >
          {busy ? "…" : "Return to stock"}
        </Button>
      ) : (
        <div className="flex flex-wrap items-end gap-2">
          <label className="text-sm">
            <span className="mb-1 block text-xs font-medium text-muted-foreground">
              Check out to person
            </span>
            <select
              value={personId}
              onChange={(e) => setPersonId(Number(e.target.value))}
              className="h-10 rounded-md border border-border bg-background px-3 text-sm"
            >
              {people.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.fullName}
                </option>
              ))}
            </select>
          </label>
          <Button
            disabled={busy || personId === ""}
            onClick={() =>
              call("assignments", {
                clientId: asset.clientId,
                assetId: asset.id,
                holderType: "PERSON",
                holderId: personId,
              })
            }
          >
            {busy ? "…" : "Assign"}
          </Button>
          <span className="text-xs text-muted-foreground">or to a desk id</span>
          <Input
            value={deskId}
            onChange={(e) => setDeskId(e.target.value)}
            placeholder="desk id"
            className="w-24"
          />
          <Button
            variant="outline"
            disabled={busy || !deskId}
            onClick={() =>
              call("assignments", {
                clientId: asset.clientId,
                assetId: asset.id,
                holderType: "LOCATION",
                holderId: Number(deskId),
              })
            }
          >
            Assign to desk
          </Button>
        </div>
      )}
      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  );
}
