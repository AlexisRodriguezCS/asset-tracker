import { NextRequest, NextResponse } from "next/server";
import { setSessionCookie } from "@/lib/session";
import { DEMO_PERSONAS, demoLoginsEnabled } from "@/lib/demo";
import type { TokenResponse } from "@/lib/types";

const GATEWAY = process.env.GATEWAY_URL ?? "http://localhost:8080";

/**
 * The demo persona switcher.
 *
 * This performs a *real* login as one of the seeded accounts and swaps the
 * session cookie for that account's token - it is not impersonation. The backend
 * has no "act as" path: every request that follows carries an ordinary token for
 * that user and is authorized exactly as if they had typed the password. That
 * matters, because a switcher implemented as server-side impersonation would be
 * a privilege-escalation hole sitting in the middle of the auth system.
 *
 * Off unless DEMO_LOGINS_ENABLED is set, and it can only ever reach the fixed
 * list of seeded demo accounts.
 */
export async function POST(req: NextRequest) {
  if (!demoLoginsEnabled()) {
    return NextResponse.json({ code: "DEMO_DISABLED" }, { status: 404 });
  }

  const { role } = await req.json().catch(() => ({}));
  const persona = DEMO_PERSONAS.find((p) => p.role === role);
  if (!persona) {
    return NextResponse.json({ code: "UNKNOWN_PERSONA" }, { status: 400 });
  }

  const res = await fetch(`${GATEWAY}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      email: persona.email,
      password: process.env.DEMO_PASSWORD ?? "Passw0rd!",
    }),
  });

  if (!res.ok) {
    // The gateway rate-limits POST /api/auth/** to protect against credential
    // stuffing, and switching personas quickly trips it. Reporting that as
    // "no seeded account" sent people looking for a seeding problem that was
    // not there, so pass the real reason through.
    if (res.status === 429) {
      return NextResponse.json(
        {
          code: "RATE_LIMITED",
          message:
            "Too many sign-ins in a row. Wait a moment before switching again.",
        },
        {
          status: 429,
          headers: { "Retry-After": res.headers.get("Retry-After") ?? "60" },
        },
      );
    }
    return NextResponse.json(
      {
        code: "DEMO_LOGIN_FAILED",
        message: `Could not sign in as ${persona.email} (gateway said ${res.status}).`,
      },
      { status: 502 },
    );
  }

  const token = (await res.json()) as TokenResponse;
  await setSessionCookie(token.token, token.expiresInMs);
  return NextResponse.json({ ok: true, email: persona.email });
}
