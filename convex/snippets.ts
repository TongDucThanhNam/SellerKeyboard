import { v } from "convex/values";
import { mutation, query } from "./_generated/server";
import { requireSession } from "./auth";

const BACKUP_SCHEMA_VERSION = 1;

const backupSnippetValidator = v.object({
  clientId: v.string(),
  title: v.string(),
  content: v.string(),
  imageUrls: v.array(v.string()),
  deleted: v.boolean(),
  updatedAt: v.number(),
  schemaVersion: v.number(),
});

export const whoAmI = query({
  args: {
    sessionToken: v.string(),
  },
  handler: async (ctx, args) => {
    const session = await requireSession(ctx, args.sessionToken);
    return {
      email: session.user.email,
      ownerToken: session.ownerToken,
    };
  },
});

export const backupBatch = mutation({
  args: {
    sessionToken: v.string(),
    snippets: v.array(backupSnippetValidator),
  },
  handler: async (ctx, args) => {
    const session = await requireSession(ctx, args.sessionToken);
    let savedCount = 0;

    for (const snippet of args.snippets) {
      const existing = await ctx.db
        .query("snippetBackups")
        .withIndex("by_owner_clientId", (q) =>
          q.eq("ownerToken", session.ownerToken).eq("clientId", snippet.clientId),
        )
        .unique();

      const document = {
        ownerToken: session.ownerToken,
        userId: session.user._id,
        clientId: snippet.clientId,
        title: snippet.title,
        content: snippet.content,
        imageUrls: snippet.imageUrls,
        deleted: snippet.deleted,
        updatedAt: snippet.updatedAt,
        schemaVersion: snippet.schemaVersion || BACKUP_SCHEMA_VERSION,
      };

      if (existing === null) {
        await ctx.db.insert("snippetBackups", document);
      } else if (snippet.updatedAt >= existing.updatedAt) {
        await ctx.db.patch(existing._id, document);
      }
      savedCount += 1;
    }

    return { savedCount };
  },
});

export const listBackup = query({
  args: {
    sessionToken: v.string(),
  },
  handler: async (ctx, args) => {
    const session = await requireSession(ctx, args.sessionToken);
    const snippets = await ctx.db
      .query("snippetBackups")
      .withIndex("by_owner", (q) => q.eq("ownerToken", session.ownerToken))
      .collect();

    return snippets
      .filter((snippet) => !snippet.deleted)
      .map((snippet) => ({
        clientId: snippet.clientId,
        title: snippet.title,
        content: snippet.content,
        imageUrls: snippet.imageUrls,
        updatedAt: snippet.updatedAt,
        schemaVersion: snippet.schemaVersion,
      }));
  },
});

export const deleteBackupItem = mutation({
  args: {
    sessionToken: v.string(),
    clientId: v.string(),
  },
  handler: async (ctx, args) => {
    const session = await requireSession(ctx, args.sessionToken);
    const existing = await ctx.db
      .query("snippetBackups")
      .withIndex("by_owner_clientId", (q) =>
        q.eq("ownerToken", session.ownerToken).eq("clientId", args.clientId),
      )
      .unique();

    if (existing === null) {
      return { deleted: false };
    }

    await ctx.db.patch(existing._id, {
      deleted: true,
      updatedAt: Date.now(),
    });
    return { deleted: true };
  },
});
