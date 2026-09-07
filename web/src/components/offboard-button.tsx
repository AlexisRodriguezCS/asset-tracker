"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import type { OffboardingResult } from "@/lib/types";

/** "Collect all assets" for an offboarding employee. Requires a signed-in user. */
export function OffboardButton({
  clientId,
  personId,
  heldCount,
  signedIn,
}: {
  clientId: number;
  personId: number;
  heldCount: number;
  signedIn: boolean;
}) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<string | null>(null);

  if (!signedIn) {
    return (
      <p className="text-sm text-muted-foreground">
        <a href="/login" className="text-primary hover:underline">
          Sign in
        </a>{" "}
        to run offboarding.
      </p>
    );
  }

  async function run() {
    setBusy(true);
    setResult(null);
    const res = await fetch(
      `/api/bff/assignments/offboard?clientId=${clientId}&personId=${personId}`,
      { method: "POST" },
    );
    setBusy(false);
    if (res.ok) {
      const b: OffboardingResult = await res.json();
      // Three outcomes, worded by what the reader has to do about each: chase a
      // person, or fix a record. They used to be reported as one number, so an
      // asset already back on the shelf read as still being with the employee.
      const parts = [`Collected ${b.returned.length}`];
      if (b.failed.length) {
        parts.push(`${b.failed.length} still out`);
      }
      if (b.unrecorded?.length) {
        parts.push(
          `${b.unrecorded.length} back in stock but not recorded — tell IT`,
        );
      }
      setResult(`${parts.join(", ")}.`);
      router.refresh();
    } else {
      setResult("Offboarding failed.");
    }
  }

  return (
    <div className="flex items-center gap-3">
      <Button disabled={busy || heldCount === 0} onClick={run}>
        {busy
          ? "Collecting…"
          : heldCount === 0
            ? "Nothing to collect"
            : `Collect all ${heldCount} assets`}
      </Button>
      {result && (
        <span className="text-sm text-muted-foreground">{result}</span>
      )}
    </div>
  );
}
