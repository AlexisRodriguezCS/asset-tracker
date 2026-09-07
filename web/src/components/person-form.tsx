"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type { Location } from "@/lib/types";

/**
 * Adds an employee to the current client.
 *
 * A desk is optional and stays optional: people are hired before they are seated,
 * and forcing a desk here would mean inventing one. Assigning gear is a separate
 * job done from the asset side, so this form ends at the person.
 */
export function PersonForm({
  clientId,
  desks,
}: {
  clientId: number;
  desks: Location[];
}) {
  const router = useRouter();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [department, setDepartment] = useState("");
  const [deskId, setDeskId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);

    const res = await fetch("/api/bff/people", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        clientId,
        fullName: fullName.trim(),
        email: email.trim(),
        department: department.trim() || null,
        deskId: deskId ? Number(deskId) : null,
      }),
    });
    setBusy(false);

    if (!res.ok) {
      const problem = await res.json().catch(() => null);
      // the server owns the rules - a duplicate email is its answer, not a guess
      setError(problem?.message ?? "Could not add this person.");
      return;
    }

    const created = await res.json().catch(() => null);
    router.push(created?.id ? `/people/${created.id}` : "/people");
    router.refresh();
  }

  return (
    <form onSubmit={submit} className="space-y-6">
      <Card className="space-y-4 p-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Full name" required>
            <input
              required
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Dana Reyes"
              className={INPUT}
            />
          </Field>
          <Field label="Email" required>
            <input
              required
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="dana.reyes@acme.example"
              className={INPUT}
            />
          </Field>
          <Field label="Department">
            <input
              value={department}
              onChange={(e) => setDepartment(e.target.value)}
              placeholder="Engineering"
              className={INPUT}
            />
          </Field>
          <Field label="Desk">
            <select
              value={deskId}
              onChange={(e) => setDeskId(e.target.value)}
              className={INPUT}
            >
              <option value="">No desk yet</option>
              {desks.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.label}
                  {d.building ? ` · ${d.building}` : ""}
                  {d.floor ? ` ${d.floor}` : ""}
                </option>
              ))}
            </select>
          </Field>
        </div>
      </Card>

      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      )}

      <div className="flex items-center gap-3">
        <Button type="submit" disabled={busy}>
          {busy ? "Adding…" : "Add person"}
        </Button>
        <p className="text-xs text-muted-foreground">
          They start as active, holding nothing. Check gear out to them from an
          asset.
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
