import { readFileSync } from "node:fs";
import { ConvexHttpClient } from "convex/browser";
import { api } from "../convex/_generated/api";

type Env = {
  CONVEX_URL?: string;
};

function readEnvLocal(): Env {
  return Object.fromEntries(
    readFileSync(".env.local", "utf8")
      .split(/\r?\n/)
      .map((line) => line.replace(/#.*/, "").trim())
      .filter((line) => line.includes("="))
      .map((line) => {
        const [key, ...rest] = line.split("=");
        return [key, rest.join("=")];
      }),
  );
}

const env = readEnvLocal();
if (env.CONVEX_URL === undefined || env.CONVEX_URL.length === 0) {
  throw new Error("CONVEX_URL missing from .env.local");
}

const client = new ConvexHttpClient(env.CONVEX_URL);
const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
const email = `codex-backend-smoke-${suffix}@example.com`;
const password = `SmokePassword-${suffix}!`;
const clientId = `backend-smoke-${suffix}`;

const registered = await client.action(api.auth.register, { email, password });
await client.action(api.auth.logout, { sessionToken: registered.sessionToken });

const loggedIn = await client.action(api.auth.login, { email, password });
const who = await client.query(api.snippets.whoAmI, {
  sessionToken: loggedIn.sessionToken,
});

const saved = await client.mutation(api.snippets.backupBatch, {
  sessionToken: loggedIn.sessionToken,
  snippets: [
    {
      clientId,
      title: "Codex backend smoke snippet",
      content: "Backend smoke content",
      imageUrls: [],
      deleted: false,
      updatedAt: Date.now(),
      schemaVersion: 1,
    },
  ],
});

const beforeDelete = await client.query(api.snippets.listBackup, {
  sessionToken: loggedIn.sessionToken,
});
const deleted = await client.mutation(api.snippets.deleteBackupItem, {
  sessionToken: loggedIn.sessionToken,
  clientId,
});
const afterDelete = await client.query(api.snippets.listBackup, {
  sessionToken: loggedIn.sessionToken,
});

await client.action(api.auth.logout, { sessionToken: loggedIn.sessionToken });

await Bun.write(
  Bun.stdout,
  `${JSON.stringify(
    {
      email,
      whoEmail: who.email,
      savedCount: saved.savedCount,
      foundBeforeDelete: beforeDelete.some((snippet) => snippet.clientId === clientId),
      deleted: deleted.deleted,
      foundAfterDelete: afterDelete.some((snippet) => snippet.clientId === clientId),
    },
    null,
    2,
  )}\n`,
);
