"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";

/**
 * Approve / deny buttons for a request that has not been decided. Shown only to
 * approvers, and the backend checks the role again on every call - this only
 * decides what to render.
 */
export function EventDecision({ id }: { id: number }) {
  const router = useRouter();
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState<"approve" | "deny" | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function decide(action: "approve" | "deny") {
    setBusy(action);
    setError(null);
    const res = await fetch(
      `/api/bff/assignments/event-requests/${id}/${action}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ note: note || null }),
      },
    );
    setBusy(null);
    if (!res.ok) {
      const problem = await res.json().catch(() => null);
      setError(problem?.message ?? "That did not work.");
      return;
    }
    router.refresh();
  }

  return (
    <div className="space-y-3">
      <input
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="Add a note (optional)"
        className="h-9 w-full rounded-md border border-border bg-background px-3 text-sm outline-none focus-visible:border-primary"
      />
      <div className="flex gap-2">
        <Button
          size="sm"
          disabled={busy !== null}
          onClick={() => decide("approve")}
        >
          {busy === "approve" ? "Approving…" : "Approve"}
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={busy !== null}
          onClick={() => decide("deny")}
        >
          {busy === "deny" ? "Denying…" : "Deny"}
        </Button>
      </div>
      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
