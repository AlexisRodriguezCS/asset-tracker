import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import {
  getPerson,
  listAssets,
  listLocations,
  personAudit,
  GatewayError,
} from "@/lib/api";
import { getSession } from "@/lib/session";
import { Card } from "@/components/ui/card";
import { OffboardButton } from "@/components/offboard-button";
import { DeskPicker } from "@/components/desk-picker";
import { PersonGear } from "@/components/person-gear";
import { PersonDetails } from "@/components/person-details";
import { AuditFeed } from "@/components/audit-feed";
import { canAssignDesks, canCollect, canOperateAssets } from "@/lib/roles";

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

  const [session, held, activity, inStock, desks] = await Promise.all([
    getSession(),
    listAssets({
      clientId: person.clientId,
      holderType: "PERSON",
      holderId: person.id,
    }),
    personAudit(person.clientId, person.id).catch(() => []),
    // the stockroom, so gear can be handed over from this page rather than from each asset
    listAssets({ clientId: person.clientId, status: "IN_STOCK" }).catch(
      () => [],
    ),
    // Fetched whether or not they sit somewhere: this used to load desks only to name the
    // one they had, and the picker has to offer the others.
    listLocations(person.clientId, "DESK").catch(() => []),
  ]);
  const desk = desks.find((d) => d.id === person.deskId) ?? null;
  const deskText = desk
    ? `${desk.label}${desk.building ? ` · ${desk.building}` : ""}${desk.floor ? ` · floor ${desk.floor}` : ""}`
    : person.deskId
      ? `desk #${person.deskId}`
      : "no desk";

  return (
    <div className="animate-fade-in-up">
      <Link
        href="/people"
        className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft className="h-4 w-4" /> People
      </Link>

      <PersonDetails
        person={person}
        canEdit={canOperateAssets(session?.role)}
      />
      <p className="mt-1 text-sm text-muted-foreground">{deskText}</p>

      {canAssignDesks(session?.role) && (
        <Card className="mt-6 space-y-3">
          <div>
            <h2 className="text-sm font-semibold">Desk</h2>
            <p className="text-xs text-muted-foreground">
              Where this person sits. Changing it moves no equipment - gear
              follows the person, not the desk.
            </p>
          </div>
          <DeskPicker
            personId={person.id}
            desks={desks}
            current={person.deskId ?? null}
          />
        </Card>
      )}

      <Card className="mt-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-sm font-semibold">Assets held ({held.length})</h2>
          <OffboardButton
            clientId={person.clientId}
            personId={person.id}
            heldCount={held.length}
            signedIn={Boolean(session)}
          />
        </div>

        <PersonGear
          clientId={person.clientId}
          personId={person.id}
          held={held}
          inStock={inStock}
          canGive={canOperateAssets(session?.role)}
          canTakeBack={canCollect(session?.role)}
        />
      </Card>

      <Card className="mt-4">
        <h2 className="text-sm font-semibold">Activity log</h2>
        <p className="text-xs text-muted-foreground">
          Recorded in the same transaction as the change.
        </p>
        <AuditFeed events={activity} />
      </Card>
    </div>
  );
}
