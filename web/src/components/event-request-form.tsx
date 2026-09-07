"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type { EventEquipmentItem } from "@/lib/types";

type Counts = Record<string, number>;

/**
 * The event sign-out form.
 *
 * Quantities, not specific assets: the requester knows they need two TVs, not
 * which two - a tech attaches real asset tags when the gear is handed out.
 *
 * The menu is the client's event equipment pool, and how much of it is free on
 * the day being asked for. Picking a date re-reads availability, because "2 TVs"
 * is only true until someone else books them. The counters cap at what is left,
 * but that is a courtesy, not the rule: the server checks again on submit, since
 * anything this form knows is already out of date by the time it is sent.
 */
export function EventRequestForm({
  clientId,
  initialItems,
}: {
  clientId: number;
  /** The pool as it stands with no date chosen - every item, nothing booked. */
  initialItems: EventEquipmentItem[];
}) {
  const router = useRouter();
  const [eventName, setEventName] = useState("");
  const [eventDate, setEventDate] = useState("");
  const [location, setLocation] = useState("");
  const [notes, setNotes] = useState("");
  const [counts, setCounts] = useState<Counts>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /**
   * What came back for one particular day. `items: null` means the read failed.
   *
   * Held with the date it belongs to so the answer below can be worked out while
   * rendering rather than mirrored into more state. Keeping a copy of
   * `initialItems` in `useState` and resetting it from the effect meant the
   * effect wrote state synchronously on every date change - which Next 16's
   * `react-hooks/set-state-in-effect` flags, and rightly: it is a render's worth
   * of derived data pretending to be a fact.
   */
  const [checked, setChecked] = useState<{
    date: string;
    items: EventEquipmentItem[] | null;
  } | null>(null);

  const forThisDay = checked?.date === eventDate ? checked : null;
  const items = forThisDay?.items ?? initialItems;
  const stale = forThisDay !== null && forThisDay.items === null;
  // a day is picked but its answer has not arrived: that *is* "still checking",
  // so it is worked out here rather than tracked as a second copy of the truth
  const checking = eventDate !== "" && forThisDay === null;

  const freeOf = useCallback(
    (itemType: string) =>
      items.find((i) => i.itemType === itemType)?.available ?? 0,
    [items],
  );

  /**
   * Re-read what is free whenever the day changes.
   *
   * A failure here is reported rather than swallowed. It used to fall back to
   * the undated pool silently, which is the worst of both: the form confidently
   * showed "2 of 2 free" for a day that had none left, and the requester only
   * found out when the server refused the submit. Numbers nobody has checked
   * must not look like numbers somebody has.
   */
  useEffect(() => {
    if (!eventDate) {
      return;
    }
    let cancelled = false;
    fetch(
      `/api/bff/assignments/event-equipment?clientId=${clientId}&date=${eventDate}`,
    )
      .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
      .then((fresh: EventEquipmentItem[]) => {
        if (cancelled) return;
        setChecked({ date: eventDate, items: fresh });
        // trim anything already picked that no longer fits
        setCounts((current) => {
          const trimmed: Counts = {};
          for (const [type, n] of Object.entries(current)) {
            const free = fresh.find((i) => i.itemType === type)?.available ?? 0;
            trimmed[type] = Math.min(n, free);
          }
          return trimmed;
        });
      })
      .catch(() => !cancelled && setChecked({ date: eventDate, items: null }));
    return () => {
      cancelled = true;
    };
  }, [clientId, eventDate]);

  const total = Object.values(counts).reduce((a, b) => a + b, 0);
  const countOf = (type: string) => counts[type] ?? 0;

  function setCount(type: string, next: number) {
    setCounts((c) => ({
      ...c,
      [type]: Math.max(0, Math.min(freeOf(type), next)),
    }));
  }

  /**
   * Step relative to whatever is in state at the time, not to what this render
   * saw. Reading the count from the closure meant two quick clicks on "+" both
   * computed 0 + 1, so the second one silently did nothing.
   */
  function bump(type: string, delta: number) {
    setCounts((c) => ({
      ...c,
      [type]: Math.max(0, Math.min(freeOf(type), (c[type] ?? 0) + delta)),
    }));
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
        lines: items
          .filter((i) => countOf(i.itemType) > 0)
          .map((i) => ({
            itemType: i.itemType,
            quantity: countOf(i.itemType),
            notes: null,
          })),
      }),
    });
    setBusy(false);
    if (!res.ok) {
      const problem = await res.json().catch(() => null);
      setError(problem?.message ?? "Could not submit the request.");
      // somebody else may have taken it while this form was open
      router.refresh();
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
        <div className="flex items-baseline justify-between gap-3">
          <h2 className="text-sm font-semibold">What you need</h2>
          <span className="text-xs text-muted-foreground">
            {checking
              ? "checking what's free…"
              : total === 0
                ? "nothing selected yet"
                : `${total} item${total === 1 ? "" : "s"}`}
          </span>
        </div>

        <p
          className={
            stale ? "text-xs text-amber-500" : "text-xs text-muted-foreground"
          }
        >
          {stale
            ? "Couldn't check what's free for that day — the counts below are the full pool. Your request will still be checked when you send it."
            : eventDate
              ? "Availability is for the day you picked — gear already booked for that day is not offered."
              : "Pick a date above to see what is free that day."}
        </p>

        {items.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted-foreground">
            This client has no event equipment set up yet. A tech can add it
            from the events page.
          </p>
        ) : (
          <ul className="divide-y divide-border/70">
            {items.map((item) => {
              const free = item.available;
              const picked = countOf(item.itemType);
              return (
                <li
                  key={item.itemType}
                  className="flex items-center justify-between gap-4 py-3"
                >
                  <div className="min-w-0">
                    <p className="text-sm font-medium">{item.itemType}</p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      {item.owned === 0
                        ? "none owned"
                        : free === 0
                          ? `all ${item.owned} booked${eventDate ? " that day" : ""}`
                          : `${free} of ${item.owned} free`}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={picked === 0}
                      aria-label={`One fewer ${item.itemType}`}
                      onClick={() => bump(item.itemType, -1)}
                    >
                      −
                    </Button>
                    <input
                      aria-label={`How many ${item.itemType}`}
                      inputMode="numeric"
                      value={picked}
                      disabled={free === 0}
                      onChange={(e) =>
                        setCount(
                          item.itemType,
                          Number(e.target.value.replace(/\D/g, "")) || 0,
                        )
                      }
                      className="h-8 w-12 rounded-md border border-border bg-background text-center text-sm tabular-nums outline-none focus-visible:border-primary disabled:opacity-50"
                    />
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={picked >= free}
                      aria-label={`One more ${item.itemType}`}
                      onClick={() => bump(item.itemType, 1)}
                    >
                      +
                    </Button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </Card>

      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      )}

      <div className="flex items-center gap-3">
        <Button type="submit" disabled={busy || checking}>
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
