"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Music } from "lucide-react";
import type { MentionItem, MentionPromptEditorProps } from "./types";
import Image from "@/components/ui/content-image";

// ── DOM helpers ──

/** Create a contentEditable=false mention chip DOM element */
function createMentionChip(name: string, item: MentionItem | undefined): HTMLSpanElement {
  const chip = document.createElement("span");
  chip.contentEditable = "false";
  chip.dataset.mention = name;
  chip.setAttribute(
    "style",
    "display:inline-flex;align-items:center;gap:3px;border-radius:4px;padding:1px 5px;" +
      "background:oklch(0.65 0.15 250/0.15);color:oklch(0.65 0.15 250);font-size:12px;" +
      "font-weight:500;vertical-align:text-bottom;user-select:none;cursor:default;line-height:1.4;" +
      "margin-bottom:-1px;",
  );

  if (item) {
    const thumbType = item.thumbnailType ?? inferThumbnailType(item);
    const thumbUrl = item.thumbnailUrl ?? inferThumbnailUrl(item);

    if (thumbType === "image" && thumbUrl) {
      const img = document.createElement("img");
      img.src = thumbUrl;
      img.alt = name;
      img.setAttribute(
        "style",
        "width:18px;height:18px;border-radius:3px;object-fit:cover;vertical-align:middle;",
      );
      chip.appendChild(img);
    } else if (thumbType === "video" && thumbUrl) {
      const vid = document.createElement("video");
      vid.src = thumbUrl;
      vid.muted = true;
      vid.preload = "metadata";
      vid.setAttribute(
        "style",
        "width:18px;height:18px;border-radius:3px;object-fit:cover;vertical-align:middle;",
      );
      chip.appendChild(vid);
    } else {
      const fallback = item.iconFallback ?? (item.kind === "file" && item.mediaKind === "AUDIO" ? "\u266B" : name[0] ?? "?");
      const icon = document.createElement("span");
      icon.setAttribute(
        "style",
        "display:inline-flex;width:18px;height:18px;align-items:center;justify-content:center;" +
          "border-radius:3px;background:oklch(0.65 0.15 250/0.2);font-size:10px;vertical-align:middle;",
      );
      icon.textContent = fallback;
      chip.appendChild(icon);
    }
  }

  const nameSpan = document.createElement("span");
  nameSpan.textContent = name;
  chip.appendChild(nameSpan);
  return chip;
}

function inferThumbnailType(item: MentionItem): "image" | "video" | "icon" {
  if (item.kind === "file") {
    if (item.mediaKind === "IMAGE") return "image";
    if (item.mediaKind === "VIDEO") return "video";
    return "icon";
  }
  // entity — default to image thumbnail
  return item.thumbnailUrl ? "image" : "icon";
}

function inferThumbnailUrl(item: MentionItem): string | null {
  if (item.thumbnailUrl) return item.thumbnailUrl;
  if (item.kind === "file" && item.fileUrl) return item.fileUrl;
  return null;
}

/** Serialize contentEditable DOM → plain text (chips become @name) */
function serializeEditor(el: HTMLElement): string {
  let text = "";
  for (const node of Array.from(el.childNodes)) {
    if (node.nodeType === Node.TEXT_NODE) {
      text += node.textContent ?? "";
    } else if (node instanceof HTMLElement) {
      if (node.dataset.mention) {
        text += `@${node.dataset.mention}`;
      } else if (node.tagName === "BR") {
        text += "\n";
      } else if (node.tagName === "DIV") {
        if (text.length > 0 && !text.endsWith("\n")) text += "\n";
        text += serializeEditor(node);
      } else {
        text += serializeEditor(node);
      }
    }
  }
  return text;
}

/** Rebuild the entire DOM from plain text (replacing @name tokens with chip elements) */
function buildEditorDOM(
  el: HTMLElement,
  text: string,
  itemMap: Map<string, MentionItem>,
) {
  el.innerHTML = "";
  if (!text) return;
  if (itemMap.size === 0) {
    el.appendChild(document.createTextNode(text));
    return;
  }
  const names = Array.from(itemMap.keys());
  const escaped = names.map((n) => n.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
  const regex = new RegExp(`@(${escaped.join("|")})(?=\\s|$)`, "g");
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = regex.exec(text)) !== null) {
    if (m.index > last) el.appendChild(document.createTextNode(text.slice(last, m.index)));
    el.appendChild(createMentionChip(m[1], itemMap.get(m[1])));
    last = m.index + m[0].length;
  }
  if (last < text.length) el.appendChild(document.createTextNode(text.slice(last)));
}

// ── Default dropdown item renderer ──

function DefaultDropdownItem({ item, onSelect }: { item: MentionItem; onSelect: (item: MentionItem) => void }) {
  const thumbType = item.thumbnailType ?? inferThumbnailType(item);
  const thumbUrl = item.thumbnailUrl ?? inferThumbnailUrl(item);

  return (
    <button
      type="button"
      className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm transition-colors hover:bg-muted/10"
      onMouseDown={(e) => { e.preventDefault(); onSelect(item); }}
    >
      {thumbType === "image" && thumbUrl ? (
        <Image src={thumbUrl} alt="" width={32} height={32} className="size-8 rounded object-cover" />
      ) : thumbType === "video" && thumbUrl ? (
        <video src={thumbUrl} className="size-8 rounded object-cover" muted preload="metadata" />
      ) : (
        <div className="flex size-8 items-center justify-center rounded bg-surface-2">
          {item.kind === "file" && item.mediaKind === "AUDIO" ? (
            <Music className="size-4 text-muted" />
          ) : (
            <span className="text-xs font-medium text-muted">
              {item.iconFallback ?? item.name[0] ?? "?"}
            </span>
          )}
        </div>
      )}
      <span className="font-medium text-foreground">{item.name}</span>
    </button>
  );
}

// ── Hover preview ──

function HoverPreview({ item, rect }: { item: MentionItem; rect: DOMRect }) {
  const thumbType = item.thumbnailType ?? inferThumbnailType(item);
  const thumbUrl = item.thumbnailUrl ?? inferThumbnailUrl(item);

  let subtitle = "";
  if (item.kind === "file") {
    subtitle = item.mimeType || item.mediaKind;
  } else {
    subtitle = item.category;
  }

  return createPortal(
    <div
      style={{
        position: "fixed",
        top: rect.top - 8,
        left: rect.left + rect.width / 2,
        transform: "translate(-50%, -100%)",
        zIndex: 9999,
      }}
      className="rounded-lg border border-muted/20 bg-overlay p-2 shadow-overlay"
    >
      <div className="flex items-center gap-2">
        {thumbType === "image" && thumbUrl ? (
          <Image src={thumbUrl} alt="" width={64} height={64} className="size-16 rounded object-cover" />
        ) : thumbType === "video" && thumbUrl ? (
          <video src={thumbUrl} className="size-16 rounded object-cover" muted preload="metadata" />
        ) : (
          <div className="flex size-16 items-center justify-center rounded bg-surface-2">
            <Music className="size-6 text-muted" />
          </div>
        )}
        <div className="text-xs">
          <p className="font-medium text-foreground">{item.name}</p>
          <p className="text-muted">{subtitle}</p>
        </div>
      </div>
    </div>,
    document.body,
  );
}

// ── Main Component ──

export function MentionPromptEditor({
  value,
  onChange,
  onSubmit,
  canSubmit,
  placeholder = "",
  disabled = false,
  mentionItems,
  mentionItemMap,
  renderDropdown,
  onMentionTrigger,
  onPasteFiles,
  className,
  style,
}: MentionPromptEditorProps) {
  const editorRef = useRef<HTMLDivElement>(null);
  const internalTextRef = useRef("");
  const isComposingRef = useRef(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const [dropdownQuery, setDropdownQuery] = useState("");
  const [dropdownPos, setDropdownPos] = useState<{ top: number; left: number } | null>(null);
  const triggerCtxRef = useRef<{ textNode: Text; startOffset: number } | null>(null);
  const [hoverInfo, setHoverInfo] = useState<{ name: string; rect: DOMRect } | null>(null);

  // Build DOM when value changes externally
  useEffect(() => {
    const el = editorRef.current;
    if (!el) return;
    if (value !== internalTextRef.current) {
      buildEditorDOM(el, value, mentionItemMap);
      internalTextRef.current = value;
    }
  }, [value, mentionItemMap]);

  // Detect @ trigger near cursor
  const detectTrigger = useCallback(() => {
    const sel = window.getSelection();
    if (!sel || sel.rangeCount === 0 || !sel.isCollapsed) {
      setShowDropdown(false);
      return;
    }
    const node = sel.getRangeAt(0).startContainer;
    const offset = sel.getRangeAt(0).startOffset;
    if (node.nodeType !== Node.TEXT_NODE) {
      setShowDropdown(false);
      return;
    }
    const textBefore = (node.textContent ?? "").slice(0, offset);
    const match = textBefore.match(/(^|\s)@([^\s@]*)$/);
    if (!match) {
      setShowDropdown(false);
      return;
    }
    triggerCtxRef.current = {
      textNode: node as Text,
      startOffset: offset - match[2].length - 1,
    };
    const query = match[2];
    setDropdownQuery(query);
    onMentionTrigger?.(query);

    const range = document.createRange();
    range.setStart(node, offset - match[2].length - 1);
    range.setEnd(node, offset);
    const rect = range.getBoundingClientRect();
    setDropdownPos({ top: rect.top, left: rect.left });
    setShowDropdown(true);
  }, [onMentionTrigger]);

  // Input handler
  const handleInput = useCallback(() => {
    if (isComposingRef.current) return;
    const el = editorRef.current;
    if (!el) return;
    const text = serializeEditor(el);
    internalTextRef.current = text;
    onChange(text);
    requestAnimationFrame(detectTrigger);
  }, [onChange, detectTrigger]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "Escape" && showDropdown) {
        e.preventDefault();
        setShowDropdown(false);
        return;
      }
      if (e.key === "Enter" && !e.shiftKey && !showDropdown) {
        e.preventDefault();
        if (canSubmit) onSubmit();
      }
    },
    [showDropdown, canSubmit, onSubmit],
  );

  const handlePaste = useCallback(
    (e: React.ClipboardEvent) => {
      // Check for file paste
      if (onPasteFiles && e.clipboardData.files.length > 0) {
        e.preventDefault();
        onPasteFiles(Array.from(e.clipboardData.files));
        return;
      }
      e.preventDefault();
      const text = e.clipboardData.getData("text/plain");
      document.execCommand("insertText", false, text);
    },
    [onPasteFiles],
  );

  const handleCompositionStart = useCallback(() => {
    isComposingRef.current = true;
  }, []);
  const handleCompositionEnd = useCallback(() => {
    isComposingRef.current = false;
    handleInput();
  }, [handleInput]);

  // Insert mention chip
  const insertMention = useCallback(
    (item: MentionItem) => {
      const ctx = triggerCtxRef.current;
      if (!ctx) return;
      const { textNode, startOffset } = ctx;
      const fullText = textNode.textContent ?? "";
      const before = fullText.slice(0, startOffset);
      const after = fullText.slice(startOffset + 1 + dropdownQuery.length);

      const parent = textNode.parentNode!;
      if (before) parent.insertBefore(document.createTextNode(before), textNode);
      parent.insertBefore(createMentionChip(item.name, item), textNode);
      const afterNode = document.createTextNode("\u00A0" + after);
      parent.insertBefore(afterNode, textNode);
      parent.removeChild(textNode);

      const sel = window.getSelection()!;
      const range = document.createRange();
      range.setStart(afterNode, 1);
      range.collapse(true);
      sel.removeAllRanges();
      sel.addRange(range);

      setShowDropdown(false);
      triggerCtxRef.current = null;

      const newText = serializeEditor(editorRef.current!);
      internalTextRef.current = newText;
      onChange(newText);
    },
    [dropdownQuery, onChange],
  );

  // Filter dropdown items
  const filteredMentions = useMemo(() => {
    if (!showDropdown) return [];
    const q = dropdownQuery.toLowerCase();
    return mentionItems.filter((a) => a.name.toLowerCase().includes(q));
  }, [showDropdown, dropdownQuery, mentionItems]);

  // Close dropdown when nothing matches
  useEffect(() => {
    if (showDropdown && filteredMentions.length === 0 && dropdownQuery.length > 4) {
      setShowDropdown(false);
    }
  }, [showDropdown, filteredMentions.length, dropdownQuery]);

  // Hover preview (event delegation)
  const handleMouseOver = useCallback((e: React.MouseEvent) => {
    const chip = (e.target as HTMLElement).closest("[data-mention]") as HTMLElement | null;
    if (chip) setHoverInfo({ name: chip.dataset.mention!, rect: chip.getBoundingClientRect() });
  }, []);
  const handleMouseOut = useCallback((e: React.MouseEvent) => {
    const related = e.relatedTarget as HTMLElement | null;
    if (!related || !related.closest("[data-mention]")) setHoverInfo(null);
  }, []);

  const hoveredItem = hoverInfo ? mentionItemMap.get(hoverInfo.name) : null;

  return (
    <>
      {/* Editor container */}
      <div
        ref={editorRef}
        contentEditable={!disabled}
        onInput={handleInput}
        onKeyDown={handleKeyDown}
        onPaste={handlePaste}
        onCompositionStart={handleCompositionStart}
        onCompositionEnd={handleCompositionEnd}
        onClick={detectTrigger}
        onMouseOver={handleMouseOver}
        onMouseOut={handleMouseOut}
        data-placeholder={placeholder}
        role="textbox"
        aria-multiline
        suppressContentEditableWarning
        className={className}
        style={{
          minHeight: 80,
          maxHeight: 200,
          overflowY: "auto",
          padding: "12px",
          fontSize: 14,
          lineHeight: 1.625,
          whiteSpace: "pre-wrap",
          wordBreak: "break-word",
          outline: "none",
          ...style,
        }}
      />

      {/* Placeholder styles */}
      <style>{`
        [data-placeholder]:empty::before {
          content: attr(data-placeholder);
          color: var(--color-muted);
          pointer-events: none;
        }
      `}</style>

      {/* Mention dropdown */}
      {showDropdown && dropdownPos && (
        renderDropdown ? (
          renderDropdown({
            query: dropdownQuery,
            position: dropdownPos,
            onSelect: insertMention,
            onClose: () => setShowDropdown(false),
          })
        ) : (
          filteredMentions.length > 0 && createPortal(
            <div
              style={{
                position: "fixed",
                top: dropdownPos.top - 4,
                left: dropdownPos.left,
                transform: "translateY(-100%)",
                zIndex: 9999,
              }}
              className="max-h-48 w-64 overflow-y-auto rounded-xl border border-muted/20 bg-overlay shadow-overlay"
            >
              {filteredMentions.map((item) => (
                <DefaultDropdownItem key={item.name} item={item} onSelect={insertMention} />
              ))}
            </div>,
            document.body,
          )
        )
      )}

      {/* Hover preview */}
      {hoverInfo && hoveredItem && (
        <HoverPreview item={hoveredItem} rect={hoverInfo.rect} />
      )}
    </>
  );
}
