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
  title: "asset-tracker",
  description:
    "IT asset tracking console — assets, people, desks, assignments.",
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
  let clients: Client[] = [];
  try {
    clients = await listClients();
  } catch {
    /* gateway may be down at build time */
  }

  return (
    <html lang="en" className={`${inter.variable} ${display.variable}`}>
      <body className="flex min-h-screen flex-col">
        <Nav
          email={session?.subject ?? null}
          role={session?.role ?? null}
          clients={clients}
          currentClient={currentClient}
          demo={demoLoginsEnabled()}
        />
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
          {children}
        </main>
        <footer className="border-t border-border/60">
          <div className="mx-auto max-w-6xl px-4 py-6 text-xs text-muted-foreground">
            asset-tracker · Next.js console · talks to the API gateway over a
            server-side BFF, JWT in an httpOnly cookie.
          </div>
        </footer>
      </body>
    </html>
  );
}
