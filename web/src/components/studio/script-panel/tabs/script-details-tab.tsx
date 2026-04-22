"use client";

/**
 * Script Details Tab
 * Displays script details with editable info, cover image, and content editor
 */

import { useState, useRef, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useLocale } from "next-intl";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  Button,
  ScrollShadow,
  TextArea,
  Tooltip,
  Input,
  Label,
  toast,
} from "@heroui/react";
import {
  Edit3,
  Eye,
  ChevronDown,
  ChevronUp,
  Calendar,
  User,
  Clock,
  Save,
  Loader2,
  History,
} from "lucide-react";
import Image from "@/components/ui/content-image";
import { projectService, ApiError, ERROR_CODES, getErrorFromException} from "@/lib/api";
import type {
  ScriptDetailDTO,
  ScriptStatus,
} from "@/lib/api/dto";
import { useWorkspace } from "@/components/providers/workspace-provider";
import { useDebouncedEntityChanges } from "@/lib/websocket";
import { openScript, clearActiveScript } from "@/lib/stores/script-store";
import { StatusBadge, CoverImageCard, FormSelect, ScriptDetailSkeleton } from "../common";
import { STATUS_OPTIONS } from "../constants";
import { formatDate } from "../utils";
import { VersionPanel } from "./version-panel";
import { EditingOverlay } from "../components";
import { useCollaboration } from "../hooks/use-collaboration";

interface ScriptDetailsTabProps {
  scriptId: string;
}

// Script Details Tab Component
export function ScriptDetailsTab({ scriptId }: ScriptDetailsTabProps) {
  const router = useRouter();
  const locale = useLocale();
  const { currentWorkspaceId } = useWorkspace();
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isHeaderCollapsed, setIsHeaderCollapsed] = useState(false);
  const [showVersionPanel, setShowVersionPanel] = useState(false);

  const [isEditing, setIsEditing] = useState(false);
  const [isEditingInfo, setIsEditingInfo] = useState(false);
  const [isReadOnlyMode, setIsReadOnlyMode] = useState(false);

  const [scriptData, setScriptData] = useState<ScriptDetailDTO | null>(null);

  const [editForm, setEditForm] = useState({
    title: "",
    synopsis: "",
    status: "DRAFT" as ScriptStatus,
  });
  const [editContent, setEditContent] = useState("");

  // Collaboration hook
  const {
    getEntityCollaborators,
    refreshEntityState,
    startEditing,
    stopEditing,
    focusEntity,
    blurEntity,
  } = useCollaboration(scriptId, "script");

  // Use refs to avoid effect re-running due to function reference changes
  const focusEntityRef = useRef(focusEntity);
  const blurEntityRef = useRef(blurEntity);
  useEffect(() => {
    focusEntityRef.current = focusEntity;
    blurEntityRef.current = blurEntity;
  });

  // Get collaboration state for script
  const collaborators = getEntityCollaborators("SCRIPT", scriptId);

  // Focus entity when component mounts
  useEffect(() => {
    focusEntityRef.current("SCRIPT", scriptId);
    return () => {
      blurEntityRef.current("SCRIPT", scriptId);
    };
  }, [scriptId]);

  const fetchScript = useCallback(async () => {
    if (!currentWorkspaceId || !scriptId) return;

    try {
      setIsLoading(true);
      setError(null);
      const data = await projectService.getScript( scriptId);
      setScriptData(data);
      setEditContent(data.content || "");
      setEditForm({
        title: data.title,
        synopsis: data.synopsis || "",
        status: data.status,
      });
    } catch (err) {
      const errorMessage = err instanceof ApiError ? err.message : "加载剧本详情失败";
      const isNotFoundError = err instanceof ApiError && (
        err.message.includes("不存在") ||
        String(err.code) === String(ERROR_CODES.RESOURCE_NOT_FOUND) ||
        String(err.code) === String(ERROR_CODES.NOT_FOUND)
      );

      // Only log unexpected errors to console
      if (!isNotFoundError) {
        console.error("Failed to fetch script:", err);
      }

      setError(errorMessage);
      toast.danger(errorMessage);

      // If script doesn't exist, clear active script and navigate to projects
      if (isNotFoundError) {
        clearActiveScript(currentWorkspaceId);
        router.replace(`/${locale}/workspace/projects`);
      }
    } finally {
      setIsLoading(false);
    }
  }, [currentWorkspaceId, scriptId, router, locale]);

  useEffect(() => {
    fetchScript();
  }, [fetchScript]);

  // Auto-refresh when the script is changed by other collaborators via WebSocket
  useDebouncedEntityChanges(
    "script",
    fetchScript,
    { entityId: scriptId },
    [fetchScript, scriptId]
  );

  // Update active script in localStorage when script data is loaded
  useEffect(() => {
    if (scriptData && currentWorkspaceId) {
      openScript(
        scriptData.id,
        scriptData.title,
        currentWorkspaceId,
        scriptData.coverUrl || undefined,
        scriptData.status
      );
    }
  }, [scriptData?.id, scriptData?.title, scriptData?.coverUrl, scriptData?.status, currentWorkspaceId]);

  const handleSaveInfo = async () => {
    if (!currentWorkspaceId || !scriptId || !scriptData) return;

    try {
      setIsSaving(true);
      const updatedData = await projectService.updateScript( scriptId, {
        title: editForm.title,
        synopsis: editForm.synopsis || undefined,
      });
      setScriptData(updatedData);
      setIsEditingInfo(false);
      stopEditing("SCRIPT", scriptId);
    } catch (err) {
      console.error("Failed to update script:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsSaving(false);
    }
  };

  const handleCancelEditInfo = () => {
    if (scriptData) {
      setEditForm({
        title: scriptData.title,
        synopsis: scriptData.synopsis || "",
        status: scriptData.status,
      });
    }
    setIsEditingInfo(false);
    stopEditing("SCRIPT", scriptId);
  };

  const handleSaveContent = async () => {
    if (!currentWorkspaceId || !scriptId) return;

    try {
      setIsSaving(true);
      const updatedData = await projectService.updateScript( scriptId, {
        content: editContent,
      });
      setScriptData(updatedData);
    } catch (err) {
      console.error("Failed to save content:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsSaving(false);
    }
  };

  const handleCoverUpload = async (file: File) => {
    if (!currentWorkspaceId || !scriptId) return;

    try {
      setIsUploading(true);

      const initResponse = await projectService.initAssetUpload( {
        name: file.name,
        fileName: file.name,
        mimeType: file.type,
        fileSize: file.size,
        scope: "SCRIPT",
        scriptId: scriptId,
        description: "剧本封面图",
      });

      const uploadResponse = await fetch(initResponse.uploadUrl, {
        method: initResponse.method,
        headers: initResponse.headers,
        body: file,
      });

      if (!uploadResponse.ok) {
        throw new Error(`Upload failed with status: ${uploadResponse.status}`);
      }

      await projectService.confirmAssetUpload( initResponse.assetId, {
        fileKey: initResponse.fileKey,
        actualFileSize: file.size,
      });

      const updatedScript = await projectService.updateScript( scriptId, {
        coverAssetId: initResponse.assetId,
      });

      setScriptData(updatedScript);
    } catch (err) {
      console.error("Failed to upload cover:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsUploading(false);
    }
  };

  const handleCoverDelete = async () => {
    if (!currentWorkspaceId || !scriptId || !scriptData) return;

    try {
      setIsSaving(true);
      const updatedData = await projectService.updateScript( scriptId, {
        coverAssetId: undefined,
      });
      setScriptData(updatedData);
    } catch (err) {
      console.error("Failed to remove cover:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return <ScriptDetailSkeleton />;
  }

  if (error || !scriptData) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 text-muted">
        <p>{error || "无法加载剧本数据"}</p>
        <Button variant="ghost" size="sm" onPress={fetchScript}>
          重新加载
        </Button>
      </div>
    );
  }

  // Determine if read-only
  const isReadOnly = collaborators.isLockedByOther || isReadOnlyMode;

  return (
    <div className="relative flex h-full flex-col gap-3">
      {/* Editing Overlay - only show when locked by other and NOT in read-only mode */}
      {collaborators.isLockedByOther && collaborators.editor && !isReadOnlyMode && (
        <EditingOverlay
          editor={collaborators.editor}
          lockedAt={collaborators.lockedAt}
          onRefresh={() => refreshEntityState("SCRIPT", scriptId)}
          onViewReadOnly={() => setIsReadOnlyMode(true)}
          onForceEdit={() => {
            startEditing("SCRIPT", scriptId);
            setIsReadOnlyMode(false);
          }}
        />
      )}
      {/* Script Info Card - Collapsible */}
      <div className="shrink-0 rounded-2xl bg-muted/5 transition-all duration-300">
        {/* Collapsed Header Bar */}
        <div
          className={`flex items-center justify-between gap-3 px-4 py-3 transition-all ${
            isHeaderCollapsed ? "" : "border-b border-muted/10"
          }`}
        >
          <div
            className="flex min-w-0 flex-1 cursor-pointer items-center gap-3"
            onClick={() => !isEditingInfo && setIsHeaderCollapsed(!isHeaderCollapsed)}
          >
            {isHeaderCollapsed && scriptData.coverUrl && (
              <div className="relative h-8 w-14 shrink-0 overflow-hidden rounded-lg">
                <Image src={scriptData.coverUrl} alt="封面" fill className="object-cover" sizes="120px" />
              </div>
            )}
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <h2 className="truncate text-sm font-semibold">{scriptData.title}</h2>
                <StatusBadge status={scriptData.status} />
              </div>
              {isHeaderCollapsed && scriptData.synopsis && (
                <p className="mt-0.5 truncate text-xs text-muted">{scriptData.synopsis}</p>
              )}
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-1">
            {!isHeaderCollapsed && (
              <>
                <Tooltip delay={0}>
                  <Button
                    variant="ghost"
                    size="sm"
                    isIconOnly
                    className="size-7"
                    onPress={() => setShowVersionPanel(true)}
                  >
                    <History className="size-3.5" />
                  </Button>
                  <Tooltip.Content>版本历史</Tooltip.Content>
                </Tooltip>
                <Tooltip delay={0}>
                  <Button
                    variant="ghost"
                    size="sm"
                    isIconOnly
                    className="size-7"
                    isDisabled={isReadOnly}
                    onPress={() => {
                      setIsEditingInfo(true);
                      startEditing("SCRIPT", scriptId);
                    }}
                  >
                    <Edit3 className="size-3.5" />
                  </Button>
                  <Tooltip.Content>编辑信息</Tooltip.Content>
                </Tooltip>
              </>
            )}
            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              className="size-7"
              isDisabled={isEditingInfo}
              onPress={() => setIsHeaderCollapsed(!isHeaderCollapsed)}
            >
              {isHeaderCollapsed ? <ChevronDown className="size-4" /> : <ChevronUp className="size-4" />}
            </Button>
          </div>
        </div>

        {/* Expandable Content */}
        {!isHeaderCollapsed && (
          <div className="flex gap-4 p-4 pt-3">
            <CoverImageCard
              coverUrl={scriptData.coverUrl}
              onUpload={handleCoverUpload}
              onDelete={handleCoverDelete}
              isEditing={isEditingInfo}
              isUploading={isUploading}
            />

            <div className="min-w-0 flex-1">
              {isEditingInfo ? (
                <div className="space-y-3">
                  <div className="flex gap-3">
                    <div className="flex-1 space-y-1">
                      <Label className="text-xs text-muted">标题</Label>
                      <Input
                        variant="secondary"
                        value={editForm.title}
                        onChange={(e) => setEditForm((prev) => ({ ...prev, title: e.target.value }))}
                        className="w-full"
                        placeholder="输入剧本标题"
                      />
                    </div>
                    <FormSelect
                      label="状态"
                      value={editForm.status}
                      onChange={(value) => setEditForm((prev) => ({ ...prev, status: value as ScriptStatus }))}
                      options={STATUS_OPTIONS.map((opt) => ({ value: opt.key, label: opt.label }))}
                      className="w-28"
                      isDisabled
                    />
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs text-muted">简介</Label>
                    <TextArea
                      variant="secondary"
                      value={editForm.synopsis}
                      onChange={(e) => setEditForm((prev) => ({ ...prev, synopsis: e.target.value }))}
                      className="w-full resize-none"
                      rows={2}
                      placeholder="输入剧本简介"
                    />
                  </div>
                  <div className="flex justify-end gap-2">
                    <Button variant="ghost" size="sm" onPress={handleCancelEditInfo} isDisabled={isSaving}>
                      取消
                    </Button>
                    <Button
                      variant="tertiary"
                      size="sm"
                      className="gap-1.5"
                      onPress={handleSaveInfo}
                      isDisabled={isSaving || !editForm.title.trim()}
                    >
                      {isSaving ? <Loader2 className="size-3.5 animate-spin" /> : <Save className="size-3.5" />}
                      保存
                    </Button>
                  </div>
                </div>
              ) : (
                <div className="flex h-full flex-col">
                  <p className="line-clamp-2 text-sm text-muted">{scriptData.synopsis || "暂无简介"}</p>
                  <div className="mt-auto flex flex-wrap items-center gap-x-4 gap-y-1.5 pt-3 text-xs text-muted">
                    <span className="inline-flex items-center gap-1.5">
                      <User className="size-3 opacity-60" />
                      {scriptData.createdByNickname || scriptData.createdByUsername}
                    </span>
                    <span className="inline-flex items-center gap-1.5">
                      <Calendar className="size-3 opacity-60" />
                      {formatDate(scriptData.createdAt)}
                    </span>
                    <span className="inline-flex items-center gap-1.5">
                      <Clock className="size-3 opacity-60" />
                      {formatDate(scriptData.updatedAt)}
                    </span>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Content Editor */}
      <div className="flex min-h-0 flex-1 flex-col rounded-2xl bg-muted/5">
        <div className="flex shrink-0 items-center justify-between px-4 py-2.5">
          <span className="text-xs font-medium text-muted">剧本正文</span>
          <div className="flex items-center gap-2">
            {isEditing && (
              <Button
                variant="tertiary"
                size="sm"
                className="gap-1.5 text-xs"
                onPress={handleSaveContent}
                isDisabled={isSaving}
              >
                {isSaving ? <Loader2 className="size-3.5 animate-spin" /> : <Save className="size-3.5" />}
                保存
              </Button>
            )}
            <Button
              variant="ghost"
              size="sm"
              className="gap-1.5 text-xs"
              isDisabled={isReadOnly && !isEditing}
              onPress={() => {
                if (isEditing) {
                  setIsEditing(false);
                  stopEditing("SCRIPT", scriptId);
                } else {
                  setIsEditing(true);
                  startEditing("SCRIPT", scriptId);
                }
              }}
            >
              {isEditing ? <Eye className="size-3.5" /> : <Edit3 className="size-3.5" />}
              {isEditing ? "预览" : "编辑"}
            </Button>
          </div>
        </div>

        <ScrollShadow className="min-h-0 flex-1 px-4 pb-4" hideScrollBar>
          {isEditing ? (
            <TextArea
              value={editContent}
              onChange={(e) => setEditContent(e.target.value)}
              className="min-h-full w-full resize-none border-none bg-transparent font-mono text-sm shadow-none focus:ring-0"
              placeholder="在此编写剧本内容..."
            />
          ) : (
            <div className="prose prose-sm dark:prose-invert max-w-none text-sm leading-relaxed">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {editContent || "*暂无内容*"}
              </ReactMarkdown>
            </div>
          )}
        </ScrollShadow>
      </div>

      {/* Version Panel Overlay */}
      {showVersionPanel && (
        <div className="absolute inset-0 z-20 overflow-hidden rounded-2xl bg-background/95 backdrop-blur-sm">
          <VersionPanel
            scriptId={scriptId}
            onRestore={fetchScript}
            onClose={() => setShowVersionPanel(false)}
          />
        </div>
      )}
    </div>
  );
}

export default ScriptDetailsTab;
