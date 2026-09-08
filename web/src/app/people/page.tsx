import Link from "next/link";
import { redirect } from "next/navigation";
import { getSession } from "@/lib/session";
import { canOperateAssets, isSelfServiceUser } from "@/lib/roles";
import { currentClientId } from "@/lib/client";
import { listPeoplePaged, peopleStats, listLocations } from "@/lib/api";
import { PersonStatusBadge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { StatStrip } from "@/components/ui/stat";
import { PageHeader, TableCard } from "@/components/ui/page-header";
import { Pagination } from "@/components/pagination";

export const metadata = { title: "People" };

/** Matches the catalog, so the two lists page at the same rate. */
const PAGE_SIZE = 50;

export default async function PeoplePage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string }>;
}) {
  const session = await getSession();
  if (!session) redirect("/welcome?next=/people");
  // staff-only, like every other page that shows the whole tenant: an employee
  // gets their own gear and the sign-out form, and nothing that lists colleagues
  if (isSelfServiceUser(session.role)) redirect("/");

  const [clientId, sp] = await Promise.all([currentClientId(), searchParams]);
  const pageIndex = Math.max(0, Number(sp.page ?? "0") || 0);

  // One page of rows and counts done in the database. This page used to fetch every person
  // and count them in the render, which is nothing at five people and the whole tenant over
  // the wire at five thousand - the same trade the catalog made when it stopped doing it.
  const [page, stats, desks] = await Promise.all([
    listPeoplePaged({ clientId, page: pageIndex, size: PAGE_SIZE }),
    peopleStats(clientId),
    listLocations(clientId, "DESK").catch(() => []),
  ]);
  const people = page.items;
  const deskLabel = new Map(desks.map((d) => [d.id, d.label]));

  return (
    <div className="animate-fade-in-up space-y-6">
      <PageHeader
        title="People"
        subtitle="Click a name to see what they hold or run offboarding"
        action={
          canOperateAssets(session.role) ? (
            <Link href="/people/new">
              <Button size="sm">Add person</Button>
            </Link>
          ) : null
        }
      />

      <StatStrip
        stats={[
          { label: "People", value: stats.total },
          { label: "Active", value: stats.active, tone: "success" },
          { label: "Offboarding", value: stats.offboarding, tone: "warn" },
          { label: "Departed", value: stats.departed },
          { label: "With a desk", value: stats.withDesk, tone: "primary" },
        ]}
      />

      <TableCard>
        <thead className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
          <tr>
            <th className="px-4 py-2.5 font-medium">Name</th>
            <th className="px-4 py-2.5 font-medium">Email</th>
            <th className="px-4 py-2.5 font-medium">Department</th>
            <th className="px-4 py-2.5 font-medium">Desk</th>
            <th className="px-4 py-2.5 font-medium">Status</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border/70">
          {people.map((p) => (
            <tr key={p.id} className="transition-colors hover:bg-accent/40">
              <td className="px-4 py-1.5">
                <Link
                  href={`/people/${p.id}`}
                  className="font-medium text-primary hover:underline"
                >
                  {p.fullName}
                </Link>
              </td>
              <td className="px-4 py-2 text-muted-foreground">{p.email}</td>
              <td className="px-4 py-1.5">
                {p.department ?? (
                  <span className="text-muted-foreground">—</span>
                )}
              </td>
              <td className="px-4 py-2 text-muted-foreground">
                {p.deskId ? (deskLabel.get(p.deskId) ?? `#${p.deskId}`) : "—"}
              </td>
              <td className="px-4 py-1.5">
                <PersonStatusBadge status={p.status} />
              </td>
            </tr>
          ))}
        </tbody>
      </TableCard>

      <Pagination
        page={page.page}
        totalPages={page.totalPages}
        total={page.total}
        size={page.size}
        href={(next) => (next > 0 ? `/people?page=${next}` : "/people")}
      />
    </div>
  );
}
