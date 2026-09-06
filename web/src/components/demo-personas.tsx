"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { DEMO_PERSONAS } from "@/lib/demo";
import { ROLE_BLURBS, ROLE_LABELS, type Role } from "@/lib/roles";

/**
 * Demo-only way in, shown on the landing page when Microsoft 365 sign-in has not
 * been configured yet. Without it a deployment with no Entra app is a dead end —
 * the page offers the only real sign-in route and it does not work.
 *
 * Each button performs a real login as that seeded account (see /api/auth/demo);
 * nothing here bypasses authorization.
 */
export function DemoPersonas() {
  const router = useRouter();
  const [busy, setBusy] = useState<Role | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function signInAs(role: Role) {
    setBusy(role);
    setError(null);
    const res = await fetch("/api/auth/demo", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ role }),
    });
    if (!res.ok) {
      setBusy(null);
      setError("That demo account isn't available on this deployment.");
      return;
    }
    // land where that role actually starts: an employee has no dashboard
    router.push(role === "USER" ? "/" : "/dashboard");
    router.refresh();
  }

  return (
    <div className="space-y-2">
      {DEMO_PERSONAS.map((p) => (
        <button
          key={p.role}
          type="button"
          disabled={busy !== null}
          onClick={() => signInAs(p.role)}
          className="flex w-full items-baseline justify-between gap-3 rounded-md border border-border px-3 py-2.5 text-left transition-colors hover:border-primary/50 hover:bg-accent disabled:opacity-60"
        >
          <span className="text-sm font-medium">{ROLE_LABELS[p.role]}</span>
          <span className="text-xs text-muted-foreground">
            {busy === p.role ? "signing in…" : ROLE_BLURBS[p.role]}
          </span>
        </button>
      ))}
      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
