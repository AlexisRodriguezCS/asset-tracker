"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { AssetTypeDef } from "@/lib/types";

type LinkedAsset = {
  id: number;
  assetTag: string;
  make: string | null;
  model: string | null;
};

export function TypeManager({
  clientId,
  types,
  usage,
}: {
  clientId: number;
  types: AssetTypeDef[];
  /** assets currently on each type, keyed by type name */
  usage: Record<string, LinkedAsset[]>;
}) {
  const router = useRouter();
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmId, setConfirmId] = useState<number | null>(null);
  const [moveTo, setMoveTo] = useState("");

  async function add(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) return;
    setBusy(true);
    setError(null);
    const res = await fetch("/api/bff/assets/types", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ clientId, name: trimmed }),
    });
    setBusy(false);
    if (res.ok) {
      setName("");
      router.refresh();
      return;
    }
    if (res.status === 409) {
      setError(`"${trimmed}" is already a type.`);
    } else {
      const b = await res.json().catch(() => null);
      setError(b?.message ?? "Could not add the type.");
    }
  }

  async function remove(id: number, reassignTo?: string) {
    setBusy(true);
    setError(null);
    const qs = reassignTo
      ? `?reassignTo=${encodeURIComponent(reassignTo)}`
      : "";
    const res = await fetch(`/api/bff/assets/types/${id}${qs}`, {
      method: "DELETE",
    });
    setBusy(false);
    if (res.ok) {
      setConfirmId(null);
      setMoveTo("");
      router.refresh();
      return;
    }
    const b = await res.json().catch(() => null);
    setError(b?.message ?? "Could not remove the type.");
  }

  return (
    <div className="space-y-6">
      <form onSubmit={add} className="flex flex-wrap items-end gap-2">
        <label className="text-sm">
          <span className="mb-1 block text-xs font-medium uppercase tracking-wide text-muted-foreground">
            New type
          </span>
          <Input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Headset"
            className="w-56"
          />
        </label>
        <Button type="submit" size="sm" disabled={busy}>
          Add type
        </Button>
      </form>
      {error && <p className="text-sm text-destructive">{error}</p>}

      <ul className="divide-y divide-border rounded-lg border border-border">
        {types.map((t) => {
          const on = usage[t.name] ?? [];
          const confirming = confirmId === t.id;
          return (
            <li key={t.id} className="px-4 py-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <span className="font-medium">{t.name}</span>
                  <span className="ml-2 text-xs text-muted-foreground">
                    {on.length === 0
                      ? "no assets"
                      : `${on.length} asset${on.length === 1 ? "" : "s"}`}
                  </span>
                </div>
                {confirming ? (
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => setConfirmId(null)}
                  >
                    Cancel
                  </Button>
                ) : (
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={busy}
                    onClick={() =>
                      on.length === 0 ? remove(t.id) : setConfirmId(t.id)
                    }
                  >
                    Delete
                  </Button>
                )}
              </div>

              {confirming && (
                <div className="mt-3 rounded-md border border-amber-500/30 bg-amber-500/5 p-3">
                  <p className="text-sm">
                    Deleting <span className="font-medium">{t.name}</span>{" "}
                    affects {on.length} asset{on.length === 1 ? "" : "s"}:
                  </p>
                  <ul className="mt-1 space-y-0.5 text-xs text-muted-foreground">
                    {on.slice(0, 8).map((a) => (
                      <li key={a.id}>
                        <span className="font-mono">{a.assetTag}</span>
                        {a.make || a.model
                          ? ` — ${[a.make, a.model].filter(Boolean).join(" ")}`
                          : ""}
                      </li>
                    ))}
                    {on.length > 8 && <li>…and {on.length - 8} more</li>}
                  </ul>
                  <div className="mt-3 flex flex-wrap items-end gap-2">
                    <label className="text-xs">
                      <span className="mb-1 block font-medium uppercase tracking-wide text-muted-foreground">
                        Move them to
                      </span>
                      <select
                        value={moveTo}
                        onChange={(e) => setMoveTo(e.target.value)}
                        className="h-9 rounded-md border border-border bg-background px-2 text-sm"
                      >
                        <option value="">Choose a type…</option>
                        {types
                          .filter((x) => x.id !== t.id)
                          .map((x) => (
                            <option key={x.id} value={x.name}>
                              {x.name}
                            </option>
                          ))}
                      </select>
                    </label>
                    <Button
                      size="sm"
                      disabled={busy || !moveTo}
                      onClick={() => remove(t.id, moveTo)}
                    >
                      Move &amp; delete
                    </Button>
                  </div>
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
