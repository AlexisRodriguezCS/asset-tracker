import Link from "next/link";
import { currentClientId } from "@/lib/client";
import { listPeople } from "@/lib/api";
import { PersonStatusBadge } from "@/components/ui/badge";

export default async function PeoplePage() {
  const clientId = await currentClientId();
  const people = await listPeople(clientId);

  return (
    <div className="animate-fade-in-up">
      <h1 className="text-3xl font-semibold tracking-tight">People</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        {people.length} in this client · click through to see what someone holds
        or run offboarding
      </p>

      <div className="mt-6 overflow-x-auto rounded-lg border border-border bg-card/70 shadow-card backdrop-blur">
        <table className="w-full text-sm">
          <thead className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
            <tr>
              <th className="px-4 py-3">Name</th>
              <th className="px-4 py-3">Email</th>
              <th className="px-4 py-3">Department</th>
              <th className="px-4 py-3">Desk</th>
              <th className="px-4 py-3">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {people.map((p) => (
              <tr key={p.id} className="hover:bg-muted/40">
                <td className="px-4 py-2.5">
                  <Link
                    href={`/people/${p.id}`}
                    className="font-medium text-primary hover:underline"
                  >
                    {p.fullName}
                  </Link>
                </td>
                <td className="px-4 py-2.5 text-muted-foreground">{p.email}</td>
                <td className="px-4 py-2.5">{p.department ?? "—"}</td>
                <td className="px-4 py-2.5 text-muted-foreground">
                  {p.deskId ? `#${p.deskId}` : "—"}
                </td>
                <td className="px-4 py-2.5">
                  <PersonStatusBadge status={p.status} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
