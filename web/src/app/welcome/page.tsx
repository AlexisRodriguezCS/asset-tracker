import Link from "next/link";
import { redirect } from "next/navigation";
import { Boxes, Mail } from "lucide-react";
import { getSession } from "@/lib/session";
import { entraConfig } from "@/lib/entra";
import { Card } from "@/components/ui/card";

export const dynamic = "force-dynamic";

const ERRORS: Record<string, string> = {
  not_configured:
    "Microsoft 365 sign-in isn't set up on this deployment yet. Use email for now.",
  state_mismatch: "That sign-in link expired. Please try again.",
  token_exchange_failed: "Microsoft rejected the sign-in. Please try again.",
  no_id_token: "Microsoft didn't return an identity token. Please try again.",
  exchange_failed: "Couldn't complete sign-in with the platform. Try again.",
  MICROSOFT_SIGNIN_NOT_CONFIGURED:
    "Microsoft 365 sign-in isn't set up on this deployment yet. Use email for now.",
};

export default async function WelcomePage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string; next?: string }>;
}) {
  const [session, sp] = await Promise.all([getSession(), searchParams]);
  if (session) redirect("/dashboard");

  const { configured } = entraConfig();
  const message = sp.error
    ? (ERRORS[sp.error] ?? decodeURIComponent(sp.error))
    : null;

  return (
    <div className="mx-auto max-w-xl animate-fade-in-up py-10">
      <span className="bg-gradient-primary inline-grid h-11 w-11 place-items-center rounded-xl text-primary-foreground">
        <Boxes className="h-6 w-6" />
      </span>
      <h1 className="mt-5 font-display text-3xl font-semibold tracking-tight">
        Track every device, from stockroom to desk.
      </h1>
      <p className="mt-2 text-muted-foreground">
        asset-tracker is the console for IT: who holds which laptop, what&apos;s
        on each desk, warranty and repair status, and a full audit trail —
        across all your client organizations.
      </p>

      <ul className="mt-5 space-y-1.5 text-sm text-muted-foreground">
        <li>· Check-out, transfer and one-click offboarding sweeps</li>
        <li>· Retire-and-replace that keeps the tag and the history</li>
        <li>· A dashboard of what needs attention today</li>
      </ul>

      {message && (
        <p className="mt-6 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground">
          {message}
        </p>
      )}

      <Card className="mt-6 space-y-3">
        <a
          href="/api/auth/microsoft/start"
          aria-disabled={!configured}
          className={
            "flex h-11 w-full items-center justify-center gap-2 rounded-md text-sm font-medium transition-[transform,filter] " +
            (configured
              ? "bg-gradient-primary text-primary-foreground hover:brightness-110 active:scale-[0.98]"
              : "pointer-events-none border border-border text-muted-foreground opacity-60")
          }
        >
          <MicrosoftMark />
          Sign in with Microsoft 365
        </a>

        <Link
          href={`/login${sp.next ? `?next=${encodeURIComponent(sp.next)}` : ""}`}
          className="flex h-11 w-full items-center justify-center gap-2 rounded-md border border-border text-sm font-medium transition-colors hover:border-primary/50 hover:bg-accent active:scale-[0.98]"
        >
          <Mail className="h-4 w-4" />
          Sign in with email
        </Link>

        {!configured && (
          <p className="text-center text-xs text-muted-foreground">
            Microsoft sign-in activates once an Entra ID app is configured.
          </p>
        )}
      </Card>

      <p className="mt-4 text-center text-sm text-muted-foreground">
        Just looking?{" "}
        <Link href="/" className="font-medium text-primary hover:underline">
          Browse assets
        </Link>
      </p>
    </div>
  );
}

function MicrosoftMark() {
  return (
    <svg viewBox="0 0 21 21" className="h-4 w-4" aria-hidden="true">
      <rect x="1" y="1" width="9" height="9" fill="#f25022" />
      <rect x="11" y="1" width="9" height="9" fill="#7fba00" />
      <rect x="1" y="11" width="9" height="9" fill="#00a4ef" />
      <rect x="11" y="11" width="9" height="9" fill="#ffb900" />
    </svg>
  );
}
