"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

type Counts = Record<string, number>;

/** Nobody signs out a hundred of anything for one event. */
const MAX_PER_ITEM = 99;

/**
 * The event sign-out form. Quantities, not specific assets: the requester knows
 * they need two TVs, not which two - a tech attaches real asset tags when the
 * gear is handed out.
 */
export function EventRequestForm({
  clientId,
  types,
}: {
  clientId: number;
  /** The client's own asset types - what can actually be signed out. */
  types: string[];
}) {
  const router = useRouter();
  const [eventName, setEventName] = useState("");
  const [eventDate, setEventDate] = useState("");
  const [location, setLocation] = useState("");
  const [notes, setNotes] = useState("");
  const [counts, setCounts] = useState<Counts>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const total = Object.values(counts).reduce((a, b) => a + b, 0);
  const countOf = (type: string) => counts[type] ?? 0;

  const clamp = (n: number) => Math.max(0, Math.min(MAX_PER_ITEM, n));

  /** Absolute value, for typing straight into the box. */
  function setCount(type: string, next: number) {
    setCounts((c) => ({ ...c, [type]: clamp(next) }));
  }

  /**
   * Step relative to whatever is in state at the time, not to what this render
   * saw. Reading the count from the closure meant two quick clicks on "+" both
   * computed 0 + 1, so the second one silently did nothing.
   */
  function bump(type: string, delta: number) {
    setCounts((c) => ({ ...c, [type]: clamp((c[type] ?? 0) + delta) }));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (total === 0) {
      setError("Pick at least one item.");
      return;
    }
    setBusy(true);
    const res = await fetch("/api/bff/assignments/event-requests", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        clientId,
        eventName,
        eventDate,
        location: location || null,
        notes: notes || null,
        lines: types
          .filter((t) => countOf(t) > 0)
          .map((t) => ({ itemType: t, quantity: countOf(t), notes: null })),
      }),
    });
    setBusy(false);
    if (!res.ok) {
      const problem = await res.json().catch(() => null);
      setError(problem?.message ?? "Could not submit the request.");
      return;
    }
    router.push("/events");
    router.refresh();
  }

  return (
    <form onSubmit={submit} className="space-y-6">
      <Card className="space-y-4 p-5">
        <h2 className="text-sm font-semibold">The event</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Event name" required>
            <input
              required
              value={eventName}
              onChange={(e) => setEventName(e.target.value)}
              placeholder="Career Fair"
              className={INPUT}
            />
          </Field>
          <Field label="Date" required>
            <input
              required
              type="date"
              value={eventDate}
              onChange={(e) => setEventDate(e.target.value)}
              className={INPUT}
            />
          </Field>
          <Field label="Where">
            <input
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder="Main Gym"
              className={INPUT}
            />
          </Field>
          <Field label="Anything else">
            <input
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Need it set up by 8am"
              className={INPUT}
            />
          </Field>
        </div>
      </Card>

      <Card className="space-y-4 p-5">
        <div className="flex items-baseline justify-between">
          <h2 className="text-sm font-semibold">What you need</h2>
          <span className="text-xs text-muted-foreground">
            {total === 0
              ? "nothing selected yet"
              : `${total} item${total === 1 ? "" : "s"}`}
          </span>
        </div>
        <ul className="divide-y divide-border/70">
          {types.map((item) => (
            <li
              key={item}
              className="flex items-center justify-between gap-4 py-3"
            >
              <p className="text-sm font-medium">{item}</p>
              <div className="flex items-center gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  aria-label={`One fewer ${item}`}
                  onClick={() => bump(item, -1)}
                >
                  −
                </Button>
                <input
                  aria-label={`How many ${item}`}
                  inputMode="numeric"
                  value={countOf(item)}
                  onChange={(e) =>
                    setCount(
                      item,
                      Number(e.target.value.replace(/\D/g, "")) || 0,
                    )
                  }
                  className="h-8 w-12 rounded-md border border-border bg-background text-center text-sm tabular-nums outline-none focus-visible:border-primary"
                />
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  aria-label={`One more ${item}`}
                  onClick={() => bump(item, 1)}
                >
                  +
                </Button>
              </div>
            </li>
          ))}
        </ul>
      </Card>

      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      )}

      <div className="flex items-center gap-3">
        <Button type="submit" disabled={busy}>
          {busy ? "Sending…" : "Submit request"}
        </Button>
        <p className="text-xs text-muted-foreground">
          A tech or your POC reviews it before anything is handed out.
        </p>
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
