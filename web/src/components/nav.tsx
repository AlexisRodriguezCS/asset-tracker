"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Boxes,
  Users,
  MapPin,
  BarChart3,
  Shapes,
  Search,
  LayoutDashboard,
  CalendarDays,
  Menu,
  X,
} from "lucide-react";
import { ClientPicker } from "@/components/client-picker";
import { UserMenu } from "@/components/user-menu";
import { cn } from "@/lib/cn";
import { canOperateAssets, isSelfServiceUser } from "@/lib/roles";
import type { Client } from "@/lib/types";

const STAFF_LINKS = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/", label: "Assets", icon: Boxes },
  { href: "/people", label: "People", icon: Users },
  { href: "/desks", label: "Desks", icon: MapPin },
  { href: "/events", label: "Events", icon: CalendarDays },
  { href: "/reports", label: "Reports", icon: BarChart3 },
];

// An employee gets two things: their own gear, and the sign-out form.
const EMPLOYEE_LINKS = [
  { href: "/", label: "My assets", icon: Boxes },
  { href: "/events", label: "Event sign-out", icon: CalendarDays },
];

// Curating the type catalog is an operator job; reports are just reads.
const OPERATOR_EXTRAS = [{ href: "/types", label: "Types", icon: Shapes }];

/**
 * The signed-in chrome. The root layout does not render this at all when there
 * is no session - signed out, the only reachable pages are the landing page and
 * the auth routes, and every link here would have bounced straight back to it.
 *
 * Widths, narrow to wide: a hamburger holds the links until there is room for
 * them; icons gain labels at `lg`; the search field appears at `md` and the
 * tenant picker at `sm`. Everything account-related lives in one menu, because
 * a row of email + role chip + "Sign out" is what made this wrap.
 */
export function Nav({
  email,
  role,
  clients,
  currentClient,
  demo = false,
}: {
  email: string;
  role: string | null;
  clients: Client[];
  currentClient: number;
  /** Demo environment: offer the persona switcher inside the account menu. */
  demo?: boolean;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const [q, setQ] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);

  const employee = isSelfServiceUser(role);
  const links = employee
    ? EMPLOYEE_LINKS
    : canOperateAssets(role)
      ? [...STAFF_LINKS, ...OPERATOR_EXTRAS]
      : STAFF_LINKS;

  const isActive = (href: string) =>
    href === "/" ? pathname === "/" : pathname.startsWith(href);

  function search(e: React.FormEvent) {
    e.preventDefault();
    const term = q.trim();
    setMenuOpen(false);
    router.push(term ? `/?q=${encodeURIComponent(term)}` : "/");
  }

  return (
    <header className="sticky top-0 z-40 border-b border-border/60 bg-background/70 backdrop-blur-xl">
      <div className="mx-auto flex h-14 max-w-7xl items-center gap-2 px-4 sm:gap-3">
        <button
          type="button"
          onClick={() => setMenuOpen((o) => !o)}
          aria-label={menuOpen ? "Close menu" : "Open menu"}
          aria-expanded={menuOpen}
          className="-ml-1 grid h-9 w-9 shrink-0 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground md:hidden"
        >
          {menuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>

        <Link
          href="/"
          className="flex shrink-0 items-center gap-2 font-display text-base font-semibold tracking-tight"
        >
          <span className="bg-gradient-primary grid h-7 w-7 place-items-center rounded-md text-primary-foreground">
            <Boxes className="h-4 w-4" />
          </span>
          <span className="hidden sm:inline">
            asset<span className="text-muted-foreground">tracker</span>
          </span>
        </Link>

        {!employee && (
          <div className="hidden shrink-0 items-center gap-3 sm:flex">
            <span className="h-5 w-px bg-border" />
            <ClientPicker clients={clients} current={currentClient} />
          </div>
        )}

        {!employee && (
          <form
            onSubmit={search}
            className="relative hidden min-w-0 flex-1 md:block"
          >
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search tag, serial, model, holder…"
              className="h-9 w-full min-w-0 rounded-md border border-border bg-background/60 pl-8 pr-3 text-sm outline-none focus-visible:border-primary"
            />
          </form>
        )}

        <nav className="ml-auto hidden items-center gap-0.5 text-sm md:flex">
          {links.map((l) => {
            const Icon = l.icon;
            return (
              <Link
                key={l.href}
                href={l.href}
                title={l.label}
                className={cn(
                  "flex items-center gap-1.5 rounded-md px-2 py-1.5 transition-colors lg:px-3",
                  isActive(l.href)
                    ? "bg-accent text-primary"
                    : "text-muted-foreground hover:bg-muted hover:text-foreground",
                )}
              >
                <Icon className="h-4 w-4 shrink-0" />
                <span className="hidden lg:inline">{l.label}</span>
              </Link>
            );
          })}
        </nav>

        <div className="ml-auto md:ml-1">
          <UserMenu email={email} role={role} demo={demo} />
        </div>
      </div>

      {menuOpen && (
        <div className="border-t border-border/60 bg-background md:hidden">
          <nav className="mx-auto flex max-w-7xl flex-col gap-0.5 px-4 py-3 text-sm">
            {links.map((l) => {
              const Icon = l.icon;
              return (
                <Link
                  key={l.href}
                  href={l.href}
                  onClick={() => setMenuOpen(false)}
                  className={cn(
                    "flex items-center gap-2.5 rounded-md px-3 py-2.5 transition-colors",
                    isActive(l.href)
                      ? "bg-accent text-primary"
                      : "text-muted-foreground hover:bg-muted hover:text-foreground",
                  )}
                >
                  <Icon className="h-4 w-4 shrink-0" />
                  {l.label}
                </Link>
              );
            })}

            {!employee && (
              <div className="mt-2 space-y-2 border-t border-border/60 pt-3">
                <ClientPicker clients={clients} current={currentClient} />
                <form onSubmit={search} className="relative">
                  <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <input
                    value={q}
                    onChange={(e) => setQ(e.target.value)}
                    placeholder="Search tag, serial, model, holder…"
                    className="h-9 w-full rounded-md border border-border bg-background/60 pl-8 pr-3 text-sm outline-none focus-visible:border-primary"
                  />
                </form>
              </div>
            )}
          </nav>
        </div>
      )}
    </header>
  );
}
