import type { ReactNode } from "react";

// ── Base mention item ──
interface MentionItemBase {
  /** Display name used for @reference (e.g. "图片1", "Alice") */
  name: string;
  /** Thumbnail URL for chip/preview rendering */
  thumbnailUrl?: string | null;
  /** How to render the thumbnail */
  thumbnailType?: "image" | "video" | "icon";
  /** Fallback character when no thumbnail (e.g. "♫", first letter of name) */
  iconFallback?: string;
}

/** File asset mention (used in Inspiration & AI Generation panels) */
export interface FileMentionItem extends MentionItemBase {
  kind: "file";
  mediaKind: "IMAGE" | "VIDEO" | "AUDIO";
  /** Full file URL for hover preview */
  fileUrl?: string;
  mimeType?: string | null;
}

/** Entity mention (used in Agent Chat — character, scene, prop, etc.) */
export interface EntityMentionItem extends MentionItemBase {
  kind: "entity";
  entityId: string;
  category: string;
}

export type MentionItem = FileMentionItem | EntityMentionItem;

/** Props passed to a custom dropdown renderer */
export interface DropdownRenderProps {
  query: string;
  position: { top: number; left: number };
  onSelect: (item: MentionItem) => void;
  onClose: () => void;
}

/** Props for the MentionPromptEditor component */
export interface MentionPromptEditorProps {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  canSubmit: boolean;
  placeholder?: string;
  disabled?: boolean;
  /** Items available for @-mention selection */
  mentionItems: MentionItem[];
  /** Pre-built map for chip rendering (name → item) */
  mentionItemMap: Map<string, MentionItem>;
  /** Custom dropdown renderer — if omitted, a default list dropdown is used */
  renderDropdown?: (props: DropdownRenderProps) => ReactNode;
  /** Callback when @ trigger is detected (for external search state) */
  onMentionTrigger?: (query: string) => void;
  /** Intercept pasted files */
  onPasteFiles?: (files: File[]) => void;
  className?: string;
  style?: React.CSSProperties;
}
