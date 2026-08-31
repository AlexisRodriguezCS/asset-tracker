"use client";

import { useRouter } from "next/navigation";
import type { Client } from "@/lib/types";

/** Switches the tenant the console is scoped to by setting the att_client cookie. */
export function ClientPicker({
  clients,
  current,
}: {
  clients: Client[];
  current: number;
}) {
  const router = useRouter();

  if (clients.length === 0) return null;

  return (
    <select
      aria-label="Client"
      value={current}
      onChange={(e) => {
        document.cookie = `att_client=${e.target.value}; path=/; max-age=31536000`;
        router.refresh();
      }}
      className="h-8 rounded-md border border-border bg-card px-2 text-xs font-medium outline-none focus-visible:border-primary"
    >
      {clients.map((c) => (
        <option key={c.id} value={c.id}>
          {c.name}
        </option>
      ))}
    </select>
  );
}
