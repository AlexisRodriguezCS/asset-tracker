import coreWebVitals from "eslint-config-next/core-web-vitals";
import typescript from "eslint-config-next/typescript";

/**
 * Flat config, replacing `.eslintrc.json` and `next lint`.
 *
 * `eslint-config-next` 16 ships flat configs directly, so these are spread in as
 * they are. Under 15 they were eslintrc-style and had to come through
 * `FlatCompat`; keeping that wrapper on 16 does not merely become redundant, it
 * breaks - the compat layer walks the config to validate it and the native one
 * is self-referential, so ESLint dies formatting the error rather than linting.
 *
 * Flat config has no `.eslintignore`; ignores live here instead, and must be in
 * their own object to apply globally rather than to one set of files.
 */
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
  ...coreWebVitals,
  ...typescript,
];

export default config;
