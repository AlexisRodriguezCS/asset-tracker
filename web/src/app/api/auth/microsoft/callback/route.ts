import { NextRequest, NextResponse } from "next/server";
import { cookies } from "next/headers";
import { entraConfig } from "@/lib/entra";
import { setSessionCookie } from "@/lib/session";
import type { TokenResponse } from "@/lib/types";

const GATEWAY = process.env.GATEWAY_URL ?? "http://localhost:8080";

/**
 * Microsoft redirects here with an authorization code. We swap it for an
 * id-token (confidential client + PKCE), hand that to auth-service which
 * validates it and mints a local session token, then set the httpOnly cookie
 * and land the user on the dashboard.
 */
export async function GET(req: NextRequest) {
  const cfg = entraConfig();
  const params = req.nextUrl.searchParams;
  const back = (error: string) =>
    NextResponse.redirect(
      new URL(`/welcome?error=${encodeURIComponent(error)}`, cfg.appUrl),
    );

  if (!cfg.configured) return back("not_configured");
  if (params.get("error")) {
    return back(params.get("error_description") || params.get("error")!);
  }

  const code = params.get("code");
  const state = params.get("state");

  const jar = await cookies();
  const verifier = jar.get("att_oauth_verifier")?.value;
  const savedState = jar.get("att_oauth_state")?.value;
  jar.delete("att_oauth_verifier");
  jar.delete("att_oauth_state");

  if (!code || !state || !verifier || state !== savedState) {
    return back("state_mismatch");
  }

  const tokenRes = await fetch(`${cfg.authority}/oauth2/v2.0/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: cfg.clientId,
      client_secret: cfg.clientSecret,
      grant_type: "authorization_code",
      code,
      redirect_uri: cfg.redirectUri,
      code_verifier: verifier,
      scope: cfg.scope,
    }),
  });
  if (!tokenRes.ok) return back("token_exchange_failed");

  const payload = (await tokenRes.json().catch(() => null)) as {
    id_token?: string;
  } | null;
  if (!payload?.id_token) return back("no_id_token");

  const exchange = await fetch(`${GATEWAY}/api/auth/microsoft`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ idToken: payload.id_token }),
  });
  if (!exchange.ok) {
    const problem = await exchange.json().catch(() => null);
    return back(problem?.code ?? "exchange_failed");
  }

  const session = (await exchange.json()) as TokenResponse;
  await setSessionCookie(session.token, session.expiresInMs);
  return NextResponse.redirect(new URL("/dashboard", cfg.appUrl));
}
