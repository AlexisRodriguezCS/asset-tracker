import "server-only";
import { cookies } from "next/headers";
import { jwtDecode } from "@/lib/jwt";

const COOKIE = process.env.SESSION_COOKIE ?? "att_session";

export interface Session {
  token: string;
  subject: string;
  role: string;
  clientIds: number[];
  expiresAt: number;
}

/** Reads and validates the session cookie. Returns null when absent or expired. */
export async function getSession(): Promise<Session | null> {
  const jar = await cookies();
  const token = jar.get(COOKIE)?.value;
  if (!token) return null;

  const claims = jwtDecode(token);
  if (!claims) return null;

  const expiresAt = (claims.exp ?? 0) * 1000;
  if (expiresAt <= Date.now()) return null;

  const clientIds = Array.isArray(claims.clientIds)
    ? claims.clientIds.map(Number).filter((n) => !Number.isNaN(n))
    : [];

  return {
    token,
    subject: String(claims.sub ?? ""),
    role: String(claims.role ?? "TECH"),
    clientIds,
    expiresAt,
  };
}

/** Writes the httpOnly session cookie. Called from the auth route handlers. */
export async function setSessionCookie(token: string, maxAgeMs: number) {
  const jar = await cookies();
  jar.set(COOKIE, token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: Math.floor(maxAgeMs / 1000),
  });
}

export async function clearSessionCookie() {
  const jar = await cookies();
  jar.delete(COOKIE);
}
