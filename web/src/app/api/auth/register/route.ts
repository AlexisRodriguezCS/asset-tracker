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

  // Demo self-signups get TECH on all three seeded clients so the console is
  // immediately usable. A real deployment would grant access via an admin.
  const reg = await fetch(`${GATEWAY}/api/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      email,
      password,
      role: "TECH",
      clientIds: [1, 2, 3],
    }),
  });

  if (!reg.ok && reg.status !== 409) {
    const problem = await reg.json().catch(() => null);
    return NextResponse.json(
      {
        code: problem?.code ?? "REGISTER_FAILED",
        message: problem?.message ?? "Could not register",
      },
      { status: 502 },
    );
  }
  const alreadyExists = reg.status === 409;

  // Registration on the platform does not return a token; log in to get one.
  const login = await fetch(`${GATEWAY}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!login.ok) {
    return NextResponse.json(
      {
        code: alreadyExists ? "EMAIL_TAKEN" : "AUTH_FAILED",
        message: alreadyExists
          ? "That email is already registered"
          : "Registered, but sign-in failed",
      },
      { status: alreadyExists ? 409 : 502 },
    );
  }

  const token = (await login.json()) as TokenResponse;
  await setSessionCookie(token.token, token.expiresInMs);
  return NextResponse.json({ ok: true });
}
