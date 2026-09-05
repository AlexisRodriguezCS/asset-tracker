"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EVENT_ITEMS } from "@/lib/types";

type Counts = Record<string, number>;

const ZERO: Counts = Object.fromEntries(EVENT_ITEMS.map((i) => [i.type, 0]));

/**
 * The event sign-out form. Quantities, not specific assets: the requester knows
 * they need two TVs, not which two - a tech attaches real asset tags when the
 * gear is handed out.
 */
export function EventRequestForm({ clientId }: { clientId: number }) {
  const router = useRouter();
  const [eventName, setEventName] = useState("");
  const [eventDate, setEventDate] = useState("");
  const [location, setLocation] = useState("");
  const [notes, setNotes] = useState("");
  const [counts, setCounts] = useState<Counts>(ZERO);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const total = Object.values(counts).reduce((a, b) => a + b, 0);

  function setCount(type: string, next: number) {
    setCounts((c) => ({ ...c, [type]: Math.max(0, Math.min(99, next)) }));
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
        lines: EVENT_ITEMS.filter((i) => counts[i.type] > 0).map((i) => ({
          itemType: i.type,
          quantity: counts[i.type],
          notes: null,
        })),
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
          {EVENT_ITEMS.map((item) => (
            <li
              key={item.type}
              className="flex items-center justify-between gap-4 py-3"
            >
              <div>
                <p className="text-sm font-medium">{item.label}</p>
                <p className="text-xs text-muted-foreground">{item.type}</p>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  aria-label={`One fewer ${item.label}`}
                  onClick={() => setCount(item.type, counts[item.type] - 1)}
                >
                  −
                </Button>
                <input
                  aria-label={`How many ${item.label}`}
                  inputMode="numeric"
                  value={counts[item.type]}
                  onChange={(e) =>
                    setCount(
                      item.type,
                      Number(e.target.value.replace(/\D/g, "")) || 0,
                    )
                  }
                  className="h-8 w-12 rounded-md border border-border bg-background text-center text-sm tabular-nums outline-none focus-visible:border-primary"
                />
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  aria-label={`One more ${item.label}`}
                  onClick={() => setCount(item.type, counts[item.type] + 1)}
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
