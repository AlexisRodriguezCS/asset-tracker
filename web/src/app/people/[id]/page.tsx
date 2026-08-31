import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { getPerson, listAssets, GatewayError } from "@/lib/api";
import { getSession } from "@/lib/session";
import { Card } from "@/components/ui/card";
import { AssetStatusBadge, PersonStatusBadge } from "@/components/ui/badge";
import { OffboardButton } from "@/components/offboard-button";
import { label } from "@/lib/format";

export default async function PersonDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  let person;
  try {
    person = await getPerson(id);
  } catch (e) {
    if (e instanceof GatewayError && e.status === 404) notFound();
    throw e;
  }

  const [session, held] = await Promise.all([
    getSession(),
    listAssets({
      clientId: person.clientId,
      holderType: "PERSON",
      holderId: person.id,
    }),
  ]);

  return (
    <div className="animate-fade-in-up">
      <Link
        href="/people"
        className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft className="h-4 w-4" /> People
      </Link>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <h1 className="font-display text-2xl font-semibold tracking-tight">
          {person.fullName}
        </h1>
        <PersonStatusBadge status={person.status} />
      </div>
      <p className="mt-1 text-sm text-muted-foreground">
        {person.email}
        {person.department ? ` · ${person.department}` : ""}
        {person.deskId ? ` · desk #${person.deskId}` : " · no desk"}
      </p>

      <Card className="mt-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-sm font-semibold">Assets held ({held.length})</h2>
          <OffboardButton
            clientId={person.clientId}
            personId={person.id}
            heldCount={held.length}
            signedIn={Boolean(session)}
          />
        </div>

        {held.length === 0 ? (
          <p className="mt-4 text-sm text-muted-foreground">
            This person holds no assets.
          </p>
        ) : (
          <ul className="mt-4 divide-y divide-border">
            {held.map((a) => (
              <li
                key={a.id}
                className="flex items-center justify-between py-2.5"
              >
                <div>
                  <Link
                    href={`/assets/${a.id}`}
                    className="font-medium text-primary hover:underline"
                  >
                    {[a.make, a.model].filter(Boolean).join(" ") ||
                      label(a.type)}
                  </Link>
                  <p className="font-mono text-xs text-muted-foreground">
                    {a.assetTag} · {label(a.type)}
                  </p>
                </div>
                <AssetStatusBadge status={a.status} />
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
