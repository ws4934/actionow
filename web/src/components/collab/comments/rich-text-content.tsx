"use client";

/**
 * RichTextContent
 * Renders comment content string with @mention and #entity chips.
 * Uses `mentions[].offset` / `mentions[].length` to locate chips.
 */

import type { CommentMentionDTO } from "@/lib/api/dto/comment.dto";
import { User, Clapperboard, MapPin, Package } from "lucide-react";

// ── Types ────────────────────────────────────────────────────────────────────

type Segment =
  | { type: "text"; value: string }
  | { type: "mention"; mention: CommentMentionDTO };

// ── Helpers ──────────────────────────────────────────────────────────────────

const MENTION_ICONS: Record<string, React.ElementType> = {
  USER: User,
  CHARACTER: User,
  STORYBOARD: Clapperboard,
  SCENE: MapPin,
  PROP: Package,
};

function buildSegments(content: string, mentions: CommentMentionDTO[] | null | undefined): Segment[] {
  if (!mentions?.length) return [{ type: "text", value: content }];

  const sorted = [...mentions].sort((a, b) => a.offset - b.offset);
  const segments: Segment[] = [];
  let cursor = 0;

  for (const m of sorted) {
    if (m.offset > cursor) {
      segments.push({ type: "text", value: content.slice(cursor, m.offset) });
    }
    if (m.offset >= cursor) {
      segments.push({ type: "mention", mention: m });
      cursor = m.offset + m.length;
    }
  }

  if (cursor < content.length) {
    segments.push({ type: "text", value: content.slice(cursor) });
  }

  return segments;
}

// ── Component ────────────────────────────────────────────────────────────────

export function RichTextContent({
  content,
  mentions = [],
}: {
  content: string;
  mentions?: CommentMentionDTO[];
}) {
  const segments = buildSegments(content, mentions);

  return (
    <span className="whitespace-pre-wrap break-words text-sm leading-relaxed">
      {segments.map((seg, i) => {
        if (seg.type === "text") return <span key={i}>{seg.value}</span>;

        const Icon = MENTION_ICONS[seg.mention.type] ?? User;
        return (
          <span
            key={i}
            className="inline-flex items-center gap-0.5 rounded px-1 py-0.5 text-xs font-medium bg-accent/15 text-accent"
          >
            <Icon className="size-2.5 shrink-0" />
            {seg.mention.name}
          </span>
        );
      })}
    </span>
  );
}
