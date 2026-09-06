import { defineConfig } from "vitest/config";
import { fileURLToPath } from "node:url";

export default defineConfig({
  test: {
    // These suites cover pure logic - roles, token decoding, the tenant cookie,
    // the BFF allow-list. None of it needs a DOM, and a node environment keeps
    // the run fast enough to sit in the same CI job as lint and typecheck.
    environment: "node",
    include: ["src/**/*.test.ts"],
  },
  resolve: {
    alias: [
      {
        find: "@",
        replacement: fileURLToPath(new URL("./src", import.meta.url)),
      },
      // `server-only` exists to fail a build that imports server code into a
      // client bundle. Under vitest there is no such bundle, so it is a no-op.
      {
        find: /^server-only$/,
        replacement: fileURLToPath(
          new URL("./src/test/server-only-stub.ts", import.meta.url),
        ),
      },
    ],
  },
});
