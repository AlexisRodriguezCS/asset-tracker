import Link from "next/link";
import { currentClientId } from "@/lib/client";
import { listPeople } from "@/lib/api";
import { PersonStatusBadge } from "@/components/ui/badge";
import { StatStrip } from "@/components/ui/stat";
import { PageHeader, TableCard } from "@/components/ui/page-header";

export default async function PeoplePage() {
  const clientId = await currentClientId();
  const people = await listPeople(clientId);

  const n = (s: string) => people.filter((p) => p.status === s).length;

  return (
    <div className="animate-fade-in-up space-y-6">
      <PageHeader
        title="People"
        subtitle="Click a name to see what they hold or run offboarding"
      />

      <StatStrip
        stats={[
          { label: "People", value: people.length },
          { label: "Active", value: n("ACTIVE"), tone: "success" },
          { label: "Offboarding", value: n("OFFBOARDING"), tone: "warn" },
          { label: "Departed", value: n("DEPARTED") },
          {
            label: "With a desk",
            value: people.filter((p) => p.deskId != null).length,
            tone: "primary",
          },
        ]}
      />

      <TableCard>
        <thead className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
          <tr>
            <th className="px-4 py-3 font-medium">Name</th>
            <th className="px-4 py-3 font-medium">Email</th>
            <th className="px-4 py-3 font-medium">Department</th>
            <th className="px-4 py-3 font-medium">Desk</th>
            <th className="px-4 py-3 font-medium">Status</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border/70">
          {people.map((p) => (
            <tr key={p.id} className="transition-colors hover:bg-accent/40">
              <td className="px-4 py-2.5">
                <Link
                  href={`/people/${p.id}`}
                  className="font-medium text-primary hover:underline"
                >
                  {p.fullName}
                </Link>
              </td>
              <td className="px-4 py-2.5 text-muted-foreground">{p.email}</td>
              <td className="px-4 py-2.5">
                {p.department ?? (
                  <span className="text-muted-foreground">—</span>
                )}
              </td>
              <td className="px-4 py-2.5 text-muted-foreground">
                {p.deskId ? (
                  <span className="font-mono text-xs">#{p.deskId}</span>
                ) : (
                  "—"
                )}
              </td>
              <td className="px-4 py-2.5">
                <PersonStatusBadge status={p.status} />
              </td>
            </tr>
          ))}
        </tbody>
      </TableCard>
    </div>
  );
}
