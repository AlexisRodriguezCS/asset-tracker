"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type { EventEquipmentItem } from "@/lib/types";

/**
 * What this client lends at events, and how many of each.
 *
 * Operators only - it is the thing the sign-out form offers, so changing it
 * changes what every employee can ask for. Setting a count to a number is the
 * only edit: there is no "add one" here, because the question the pool answers
 * is "how many do you own", not "how many did you just buy".
 */
export function EventEquipmentManager({
  clientId,
  items,
}: {
  clientId: number;
  items: EventEquipmentItem[];
}) {
  const router = useRouter();
  const [newType, setNewType] = useState("");
  const [newQty, setNewQty] = useState(1);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function save(itemType: string, quantity: number) {
    setBusy(itemType);
    setError(null);
    const res = await fetch("/api/bff/assignments/event-equipment", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ clientId, itemType, quantity }),
    });
    setBusy(null);
    if (!res.ok) {
      const problem = await res.json().catch(() => null);
      setError(problem?.message ?? `Could not save ${itemType}.`);
      return;
    }
    setNewType("");
    setNewQty(1);
    router.refresh();
  }

  async function remove(item: EventEquipmentItem) {
    setBusy(item.itemType);
    setError(null);
    const res = await fetch(`/api/bff/assignments/event-equipment/${item.id}`, {
      method: "DELETE",
    });
    setBusy(null);
    if (!res.ok) {
      setError(`Could not remove ${item.itemType}.`);
      return;
    }
    router.refresh();
  }

  return (
    <Card className="space-y-4 p-5">
      <div>
        <h2 className="text-sm font-semibold">Event equipment</h2>
        <p className="mt-1 text-xs text-muted-foreground">
          What this client lends at events. These counts are what the sign-out
          form offers, and a day&apos;s requests are checked against them.
        </p>
      </div>

      {items.length > 0 && (
        <ul className="divide-y divide-border/70">
          {items.map((item) => (
            <li
              key={item.id}
              className="flex items-center justify-between gap-4 py-2.5"
            >
              <p className="min-w-0 truncate text-sm font-medium">
                {item.itemType}
              </p>
              <div className="flex items-center gap-2">
                <QuantityBox
                  label={`How many ${item.itemType}`}
                  value={item.owned}
                  disabled={busy === item.itemType}
                  onCommit={(n) => n !== item.owned && save(item.itemType, n)}
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  aria-label={`Remove ${item.itemType}`}
                  disabled={busy === item.itemType}
                  onClick={() => remove(item)}
                >
                  <Trash2 className="h-4 w-4 text-muted-foreground" />
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <form
        className="flex flex-wrap items-end gap-2 border-t border-border/70 pt-4"
        onSubmit={(e) => {
          e.preventDefault();
          if (newType.trim()) save(newType.trim(), newQty);
        }}
      >
        <label className="min-w-0 flex-1 space-y-1.5">
          <span className="text-xs font-medium text-muted-foreground">
            Add equipment
          </span>
          <input
            value={newType}
            onChange={(e) => setNewType(e.target.value)}
            placeholder="Mic"
            className="h-9 w-full rounded-md border border-border bg-background px-3 text-sm outline-none focus-visible:border-primary"
          />
        </label>
        <label className="space-y-1.5">
          <span className="text-xs font-medium text-muted-foreground">
            How many
          </span>
          <input
            aria-label="How many of the new item"
            inputMode="numeric"
            value={newQty}
            onChange={(e) =>
              setNewQty(Number(e.target.value.replace(/\D/g, "")) || 0)
            }
            className="h-9 w-16 rounded-md border border-border bg-background text-center text-sm tabular-nums outline-none focus-visible:border-primary"
          />
        </label>
        <Button type="submit" size="sm" disabled={!newType.trim() || !!busy}>
          Save
        </Button>
      </form>

      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      )}
    </Card>
  );
}

/**
 * A number box that saves when you leave it or press Enter, rather than on every
 * keystroke - typing "12" over a "2" should not briefly save "1".
 */
function QuantityBox({
  label,
  value,
  disabled,
  onCommit,
}: {
  label: string;
  value: number;
  disabled?: boolean;
  onCommit: (next: number) => void;
}) {
  const [draft, setDraft] = useState(String(value));

  return (
    <input
      aria-label={label}
      inputMode="numeric"
      value={draft}
      disabled={disabled}
      onChange={(e) => setDraft(e.target.value.replace(/\D/g, ""))}
      onBlur={() => onCommit(Number(draft) || 0)}
      onKeyDown={(e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          e.currentTarget.blur();
        }
      }}
      className="h-8 w-14 rounded-md border border-border bg-background text-center text-sm tabular-nums outline-none focus-visible:border-primary disabled:opacity-50"
    />
  );
}
