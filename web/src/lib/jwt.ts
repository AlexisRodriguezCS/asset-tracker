// Minimal JWT payload reader. We do NOT verify the signature here - the
// api-gateway is the authority on token validity. This is only used to read the
// expiry / subject / roles for UI purposes (showing the user's email, hiding
// links, deciding when to bounce to /login).

export interface JwtClaims {
  sub?: string;
  iss?: string;
  exp?: number;
  iat?: number;
  roles?: unknown;
  [key: string]: unknown;
}

export function jwtDecode(token: string): JwtClaims | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const json = Buffer.from(payload, "base64").toString("utf8");
    return JSON.parse(json) as JwtClaims;
  } catch {
    return null;
  }
}
