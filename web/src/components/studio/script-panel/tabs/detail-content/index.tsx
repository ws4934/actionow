"use client";

/**
 * Entity Detail Content Components
 * Individual detail views for each entity type
 */

import { useState, useCallback, useEffect } from "react";
import { Button, ScrollShadow, TextArea, Input, Label, Tooltip, toast} from "@heroui/react";
import ReactMarkdown from "react-markdown";
import { AssetDetailContent } from "./asset-detail-content";
import remarkGfm from "remark-gfm";
import {
  Calendar,
  User,
  Clock,
  Info,
  Camera,
  MessageSquare,
  Users,
  MapPin,
  Package,
  Sparkles,
  Edit3,
  Eye,
  ChevronDown,
  ChevronUp,
  Save,
  Loader2,
  History,
  RefreshCw,
} from "lucide-react";
import Image from "@/components/ui/content-image";
import { projectService, getErrorFromException} from "@/lib/api";
import type {
  EpisodeDetailDTO,
  StoryboardDetailDTO,
  CharacterDetailDTO,
  SceneDetailDTO,
  PropDetailDTO,
  AssetDetailDTO,
  EntityAssetRelationDTO,
  RelationType,
} from "@/lib/api/dto";
import type { AssetWithRelation } from "../../../asset-card";
import type { TabKey } from "../../types";
import { formatDate } from "../../utils";
import { StatusBadge, ExtraInfoRenderer, AssetsSection, CoverImageCard, DetailPaneSkeleton } from "../../common";
import { useWorkspace } from "@/components/providers/workspace-provider";
import { EpisodeVersionPanel } from "../episode-version-panel";
import { useLocale } from "next-intl";

// Shared props for detail content components with assets
interface DetailContentWithAssetsProps {
  assets: AssetWithRelation[];
  onAssetStatusChange?: (asset: AssetWithRelation, relationType: RelationType) => void;
}

// Episode Detail Content - matches script-details-tab layout
export function EpisodeDetailContent({
  data,
  scriptId,
  onUpdate,
  onStartEditing,
  onStopEditing,
  isReadOnly = false,
}: {
  data: EpisodeDetailDTO;
  scriptId?: string;
  onUpdate?: () => void;
  onStartEditing?: () => void;
  onStopEditing?: () => void;
  isReadOnly?: boolean;
}) {
  const locale = useLocale();
  const { currentWorkspaceId } = useWorkspace();
  const [isHeaderCollapsed, setIsHeaderCollapsed] = useState(true);
  const [isEditingInfo, setIsEditingInfo] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [showVersionPanel, setShowVersionPanel] = useState(false);

  const [editForm, setEditForm] = useState({
    title: data.title || "",
    synopsis: data.synopsis || "",
  });
  const [editContent, setEditContent] = useState(data.content || "");

  // Sync with data changes
  useEffect(() => {
    setEditForm({
      title: data.title || "",
      synopsis: data.synopsis || "",
    });
    setEditContent(data.content || "");
  }, [data]);

  const handleSaveInfo = async () => {
    if (!currentWorkspaceId) return;

    try {
      setIsSaving(true);
      await projectService.updateEpisode( data.id, {
        title: editForm.title,
        synopsis: editForm.synopsis || undefined,
      });
      setIsEditingInfo(false);
      onStopEditing?.();
      onUpdate?.();
    } catch (err) {
      console.error("Failed to update episode:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsSaving(false);
    }
  };

  const handleCancelEditInfo = () => {
    setEditForm({
      title: data.title || "",
      synopsis: data.synopsis || "",
    });
    setIsEditingInfo(false);
    onStopEditing?.();
  };

  const handleSaveContent = async () => {
    if (!currentWorkspaceId) return;

    try {
      setIsSaving(true);
      await projectService.updateEpisode( data.id, {
        content: editContent,
      });
      onUpdate?.();
    } catch (err) {
      console.error("Failed to save content:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsSaving(false);
    }
  };

  const handleCoverUpload = async (file: File) => {
    if (!currentWorkspaceId) return;

    try {
      setIsUploading(true);

      const initResponse = await projectService.initAssetUpload( {
        name: file.name,
        fileName: file.name,
        mimeType: file.type,
        fileSize: file.size,
        scope: "SCRIPT",
        scriptId: scriptId,
        description: "剧集封面图",
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

      await projectService.updateEpisode( data.id, {
        coverAssetId: initResponse.assetId,
      });

      onUpdate?.();
    } catch (err) {
      console.error("Failed to upload cover:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsUploading(false);
    }
  };

  const handleCoverDelete = async () => {
    if (!currentWorkspaceId) return;

    try {
      setIsSaving(true);
      await projectService.updateEpisode( data.id, {
        coverAssetId: undefined,
      });
      onUpdate?.();
    } catch (err) {
      console.error("Failed to remove cover:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="relative flex h-full flex-col gap-3">
      {/* Episode Info Card - Collapsible */}
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
            {isHeaderCollapsed && data.coverUrl && (
              <div className="relative h-8 w-14 shrink-0 overflow-hidden rounded-lg">
                <Image src={data.coverUrl} alt="封面" fill className="object-cover" sizes="120px" />
              </div>
            )}
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <h2 className="truncate text-sm font-semibold">{data.title}</h2>
                <StatusBadge status={data.status} />
                <span className="text-xs text-muted">第 {data.sequence} 集</span>
              </div>
              {isHeaderCollapsed && data.synopsis && (
                <p className="mt-0.5 truncate text-xs text-muted">{data.synopsis}</p>
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
                    onPress={() => onUpdate?.()}
                  >
                    <RefreshCw className="size-3.5" />
                  </Button>
                  <Tooltip.Content>刷新数据</Tooltip.Content>
                </Tooltip>
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
                      onStartEditing?.();
                    }}
                  >
                    <Edit3 className="size-3.5" />
                  </Button>
                  <Tooltip.Content>{isReadOnly ? "只读模式" : "编辑信息"}</Tooltip.Content>
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
              coverUrl={data.coverUrl}
              onUpload={handleCoverUpload}
              onDelete={handleCoverDelete}
              isEditing={isEditingInfo}
              isUploading={isUploading}
            />

            <div className="min-w-0 flex-1">
              {isEditingInfo ? (
                <div className="space-y-3">
                  <div className="space-y-1">
                    <Label className="text-xs text-muted">标题</Label>
                    <Input
                      variant="secondary"
                      value={editForm.title}
                      onChange={(e) => setEditForm((prev) => ({ ...prev, title: e.target.value }))}
                      className="w-full"
                      placeholder="输入剧集标题"
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
                      placeholder="输入剧集简介"
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
                  <p className="line-clamp-2 text-sm text-muted">{data.synopsis || "暂无简介"}</p>
                  <div className="mt-auto flex flex-wrap items-center gap-x-4 gap-y-1.5 pt-3 text-xs text-muted">
                    <span className="inline-flex items-center gap-1.5">
                      <User className="size-3 opacity-60" />
                      {data.createdByNickname || data.createdByUsername}
                    </span>
                    <span className="inline-flex items-center gap-1.5">
                      <Calendar className="size-3 opacity-60" />
                      {formatDate(data.createdAt)}
                    </span>
                    <span className="inline-flex items-center gap-1.5">
                      <Clock className="size-3 opacity-60" />
                      {formatDate(data.updatedAt)}
                    </span>
                    <span className="text-xs text-muted">{data.storyboardCount} 个分镜</span>
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
          <span className="text-xs font-medium text-muted">剧集内容</span>
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
                if (!isEditing) {
                  setIsEditing(true);
                  onStartEditing?.();
                } else {
                  setIsEditing(false);
                  onStopEditing?.();
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
              placeholder="在此编写剧集内容..."
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

      {/* Extra Info */}
      {data.extraInfo && Object.keys(data.extraInfo).length > 0 && (
        <div className="shrink-0 rounded-xl bg-muted/5 p-4">
          <h4 className="mb-3 text-sm font-medium">扩展信息</h4>
          <ExtraInfoRenderer data={data.extraInfo} />
        </div>
      )}

      {/* Version Panel Overlay */}
      {showVersionPanel && (
        <div className="absolute inset-0 z-20 overflow-hidden rounded-2xl bg-background/95 backdrop-blur-sm">
          <EpisodeVersionPanel
            episodeId={data.id}
            onRestore={() => {
              onUpdate?.();
            }}
            onClose={() => setShowVersionPanel(false)}
          />
        </div>
      )}
    </div>
  );
}

// Storyboard Detail Content
export function StoryboardDetailContent({
  data,
  assets,
  onAssetStatusChange,
}: { data: StoryboardDetailDTO } & DetailContentWithAssetsProps) {
  const [isHeaderCollapsed, setIsHeaderCollapsed] = useState(true);

  // Filter assets by type
  const imageAssets = assets.filter(a => a.assetType === "IMAGE");
  const videoAssets = assets.filter(a => a.assetType === "VIDEO");
  const audioAssets = assets.filter(a => a.assetType === "AUDIO");

  // Visual description labels
  const visualDescLabels: Record<string, string> = {
    lighting: "光线",
    shotType: "镜头类型",
    cameraAngle: "机位角度",
    cameraMovement: "镜头运动",
    colorGrading: "调色",
    visualEffects: "视觉特效",
  };

  // Audio description labels
  const audioDescLabels: Record<string, string> = {
    narration: "旁白",
    soundEffects: "音效",
  };

  return (
    <ScrollShadow className="h-full p-4" hideScrollBar>
      <div className="space-y-4">
        {/* Header with cover and basic info - Collapsible */}
        <div className="rounded-xl bg-muted/5 transition-all duration-300">
          {/* Collapsed Header Bar */}
          <div
            className={`flex items-center justify-between gap-3 px-4 py-3 transition-all ${
              isHeaderCollapsed ? "" : "border-b border-muted/10"
            }`}
          >
            <div
              className="flex min-w-0 flex-1 cursor-pointer items-center gap-3"
              onClick={() => setIsHeaderCollapsed(!isHeaderCollapsed)}
            >
              {isHeaderCollapsed && data.coverUrl && (
                <div className="relative h-8 w-14 shrink-0 overflow-hidden rounded-lg">
                  <Image src={data.coverUrl} alt="" fill className="object-cover" sizes="120px" />
                </div>
              )}
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <h3 className="truncate text-sm font-semibold">{data.title || `分镜 ${data.sequence}`}</h3>
                  <StatusBadge status={data.status} />
                  {data.duration && <span className="text-xs text-muted">{data.duration}s</span>}
                </div>
                {isHeaderCollapsed && data.synopsis && (
                  <p className="mt-0.5 truncate text-xs text-muted">{data.synopsis}</p>
                )}
              </div>
            </div>
            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              className="size-7"
              onPress={() => setIsHeaderCollapsed(!isHeaderCollapsed)}
            >
              {isHeaderCollapsed ? <ChevronDown className="size-4" /> : <ChevronUp className="size-4" />}
            </Button>
          </div>

          {/* Expandable Content */}
          {!isHeaderCollapsed && (
            <div className="p-4 pt-3">
              <div className="flex items-start gap-4">
                {data.coverUrl && (
                  <div className="relative h-24 w-40 shrink-0 overflow-hidden rounded-lg">
                    <Image src={data.coverUrl} alt="" fill className="object-cover" sizes="(min-width: 768px) 25vw, 50vw" />
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-3">
                    {data.styleName && (
                      <span className="inline-flex items-center gap-1 text-xs text-muted">
                        <Sparkles className="size-3" />
                        {data.styleName}
                      </span>
                    )}
                  </div>
                  {data.synopsis && (
                    <p className="mt-3 text-sm text-muted">{data.synopsis}</p>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Scene */}
        {data.scene && (
          <div className="rounded-xl bg-muted/5 p-4">
            <h4 className="mb-3 flex items-center gap-2 text-sm font-medium">
              <MapPin className="size-4 text-accent" />
              场景
            </h4>
            <div className="flex items-center gap-3">
              {data.scene.coverUrl && (
                <div className="relative size-12 shrink-0 overflow-hidden rounded-lg">
                  <Image src={data.scene.coverUrl} alt="" fill className="object-cover" sizes="120px" />
                </div>
              )}
              <div>
                <p className="text-sm font-medium">{data.scene.sceneName}</p>
                {data.scene.description && (
                  <p className="mt-0.5 text-xs text-muted">{data.scene.description}</p>
                )}
              </div>
            </div>
          </div>
        )}

        {/* Characters */}
        {data.characters && data.characters.length > 0 && (
          <div className="rounded-xl bg-muted/5 p-4">
            <h4 className="mb-3 flex items-center gap-2 text-sm font-medium">
              <Users className="size-4 text-accent" />
              角色 ({data.characters.length})
            </h4>
            <div className="space-y-2">
              {data.characters.map((char) => (
                <div key={char.relationId} className="flex items-center gap-3 rounded-lg bg-muted/5 p-2">
                  {char.coverUrl ? (
                    <div className="relative size-10 shrink-0 overflow-hidden rounded-full">
                      <Image src={char.coverUrl} alt="" fill className="object-cover" sizes="120px" />
                    </div>
                  ) : (
                    <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-muted/20">
                      <Users className="size-4 text-muted" />
                    </div>
                  )}
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium">{char.characterName}</p>
                    <div className="mt-0.5 flex flex-wrap gap-2 text-xs text-muted">
                      {char.action && <span>动作: {char.action}</span>}
                      {char.expression && <span>表情: {char.expression}</span>}
                      {char.position && <span>位置: {char.position}</span>}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Props */}
        {data.props && data.props.length > 0 && (
          <div className="rounded-xl bg-muted/5 p-4">
            <h4 className="mb-3 flex items-center gap-2 text-sm font-medium">
              <Package className="size-4 text-accent" />
              道具 ({data.props.length})
            </h4>
            <div className="flex flex-wrap gap-2">
              {data.props.map((prop) => (
                <div key={prop.relationId} className="flex items-center gap-2 rounded-lg bg-muted/5 px-2.5 py-1.5">
                  {prop.coverUrl && (
                    <div className="relative size-6 shrink-0 overflow-hidden rounded">
                      <Image src={prop.coverUrl} alt="" fill className="object-cover" sizes="120px" />
                    </div>
                  )}
                  <span className="text-xs">{prop.propName}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Dialogues */}
        {data.dialogues && data.dialogues.length > 0 && (
          <div className="rounded-xl bg-muted/5 p-4">
            <h4 className="mb-3 flex items-center gap-2 text-sm font-medium">
              <MessageSquare className="size-4 text-accent" />
              对白 ({data.dialogues.length})
            </h4>
            <div className="space-y-2">
              {data.dialogues.map((dialogue) => (
                <div key={dialogue.id} className="rounded-lg bg-muted/5 p-3">
                  <div className="flex items-center gap-2">
                    {dialogue.characterName && (
                      <span className="text-sm font-medium">{dialogue.characterName}</span>
                    )}
                    {dialogue.emotion && (
                      <span className="text-xs text-muted">({dialogue.emotion})</span>
                    )}
                  </div>
                  <p className="mt-1 text-sm text-muted">{dialogue.content}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Visual Description */}
        {data.visualDesc && Object.values(data.visualDesc).some(v => v !== null) && (
          <div className="rounded-xl bg-muted/5 p-4">
            <h4 className="mb-3 flex items-center gap-2 text-sm font-medium">
              <Camera className="size-4 text-accent" />
              视觉描述
            </h4>
            <div className="grid grid-cols-2 gap-3">
              {Object.entries(data.visualDesc).map(([key, value]) => {
                if (value === null || key === "transition") return null;
                return (
                  <div key={key}>
                    <span className="text-xs text-muted">{visualDescLabels[key] || key}</span>
                    <p className="mt-0.5 text-sm">{String(value)}</p>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Assets Section */}
        <AssetsSection
          title="相关素材"
          imageAssets={imageAssets}
          videoAssets={videoAssets}
          audioAssets={audioAssets}
          onStatusChange={onAssetStatusChange}
          columns={3}
        />
      </div>
    </ScrollShadow>
  );
}

// Character Detail Content
export function CharacterDetailContent({
  data,
  assets,
  onAssetStatusChange,
}: { data: CharacterDetailDTO } & DetailContentWithAssetsProps) {
  const [isHeaderCollapsed, setIsHeaderCollapsed] = useState(true);
  const imageAssets = assets.filter(a => a.assetType === "IMAGE");
  const videoAssets = assets.filter(a => a.assetType === "VIDEO");
  const audioAssets = assets.filter(a => a.assetType === "AUDIO");

  return (
    <ScrollShadow className="h-full p-4" hideScrollBar>
      <div className="space-y-4">
        {/* Header - Collapsible */}
        <div className="rounded-xl bg-muted/5 transition-all duration-300">
          {/* Collapsed Header Bar */}
          <div
            className={`flex items-center justify-between gap-3 px-4 py-3 transition-all ${
              isHeaderCollapsed ? "" : "border-b border-muted/10"
            }`}
          >
            <div
              className="flex min-w-0 flex-1 cursor-pointer items-center gap-3"
              onClick={() => setIsHeaderCollapsed(!isHeaderCollapsed)}
            >
              {isHeaderCollapsed && (
                data.coverUrl ? (
                  <div className="relative size-8 shrink-0 overflow-hidden rounded-full">
                    <Image src={data.coverUrl} alt="" fill className="object-cover" sizes="120px" />
                  </div>
                ) : (
                  <div className="flex size-8 shrink-0 items-center justify-center rounded-full bg-muted/20">
                    <Users className="size-4 text-muted" />
                  </div>
                )
              )}
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <h3 className="truncate text-sm font-semibold">{data.name}</h3>
                  {data.gender && <span className="text-xs text-muted">{data.gender}</span>}
                  {data.characterType && <span className="text-xs text-muted">{data.characterType}</span>}
                </div>
                {isHeaderCollapsed && data.description && (
                  <p className="mt-0.5 truncate text-xs text-muted">{data.description}</p>
                )}
              </div>
            </div>
            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              className="size-7"
              onPress={() => setIsHeaderCollapsed(!isHeaderCollapsed)}
            >
              {isHeaderCollapsed ? <ChevronDown className="size-4" /> : <ChevronUp className="size-4" />}
            </Button>
          </div>

          {/* Expandable Content */}
          {!isHeaderCollapsed && (
            <div className="p-4 pt-3">
              <div className="flex items-start gap-4">
                {data.coverUrl ? (
                  <div className="relative size-20 shrink-0 overflow-hidden rounded-full">
                    <Image src={data.coverUrl} alt="" fill className="object-cover" sizes="120px" />
                  </div>
                ) : (
                  <div className="flex size-20 shrink-0 items-center justify-center rounded-full bg-muted/20">
                    <Users className="size-8 text-muted" />
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-3 text-xs text-muted">
                    {data.age && <span>{data.age}岁</span>}
                  </div>
                  {data.description && (
                    <p className="mt-3 text-sm text-muted">{data.description}</p>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Fixed Description */}
        {data.fixedDesc && (
          <div className="rounded-xl bg-muted/5 p-4">
            <h4 className="mb-2 text-sm font-medium">固定描述</h4>
            <p className="text-sm text-muted">{data.fixedDesc}</p>
          </div>
        )}

        {/* Appearance Data */}
        {data.appearanceData && Object.keys(data.appearanceData).length > 0 && (
          <div className="rounded-xl bg-muted/5 p-4">
            <h4 className="mb-3 text-sm font-medium">外观设定</h4>
            <ExtraInfoRenderer data={data.appearanceData} />
          </div>
        )}

        {/* Assets */}
        <AssetsSection
          title="相关素材"
          imageAssets={imageAssets}
          videoAssets={videoAssets}
          audioAssets={audioAssets}
          onStatusChange={onAssetStatusChange}
          columns={3}
        />
      </div>
    </ScrollShadow>
  );
}

// Scene Detail Content
export function SceneDetailContent({
  data,
  assets,
  onAssetStatusChange,
}: { data: SceneDetailDTO } & DetailContentWithAssetsProps) {
  const [isHeaderCollapsed, setIsHeaderCollapsed] = useState(true);
  const imageAssets = assets.filter(a => a.assetType === "IMAGE");
  const videoAssets = assets.filter(a => a.assetType === "VIDEO");
  const audioAssets = assets.filter(a => a.assetType === "AUDIO");

  return (
    <ScrollShadow className="h-full p-4" hideScrollBar>
      <div className="space-y-4">
        {/* Header - Collapsible */}
        <div className="rounded-xl bg-muted/5 transition-all duration-300">
          {/* Collapsed Header Bar */}
          <div
            className={`flex items-center justify-between gap-3 px-4 py-3 transition-all ${
              isHeaderCollapsed ? "" : "border-b border-muted/10"
            }`}
          >
            <div
              className="flex min-w-0 flex-1 cursor-pointer items-center gap-3"
              onClick={() => setIsHeaderCollapsed(!isHeaderCollapsed)}
            >
              {isHeaderCollapsed && data.coverUrl && (
                <div className="relative h-8 w-14 shrink-0 overflow-hidden rounded-lg">
                  <Image src={data.coverUrl} alt="" fill className="object-cover" sizes="120px" />
                </div>
              )}
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <h3 className="truncate text-sm font-semibold">{data.name}</h3>
                </div>
                {isHeaderCollapsed && data.description && (
                  <p className="mt-0.5 truncate text-xs text-muted">{data.description}</p>
                )}
              </div>
            </div>
            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              className="size-7"
              onPress={() => setIsHeaderCollapsed(!isHeaderCollapsed)}
            >
              {isHeaderCollapsed ? <ChevronDown className="size-4" /> : <ChevronUp className="size-4" />}
            </Button>
          </div>

          {/* Expandable Content */}
          {!isHeaderCollapsed && (
            <div className="p-4 pt-3">
              <div className="flex items-start gap-4">
                {data.coverUrl && (
                  <div className="relative h-24 w-40 shrink-0 overflow-hidden rounded-lg">
                    <Image src={data.coverUrl} alt="" fill className="object-cover" sizes="(min-width: 768px) 25vw, 50vw" />
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  {data.description && (
                    <p className="mt-2 text-sm text-muted">{data.description}</p>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Fixed Description */}
        {data.fixedDesc && (
          <div className="rounded-xl bg-muted/5 p-4">
            <h4 className="mb-2 text-sm font-medium">固定描述</h4>
            <p className="text-sm text-muted">{data.fixedDesc}</p>
          </div>
        )}

        {/* Environment */}
        {data.environment && Object.keys(data.environment).length > 0 && (
          <div className="rounded-xl bg-muted/5 p-4">
            <h4 className="mb-3 text-sm font-medium">环境设定</h4>
            <ExtraInfoRenderer data={data.environment} />
          </div>
        )}

        {/* Atmosphere */}
        {data.atmosphere && Object.keys(data.atmosphere).length > 0 && (
          <div className="rounded-xl bg-muted/5 p-4">
            <h4 className="mb-3 text-sm font-medium">氛围设定</h4>
            <ExtraInfoRenderer data={data.atmosphere} />
          </div>
        )}

        {/* Assets */}
        <AssetsSection
          title="相关素材"
          imageAssets={imageAssets}
          videoAssets={videoAssets}
          audioAssets={audioAssets}
          onStatusChange={onAssetStatusChange}
          columns={3}
        />
      </div>
    </ScrollShadow>
  );
}

// Prop Detail Content
export function PropDetailContent({
  data,
  assets,
  onAssetStatusChange,
}: { data: PropDetailDTO } & DetailContentWithAssetsProps) {
  const [isHeaderCollapsed, setIsHeaderCollapsed] = useState(true);
  const imageAssets = assets.filter(a => a.assetType === "IMAGE");
  const videoAssets = assets.filter(a => a.assetType === "VIDEO");
  const audioAssets = assets.filter(a => a.assetType === "AUDIO");

  return (
    <ScrollShadow className="h-full p-4" hideScrollBar>
      <div className="space-y-4">
        {/* Header - Collapsible */}
        <div className="rounded-xl bg-muted/5 transition-all duration-300">
          {/* Collapsed Header Bar */}
          <div
            className={`flex items-center justify-between gap-3 px-4 py-3 transition-all ${
              isHeaderCollapsed ? "" : "border-b border-muted/10"
            }`}
          >
            <div
              className="flex min-w-0 flex-1 cursor-pointer items-center gap-3"
              onClick={() => setIsHeaderCollapsed(!isHeaderCollapsed)}
            >
              {isHeaderCollapsed && data.coverUrl && (
                <div className="relative size-8 shrink-0 overflow-hidden rounded-lg">
                  <Image src={data.coverUrl} alt="" fill className="object-cover" sizes="120px" />
                </div>
              )}
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <h3 className="truncate text-sm font-semibold">{data.name}</h3>
                  {data.propType && <span className="text-xs text-muted">{data.propType}</span>}
                </div>
                {isHeaderCollapsed && data.description && (
                  <p className="mt-0.5 truncate text-xs text-muted">{data.description}</p>
                )}
              </div>
            </div>
            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              className="size-7"
              onPress={() => setIsHeaderCollapsed(!isHeaderCollapsed)}
            >
              {isHeaderCollapsed ? <ChevronDown className="size-4" /> : <ChevronUp className="size-4" />}
            </Button>
          </div>

          {/* Expandable Content */}
          {!isHeaderCollapsed && (
            <div className="p-4 pt-3">
              <div className="flex items-start gap-4">
                {data.coverUrl && (
                  <div className="relative size-20 shrink-0 overflow-hidden rounded-lg">
                    <Image src={data.coverUrl} alt="" fill className="object-cover" sizes="120px" />
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  {data.description && (
                    <p className="mt-2 text-sm text-muted">{data.description}</p>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Fixed Description */}
        {data.fixedDesc && (
          <div className="rounded-xl bg-muted/5 p-4">
            <h4 className="mb-2 text-sm font-medium">固定描述</h4>
            <p className="text-sm text-muted">{data.fixedDesc}</p>
          </div>
        )}

        {/* Appearance Data */}
        {data.appearanceData && Object.keys(data.appearanceData).length > 0 && (
          <div className="rounded-xl bg-muted/5 p-4">
            <h4 className="mb-3 text-sm font-medium">外观设定</h4>
            <ExtraInfoRenderer data={data.appearanceData} />
          </div>
        )}

        {/* Assets */}
        <AssetsSection
          title="相关素材"
          imageAssets={imageAssets}
          videoAssets={videoAssets}
          audioAssets={audioAssets}
          onStatusChange={onAssetStatusChange}
          columns={3}
        />
      </div>
    </ScrollShadow>
  );
}

// Asset Detail Content
// Re-exported from dedicated file
export { AssetDetailContent } from "./asset-detail-content";

// Entity Detail Content Wrapper - fetches and renders based on entity type
export function EntityDetailContent({
  entityId,
  tabKey,
  scriptId,
}: {
  entityId: string;
  tabKey: TabKey;
  scriptId: string;
}) {
  const locale = useLocale();
  const { currentWorkspaceId } = useWorkspace();
  const [isLoading, setIsLoading] = useState(true);
  const [detailData, setDetailData] = useState<unknown>(null);
  const [relatedAssets, setRelatedAssets] = useState<AssetWithRelation[]>([]);

  // Map tabKey to entity type for API calls
  const getEntityType = (key: TabKey): string | null => {
    switch (key) {
      case "episodes": return "EPISODE";
      case "storyboards": return "STORYBOARD";
      case "characters": return "CHARACTER";
      case "scenes": return "SCENE";
      case "props": return "PROP";
      default: return null;
    }
  };

  // Fetch detail data
  const fetchDetail = useCallback(async () => {
    if (!currentWorkspaceId || !entityId) return;

    try {
      setIsLoading(true);
      let data: unknown = null;

      switch (tabKey) {
        case "episodes":
          data = await projectService.getEpisode( entityId);
          break;
        case "storyboards":
          data = await projectService.getStoryboard( entityId);
          break;
        case "characters":
          data = await projectService.getCharacter( entityId);
          break;
        case "scenes":
          data = await projectService.getScene( entityId);
          break;
        case "props":
          data = await projectService.getProp( entityId);
          break;
        case "assets":
          data = await projectService.getAsset( entityId);
          break;
      }

      setDetailData(data);

      // Fetch entity-asset relations for entities that have them
      const entityType = getEntityType(tabKey);
      if (entityType) {
        try {
          const relations = await projectService.getEntityAssetRelations( entityType, entityId);
          const assetsWithRelations: AssetWithRelation[] = relations.map((rel: EntityAssetRelationDTO) => ({
            ...rel.asset,
            relationId: rel.id,
            relationType: rel.relationType,
          }));
          setRelatedAssets(assetsWithRelations);
        } catch {
          // Ignore asset fetch errors
        }
      }
    } catch (err) {
      console.error(`Failed to fetch ${tabKey} detail:`, err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsLoading(false);
    }
  }, [currentWorkspaceId, entityId, tabKey]);

  useEffect(() => {
    fetchDetail();
  }, [fetchDetail]);

  // Handle asset status change
  const handleAssetStatusChange = useCallback(async (asset: AssetWithRelation, relationType: RelationType) => {
    if (!currentWorkspaceId || !asset.relationId) return;
    try {
      await projectService.updateEntityAssetRelation( asset.relationId, { relationType });
      setRelatedAssets(prev => prev.map(a =>
        a.id === asset.id ? { ...a, relationType } : a
      ));
    } catch (err) {
      console.error("Failed to update asset relation type:", err);
      toast.danger(getErrorFromException(err, locale));
    }
  }, [currentWorkspaceId]);

  if (isLoading) {
    return <DetailPaneSkeleton />;
  }

  if (!detailData) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-muted">
        无法加载详情
      </div>
    );
  }

  // Render based on entity type
  switch (tabKey) {
    case "episodes":
      return <EpisodeDetailContent key={entityId} data={detailData as EpisodeDetailDTO} scriptId={scriptId} onUpdate={fetchDetail} />;
    case "storyboards":
      return <StoryboardDetailContent key={entityId} data={detailData as StoryboardDetailDTO} assets={relatedAssets} onAssetStatusChange={handleAssetStatusChange} />;
    case "characters":
      return <CharacterDetailContent key={entityId} data={detailData as CharacterDetailDTO} assets={relatedAssets} onAssetStatusChange={handleAssetStatusChange} />;
    case "scenes":
      return <SceneDetailContent key={entityId} data={detailData as SceneDetailDTO} assets={relatedAssets} onAssetStatusChange={handleAssetStatusChange} />;
    case "props":
      return <PropDetailContent key={entityId} data={detailData as PropDetailDTO} assets={relatedAssets} onAssetStatusChange={handleAssetStatusChange} />;
    case "assets":
      return <AssetDetailContent key={entityId} data={detailData as AssetDetailDTO} />;
    default:
      return null;
  }
}
