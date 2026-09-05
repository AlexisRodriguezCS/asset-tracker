"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ChevronDown, UserCog } from "lucide-react";
import { DEMO_PERSONAS } from "@/lib/demo";
import { ROLE_BLURBS, ROLE_LABELS, type Role } from "@/lib/roles";
import { cn } from "@/lib/cn";

/**
 * Demo affordance: jump between the seeded accounts to see each role's view.
 * Signs in for real as that account (see /api/auth/demo) rather than pretending,
 * so what you see is exactly what that user's token is allowed to fetch.
 */
export function PersonaSwitcher({
  email,
  role,
}: {
  email: string;
  role: string | null;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState<Role | null>(null);

  async function switchTo(next: Role) {
    setBusy(next);
    const res = await fetch("/api/auth/demo", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ role: next }),
    });
    setBusy(null);
    setOpen(false);
    if (res.ok) {
      router.push("/");
      router.refresh();
    }
  }

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-haspopup="menu"
        className="flex items-center gap-1.5 rounded-md px-2 py-1 text-xs text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
      >
        <span className="hidden max-w-[14rem] truncate md:inline">{email}</span>
        {role && (
          <span className="rounded bg-muted px-1.5 py-0.5 font-medium">
            {ROLE_LABELS[role as Role] ?? role}
          </span>
        )}
        <ChevronDown className="h-3.5 w-3.5" />
      </button>

      {open && (
        <>
          <button
            type="button"
            aria-label="Close"
            className="fixed inset-0 z-40 cursor-default"
            onClick={() => setOpen(false)}
          />
          <div
            role="menu"
            className="absolute right-0 z-50 mt-2 w-72 overflow-hidden rounded-lg border border-border bg-background shadow-lift"
          >
            <div className="flex items-center gap-2 border-b border-border/70 px-3 py-2">
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
                        active ? "bg-accent text-primary" : "hover:bg-muted",
                      )}
                    >
                      <span className="flex items-baseline justify-between gap-2">
                        <span className="text-sm font-medium">
                          {ROLE_LABELS[p.role]}
                        </span>
                        {busy === p.role && (
                          <span className="text-xs text-muted-foreground">
                            switching…
                          </span>
                        )}
                        {active && !busy && (
                          <span className="text-xs text-muted-foreground">
                            current
                          </span>
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
            <p className="border-t border-border/70 px-3 py-2 text-[11px] leading-snug text-muted-foreground">
              Demo only. Each option signs in as that seeded account for real —
              nothing here bypasses authorization.
            </p>
          </div>
        </>
      )}
    </div>
  );
}
