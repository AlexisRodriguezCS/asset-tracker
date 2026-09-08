"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import type { Location } from "@/lib/types";

/**
 * Moves a person to a desk, or takes their desk away.
 *
 * <p>The console could set a desk when creating somebody and never again, so a new starter given a
 * seat on their second day had to be fixed in the database. The endpoint existed the whole time.
 *
 * Saving is explicit rather than on-change: this is a shared record, and a stray click in a select
 * should not quietly move somebody's seat.
 */
export function DeskPicker({
  personId,
  desks,
  current,
}: {
  personId: number;
  desks: Location[];
  /** The desk they sit at now, or null. */
  current: number | null;
}) {
  const router = useRouter();
  const [selected, setSelected] = useState<string>(
    current ? String(current) : "",
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const chosen = selected === "" ? null : Number(selected);
  const changed = chosen !== current;

  async function save() {
    setBusy(true);
    setError(null);
    const res = await fetch(`/api/bff/people/${personId}/desk`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ deskId: chosen }),
    });
    setBusy(false);

    if (!res.ok) {
      const problem = await res.json().catch(() => null);
      setError(problem?.message ?? "Could not change this desk.");
      return;
    }
    router.refresh();
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <label className="sr-only" htmlFor="desk">
        Desk
      </label>
      <select
        id="desk"
        value={selected}
        disabled={busy}
        onChange={(e) => setSelected(e.target.value)}
        className="h-9 rounded-md border border-border bg-background px-3 text-sm outline-none focus-visible:border-primary"
      >
        <option value="">No desk</option>
        {desks.map((d) => (
          <option key={d.id} value={d.id}>
            {[d.label, d.building, d.floor && `floor ${d.floor}`]
              .filter(Boolean)
              .join(" · ")}
          </option>
        ))}
      </select>

      <Button
        size="sm"
        variant="outline"
        disabled={!changed || busy}
        onClick={save}
      >
        {busy
          ? "Saving…"
          : changed && chosen === null
            ? "Clear desk"
            : "Move desk"}
      </Button>

      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
