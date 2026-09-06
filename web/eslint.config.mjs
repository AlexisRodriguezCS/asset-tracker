import { dirname } from "path";
import { fileURLToPath } from "url";
import { FlatCompat } from "@eslint/eslintrc";

/**
 * Flat config, replacing `.eslintrc.json` and `next lint`.
 *
 * `next lint` is deprecated and goes away in Next 16, so this calls the ESLint
 * CLI directly. `eslint-config-next` 15 still ships only eslintrc-style configs,
 * so `FlatCompat` bridges them - the same shape the official codemod produces.
 * When that package ships a flat export, the compat wrapper is what to delete.
 *
 * Flat config has no `.eslintignore`; ignores live here instead, and must be in
 * their own object to apply globally rather than to one set of files.
 */
const compat = new FlatCompat({
  baseDirectory: dirname(fileURLToPath(import.meta.url)),
});

const config = [
  {
    ignores: [
      ".next/**",
      "out/**",
      "build/**",
      "node_modules/**",
      "next-env.d.ts",
    ],
  },
  ...compat.extends("next/core-web-vitals", "next/typescript"),
];

export default config;
