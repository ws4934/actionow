"use client";

/**
 * CommentInput
 * Textarea with @user and #entity mention detection and popups.
 * Tracks mentions (without offsets) and computes final positions on submit.
 */

import { useState, useRef, useCallback, useEffect, useMemo } from "react";
import { Button, Avatar, Spinner, toast} from "@heroui/react";
import { SendHorizontal, Paperclip } from "lucide-react";
import { useTranslations, useLocale} from "next-intl";
import { useCommentFileUpload } from "./hooks/use-comment-file-upload";
import { AttachmentPreviewBar } from "@/components/studio/agent-chat/components/attachment-preview-bar";
import { commentService } from "@/lib/api/services/comment.service";
import { workspaceService } from "@/lib/api/services/workspace.service";
import { projectService } from "@/lib/api/services/project.service";
import type {
  CommentMentionDTO,
  CommentResponseDTO,
  CommentTargetType,
  MentionType,
  CreateCommentDTO,
} from "@/lib/api/dto/comment.dto";
import type { WorkspaceMemberDTO } from "@/lib/api/dto/workspace.dto";
import type { CharacterListDTO, SceneListDTO, PropListDTO } from "@/lib/api/dto/project.dto";
import { getErrorFromException } from "@/lib/api";

// ── Local types ──────────────────────────────────────────────────────────────

interface MentionEntry {
  type: MentionType;
  id: string;
  name: string;
}

interface TriggerInfo {
  /** "user" for @ trigger, "entity" for # trigger */
  type: "user" | "entity";
  /** Absolute index of @ or # in the value string */
  triggerIdx: number;
  /** Text typed after the trigger char (the search query) */
  query: string;
}

interface EntityStore {
  CHARACTER: CharacterListDTO[];
  SCENE: SceneListDTO[];
  PROP: PropListDTO[];
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Detect active mention trigger from text before cursor.
 * Matches @ or # that is at the start or preceded by whitespace.
 */
function detectTrigger(value: string, cursorPos: number): TriggerInfo | null {
  const locale = useLocale();
  const before = value.slice(0, cursorPos);
  const match = before.match(/(^|\s)(@|#)([^\s@#]*)$/);
  if (!match) return null;

  const triggerChar = match[2];
  const query = match[3];
  const triggerIdx = before.length - query.length - 1; // position of @ or #

  return {
    type: triggerChar === "@" ? "user" : "entity",
    triggerIdx,
    query,
  };
}

/**
 * Compute mention DTOs with correct offsets from final content string.
 * Scans content for `@name` / `#name` patterns (word-boundary aware).
 */
function computeMentionPositions(
  content: string,
  entries: MentionEntry[]
): CommentMentionDTO[] {
  const result: CommentMentionDTO[] = [];
  const usedPositions = new Set<number>();

  for (const entry of entries) {
    const triggerChar = entry.type === "USER" ? "@" : "#";
    const needle = `${triggerChar}${entry.name}`;
    let searchFrom = 0;

    while (searchFrom < content.length) {
      const idx = content.indexOf(needle, searchFrom);
      if (idx === -1) break;

      if (!usedPositions.has(idx)) {
        const boundaryOk = idx === 0 || /\s/.test(content[idx - 1]);
        if (boundaryOk) {
          usedPositions.add(idx);
          result.push({
            type: entry.type,
            id: entry.id,
            name: entry.name,
            offset: idx,
            length: needle.length,
          });
          break;
        }
      }
      searchFrom = idx + 1;
    }
  }

  return result;
}

// ── Component ────────────────────────────────────────────────────────────────

export interface CommentInputProps {
  targetType: CommentTargetType;
  targetId: string;
  scriptId: string;
  /** Set when this input is for a reply */
  parentId?: string;
  /** Callback with created comment */
  onCreated?: (comment: CommentResponseDTO) => void;
  placeholder?: string;
  autoFocus?: boolean;
  /** Shows a Cancel button and calls this when clicked */
  onCancel?: () => void;
}

export function CommentInput({
  targetType,
  targetId,
  scriptId,
  parentId,
  onCreated,
  placeholder,
  autoFocus = false,
  onCancel,
}: CommentInputProps) {
  const locale = useLocale();
  const t = useTranslations("workspace.collab.input");
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // File upload
  const {
    attachments: fileAttachments,
    addFiles,
    removeAttachment,
    clearAttachments,
    toCommentAttachments,
    hasUploading,
    acceptString,
  } = useCommentFileUpload(scriptId);

  // Input state
  const [value, setValue] = useState("");
  const [mentionEntries, setMentionEntries] = useState<MentionEntry[]>([]);
  const [triggerInfo, setTriggerInfo] = useState<TriggerInfo | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Member data for @ mentions
  const [members, setMembers] = useState<WorkspaceMemberDTO[]>([]);
  const [membersLoaded, setMembersLoaded] = useState(false);
  const [membersLoading, setMembersLoading] = useState(false);

  // Entity data for # mentions
  const [entities, setEntities] = useState<EntityStore>({ CHARACTER: [], SCENE: [], PROP: [] });
  const [entitiesLoaded, setEntitiesLoaded] = useState(false);
  const [entitiesLoading, setEntitiesLoading] = useState(false);
  const [entityTab, setEntityTab] = useState<keyof EntityStore>("CHARACTER");

  // Load workspace members when @ is first triggered
  useEffect(() => {
    if (triggerInfo?.type !== "user" || membersLoaded || membersLoading) return;
    let cancelled = false;
    setMembersLoading(true);
    workspaceService
      .getMembers({ size: 100 })
      .then((res) => {
        if (!cancelled) {
          setMembers(res.records ?? []);
          setMembersLoaded(true);
        }
      })
      .catch(() => {
        if (!cancelled) setMembersLoaded(true);
      })
      .finally(() => {
        if (!cancelled) setMembersLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [triggerInfo?.type, membersLoaded, membersLoading]);

  // Load script entities when # is first triggered
  useEffect(() => {
    if (triggerInfo?.type !== "entity" || entitiesLoaded || entitiesLoading) return;
    let cancelled = false;
    setEntitiesLoading(true);
    Promise.all([
      projectService.getCharactersAvailable(scriptId),
      projectService.getScenesAvailable(scriptId),
      projectService.getPropsAvailable(scriptId),
    ])
      .then(([chars, scenes, props]) => {
        if (!cancelled) {
          setEntities({ CHARACTER: chars, SCENE: scenes, PROP: props });
          setEntitiesLoaded(true);
        }
      })
      .catch(() => {
        if (!cancelled) setEntitiesLoaded(true);
      })
      .finally(() => {
        if (!cancelled) setEntitiesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [triggerInfo?.type, entitiesLoaded, entitiesLoading, scriptId]);

  // Filtered members based on trigger query
  const filteredMembers = useMemo(() => {
    if (triggerInfo?.type !== "user") return [];
    const q = triggerInfo.query.toLowerCase();
    return members
      .filter((m) => {
        const display = (m.nickname ?? m.username).toLowerCase();
        return display.includes(q) || m.username.toLowerCase().includes(q);
      })
      .slice(0, 8);
  }, [members, triggerInfo]);

  // Filtered entities based on trigger query and active tab
  const filteredEntities = useMemo(() => {
    if (triggerInfo?.type !== "entity") return [];
    const q = triggerInfo.query.toLowerCase();
    return (entities[entityTab] as Array<{ id: string; name: string }>)
      .filter((e) => e.name.toLowerCase().includes(q))
      .slice(0, 8);
  }, [entities, entityTab, triggerInfo]);

  // Whether the popup should be visible
  const showPopup =
    triggerInfo !== null &&
    (triggerInfo.type === "user" || triggerInfo.type === "entity");

  // ── Handlers ──────────────────────────────────────────────────────────────

  const handleChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const newValue = e.target.value;
    setValue(newValue);
    const cursor = e.target.selectionStart ?? newValue.length;
    setTriggerInfo(detectTrigger(newValue, cursor));
  }, []);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === "Escape") {
        setTriggerInfo(null);
        return;
      }
      if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
        e.preventDefault();
        doSubmit();
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [value, mentionEntries]
  );

  /** Replace the trigger + query in the textarea with the selected mention text. */
  const insertMention = useCallback(
    (entry: MentionEntry) => {
      if (!triggerInfo) return;
      const triggerChar = entry.type === "USER" ? "@" : "#";
      const insertText = `${triggerChar}${entry.name} `;

      const before = value.slice(0, triggerInfo.triggerIdx);
      const afterQuery = value.slice(triggerInfo.triggerIdx + 1 + triggerInfo.query.length);
      const newValue = before + insertText + afterQuery;

      setValue(newValue);
      setMentionEntries((prev) =>
        prev.some((m) => m.id === entry.id) ? prev : [...prev, entry]
      );
      setTriggerInfo(null);

      // Restore cursor after inserted text
      setTimeout(() => {
        const el = textareaRef.current;
        if (el) {
          const cursorPos = before.length + insertText.length;
          el.focus();
          el.setSelectionRange(cursorPos, cursorPos);
        }
      }, 0);
    },
    [value, triggerInfo]
  );

  const handleSelectMember = useCallback(
    (member: WorkspaceMemberDTO) => {
      insertMention({
        type: "USER",
        id: member.userId,
        name: member.nickname ?? member.username,
      });
    },
    [insertMention]
  );

  const handleSelectEntity = useCallback(
    (entity: { id: string; name: string }) => {
      insertMention({ type: entityTab, id: entity.id, name: entity.name });
    },
    [insertMention, entityTab]
  );

  const doSubmit = useCallback(async () => {
    const trimmed = value.trim();
    if (!trimmed || submitting || hasUploading) return;

    setSubmitting(true);
    try {
      const mentions = computeMentionPositions(trimmed, mentionEntries);
      const commentAttachments = toCommentAttachments();
      const payload: CreateCommentDTO = {
        targetType,
        targetId,
        scriptId,
        content: trimmed,
        mentions: mentions.length > 0 ? mentions : undefined,
        attachments: commentAttachments.length > 0 ? commentAttachments : undefined,
      };
      if (parentId) payload.parentId = parentId;

      const comment = await commentService.createComment(payload);
      setValue("");
      setMentionEntries([]);
      setTriggerInfo(null);
      clearAttachments();
      onCreated?.(comment);
    } catch (err) {
      console.error("Failed to create comment:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setSubmitting(false);
    }
  }, [value, submitting, hasUploading, mentionEntries, targetType, targetId, scriptId, parentId, onCreated, toCommentAttachments, clearAttachments]);

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="relative">
      {/* ── Mention popup (floats above textarea) ── */}
      {showPopup && (
        <div className="absolute bottom-full left-0 right-0 z-10 mb-1 max-h-56 overflow-hidden rounded-xl border border-muted/20 bg-overlay shadow-overlay">
          {triggerInfo!.type === "user" ? (
            /* User mention list */
            <div className="overflow-y-auto max-h-56">
              {membersLoading ? (
                <div className="flex justify-center py-4">
                  <Spinner size="sm" />
                </div>
              ) : filteredMembers.length === 0 ? (
                <p className="px-3 py-4 text-center text-xs text-muted">
                  {membersLoaded ? t("membersNotFound") : t("membersLoading")}
                </p>
              ) : (
                filteredMembers.map((m) => (
                  <button
                    key={m.userId}
                    className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-muted/10 transition-colors"
                    onMouseDown={(e) => {
                      e.preventDefault();
                      handleSelectMember(m);
                    }}
                  >
                    <Avatar size="sm" className="shrink-0">
                      {m.avatar ? (
                        <Avatar.Image src={m.avatar} alt={m.nickname ?? m.username} />
                      ) : null}
                      <Avatar.Fallback>
                        {(m.nickname ?? m.username).charAt(0).toUpperCase()}
                      </Avatar.Fallback>
                    </Avatar>
                    <span className="flex-1 truncate">
                      {m.nickname ?? m.username}
                    </span>
                    {m.nickname && (
                      <span className="text-xs text-muted">@{m.username}</span>
                    )}
                  </button>
                ))
              )}
            </div>
          ) : (
            /* Entity mention list with tabs */
            <div>
              <div className="flex border-b border-muted/10 px-2 pt-1">
                {(["CHARACTER", "SCENE", "PROP"] as const).map((tab) => (
                  <button
                    key={tab}
                    onMouseDown={(e) => {
                      e.preventDefault();
                      setEntityTab(tab);
                    }}
                    className={`px-3 py-1.5 text-xs transition-colors ${
                      entityTab === tab
                        ? "border-b-2 border-accent font-medium text-accent"
                        : "text-muted hover:text-foreground"
                    }`}
                  >
                    {tab === "CHARACTER" ? t("tabCharacter") : tab === "SCENE" ? t("tabScene") : t("tabProp")}
                  </button>
                ))}
              </div>
              <div className="max-h-40 overflow-y-auto">
                {entitiesLoading ? (
                  <div className="flex justify-center py-4">
                    <Spinner size="sm" />
                  </div>
                ) : filteredEntities.length === 0 ? (
                  <p className="px-3 py-4 text-center text-xs text-muted">
                    {entitiesLoaded ? t("entitiesNotFound") : t("entitiesLoading")}
                  </p>
                ) : (
                  filteredEntities.map((e) => (
                    <button
                      key={e.id}
                      className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-muted/10 transition-colors"
                      onMouseDown={(ev) => {
                        ev.preventDefault();
                        handleSelectEntity(e);
                      }}
                    >
                      <span className="truncate">{e.name}</span>
                    </button>
                  ))
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* ── Textarea ── */}
      <textarea
        ref={textareaRef}
        value={value}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        placeholder={placeholder ?? t("defaultPlaceholder")}
        autoFocus={autoFocus}
        rows={2}
        className="w-full resize-none rounded-lg bg-muted/10 px-3 py-2 text-sm placeholder:text-muted/50 outline-none focus:bg-muted/15 transition-colors"
      />

      {/* ── Attachment preview ── */}
      {fileAttachments.length > 0 && (
        <AttachmentPreviewBar
          attachments={fileAttachments}
          onRemove={removeAttachment}
        />
      )}

      {/* ── Hidden file input ── */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept={acceptString}
        className="hidden"
        onChange={(e) => {
          const files = e.target.files;
          if (files && files.length > 0) {
            addFiles(Array.from(files));
          }
          e.target.value = "";
        }}
      />

      {/* ── Footer ── */}
      <div className="mt-1 flex items-center justify-between gap-2">
        <div className="flex items-center gap-1.5">
          <Button
            isIconOnly
            size="sm"
            variant="ghost"
            className="size-6 min-w-0"
            onPress={() => fileInputRef.current?.click()}
            aria-label={t("attach")}
          >
            <Paperclip className="size-3 text-muted" />
          </Button>
          <p className="text-[10px] text-muted/50">
            {t("hint")}
          </p>
        </div>
        <div className="flex items-center gap-1.5">
          {onCancel && (
            <Button
              variant="ghost"
              size="sm"
              className="h-7 text-xs"
              onPress={onCancel}
            >
              {t("cancel")}
            </Button>
          )}
          <Button
            size="sm"
            className="h-7 gap-1 text-xs"
            isDisabled={!value.trim() || submitting || hasUploading}
            isPending={submitting}
            onPress={() => { doSubmit(); }}
          >
            {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <SendHorizontal className="size-3" />}{t("send")}</>)}
          </Button>
        </div>
      </div>
    </div>
  );
}
