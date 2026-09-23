import { defineSchema, defineTable } from "convex/server";
import { v } from "convex/values";

export default defineSchema({
  users: defineTable({
    email: v.string(),
    passwordHash: v.string(),
    createdAt: v.number(),
    updatedAt: v.number(),
  }).index("by_email", ["email"]),

  sessions: defineTable({
    userId: v.id("users"),
    sessionTokenHash: v.string(),
    createdAt: v.number(),
    expiresAt: v.number(),
    revokedAt: v.optional(v.number()),
  })
    .index("by_token_hash", ["sessionTokenHash"])
    .index("by_user", ["userId"]),

  snippetBackups: defineTable({
    ownerToken: v.string(),
    userId: v.id("users"),
    clientId: v.string(),
    title: v.string(),
    content: v.string(),
    imageUrls: v.array(v.string()),
    deleted: v.boolean(),
    updatedAt: v.number(),
    schemaVersion: v.number(),
  })
    .index("by_owner", ["ownerToken"])
    .index("by_owner_clientId", ["ownerToken", "clientId"]),
});
