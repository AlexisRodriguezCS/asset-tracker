import { NextRequest, NextResponse } from "next/server";
import { setSessionCookie } from "@/lib/session";
import type { TokenResponse } from "@/lib/types";

const GATEWAY = process.env.GATEWAY_URL ?? "http://localhost:8080";

export async function POST(req: NextRequest) {
  const { email, password } = await req.json().catch(() => ({}));
  if (!email || !password) {
    return NextResponse.json(
      { code: "BAD_REQUEST", message: "email and password required" },
      { status: 400 },
    );
  }

  const res = await fetch(`${GATEWAY}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });

  if (!res.ok) {
    const problem = await res.json().catch(() => null);
    return NextResponse.json(
      { code: problem?.code ?? "AUTH_FAILED", message: "Invalid credentials" },
      { status: res.status === 401 ? 401 : 502 },
    );
  }

  const token = (await res.json()) as TokenResponse;
  await setSessionCookie(token.token, token.expiresInMs);
  return NextResponse.json({ ok: true });
}
