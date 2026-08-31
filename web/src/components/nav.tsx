"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Boxes, Users, MapPin } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ClientPicker } from "@/components/client-picker";
import { cn } from "@/lib/cn";
import type { Client } from "@/lib/types";

const LINKS = [
  { href: "/", label: "Assets", icon: Boxes },
  { href: "/people", label: "People", icon: Users },
  { href: "/desks", label: "Desks", icon: MapPin },
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
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between gap-4 px-4">
        <div className="flex items-center gap-3">
          <Link
            href="/"
            className="flex items-center gap-2 font-display text-base font-semibold tracking-tight"
          >
            <span className="bg-gradient-primary grid h-7 w-7 place-items-center rounded-md text-primary-foreground">
              <Boxes className="h-4 w-4" />
            </span>
            asset<span className="text-muted-foreground">tracker</span>
          </Link>
          <span className="hidden h-5 w-px bg-border sm:block" />
          <ClientPicker clients={clients} current={currentClient} />
        </div>

        <nav className="flex items-center gap-1 text-sm">
          {LINKS.map((l) => {
            const active =
              l.href === "/" ? pathname === "/" : pathname.startsWith(l.href);
            const Icon = l.icon;
            return (
              <Link
                key={l.href}
                href={l.href}
                className={cn(
                  "flex items-center gap-1.5 rounded-md px-3 py-1.5 transition-colors",
                  active
                    ? "bg-accent text-primary"
                    : "text-muted-foreground hover:bg-muted hover:text-foreground",
                )}
              >
                <Icon className="h-4 w-4" />
                <span className="hidden sm:inline">{l.label}</span>
              </Link>
            );
          })}

          <div className="ml-2 flex items-center gap-2">
            {email ? (
              <>
                <span className="hidden text-xs text-muted-foreground md:inline">
                  {email}
                  {role && (
                    <span className="ml-1 rounded bg-muted px-1.5 py-0.5 font-medium">
                      {role}
                    </span>
                  )}
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
