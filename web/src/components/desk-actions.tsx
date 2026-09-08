"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { label } from "@/lib/format";
import type { Asset, Person } from "@/lib/types";

/**
 * Seating somebody at this desk, and putting gear on it - from the desk.
 *
 * This page is described as the view a QR scan at the desk would open, which makes it the one
 * place you are standing when both of these questions come up. Until now it could only be read:
 * seating happened on the person's page, and placing a device happened on the asset's, so the
 * answer to "what is this desk" and the ability to change it lived in different rooms.
 *
 * Folded away behind one button per card. Twenty-eight desks with two open pickers each is a wall
 * of controls, and the grid is meant to be scanned.
 */
export function DeskActions({
  deskId,
  clientId,
  deskLabel,
  itemCount,
  seated,
  people,
  inStock,
  canSeat,
  canPlace,
}: {
  deskId: number;
  clientId: number;
  /** Who sits here now, if anybody. */
  seated: Person | null;
  people: Person[];
  inStock: Asset[];
  canSeat: boolean;
  canPlace: boolean;
  /** What this desk is called, so a correction starts from the current value. */
  deskLabel: string;
  /** Whether anything is on the desk - deleting one that is in use is refused by the service. */
  itemCount: number;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [person, setPerson] = useState<string>(seated ? String(seated.id) : "");
  const [asset, setAsset] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState(deskLabel);

  if (!canSeat && !canPlace) {
    return null;
  }

  async function send(path: string, body: unknown, method = "POST") {
    setBusy(true);
    setError(null);
    const res = await fetch(`/api/bff/${path}`, {
      method,
      ...(body === undefined
        ? {}
        : {
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
          }),
    });
    setBusy(false);
    if (!res.ok) {
      const problem = await res.json().catch(() => null);
      setError(problem?.message ?? "That did not work.");
      return false;
    }
    router.refresh();
    return true;
  }

  /**
   * Seating is a change to the person's record, not the desk's - a desk does not know who sits at
   * it, a person knows where they sit. So moving somebody in means setting their desk, and moving
   * them out means clearing the desk of whoever is currently here.
   */
  async function seat() {
    const chosen = person === "" ? null : Number(person);
    if (chosen === (seated?.id ?? null)) return;

    if (chosen === null) {
      await send(`people/${seated!.id}/desk`, { deskId: null });
      return;
    }
    const ok = await send(`people/${chosen}/desk`, { deskId });
    // whoever was here has been replaced, so their record has to let this desk go too
    if (ok && seated && seated.id !== chosen) {
      await send(`people/${seated.id}/desk`, { deskId: null });
    }
  }

  async function rename() {
    await send(`locations/${deskId}`, { label: name.trim() }, "PATCH");
  }

  async function remove() {
    // no confirm dialog: an empty desk carries nothing, and the audit trail keeps a
    // LOCATION_DELETED row with its name and tag if anyone asks what happened to it
    await send(`locations/${deskId}`, undefined, "DELETE");
  }

  async function place() {
    if (!asset) return;
    const ok = await send("assignments", {
      clientId,
      assetId: Number(asset),
      holderType: "LOCATION",
      holderId: deskId,
    });
    if (ok) setAsset("");
  }

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="mt-3 text-xs text-muted-foreground underline-offset-4 transition-colors hover:text-foreground hover:underline"
      >
        Seat or place…
      </button>
    );
  }

  return (
    <div className="mt-3 space-y-2 border-t border-border pt-3">
      {canSeat && (
        <div className="flex items-center gap-2">
          <label className="sr-only" htmlFor={`seat-${deskId}`}>
            Who sits here
          </label>
          <select
            id={`seat-${deskId}`}
            value={person}
            disabled={busy}
            onChange={(e) => setPerson(e.target.value)}
            className="h-8 min-w-0 flex-1 rounded-md border border-border bg-background px-2 text-xs outline-none focus-visible:border-primary"
          >
            <option value="">Nobody</option>
            {people.map((p) => (
              <option key={p.id} value={p.id}>
                {p.fullName}
              </option>
            ))}
          </select>
          <Button size="sm" variant="outline" disabled={busy} onClick={seat}>
            Seat
          </Button>
        </div>
      )}

      {canPlace && (
        <div className="flex items-center gap-2">
          <label className="sr-only" htmlFor={`place-${deskId}`}>
            Device to place here
          </label>
          <select
            id={`place-${deskId}`}
            value={asset}
            disabled={busy || inStock.length === 0}
            onChange={(e) => setAsset(e.target.value)}
            className="h-8 min-w-0 flex-1 rounded-md border border-border bg-background px-2 text-xs outline-none focus-visible:border-primary"
          >
            <option value="">
              {inStock.length === 0 ? "Nothing in stock" : "Place a device…"}
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
            variant="outline"
            disabled={busy || !asset}
            onClick={place}
          >
            Place
          </Button>
        </div>
      )}

      {canPlace && (
        <div className="flex items-center gap-2">
          <label className="sr-only" htmlFor={`name-${deskId}`}>
            Desk name
          </label>
          <input
            id={`name-${deskId}`}
            value={name}
            disabled={busy}
            onChange={(e) => setName(e.target.value)}
            className="h-8 min-w-0 flex-1 rounded-md border border-border bg-background px-2 text-xs outline-none focus-visible:border-primary"
          />
          <Button
            size="sm"
            variant="outline"
            disabled={busy || name.trim() === deskLabel || !name.trim()}
            onClick={rename}
          >
            Rename
          </Button>
          {/*
            Only offered for an empty desk. The service refuses either way - it asks
            asset-service what is on it first - but a button that is always refused is
            the thing we just spent a change removing from the dashboard.
          */}
          {itemCount === 0 && (
            <Button size="sm" variant="ghost" disabled={busy} onClick={remove}>
              Delete
            </Button>
          )}
        </div>
      )}

      {error && (
        <p className="text-xs text-destructive" role="alert">
          {error}
        </p>
      )}

      <button
        type="button"
        onClick={() => setOpen(false)}
        className="text-xs text-muted-foreground underline-offset-4 hover:underline"
      >
        Done
      </button>
    </div>
  );
}
