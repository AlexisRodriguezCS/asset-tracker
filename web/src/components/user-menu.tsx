"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ChevronDown, LogOut, UserCog } from "lucide-react";
import { DEMO_PERSONAS } from "@/lib/demo";
import { ROLE_BLURBS, ROLE_LABELS, type Role } from "@/lib/roles";
import { cn } from "@/lib/cn";

/**
 * The account menu: who you are, and everything you can do about it — switching
 * demo persona and signing out. Both used to sit loose in the nav bar, where
 * "Sign out" was wide enough to wrap onto two lines once the role chip appeared
 * next to it.
 */
export function UserMenu({
  email,
  role,
  demo,
}: {
  email: string;
  role: string | null;
  /** Demo environment: offer the seeded personas as well. */
  demo: boolean;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState<Role | null>(null);
  const [switchError, setSwitchError] = useState<string | null>(null);
  const roleLabel = role ? (ROLE_LABELS[role as Role] ?? role) : null;

  async function switchTo(next: Role) {
    setBusy(next);
    const res = await fetch("/api/auth/demo", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ role: next }),
    });
    setBusy(null);
    if (!res.ok) {
      const problem = await res.json().catch(() => null);
      setSwitchError(problem?.message ?? "Could not switch account.");
      return;
    }
    setSwitchError(null);
    setOpen(false);
    router.push(next === "USER" ? "/" : "/dashboard");
    router.refresh();
  }

  async function logout() {
    await fetch("/api/auth/logout", { method: "POST" });
    setOpen(false);
    router.push("/");
    router.refresh();
  }

  return (
    <div className="relative shrink-0">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-haspopup="menu"
        aria-label="Account"
        className="touch-target flex h-9 items-center gap-1.5 rounded-md px-1.5 text-sm transition-colors hover:bg-muted"
      >
        <span className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-accent text-xs font-semibold uppercase text-primary">
          {email.slice(0, 1)}
        </span>
        {roleLabel && (
          <span className="hidden whitespace-nowrap text-xs text-muted-foreground xl:inline">
            {roleLabel}
          </span>
        )}
        <ChevronDown className="hidden h-3.5 w-3.5 shrink-0 text-muted-foreground sm:block" />
      </button>

      {open && (
        <>
          <button
            type="button"
            aria-label="Close menu"
            className="fixed inset-0 z-40 cursor-default"
            onClick={() => setOpen(false)}
          />
          <div
            role="menu"
            className="absolute right-0 z-50 mt-2 w-72 origin-top-right animate-pop-in overflow-hidden rounded-lg border border-border bg-card shadow-lift"
          >
            <div className="border-b border-border/70 px-3 py-2.5">
              <p className="truncate text-sm font-medium">{email}</p>
              {roleLabel && (
                <p className="mt-0.5 text-xs text-muted-foreground">
                  {roleLabel}
                </p>
              )}
            </div>

            {demo && (
              <>
                <div className="flex items-center gap-2 px-3 pb-1 pt-2.5">
                  <UserCog className="h-3.5 w-3.5 text-muted-foreground" />
                  <p className="text-xs font-medium">View the console as…</p>
                </div>
                <ul className="p-1">
                  {DEMO_PERSONAS.map((p) => {
                    const active = p.role === role;
                    return (
                      <li key={p.role}>
                        <button
                          type="button"
                          role="menuitem"
                          disabled={busy !== null}
                          onClick={() => switchTo(p.role)}
                          className={cn(
                            "w-full rounded-md px-2.5 py-2 text-left transition-colors",
                            active
                              ? "bg-accent text-primary"
                              : "hover:bg-muted",
                          )}
                        >
                          <span className="flex items-baseline justify-between gap-2">
                            <span className="text-sm font-medium">
                              {ROLE_LABELS[p.role]}
                            </span>
                            {busy === p.role ? (
                              <span className="text-xs text-muted-foreground">
                                switching…
                              </span>
                            ) : (
                              active && (
                                <span className="text-xs text-muted-foreground">
                                  current
                                </span>
                              )
                            )}
                          </span>
                          <span className="mt-0.5 block text-xs text-muted-foreground">
                            {ROLE_BLURBS[p.role]}
                          </span>
                        </button>
                      </li>
                    );
                  })}
                </ul>
                {switchError && (
                  <p
                    role="alert"
                    className="border-t border-border/70 px-3 py-2 text-xs text-destructive"
                  >
                    {switchError}
                  </p>
                )}
                <p className="border-t border-border/70 px-3 py-2 text-[11px] leading-snug text-muted-foreground">
                  Demo only. Each option signs in as that seeded account for
                  real — nothing here bypasses authorization.
                </p>
              </>
            )}

            <div className="border-t border-border/70 p-1">
              <button
                type="button"
                role="menuitem"
                onClick={logout}
                className="flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-left text-sm transition-colors hover:bg-muted"
              >
                <LogOut className="h-4 w-4 text-muted-foreground" />
                Sign out
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
