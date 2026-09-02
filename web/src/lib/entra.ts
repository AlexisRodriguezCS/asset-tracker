import "server-only";
import { createHash, randomBytes } from "node:crypto";

/**
 * "Sign in with Microsoft 365" configuration, read from the server environment.
 * `configured` is false until an Entra app registration is filled in, in which
 * case the welcome page hides the Microsoft button and the OIDC routes bounce
 * back with `?error=not_configured`.
 */
export function entraConfig() {
  const tenantId = process.env.AZURE_AD_TENANT_ID ?? "";
  const clientId = process.env.AZURE_AD_CLIENT_ID ?? "";
  const clientSecret = process.env.AZURE_AD_CLIENT_SECRET ?? "";
  const appUrl = process.env.APP_URL ?? "http://localhost:3000";
  const redirectUri =
    process.env.AZURE_AD_REDIRECT_URI ??
    `${appUrl}/api/auth/microsoft/callback`;

  return {
    tenantId,
    clientId,
    clientSecret,
    appUrl,
    redirectUri,
    scope: "openid profile email",
    authority: `https://login.microsoftonline.com/${tenantId || "common"}`,
    configured: Boolean(tenantId && clientId && clientSecret),
  };
}

export type EntraConfig = ReturnType<typeof entraConfig>;

export function authorizeUrl(
  cfg: EntraConfig,
  state: string,
  codeChallenge: string,
): string {
  const url = new URL(`${cfg.authority}/oauth2/v2.0/authorize`);
  url.searchParams.set("client_id", cfg.clientId);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("redirect_uri", cfg.redirectUri);
  url.searchParams.set("response_mode", "query");
  url.searchParams.set("scope", cfg.scope);
  url.searchParams.set("state", state);
  url.searchParams.set("code_challenge", codeChallenge);
  url.searchParams.set("code_challenge_method", "S256");
  return url.toString();
}

/** PKCE: a high-entropy verifier and its S256 challenge, both base64url. */
export function pkcePair() {
  const verifier = base64url(randomBytes(32));
  const codeChallenge = base64url(
    createHash("sha256").update(verifier).digest(),
  );
  return { verifier, codeChallenge };
}

export const randomState = () => base64url(randomBytes(16));

function base64url(buf: Buffer): string {
  return buf.toString("base64url");
}
