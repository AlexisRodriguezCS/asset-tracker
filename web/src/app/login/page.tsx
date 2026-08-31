"use client";

import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";

function LoginForm() {
  const router = useRouter();
  const next = useSearchParams().get("next") || "/";
  const [email, setEmail] = useState("tech@acme.example");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const res = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    setBusy(false);
    if (res.ok) {
      router.push(next);
      router.refresh();
    } else {
      const b = await res.json().catch(() => null);
      setError(b?.message ?? "Sign-in failed");
    }
  }

  return (
    <div className="mx-auto max-w-sm animate-fade-in-up py-8">
      <h1 className="text-2xl font-semibold tracking-tight">Sign in</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        Seeded: <code>tech@acme.example</code> / <code>Passw0rd!</code>
      </p>
      <Card className="mt-6">
        <form onSubmit={submit} className="space-y-3">
          <div className="space-y-1.5">
            <label className="text-sm font-medium" htmlFor="email">
              Email
            </label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          <div className="space-y-1.5">
            <label className="text-sm font-medium" htmlFor="password">
              Password
            </label>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          <Button type="submit" disabled={busy} className="w-full">
            {busy ? "Signing in…" : "Sign in"}
          </Button>
        </form>
      </Card>
      <p className="mt-4 text-center text-sm text-muted-foreground">
        No account?{" "}
        <Link
          href="/register"
          className="font-medium text-primary hover:underline"
        >
          Create one
        </Link>
      </p>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
