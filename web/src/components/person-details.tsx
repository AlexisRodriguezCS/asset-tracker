"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Pencil } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PersonStatusBadge } from "@/components/ui/badge";
import type { Person } from "@/lib/types";

/**
 * The person's name and details, correctable in place.
 *
 * There was no way to fix a typo at all: people-service could create somebody and never edit them,
 * so a misspelled name or a changed surname meant a database. The heading doubles as the form
 * rather than living on a separate edit page, because the thing being corrected is the thing you
 * are looking at.
 *
 * Reading stays open to anyone who can see the page; only the pencil is gated.
 */
export function PersonDetails({
  person,
  canEdit,
}: {
  person: Person;
  canEdit: boolean;
}) {
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [fullName, setFullName] = useState(person.fullName);
  const [email, setEmail] = useState(person.email);
  const [department, setDepartment] = useState(person.department ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const res = await fetch(`/api/bff/people/${person.id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        fullName: fullName.trim(),
        email: email.trim(),
        department: department.trim(),
      }),
    });
    setBusy(false);

    if (!res.ok) {
      const problem = await res.json().catch(() => null);
      setError(problem?.message ?? "Could not save these details.");
      return;
    }
    setEditing(false);
    router.refresh();
  }

  function cancel() {
    // back to what is on the record, not to whatever was half-typed
    setFullName(person.fullName);
    setEmail(person.email);
    setDepartment(person.department ?? "");
    setError(null);
    setEditing(false);
  }

  if (!editing) {
    return (
      <>
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <h1 className="font-display text-2xl font-semibold tracking-tight">
            {person.fullName}
          </h1>
          <PersonStatusBadge status={person.status} />
          {canEdit && (
            <button
              type="button"
              onClick={() => setEditing(true)}
              className="touch-target inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            >
              <Pencil className="h-3.5 w-3.5" /> Edit
            </button>
          )}
        </div>
        <p className="mt-1 text-sm text-muted-foreground">
          {person.email}
          {person.department ? ` · ${person.department}` : ""}
        </p>
      </>
    );
  }

  return (
    <form onSubmit={save} className="mt-4 space-y-3">
      <div className="grid gap-3 sm:grid-cols-3">
        <label className="block space-y-1.5">
          <span className="text-xs font-medium text-muted-foreground">
            Full name
          </span>
          <Input
            required
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
          />
        </label>
        <label className="block space-y-1.5">
          <span className="text-xs font-medium text-muted-foreground">
            Email
          </span>
          <Input
            required
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>
        <label className="block space-y-1.5">
          <span className="text-xs font-medium text-muted-foreground">
            Department
          </span>
          <Input
            value={department}
            onChange={(e) => setDepartment(e.target.value)}
            placeholder="none"
          />
        </label>
      </div>

      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      )}

      <div className="flex items-center gap-2">
        <Button type="submit" size="sm" disabled={busy}>
          {busy ? "Saving…" : "Save"}
        </Button>
        <Button
          type="button"
          size="sm"
          variant="ghost"
          disabled={busy}
          onClick={cancel}
        >
          Cancel
        </Button>
      </div>
    </form>
  );
}
