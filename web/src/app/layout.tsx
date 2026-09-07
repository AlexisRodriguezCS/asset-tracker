import type { Metadata } from "next";
import { Inter, Space_Grotesk } from "next/font/google";
import "./globals.css";
import { demoLoginsEnabled } from "@/lib/demo";
import { Nav } from "@/components/nav";
import { getSession } from "@/lib/session";
import { currentClientId } from "@/lib/client";
import { listClients } from "@/lib/api";
import type { Client } from "@/lib/types";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-sans",
  display: "swap",
});
const display = Space_Grotesk({
  subsets: ["latin"],
  variable: "--font-display",
  display: "swap",
});

export const metadata: Metadata = {
  /*
   * A template, so a tab reads "Assets · Asset Tracker" rather than the product
   * name twenty times over. Pages set only their own half.
   */
  title: {
    default: "Asset Tracker",
    template: "%s · Asset Tracker",
  },
  description:
    "Track which person or desk holds which laptop, charger and cable — across every client organisation.",
};

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const [session, currentClient] = await Promise.all([
    getSession(),
    currentClientId(),
  ]);
  // The tenant list is only needed by the nav, which only signed-in people see.
  // Narrowed to the tenants this token may act on: the picker used to offer every
  // client, so a POC scoped to one organisation could select another and get a
  // page of zeroes rather than being told they cannot see it.
  let clients: Client[] = [];
  if (session) {
    try {
      const all = await listClients();
      clients = all.filter((c) => session.clientIds.includes(c.id));
    } catch {
      /* gateway may be down at build time */
    }
  }

  return (
    <html lang="en" className={`${inter.variable} ${display.variable}`}>
      <body className="flex min-h-screen flex-col">
        {session && (
          <Nav
            email={session.subject}
            role={session.role}
            clients={clients}
            currentClient={currentClient}
            demo={demoLoginsEnabled()}
          />
        )}
        <main className="shell flex-1 py-8">{children}</main>
        <footer className="border-t border-border/60">
          <div className="shell py-6 text-xs text-muted-foreground">
            Asset Tracker · Next.js console · talks to the API gateway over a
            server-side BFF, JWT in an httpOnly cookie.
          </div>
        </footer>
      </body>
    </html>
  );
}
