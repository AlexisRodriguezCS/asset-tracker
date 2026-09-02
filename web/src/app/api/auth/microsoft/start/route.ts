import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { authorizeUrl, entraConfig, pkcePair, randomState } from "@/lib/entra";

const OAUTH_TTL_SECONDS = 600;

/** Kicks off the Microsoft OIDC Authorization Code + PKCE flow. */
export async function GET() {
  const cfg = entraConfig();
  if (!cfg.configured) {
    return NextResponse.redirect(
      new URL("/welcome?error=not_configured", cfg.appUrl),
    );
  }

  const { verifier, codeChallenge } = pkcePair();
  const state = randomState();

  const jar = await cookies();
  const opts = {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path: "/",
    maxAge: OAUTH_TTL_SECONDS,
  };
  jar.set("att_oauth_verifier", verifier, opts);
  jar.set("att_oauth_state", state, opts);

  return NextResponse.redirect(authorizeUrl(cfg, state, codeChallenge));
}
