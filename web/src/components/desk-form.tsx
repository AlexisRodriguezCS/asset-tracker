"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

/**
 * Adds a desk, room or site to the current client.
 *
 * The QR tag is what someone scans while standing in front of the thing, so it
 * has to be unique and is required. Building and floor are optional but drive
 * the desk map's grouping - a desk without them still works, it just sits under
 * "Unassigned" there, which the hint below says out loud rather than leaving to
 * be discovered.
 */
const KINDS = [
  { value: "DESK", label: "Desk" },
  { value: "ROOM", label: "Room" },
  { value: "SITE", label: "Site" },
] as const;

export function DeskForm({ clientId }: { clientId: number }) {
  const router = useRouter();
  const [kind, setKind] = useState<string>("DESK");
  const [label, setLabel] = useState("");
  const [qrTag, setQrTag] = useState("");
  const [building, setBuilding] = useState("");
  const [floor, setFloor] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);

    const res = await fetch("/api/bff/locations", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        clientId,
        kind,
        label: label.trim(),
        qrTag: qrTag.trim(),
        building: building.trim() || null,
        floor: floor.trim() || null,
      }),
    });
    setBusy(false);

    if (!res.ok) {
      const problem = await res.json().catch(() => null);
      setError(problem?.message ?? "Could not add this location.");
      return;
    }

    router.push("/desks");
    router.refresh();
  }

  return (
    <form onSubmit={submit} className="space-y-6">
      <Card className="space-y-4 p-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Kind" required>
            <select
              value={kind}
              onChange={(e) => setKind(e.target.value)}
              className={INPUT}
            >
              {KINDS.map((k) => (
                <option key={k.value} value={k.value}>
                  {k.label}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Label" required>
            <input
              required
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              placeholder="Desk 014"
              className={INPUT}
            />
          </Field>
          <Field label="QR tag" required>
            <input
              required
              value={qrTag}
              onChange={(e) => setQrTag(e.target.value)}
              placeholder="ACME-DESK-014"
              className={`${INPUT} font-mono`}
            />
          </Field>
          <Field label="Building">
            <input
              value={building}
              onChange={(e) => setBuilding(e.target.value)}
              placeholder="North"
              className={INPUT}
            />
          </Field>
          <Field label="Floor">
            <input
              value={floor}
              onChange={(e) => setFloor(e.target.value)}
              placeholder="2"
              className={INPUT}
            />
          </Field>
        </div>
        <p className="text-xs text-muted-foreground">
          The QR tag is what gets scanned at the desk, so it has to be unique
          within this client. Without a building and floor it still works — it
          just groups under “Unassigned” on the desk map.
        </p>
      </Card>

      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      )}

      <div className="flex items-center gap-3">
        <Button type="submit" disabled={busy}>
          {busy ? "Adding…" : "Add location"}
        </Button>
      </div>
    </form>
  );
}

const INPUT =
  "h-9 w-full rounded-md border border-border bg-background px-3 text-sm outline-none focus-visible:border-primary";

function Field({
  label,
  required,
  children,
}: {
  label: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <label className="block space-y-1.5">
      <span className="text-xs font-medium text-muted-foreground">
        {label}
        {required && <span className="text-destructive"> *</span>}
      </span>
      {children}
    </label>
  );
}
