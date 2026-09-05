import type { Role } from "@/lib/roles";

/**
 * The seeded accounts the demo switcher can sign in as. Deliberately a fixed
 * list rather than anything caller-supplied: the switcher performs a real login,
 * so this is the full set of identities it can ever reach.
 */
export const DEMO_PERSONAS: { role: Role; email: string }[] = [
  { role: "TECH", email: "tech@acme.example" },
  { role: "POC", email: "poc@acme.example" },
  { role: "HR", email: "hr@acme.example" },
  { role: "USER", email: "dana.reyes@acme.example" },
  { role: "ADMIN", email: "admin@platform.example" },
];

/** Never on unless explicitly switched on for a demo environment. */
export function demoLoginsEnabled(): boolean {
  return process.env.DEMO_LOGINS_ENABLED === "true";
}
