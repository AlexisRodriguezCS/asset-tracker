"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Boxes } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ClientPicker } from "@/components/client-picker";
import { cn } from "@/lib/cn";
import type { Client } from "@/lib/types";

const LINKS = [
  { href: "/", label: "Assets" },
  { href: "/people", label: "People" },
  { href: "/desks", label: "Desks" },
];

export function Nav({
  email,
  role,
  clients,
  currentClient,
}: {
  email: string | null;
  role: string | null;
  clients: Client[];
  currentClient: number;
}) {
  const router = useRouter();
  const pathname = usePathname();

  async function logout() {
    await fetch("/api/auth/logout", { method: "POST" });
    router.push("/");
    router.refresh();
  }

  return (
    <header className="sticky top-0 z-40 border-b border-border/60 bg-background/70 backdrop-blur-xl">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4">
        <div className="flex items-center gap-3">
          <Link
            href="/"
            className="flex items-center gap-2 font-display text-base font-semibold tracking-tight"
          >
            <Boxes className="h-[18px] w-[18px] text-primary" />
            asset<span className="text-muted-foreground">tracker</span>
          </Link>
          <ClientPicker clients={clients} current={currentClient} />
        </div>

        <nav className="flex items-center gap-1 text-sm">
          {LINKS.map((l) => {
            const active =
              l.href === "/" ? pathname === "/" : pathname.startsWith(l.href);
            return (
              <Link
                key={l.href}
                href={l.href}
                className={cn(
                  "rounded-md px-3 py-1.5 transition-colors",
                  active
                    ? "bg-accent text-primary"
                    : "text-muted-foreground hover:bg-muted hover:text-foreground",
                )}
              >
                {l.label}
              </Link>
            );
          })}

          <div className="ml-2 flex items-center gap-2">
            {email ? (
              <>
                <span className="hidden text-xs text-muted-foreground sm:inline">
                  {email} · {role}
                </span>
                <Button variant="outline" size="sm" onClick={logout}>
                  Sign out
                </Button>
              </>
            ) : (
              <Link href="/login">
                <Button size="sm">Sign in</Button>
              </Link>
            )}
          </div>
        </nav>
      </div>
    </header>
  );
}
