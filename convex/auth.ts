import { v } from "convex/values";
import { action, internalMutation, internalQuery, query } from "./_generated/server";
import { internal } from "./_generated/api";
import type { Doc } from "./_generated/dataModel";
import type { MutationCtx, QueryCtx } from "./_generated/server";

const SESSION_DURATION_MS = 30 * 24 * 60 * 60 * 1000;
const MIN_PASSWORD_LENGTH = 8;
const PASSWORD_HASH_PREFIX = "pbkdf2-sha256";
const PASSWORD_ITERATIONS = 210_000;
const PASSWORD_SALT_BYTES = 16;
const PASSWORD_KEY_LENGTH_BITS = 256;

type AuthActionResult = {
  sessionToken: string;
  email: string;
};

type LogoutActionResult = {
  signedOut: boolean;
};

function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

function assertPassword(password: string) {
  if (password.length < MIN_PASSWORD_LENGTH) {
    throw new Error("Password must be at least 8 characters.");
  }
}

function assertEmail(email: string) {
  if (!email.includes("@") || email.length < 3) {
    throw new Error("Enter a valid email address.");
  }
}

function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes)
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function hexToBytes(hex: string): Uint8Array {
  if (hex.length % 2 !== 0 || /[^0-9a-f]/i.test(hex)) {
    throw new Error("Invalid encoded hash.");
  }
  const bytes = new Uint8Array(hex.length / 2);
  for (let index = 0; index < bytes.length; index += 1) {
    bytes[index] = Number.parseInt(hex.slice(index * 2, index * 2 + 2), 16);
  }
  return bytes;
}

async function sha256(value: string): Promise<string> {
  const encoded = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", encoded);
  return bytesToHex(new Uint8Array(digest));
}

function randomHex(byteLength: number): string {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return bytesToHex(bytes);
}

async function derivePasswordHash(
  password: string,
  saltHex: string,
  iterations: number,
): Promise<string> {
  const keyMaterial = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      salt: hexToBytes(saltHex),
      iterations,
      hash: "SHA-256",
    },
    keyMaterial,
    PASSWORD_KEY_LENGTH_BITS,
  );
  return bytesToHex(new Uint8Array(bits));
}

async function hashPassword(password: string): Promise<string> {
  const saltHex = randomHex(PASSWORD_SALT_BYTES);
  const hashHex = await derivePasswordHash(password, saltHex, PASSWORD_ITERATIONS);
  return `${PASSWORD_HASH_PREFIX}:${PASSWORD_ITERATIONS}:${saltHex}:${hashHex}`;
}

async function verifyPassword(password: string, passwordHash: string): Promise<boolean> {
  const [prefix, iterationsText, saltHex, expectedHashHex] = passwordHash.split(":");
  const iterations = Number.parseInt(iterationsText ?? "", 10);
  if (
    prefix !== PASSWORD_HASH_PREFIX ||
    !Number.isFinite(iterations) ||
    saltHex === undefined ||
    expectedHashHex === undefined
  ) {
    return false;
  }
  const actualHashHex = await derivePasswordHash(password, saltHex, iterations);
  return constantTimeEquals(actualHashHex, expectedHashHex);
}

function constantTimeEquals(left: string, right: string): boolean {
  if (left.length !== right.length) {
    return false;
  }
  let diff = 0;
  for (let index = 0; index < left.length; index += 1) {
    diff |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return diff === 0;
}

export async function hashSessionToken(sessionToken: string): Promise<string> {
  return sha256(`sellerkeyboard-session:${sessionToken}`);
}

export const register = action({
  args: {
    email: v.string(),
    password: v.string(),
  },
  handler: async (ctx, args): Promise<AuthActionResult> => {
    const email = normalizeEmail(args.email);
    assertEmail(email);
    assertPassword(args.password);

    const existing = await ctx.runQuery(internal.auth.findUserByEmail, { email }) as
      | Doc<"users">
      | null;
    if (existing !== null) {
      throw new Error("An account already exists for this email.");
    }

    const passwordHash = await hashPassword(args.password);
    const sessionToken = randomHex(32);
    const sessionTokenHash = await hashSessionToken(sessionToken);
    const now = Date.now();
    const user = await ctx.runMutation(internal.auth.createUserWithSession, {
      email,
      passwordHash,
      sessionTokenHash,
      now,
      expiresAt: now + SESSION_DURATION_MS,
    }) as Doc<"users">;

    return {
      sessionToken,
      email: user.email,
    };
  },
});

export const login = action({
  args: {
    email: v.string(),
    password: v.string(),
  },
  handler: async (ctx, args): Promise<AuthActionResult> => {
    const email = normalizeEmail(args.email);
    assertEmail(email);
    const user = await ctx.runQuery(internal.auth.findUserByEmail, { email }) as
      | Doc<"users">
      | null;
    if (user === null) {
      throw new Error("Invalid email or password.");
    }

    const passwordMatches = await verifyPassword(args.password, user.passwordHash);
    if (!passwordMatches) {
      throw new Error("Invalid email or password.");
    }

    const sessionToken = randomHex(32);
    const sessionTokenHash = await hashSessionToken(sessionToken);
    const now = Date.now();
    await ctx.runMutation(internal.auth.createSession, {
      userId: user._id,
      sessionTokenHash,
      now,
      expiresAt: now + SESSION_DURATION_MS,
    });

    return {
      sessionToken,
      email: user.email,
    };
  },
});

export const logout = action({
  args: {
    sessionToken: v.string(),
  },
  handler: async (ctx, args): Promise<LogoutActionResult> => {
    const sessionTokenHash = await hashSessionToken(args.sessionToken);
    await ctx.runMutation(internal.auth.revokeSessionByTokenHash, {
      sessionTokenHash,
      now: Date.now(),
    });
    return { signedOut: true };
  },
});

export const whoAmI = query({
  args: {
    sessionToken: v.string(),
  },
  handler: async (ctx, args) => {
    const session = await requireSession(ctx, args.sessionToken);
    return {
      email: session.user.email,
      userId: session.user._id,
    };
  },
});

export async function requireSession(
  ctx: QueryCtx | MutationCtx,
  sessionToken: string,
) {
  const sessionTokenHash = await hashSessionToken(sessionToken);
  const session = await ctx.db
    .query("sessions")
    .withIndex("by_token_hash", (q) => q.eq("sessionTokenHash", sessionTokenHash))
    .unique();

  if (session === null || session.revokedAt !== undefined || session.expiresAt <= Date.now()) {
    throw new Error("Unauthenticated");
  }

  const user = await ctx.db.get(session.userId);
  if (user === null) {
    throw new Error("Unauthenticated");
  }

  return {
    session,
    user,
    ownerToken: session.userId,
  };
}

export const findUserByEmail = internalQuery({
  args: {
    email: v.string(),
  },
  handler: async (ctx, args) => {
    return await ctx.db
      .query("users")
      .withIndex("by_email", (q) => q.eq("email", args.email))
      .unique();
  },
});

export const createUserWithSession = internalMutation({
  args: {
    email: v.string(),
    passwordHash: v.string(),
    sessionTokenHash: v.string(),
    now: v.number(),
    expiresAt: v.number(),
  },
  handler: async (ctx, args) => {
    const userId = await ctx.db.insert("users", {
      email: args.email,
      passwordHash: args.passwordHash,
      createdAt: args.now,
      updatedAt: args.now,
    });
    await ctx.db.insert("sessions", {
      userId,
      sessionTokenHash: args.sessionTokenHash,
      createdAt: args.now,
      expiresAt: args.expiresAt,
    });
    const user = await ctx.db.get(userId);
    if (user === null) {
      throw new Error("Could not create user.");
    }
    return user;
  },
});

export const createSession = internalMutation({
  args: {
    userId: v.id("users"),
    sessionTokenHash: v.string(),
    now: v.number(),
    expiresAt: v.number(),
  },
  handler: async (ctx, args) => {
    await ctx.db.insert("sessions", {
      userId: args.userId,
      sessionTokenHash: args.sessionTokenHash,
      createdAt: args.now,
      expiresAt: args.expiresAt,
    });
  },
});

export const revokeSessionByTokenHash = internalMutation({
  args: {
    sessionTokenHash: v.string(),
    now: v.number(),
  },
  handler: async (ctx, args) => {
    const session = await ctx.db
      .query("sessions")
      .withIndex("by_token_hash", (q) => q.eq("sessionTokenHash", args.sessionTokenHash))
      .unique();
    if (session !== null && session.revokedAt === undefined) {
      await ctx.db.patch(session._id, { revokedAt: args.now });
    }
  },
});
