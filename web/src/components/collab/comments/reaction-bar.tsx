"use client";

/**
 * ReactionBar
 * Shows existing emoji reactions with counts and a picker button for adding new ones.
 * Always visible — no hover show/hide to avoid flickering.
 */

import { useState, useCallback } from "react";
import { Popover, Tooltip } from "@heroui/react";
import { Smile } from "lucide-react";
import { useTranslations } from "next-intl";
import type { ReactionSummaryDTO, EmojiType } from "@/lib/api/dto/comment.dto";

// ── Constants ────────────────────────────────────────────────────────────────

export const EMOJI_MAP: Record<EmojiType, string> = {
  thumbsup: "\u{1F44D}",
  heart: "\u2764\uFE0F",
  fire: "\u{1F525}",
  eyes: "\u{1F440}",
  tada: "\u{1F389}",
  laugh: "\u{1F604}",
};

const ALL_EMOJIS: EmojiType[] = ["thumbsup", "heart", "fire", "eyes", "tada", "laugh"];

// ── Component ────────────────────────────────────────────────────────────────

interface ReactionBarProps {
  reactions: ReactionSummaryDTO[];
  /** Called when the user toggles an existing reaction */
  onToggle: (emoji: EmojiType) => void;
  /** Called when the user picks a new reaction from the picker */
  onPickNew?: (emoji: EmojiType) => void;
  /** Whether to show the "+" picker button */
  showPicker?: boolean;
  disabled?: boolean;
}

export function ReactionBar({
  reactions,
  onToggle,
  onPickNew,
  showPicker = true,
  disabled,
}: ReactionBarProps) {
  const t = useTranslations("workspace.collab.item");
  const [pickerOpen, setPickerOpen] = useState(false);

  const visible = (reactions ?? []).filter((r) => r.count > 0);

  const handlePickEmoji = useCallback(
    (emoji: EmojiType) => {
      setPickerOpen(false);
      onPickNew?.(emoji);
    },
    [onPickNew]
  );

  if (visible.length === 0 && (!showPicker || !onPickNew)) return null;

  return (
    <div className="flex flex-wrap items-center gap-1">
      {/* Existing reactions */}
      {visible.map((r) => (
        <Tooltip key={r.emoji} delay={0}>
          <Tooltip.Trigger>
            <button
              disabled={disabled}
              onClick={() => onToggle(r.emoji)}
              className={`inline-flex items-center gap-1 rounded-full border px-1.5 py-0.5 text-[11px] transition-colors
                ${r.reacted
                  ? "border-accent/50 bg-accent/15 text-accent"
                  : "border-muted/15 bg-muted/5 text-foreground hover:border-accent/30 hover:bg-accent/10"
                }
                disabled:pointer-events-none disabled:opacity-50`}
            >
              <span className="text-xs">{EMOJI_MAP[r.emoji]}</span>
              <span>{r.count}</span>
            </button>
          </Tooltip.Trigger>
          <Tooltip.Content>{r.reacted ? t("removeReaction") : t("addReaction")}</Tooltip.Content>
        </Tooltip>
      ))}

      {/* Add reaction picker — always visible, subtle style */}
      {showPicker && onPickNew && (
        <Popover isOpen={pickerOpen} onOpenChange={setPickerOpen}>
          <Popover.Trigger>
            <button
              disabled={disabled}
              className="inline-flex size-5 items-center justify-center rounded-full border border-muted/15 text-muted/40 transition-colors hover:border-accent/30 hover:bg-accent/10 hover:text-accent disabled:pointer-events-none"
            >
              <Smile className="size-3" />
            </button>
          </Popover.Trigger>
          <Popover.Content placement="top" className="p-1.5">
            <div className="flex gap-0.5">
              {ALL_EMOJIS.map((emoji) => (
                <button
                  key={emoji}
                  onClick={() => handlePickEmoji(emoji)}
                  className="flex size-7 items-center justify-center rounded-md text-sm hover:bg-muted/10 transition-colors"
                >
                  {EMOJI_MAP[emoji]}
                </button>
              ))}
            </div>
          </Popover.Content>
        </Popover>
      )}
    </div>
  );
}
