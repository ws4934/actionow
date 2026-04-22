"use client";

/**
 * Scene Tab Component
 * Dedicated tab component for managing scenes with list/grid/detail views
 */

import { useState, useEffect, useCallback, useRef } from "react";
import {
  Button,
  ButtonGroup,
  ScrollShadow,
  Tooltip,
  Modal,
  Input,
  Label,
  TextArea,
  Spinner,
  Separator,
  SearchField,
  Surface,
  toast,
} from "@heroui/react";
import {
  MapPin,
  LayoutGrid,
  List,
  PanelLeft,
  PanelLeftClose,
  Plus,
  X,
  RefreshCw,
  Loader2,
  ChevronDown,
  ChevronUp,
  History,
  Edit3,
  Trash2,
  Sun,
  Cloud,
  Thermometer,
  Clock,
  Volume2,
  Lightbulb,
  Image,
  Upload,
  Sparkles,
  GripVertical,
  Link2,
  Film,
} from "lucide-react";
import NextImage from "@/components/ui/content-image";
import { projectService, getErrorFromException} from "@/lib/api";
import { commentService } from "@/lib/api/services/comment.service";
import type { SceneListDTO, SceneDetailDTO, VersionInfoDTO, EntityAssetRelationDTO, AssetListDTO } from "@/lib/api/dto";
import { useWorkspace } from "@/components/providers/workspace-provider";
import { useAIGeneration } from "@/components/providers/ai-generation-provider";
import { useDebouncedEntityChanges } from "@/lib/websocket";
import type { ViewMode, EntityItem } from "../types";
import {
  transformToEntityItem,
  getStoredViewMode,
  getStoredSidebarCollapsed,
  setViewMode as saveViewMode,
  setSidebarCollapsed as saveSidebarCollapsed,
  formatDate,
} from "../utils";
import { EntityListView, EntityGridView, DetailSidebar, AssetCard, VoiceSection, EntityTabSkeleton, DetailPaneSkeleton, AssetsSectionSkeleton, VersionListSkeleton, SortButtons, sortItems, EmptyState, EntityPickerModal, type SortKey } from "../common";
import { useDragDropActions, createAssetDragData, ASSET_DRAG_TYPE } from "@/lib/stores/drag-drop-store";
import { useCommentPanelStore } from "@/lib/stores/comment-panel-store";
import { EditingOverlay } from "../components";
import { useCollaboration } from "../hooks/use-collaboration";
import { useLocale } from "next-intl";

interface SceneTabProps {
  scriptId: string;
}

// Environment field config
const ENVIRONMENT_FIELDS = [
  { key: "location", label: "地点", placeholder: "如: 室内、室外、城市" },
  { key: "timeOfDay", label: "时间", placeholder: "如: 清晨、正午、黄昏" },
  { key: "weather", label: "天气", placeholder: "如: 晴朗、阴天、雨天" },
  { key: "season", label: "季节", placeholder: "如: 春、夏、秋、冬" },
  { key: "temperature", label: "温度", placeholder: "如: 炎热、温暖、寒冷" },
  { key: "lighting", label: "光线", placeholder: "如: 明亮、昏暗、柔和" },
];

// Atmosphere field config
const ATMOSPHERE_FIELDS = [
  { key: "mood", label: "情绪", placeholder: "如: 紧张、温馨、神秘" },
  { key: "ambiance", label: "氛围", placeholder: "如: 安静、喧闹、庄严" },
  { key: "soundscape", label: "声音环境", placeholder: "如: 鸟鸣、交通声、寂静" },
];

// Detail Content Component with editable fields (matching storyboard/character layout)
function SceneDetailContent({
  data,
  scriptId,
  onUpdate,
  onStartEditing,
  onStopEditing,
  isReadOnly = false,
}: {
  data: SceneDetailDTO;
  scriptId?: string;
  onUpdate?: () => void;
  onStartEditing?: () => void;
  onStopEditing?: () => void;
  isReadOnly?: boolean;
}) {
  const locale = useLocale();
  const { currentWorkspaceId } = useWorkspace();
  const { openPanel, updateEntity, entityId: aiEntityId } = useAIGeneration();
  const [isHeaderCollapsed, setIsHeaderCollapsed] = useState(true);
  const [isEditingInfo, setIsEditingInfo] = useState(false);
  const [isEditingFixedDesc, setIsEditingFixedDesc] = useState(false);
  const [isEditingEnvironment, setIsEditingEnvironment] = useState(false);
  const [isEditingAtmosphere, setIsEditingAtmosphere] = useState(false);
  const [isEnvironmentCollapsed, setIsEnvironmentCollapsed] = useState(true);
  const [isAtmosphereCollapsed, setIsAtmosphereCollapsed] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [showVersionPanel, setShowVersionPanel] = useState(false);

  // Related assets
  const [relatedAssets, setRelatedAssets] = useState<EntityAssetRelationDTO[]>([]);
  const [isLoadingAssets, setIsLoadingAssets] = useState(false);
  const [isUploadingAsset, setIsUploadingAsset] = useState(false);
  const [assetFilter, setAssetFilter] = useState<"ALL" | "IMAGE" | "VIDEO" | "AUDIO" | "OTHER">("ALL");
  const assetFileInputRef = useRef<HTMLInputElement>(null);

  // Unattached asset picker
  const [isUnattachedPickerOpen, setIsUnattachedPickerOpen] = useState(false);
  const [unattachedAssets, setUnattachedAssets] = useState<AssetListDTO[]>([]);
  const [isLoadingUnattached, setIsLoadingUnattached] = useState(false);
  const [isAttachingAsset, setIsAttachingAsset] = useState(false);

  // Version history
  const [versions, setVersions] = useState<VersionInfoDTO[]>([]);
  const [isLoadingVersions, setIsLoadingVersions] = useState(false);

  // Edit form state - Basic info
  const [editForm, setEditForm] = useState({
    name: data.name || "",
    description: data.description || "",
    fixedDesc: data.fixedDesc || "",
  });

  // Edit form state - Fixed Description (separate)
  const [fixedDescEdit, setFixedDescEdit] = useState(data.fixedDesc || "");

  // Edit form state - Environment
  const environmentData = (data.environment || {}) as Record<string, unknown>;
  const [environmentEdit, setEnvironmentEdit] = useState({
    location: (environmentData.location as string) || "",
    timeOfDay: (environmentData.timeOfDay as string) || "",
    weather: (environmentData.weather as string) || "",
    season: (environmentData.season as string) || "",
    temperature: (environmentData.temperature as string) || "",
    lighting: (environmentData.lighting as string) || "",
  });

  // Edit form state - Atmosphere
  const atmosphereData = (data.atmosphere || {}) as Record<string, unknown>;
  const [atmosphereEdit, setAtmosphereEdit] = useState({
    mood: (atmosphereData.mood as string) || "",
    ambiance: (atmosphereData.ambiance as string) || "",
    soundscape: (atmosphereData.soundscape as string) || "",
  });

  // Sync with data changes
  useEffect(() => {
    setEditForm({
      name: data.name || "",
      description: data.description || "",
      fixedDesc: data.fixedDesc || "",
    });

    setFixedDescEdit(data.fixedDesc || "");

    const environmentData = (data.environment || {}) as Record<string, unknown>;
    setEnvironmentEdit({
      location: (environmentData.location as string) || "",
      timeOfDay: (environmentData.timeOfDay as string) || "",
      weather: (environmentData.weather as string) || "",
      season: (environmentData.season as string) || "",
      temperature: (environmentData.temperature as string) || "",
      lighting: (environmentData.lighting as string) || "",
    });

    const atmosphereData = (data.atmosphere || {}) as Record<string, unknown>;
    setAtmosphereEdit({
      mood: (atmosphereData.mood as string) || "",
      ambiance: (atmosphereData.ambiance as string) || "",
      soundscape: (atmosphereData.soundscape as string) || "",
    });
  }, [data]);

  // Fetch related assets with auto-refresh for generating items
  useEffect(() => {
    const fetchRelatedAssets = async () => {
      if (!currentWorkspaceId || !data.id) return;
      try {
        setIsLoadingAssets(true);
        const assets = await projectService.getEntityAssetRelations( "SCENE", data.id);
        setRelatedAssets(assets);
      } catch (err) {
        console.error("Failed to fetch related assets:", err);
        toast.danger(getErrorFromException(err, locale));
      } finally {
        setIsLoadingAssets(false);
      }
    };
    fetchRelatedAssets();
  }, [currentWorkspaceId, data.id]);

  // Auto-refresh when there are generating assets
  useEffect(() => {
    const hasGeneratingAssets = relatedAssets.some(r => r.asset && r.asset.generationStatus === "GENERATING");
    if (!hasGeneratingAssets || !currentWorkspaceId || !data.id) return;

    const refreshInterval = setInterval(async () => {
      try {
        const assets = await projectService.getEntityAssetRelations( "SCENE", data.id);
        setRelatedAssets(assets);
      } catch (err) {
        console.error("Failed to refresh assets:", err);
        toast.danger(getErrorFromException(err, locale));
      }
    }, 3000);

    return () => clearInterval(refreshInterval);
  }, [relatedAssets, currentWorkspaceId, data.id]);

  // Fetch versions
  const fetchVersions = async () => {
    if (!currentWorkspaceId) return;
    try {
      setIsLoadingVersions(true);
      const vers = await projectService.getSceneVersions( data.id);
      setVersions(vers);
    } catch (err) {
      console.error("Failed to fetch versions:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsLoadingVersions(false);
    }
  };

  // Save basic info
  const handleSaveInfo = async () => {
    if (!currentWorkspaceId) return;
    try {
      setIsSaving(true);
      await projectService.updateScene( data.id, {
        name: editForm.name || undefined,
        description: editForm.description || undefined,
      });
      setIsEditingInfo(false);
      onStopEditing?.();
      // Sync with AI generation panel if it's showing this entity
      if (aiEntityId === data.id) {
        updateEntity({
          entityType: "SCENE",
          entityId: data.id,
          entityName: editForm.name || data.name,
          entityDescription: editForm.description || undefined,
          entityFixedDesc: data.fixedDesc || undefined,
          entityCoverUrl: data.coverUrl || undefined,
          scriptId,
        });
      }
      onUpdate?.();
    } catch (err) {
      console.error("Failed to update scene:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsSaving(false);
    }
  };

  // Save fixed description (separate)
  const handleSaveFixedDesc = async () => {
    if (!currentWorkspaceId) return;
    try {
      setIsSaving(true);
      await projectService.updateScene( data.id, {
        fixedDesc: fixedDescEdit || undefined,
      });
      setIsEditingFixedDesc(false);
      onStopEditing?.();
      // Sync with AI generation panel if it's showing this entity
      if (aiEntityId === data.id) {
        updateEntity({
          entityType: "SCENE",
          entityId: data.id,
          entityName: data.name,
          entityDescription: data.description || undefined,
          entityFixedDesc: fixedDescEdit || undefined,
          entityCoverUrl: data.coverUrl || undefined,
          scriptId,
        });
      }
      onUpdate?.();
    } catch (err) {
      console.error("Failed to update fixed description:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsSaving(false);
    }
  };

  // Save environment
  const handleSaveEnvironment = async () => {
    if (!currentWorkspaceId) return;
    try {
      setIsSaving(true);
      await projectService.updateScene( data.id, {
        environment: {
          location: environmentEdit.location || null,
          timeOfDay: environmentEdit.timeOfDay || null,
          weather: environmentEdit.weather || null,
          season: environmentEdit.season || null,
          temperature: environmentEdit.temperature || null,
          lighting: environmentEdit.lighting || null,
        },
      });
      setIsEditingEnvironment(false);
      onStopEditing?.();
      onUpdate?.();
    } catch (err) {
      console.error("Failed to update environment:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsSaving(false);
    }
  };

  // Save atmosphere
  const handleSaveAtmosphere = async () => {
    if (!currentWorkspaceId) return;
    try {
      setIsSaving(true);
      await projectService.updateScene( data.id, {
        atmosphere: {
          mood: atmosphereEdit.mood || null,
          ambiance: atmosphereEdit.ambiance || null,
          soundscape: atmosphereEdit.soundscape || null,
        },
      });
      setIsEditingAtmosphere(false);
      onStopEditing?.();
      onUpdate?.();
    } catch (err) {
      console.error("Failed to update atmosphere:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsSaving(false);
    }
  };

  // Cover upload
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
        description: "场景封面图",
      });
      await fetch(initResponse.uploadUrl, {
        method: initResponse.method,
        headers: initResponse.headers,
        body: file,
      });
      const confirmResult = await projectService.confirmAssetUpload( initResponse.assetId, {
        fileKey: initResponse.fileKey,
        actualFileSize: file.size,
      });
      await projectService.updateScene( data.id, {
        coverAssetId: initResponse.assetId,
      });
      // Sync cover with AI generation panel
      if (aiEntityId === data.id && confirmResult.fileUrl) {
        updateEntity({
          entityType: "SCENE",
          entityId: data.id,
          entityName: data.name,
          entityDescription: data.description || undefined,
          entityFixedDesc: data.fixedDesc || undefined,
          entityCoverUrl: confirmResult.fileUrl,
          scriptId,
        });
      }
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
      await projectService.updateScene( data.id, {
        coverAssetId: null,
      });
      onUpdate?.();
    } catch (err) {
      console.error("Failed to delete cover:", err);
      toast.danger(getErrorFromException(err, locale));
    }
  };

  // Asset upload (supports batch)
  const handleAssetUpload = async (files: FileList | File[]) => {
    if (!currentWorkspaceId || !scriptId) return;
    const fileArray = Array.from(files);
    if (fileArray.length === 0) return;

    try {
      setIsUploadingAsset(true);

      // Upload all files in parallel
      await Promise.all(
        fileArray.map(async (file) => {
          const initResponse = await projectService.initAssetUpload( {
            name: file.name,
            fileName: file.name,
            mimeType: file.type,
            fileSize: file.size,
            scope: "SCRIPT",
            scriptId: scriptId,
            description: "场景参考素材",
          });
          await fetch(initResponse.uploadUrl, {
            method: initResponse.method,
            headers: initResponse.headers,
            body: file,
          });
          await projectService.confirmAssetUpload( initResponse.assetId, {
            fileKey: initResponse.fileKey,
            actualFileSize: file.size,
          });
          await projectService.createEntityAssetRelation( {
            entityType: "SCENE",
            entityId: data.id,
            assetId: initResponse.assetId,
            relationType: "REFERENCE",
          });
        })
      );

      // Refresh assets list
      const assets = await projectService.getEntityAssetRelations( "SCENE", data.id);
      setRelatedAssets(assets);
    } catch (err) {
      console.error("Failed to upload asset:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsUploadingAsset(false);
      if (assetFileInputRef.current) {
        assetFileInputRef.current.value = "";
      }
    }
  };

  // Delete asset relation (also delete failed assets)
  const handleDeleteAssetRelation = async (relationId: string) => {
    if (!currentWorkspaceId) return;
    try {
      const relation = relatedAssets.find((r) => r.id === relationId);
      await projectService.deleteEntityAssetRelation( relationId);
      if (relation?.asset?.generationStatus === "FAILED") {
        await projectService.deleteAsset( relation.asset.id);
      }
      setRelatedAssets((prev) => prev.filter((r) => r.id !== relationId));
    } catch (err) {
      console.error("Failed to delete asset relation:", err);
      toast.danger(getErrorFromException(err, locale));
    }
  };

  // Save edited image from image editor
  const handleSaveEditedImage = async (relation: EntityAssetRelationDTO, dataUrl: string) => {
    if (!currentWorkspaceId || !scriptId) return;
    try {
      // Convert dataUrl to Blob
      const response = await fetch(dataUrl);
      const blob = await response.blob();
      const file = new File([blob], `edited_${relation.asset.name || "image"}.png`, { type: "image/png" });

      // Upload using the same flow as handleAssetUpload
      const initResponse = await projectService.initAssetUpload( {
        name: file.name,
        fileName: file.name,
        mimeType: file.type,
        fileSize: file.size,
        scope: "SCRIPT",
        scriptId: scriptId,
        description: "编辑后的场景素材",
      });

      await fetch(initResponse.uploadUrl, {
        method: initResponse.method,
        headers: initResponse.headers,
        body: file,
      });

      await projectService.confirmAssetUpload( initResponse.assetId, {
        fileKey: initResponse.fileKey,
        actualFileSize: file.size,
      });

      await projectService.createEntityAssetRelation( {
        entityType: "SCENE",
        entityId: data.id,
        assetId: initResponse.assetId,
        relationType: "REFERENCE",
      });

      // Refresh assets
      const assets = await projectService.getEntityAssetRelations( "SCENE", data.id);
      setRelatedAssets(assets);
    } catch (err) {
      console.error("Failed to save edited image:", err);
      toast.danger(getErrorFromException(err, locale));
    }
  };

  // Restore version
  const [restoringVersion, setRestoringVersion] = useState<number | null>(null);
  const handleRestoreVersion = async (versionNumber: number) => {
    if (!currentWorkspaceId) return;
    setRestoringVersion(versionNumber);
    try {
      await projectService.restoreSceneVersion( data.id, {
        versionNumber,
        reason: `恢复到版本 ${versionNumber}`,
      });
      setShowVersionPanel(false);
      onUpdate?.();
    } catch (err) {
      console.error("Failed to restore version:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setRestoringVersion(null);
    }
  };

  const filteredAssets = relatedAssets.filter((r) => {
    if (!r.asset) return false;
    if (assetFilter === "ALL") return true;
    if (assetFilter === "OTHER") return !["IMAGE", "VIDEO", "AUDIO"].includes(r.asset.assetType ?? "");
    return r.asset.assetType === assetFilter;
  });

  // Drag handlers for assets
  const { startDrag, endDrag } = useDragDropActions();

  const handleAssetDragStart = (e: React.DragEvent, relation: EntityAssetRelationDTO) => {
    const dragData = createAssetDragData({
      assetId: relation.asset.id,
      url: relation.asset.fileUrl,
      name: relation.asset.name,
      mimeType: relation.asset.mimeType,
      fileSize: relation.asset.fileSize,
      assetType: relation.asset.assetType,
    });
    e.dataTransfer.setData(ASSET_DRAG_TYPE, JSON.stringify(dragData));
    e.dataTransfer.effectAllowed = "copy";
    startDrag(dragData);
  };

  const handleAssetDragEnd = () => {
    endDrag();
  };

  // Fetch unattached assets for the picker
  const fetchUnattachedAssets = useCallback(async () => {
    if (!scriptId) return;
    try {
      setIsLoadingUnattached(true);
      const result = await projectService.getUnattachedAssets(scriptId, { size: 100 });
      setUnattachedAssets(result.records || []);
    } catch (err) {
      console.error("Failed to fetch unattached assets:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsLoadingUnattached(false);
    }
  }, [scriptId]);

  // Attach an unattached asset to this entity
  const handleAttachAsset = async (assetId: string) => {
    if (!currentWorkspaceId) return;
    try {
      setIsAttachingAsset(true);
      await projectService.createEntityAssetRelation({
        entityType: "SCENE",
        entityId: data.id,
        assetId,
        relationType: "DRAFT",
      });
      toast.success("素材关联成功");
      const assets = await projectService.getEntityAssetRelations("SCENE", data.id);
      setRelatedAssets(assets);
      setUnattachedAssets(prev => prev.filter(a => a.id !== assetId));
    } catch (err) {
      console.error("Failed to attach asset:", err);
      toast.danger("关联失败");
    } finally {
      setIsAttachingAsset(false);
    }
  };

  return (
    <div className="relative flex h-full flex-col gap-3">
      {/* Header Card - Collapsible */}
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
                <NextImage src={data.coverUrl} alt="封面" fill className="object-cover" sizes="120px" />
              </div>
            )}
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <MapPin className="size-4 shrink-0 text-emerald-500" />
                <h2 className="truncate text-sm font-semibold">{data.name}</h2>
              </div>
              {isHeaderCollapsed && (
                <p className="mt-0.5 truncate text-xs text-muted">
                  {[
                    environmentEdit.location,
                    environmentEdit.timeOfDay,
                    `v${data.versionNumber}`,
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
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
                    onPress={() => openPanel({
                      entityType: "SCENE",
                      entityId: data.id,
                      entityName: data.name,
                      entityDescription: data.description || undefined,
                      entityFixedDesc: data.fixedDesc || undefined,
                      entityCoverUrl: data.coverUrl || undefined,
                      scriptId,
                    }, "IMAGE")}
                  >
                    <Sparkles className="size-3.5 text-accent" />
                  </Button>
                  <Tooltip.Content>AI 生成场景图</Tooltip.Content>
                </Tooltip>
                <Tooltip delay={0}>
                  <Button
                    variant="ghost"
                    size="sm"
                    isIconOnly
                    className="size-7"
                    onPress={() => {
                      setShowVersionPanel(true);
                      fetchVersions();
                    }}
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
          <div className="p-4 pt-3">
            <div className="min-w-0 flex-1">
              {isEditingInfo ? (
                <div className="space-y-3">
                  <div className="space-y-1">
                    <Label className="text-xs text-muted">名称</Label>
                    <Input
                      variant="secondary"
                      value={editForm.name}
                      onChange={(e) => setEditForm((prev) => ({ ...prev, name: e.target.value }))}
                      className="w-full"
                      placeholder="输入场景名称"
                    />
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs text-muted">描述</Label>
                    <Input
                      variant="secondary"
                      value={editForm.description}
                      onChange={(e) => setEditForm((prev) => ({ ...prev, description: e.target.value }))}
                      className="w-full"
                      placeholder="输入场景描述"
                    />
                  </div>
                  <div className="flex justify-end gap-2">
                    <Button variant="ghost" size="sm" onPress={() => {
                      setIsEditingInfo(false);
                      onStopEditing?.();
                    }} isDisabled={isSaving}>
                      取消
                    </Button>
                    <Button variant="tertiary" size="sm" className="gap-1.5" onPress={handleSaveInfo} isDisabled={isSaving}>
                      {isSaving ? <Loader2 className="size-3.5 animate-spin" /> : null}
                      保存
                    </Button>
                  </div>
                </div>
              ) : (
                <>
                  {data.description && <p className="text-sm text-muted">{data.description}</p>}
                  {!data.description && <p className="text-sm text-muted/40">暂无描述</p>}
                  <div className="mt-auto flex flex-wrap items-center gap-x-4 gap-y-1 pt-3 text-[11px] text-muted/70">
                    <span>创建: {formatDate(data.createdAt)}</span>
                    <span>更新: {formatDate(data.updatedAt)}</span>
                    <span>版本: v{data.versionNumber}</span>
                  </div>
                </>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Fixed Description + Voice Section - Side by Side 1:1 */}
      <div className="grid shrink-0 grid-cols-2 gap-3">
        {/* Fixed Description Card */}
        <div className="rounded-2xl border border-accent/20 bg-gradient-to-br from-accent/5 to-transparent transition-all duration-300">
          <div className="flex items-center justify-between gap-3 px-4 py-3">
            <div className="flex items-center gap-2">
              <Sparkles className="size-4 text-accent" />
              <span className="text-xs font-semibold text-accent">固定提示词</span>
              <span className="text-[10px] text-muted">用于 AI 生成</span>
            </div>
            <div className="flex items-center gap-1">
              {!isEditingFixedDesc && (
                <>
                  <Tooltip delay={0}>
                    <Button
                      variant="ghost"
                      size="sm"
                      isIconOnly
                      className="size-7"
                      onPress={() => openPanel({
                        entityType: "SCENE",
                        entityId: data.id,
                        entityName: data.name,
                        entityDescription: data.description || undefined,
                        entityFixedDesc: data.fixedDesc || undefined,
                        entityCoverUrl: data.coverUrl || undefined,
                        scriptId,
                      }, "IMAGE")}
                    >
                      <Sparkles className="size-3.5 text-accent" />
                    </Button>
                    <Tooltip.Content>AI 生成</Tooltip.Content>
                  </Tooltip>
                  <Tooltip delay={0}>
                    <Button
                      variant="ghost"
                      size="sm"
                      isIconOnly
                      className="size-7"
                      isDisabled={isReadOnly}
                      onPress={() => {
                        setIsEditingFixedDesc(true);
                        onStartEditing?.();
                      }}
                    >
                      <Edit3 className="size-3.5" />
                    </Button>
                    <Tooltip.Content>编辑</Tooltip.Content>
                  </Tooltip>
                </>
              )}
            </div>
          </div>
          <div className="px-4 pb-4">
            {isEditingFixedDesc ? (
              <div className="space-y-3">
                <TextArea
                  variant="secondary"
                  value={fixedDescEdit}
                  onChange={(e) => setFixedDescEdit(e.target.value)}
                  className="min-h-20 w-full text-xs"
                  placeholder="输入固定提示词，如场景风格、光线等，将自动填充到 AI 生成的提示词输入框"
                />
                <div className="flex justify-end gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    onPress={() => {
                      setIsEditingFixedDesc(false);
                      setFixedDescEdit(data.fixedDesc || "");
                      onStopEditing?.();
                    }}
                    isDisabled={isSaving}
                  >
                    取消
                  </Button>
                  <Button variant="tertiary" size="sm" className="gap-1.5" onPress={handleSaveFixedDesc} isDisabled={isSaving}>
                    {isSaving ? <Loader2 className="size-3.5 animate-spin" /> : null}
                    保存
                  </Button>
                </div>
              </div>
            ) : data.fixedDesc ? (
              <p className="text-sm leading-relaxed text-foreground/80">{data.fixedDesc}</p>
            ) : (
              <p className="text-sm text-muted/50">
                暂无固定提示词，点击编辑添加
              </p>
            )}
          </div>
        </div>

        {/* Voice Section - Ambient Audio */}
        {scriptId && (
          <VoiceSection
            entityType="SCENE"
            entityId={data.id}
            voiceUrl={data.voiceUrl}
            voiceAssetId={data.voiceAssetId}
            scriptId={scriptId}
            onVoiceChanged={onUpdate}
          />
        )}
      </div>

      {/* Scrollable Content */}
      <ScrollShadow className="min-h-0 flex-1" hideScrollBar>
        <div className="space-y-4">
          {/* Related Assets Section */}
          <div className="rounded-xl bg-muted/5 p-4">
            <input
              ref={assetFileInputRef}
              type="file"
              accept="image/*,video/*,audio/*"
              multiple
              className="hidden"
              onChange={(e) => {
                if (e.target.files && e.target.files.length > 0) {
                  handleAssetUpload(e.target.files);
                }
              }}
            />

            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <h4 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted">
                  <Image className="size-3.5 text-accent" />
                  参考素材 ({filteredAssets.length})
                </h4>
                <ButtonGroup size="sm" variant="ghost" className="h-6">
                  {[
                    { key: "ALL" as const, label: "全部" },
                    { key: "IMAGE" as const, label: "图片" },
                    { key: "VIDEO" as const, label: "视频" },
                    { key: "AUDIO" as const, label: "音频" },
                    { key: "OTHER" as const, label: "其它" },
                  ].map(({ key, label }) => (
                    <Button
                      key={key}
                      variant={assetFilter === key ? "primary" : undefined}
                      className="h-6 min-w-0 px-2 text-[10px]"
                      onPress={() => setAssetFilter(key)}
                    >
                      {label}
                    </Button>
                  ))}
                </ButtonGroup>
              </div>
              <div className="flex items-center gap-1">
                <Tooltip delay={0}>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 gap-1.5 px-2 text-xs"
                    onPress={() => {
                      setIsUnattachedPickerOpen(true);
                      fetchUnattachedAssets();
                    }}
                  >
                    <Link2 className="size-3.5" />
                    关联素材
                  </Button>
                  <Tooltip.Content>从剧本游离素材中关联</Tooltip.Content>
                </Tooltip>
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-7 gap-1.5 px-2 text-xs"
                  onPress={() => assetFileInputRef.current?.click()}
                  isDisabled={isUploadingAsset}
                >
                  {isUploadingAsset ? (
                    <Loader2 className="size-3.5 animate-spin" />
                  ) : (
                    <Upload className="size-3.5" />
                  )}
                  上传素材
                </Button>
              </div>
            </div>

            {isLoadingAssets ? (
              <AssetsSectionSkeleton />
            ) : filteredAssets.length === 0 ? (
              <div className="flex h-20 flex-col items-center justify-center gap-1 text-muted/50">
                <Image className="size-6" />
                <span className="text-xs">暂无参考素材</span>
              </div>
            ) : (
              <div className="grid auto-rows-auto grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-3">
                {filteredAssets.map((relation) => (
                  <AssetCard
                    key={relation.id}
                    relation={relation}
                    scriptId={scriptId}
                    workspaceId={currentWorkspaceId || undefined}
                    entityType="SCENE"
                    entityId={data.id}
                    refImages={filteredAssets
                      .filter(r => r.asset && r.asset.assetType === "IMAGE" && r.asset.fileUrl)
                      .map(r => r.asset.fileUrl!)}
                    onDragStart={handleAssetDragStart}
                    onDragEnd={handleAssetDragEnd}
                    onDelete={handleDeleteAssetRelation}
                    onSave={handleSaveEditedImage}
                  />
                ))}
              </div>
            )}
          </div>

          {/* Environment Section - Collapsible */}
          <div className="rounded-xl bg-muted/5">
            <div
              className="flex cursor-pointer items-center justify-between p-4"
              onClick={() => !isEditingEnvironment && setIsEnvironmentCollapsed(!isEnvironmentCollapsed)}
            >
              <h4 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted">
                <Sun className="size-3.5 text-accent" />
                环境设定
                {isEnvironmentCollapsed && (
                  <span className="ml-1 text-[10px] font-normal normal-case text-muted/50">
                    (点击展开)
                  </span>
                )}
              </h4>
              <div className="flex items-center gap-1">
                {!isEnvironmentCollapsed && (
                  isEditingEnvironment ? (
                    <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
                      <Button variant="ghost" size="sm" className="h-6 px-2 text-xs" onPress={() => {
                        setIsEditingEnvironment(false);
                        onStopEditing?.();
                      }}>
                        取消
                      </Button>
                      <Button variant="tertiary" size="sm" className="h-6 px-2 text-xs" onPress={handleSaveEnvironment} isDisabled={isSaving}>
                        保存
                      </Button>
                    </div>
                  ) : (
                    <Button
                      variant="ghost"
                      size="sm"
                      isIconOnly
                      className="size-6"
                      isDisabled={isReadOnly}
                      onClick={(e) => e.stopPropagation()}
                      onPress={() => {
                        setIsEditingEnvironment(true);
                        onStartEditing?.();
                      }}
                    >
                      <Edit3 className="size-3" />
                    </Button>
                  )
                )}
                <Button variant="ghost" size="sm" isIconOnly className="size-6">
                  {isEnvironmentCollapsed ? <ChevronDown className="size-3.5" /> : <ChevronUp className="size-3.5" />}
                </Button>
              </div>
            </div>

            {!isEnvironmentCollapsed && (
              <div className="border-t border-muted/10 p-4 pt-3">
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  {ENVIRONMENT_FIELDS.map(({ key, label, placeholder }) => (
                    <div key={key} className="rounded-lg bg-background/50 p-2.5">
                      <div className="flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-wide text-muted/70">
                        {key === "timeOfDay" && <Clock className="size-3" />}
                        {key === "weather" && <Cloud className="size-3" />}
                        {key === "temperature" && <Thermometer className="size-3" />}
                        {key === "lighting" && <Lightbulb className="size-3" />}
                        {key === "location" && <MapPin className="size-3" />}
                        {key === "season" && <Sun className="size-3" />}
                        {label}
                      </div>
                      {isEditingEnvironment ? (
                        <Input
                          variant="secondary"
                          className="mt-1 h-7 text-xs"
                          placeholder={placeholder}
                          value={environmentEdit[key as keyof typeof environmentEdit]}
                          onChange={(e) => setEnvironmentEdit((prev) => ({ ...prev, [key]: e.target.value }))}
                        />
                      ) : (
                        <p className="mt-1 text-sm text-foreground">
                          {environmentEdit[key as keyof typeof environmentEdit] || (
                            <span className="text-muted/40">未设置</span>
                          )}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Atmosphere Section - Collapsible */}
          <div className="rounded-xl bg-muted/5">
            <div
              className="flex cursor-pointer items-center justify-between p-4"
              onClick={() => !isEditingAtmosphere && setIsAtmosphereCollapsed(!isAtmosphereCollapsed)}
            >
              <h4 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted">
                <Volume2 className="size-3.5 text-accent" />
                氛围描述
                {isAtmosphereCollapsed && (
                  <span className="ml-1 text-[10px] font-normal normal-case text-muted/50">
                    (点击展开)
                  </span>
                )}
              </h4>
              <div className="flex items-center gap-1">
                {!isAtmosphereCollapsed && (
                  isEditingAtmosphere ? (
                    <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
                      <Button variant="ghost" size="sm" className="h-6 px-2 text-xs" onPress={() => {
                        setIsEditingAtmosphere(false);
                        onStopEditing?.();
                      }}>
                        取消
                      </Button>
                      <Button variant="tertiary" size="sm" className="h-6 px-2 text-xs" onPress={handleSaveAtmosphere} isDisabled={isSaving}>
                        保存
                      </Button>
                    </div>
                  ) : (
                    <Button
                      variant="ghost"
                      size="sm"
                      isIconOnly
                      className="size-6"
                      isDisabled={isReadOnly}
                      onClick={(e) => e.stopPropagation()}
                      onPress={() => {
                        setIsEditingAtmosphere(true);
                        onStartEditing?.();
                      }}
                    >
                      <Edit3 className="size-3" />
                    </Button>
                  )
                )}
                <Button variant="ghost" size="sm" isIconOnly className="size-6">
                  {isAtmosphereCollapsed ? <ChevronDown className="size-3.5" /> : <ChevronUp className="size-3.5" />}
                </Button>
              </div>
            </div>

            {!isAtmosphereCollapsed && (
              <div className="border-t border-muted/10 p-4 pt-3">
                <div className="grid grid-cols-3 gap-3">
                  {ATMOSPHERE_FIELDS.map(({ key, label, placeholder }) => (
                    <div key={key} className="rounded-lg bg-background/50 p-2.5">
                      <div className="text-[10px] font-medium uppercase tracking-wide text-muted/70">
                        {label}
                      </div>
                      {isEditingAtmosphere ? (
                        <Input
                          variant="secondary"
                          className="mt-1 h-7 text-xs"
                          placeholder={placeholder}
                          value={atmosphereEdit[key as keyof typeof atmosphereEdit]}
                          onChange={(e) => setAtmosphereEdit((prev) => ({ ...prev, [key]: e.target.value }))}
                        />
                      ) : (
                        <p className="mt-1 text-sm text-foreground">
                          {atmosphereEdit[key as keyof typeof atmosphereEdit] || (
                            <span className="text-muted/40">未设置</span>
                          )}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      </ScrollShadow>

      {/* Version Panel Overlay */}
      {showVersionPanel && (
        <div className="absolute inset-0 z-10 rounded-2xl bg-background/95 backdrop-blur">
          <div className="flex h-full flex-col">
            <div className="flex shrink-0 items-center justify-between border-b border-muted/10 px-4 py-3">
              <h3 className="text-sm font-semibold">版本历史</h3>
              <Button variant="ghost" size="sm" isIconOnly className="size-7" onPress={() => setShowVersionPanel(false)}>
                <X className="size-4" />
              </Button>
            </div>
            <ScrollShadow className="min-h-0 flex-1 p-4" hideScrollBar>
              {isLoadingVersions ? (
                <VersionListSkeleton />
              ) : versions.length > 0 ? (
                <div className="space-y-2">
                  {versions.map((version) => (
                    <div
                      key={version.id}
                      className={`rounded-xl p-3 ${
                        version.isCurrent ? "bg-accent/10 ring-1 ring-accent/30" : "bg-muted/5 hover:bg-muted/10"
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-medium">v{version.versionNumber}</span>
                            {version.isCurrent && (
                              <span className="rounded-full bg-accent-soft-hover px-1.5 py-0.5 text-[10px] font-medium text-accent">
                                当前版本
                              </span>
                            )}
                          </div>
                          <p className="mt-1 text-xs text-muted">{version.changeSummary || "无变更说明"}</p>
                          <p className="mt-1 text-[11px] text-muted/70">{formatDate(version.createdAt)}</p>
                        </div>
                        {!version.isCurrent && (
                          <Button
                            variant="ghost"
                            size="sm"
                            className="shrink-0 gap-1 text-xs"
                            isPending={restoringVersion === version.versionNumber}
                            isDisabled={restoringVersion !== null}
                            onPress={() => handleRestoreVersion(version.versionNumber)}
                          >
                            {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}恢复</>)}
                          </Button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="py-8 text-center text-sm text-muted">暂无版本历史</div>
              )}
            </ScrollShadow>
          </div>
        </div>
      )}

      {/* Unattached Asset Picker Modal */}
      <Modal.Backdrop isOpen={isUnattachedPickerOpen} onOpenChange={(open) => !open && setIsUnattachedPickerOpen(false)}>
        <Modal.Container>
          <Modal.Dialog>
            <Modal.Header>
              <Modal.Heading className="flex items-center gap-2">
                <Link2 className="size-4 text-accent" />
                关联游离素材
              </Modal.Heading>
            </Modal.Header>
            <Modal.Body className="max-h-[60vh] p-0">
              <ScrollShadow className="max-h-[50vh]" hideScrollBar>
                {isLoadingUnattached ? (
                  <div className="flex items-center justify-center py-8">
                    <Loader2 className="size-5 animate-spin text-muted" />
                  </div>
                ) : unattachedAssets.length === 0 ? (
                  <div className="flex flex-col items-center justify-center py-8 text-muted">
                    <Image className="mb-2 size-8 opacity-20" />
                    <p className="text-xs">暂无游离素材</p>
                  </div>
                ) : (
                  <div className="grid grid-cols-4 gap-1.5 p-2">
                    {unattachedAssets.map((asset) => (
                      <button
                        key={asset.id}
                        className="group relative aspect-video overflow-hidden rounded-lg bg-muted/10 transition-all hover:ring-2 hover:ring-accent"
                        onClick={() => handleAttachAsset(asset.id)}
                        disabled={isAttachingAsset}
                      >
                        {asset.assetType === "IMAGE" && asset.fileUrl ? (
                          <NextImage src={asset.fileUrl} alt={asset.name} fill className="object-cover" sizes="(min-width: 768px) 25vw, 50vw" />
                        ) : asset.assetType === "VIDEO" && asset.fileUrl ? (
                          <video src={asset.fileUrl} className="size-full object-cover" muted />
                        ) : (
                          <div className="flex size-full items-center justify-center">
                            <Image className="size-6 text-muted/30" />
                          </div>
                        )}
                        <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/60 to-transparent px-1.5 py-1">
                          <p className="truncate text-[10px] text-white">{asset.name}</p>
                        </div>
                        <div className="absolute inset-0 flex items-center justify-center bg-black/40 opacity-0 transition-opacity group-hover:opacity-100">
                          <span className="rounded-full bg-accent px-2 py-0.5 text-[10px] font-medium text-white">关联</span>
                        </div>
                      </button>
                    ))}
                  </div>
                )}
              </ScrollShadow>
            </Modal.Body>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </div>
  );
}

// Scene Detail View with sidebar
function SceneDetailView({
  items,
  selectedId,
  onSelect,
  onDelete,
  isSidebarCollapsed,
  onToggleSidebar,
  onRefresh,
  scriptId,
}: {
  items: EntityItem[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
  isSidebarCollapsed: boolean;
  onToggleSidebar: () => void;
  onRefresh: () => void;
  scriptId: string;
}) {
  const locale = useLocale();
  const { currentWorkspaceId } = useWorkspace();
  const { updateEntity, activePanel } = useAIGeneration();
  const [sceneData, setSceneData] = useState<SceneDetailDTO | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isReadOnlyMode, setIsReadOnlyMode] = useState(false);

  const selectedItem = items.find((item) => item.id === selectedId) || items[0];

  // Comment panel integration
  const openCommentPanel = useCommentPanelStore((s) => s.open);
  const setCommentTarget = useCommentPanelStore((s) => s.setTarget);
  const isCommentPanelOpen = useCommentPanelStore((s) => s.isOpen);

  useEffect(() => {
    if (!isCommentPanelOpen || !selectedItem) return;
    setCommentTarget({ type: "SCENE", id: selectedItem.id, name: selectedItem.title, scriptId });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedItem?.id, isCommentPanelOpen, scriptId]);

  const handleCommentOpen = useCallback(
    (id: string) => {
      const item = items.find((i) => i.id === id);
      if (!item) return;
      openCommentPanel({ type: "SCENE", id: item.id, name: item.title, scriptId });
    },
    [items, scriptId, openCommentPanel]
  );

  // Collaboration hook
  const {
    getEntityCollaborators,
    refreshEntityState,
    startEditing,
    stopEditing,
    focusEntity,
    blurEntity,
  } = useCollaboration(scriptId, "scenes");

  // Use refs to avoid effect re-running due to function reference changes
  const focusEntityRef = useRef(focusEntity);
  const blurEntityRef = useRef(blurEntity);
  useEffect(() => {
    focusEntityRef.current = focusEntity;
    blurEntityRef.current = blurEntity;
  });

  // Get collaboration state for selected entity
  const collaborators = selectedItem?.id
    ? getEntityCollaborators("SCENE", selectedItem.id)
    : { editor: null, lockedAt: null, isLockedByOther: false };

  // Focus entity when selection changes
  useEffect(() => {
    if (selectedItem?.id) {
      focusEntityRef.current("SCENE", selectedItem.id);
    }
    return () => {
      if (selectedItem?.id) {
        blurEntityRef.current("SCENE", selectedItem.id);
      }
    };
  }, [selectedItem?.id]);

  // Fetch scene detail when selection changes
  const fetchSceneDetail = useCallback(async () => {
    if (!currentWorkspaceId || !selectedItem?.id) return;

    try {
      setIsLoading(true);
      const data = await projectService.getScene( selectedItem.id);
      setSceneData(data);
      setIsReadOnlyMode(false); // Reset read-only mode on new selection
    } catch (err) {
      console.error("Failed to fetch scene detail:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsLoading(false);
    }
  }, [currentWorkspaceId, selectedItem?.id]);

  useEffect(() => {
    fetchSceneDetail();
  }, [fetchSceneDetail]);

  // Auto-sync with AI generation panel when scene changes
  useEffect(() => {
    if (activePanel === "ai-generation" && sceneData) {
      updateEntity({
        entityType: "SCENE",
        entityId: sceneData.id,
        entityName: sceneData.name,
        entityDescription: sceneData.description || undefined,
        entityFixedDesc: sceneData.fixedDesc || undefined,
        entityCoverUrl: sceneData.coverUrl || undefined,
        scriptId,
      });
    }
  }, [sceneData?.id, activePanel, updateEntity, scriptId]);

  if (items.length === 0) {
    return <EmptyState icon={<Film className="size-16" />} title="暂无场景" />;
  }

  return (
    <div className="flex h-full gap-3">
      {/* Sidebar */}
      <DetailSidebar
        items={items}
        selectedId={selectedId}
        onSelect={onSelect}
        onDelete={onDelete}
        onComment={handleCommentOpen}
        isCollapsed={isSidebarCollapsed}
        onToggle={onToggleSidebar}
        placeholderIcon={<MapPin className="size-5 text-emerald-500/30" />}
        entityTypeLabel="场景"
      />

      {/* Detail Content */}
      <div className="relative min-w-0 flex-1 overflow-hidden rounded-xl">
        {isLoading ? (
          <DetailPaneSkeleton />
        ) : sceneData ? (
          <>
            <SceneDetailContent
              data={sceneData}
              scriptId={scriptId}
              onUpdate={fetchSceneDetail}
              onStartEditing={() => startEditing("SCENE", selectedItem!.id)}
              onStopEditing={() => stopEditing("SCENE", selectedItem!.id)}
              isReadOnly={isReadOnlyMode}
            />
            {/* Editing Overlay - show when locked by others and not in read-only mode */}
            {collaborators.isLockedByOther && collaborators.editor && !isReadOnlyMode && (
              <EditingOverlay
                editor={collaborators.editor}
                lockedAt={collaborators.lockedAt}
                timeoutMinutes={5}
                onViewReadOnly={() => setIsReadOnlyMode(true)}
                onRefresh={() => refreshEntityState("SCENE", selectedItem!.id)}
                onForceEdit={() => startEditing("SCENE", selectedItem!.id)}
              />
            )}
          </>
        ) : (
          <div className="flex h-full items-center justify-center text-sm text-muted">
            请选择一个场景
          </div>
        )}
      </div>
    </div>
  );
}

// Scene Tab Component
export function SceneTab({ scriptId }: SceneTabProps) {
  const locale = useLocale();
  const { currentWorkspaceId } = useWorkspace();
  const [viewMode, setViewMode] = useState<ViewMode>(() => getStoredViewMode("scenes"));
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [items, setItems] = useState<EntityItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(() => getStoredSidebarCollapsed("scenes"));
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [showPicker, setShowPicker] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [sortKeys, setSortKeys] = useState<SortKey[]>(["updatedAt_desc"]);

  // Create form state
  const [createForm, setCreateForm] = useState({
    name: "",
    description: "",
  });

  const resetCreateForm = () => {
    setCreateForm({ name: "", description: "" });
  };

  // Fetch scenes
  const fetchScenes = useCallback(async () => {
    if (!currentWorkspaceId || !scriptId) return;

    try {
      setIsLoading(true);
      setError(null);

      const scenes = await projectService.getScenesByScript( scriptId);
      const entityItems = scenes.map((item) => transformToEntityItem(item, "scenes"));

      const countResults = await Promise.allSettled(
        scenes.map((s) => commentService.getCommentCount("SCENE", s.id))
      );
      countResults.forEach((result, idx) => {
        if (result.status === "fulfilled" && result.value > 0) {
          entityItems[idx].commentCount = result.value;
        }
      });

      setItems(entityItems);
    } catch (err) {
      console.error("Failed to fetch scenes:", err);
      toast.danger(getErrorFromException(err, locale));
      setError("加载场景失败");
    } finally {
      setIsLoading(false);
    }
  }, [currentWorkspaceId, scriptId]);

  useEffect(() => {
    fetchScenes();
  }, [fetchScenes]);

  // Auto-refresh when scenes are changed by other collaborators via WebSocket
  useDebouncedEntityChanges(
    "scene",
    fetchScenes,
    { onDeleted: (id) => { if (selectedId === id) setSelectedId(null); } },
    [fetchScenes, selectedId]
  );

  const handleSelect = (id: string) => {
    setSelectedId(id);
    if (viewMode !== "detail") {
      setViewMode("detail");
      saveViewMode("scenes", "detail");
    }
  };

  const handleViewModeChange = (mode: ViewMode) => {
    setViewMode(mode);
    saveViewMode("scenes", mode);
  };

  const handleToggleSidebar = () => {
    const newCollapsed = !isSidebarCollapsed;
    setIsSidebarCollapsed(newCollapsed);
    saveSidebarCollapsed("scenes", newCollapsed);
  };

  // Filter items
  const filteredItems = sortItems(
    items.filter((item) => {
      if (!searchKeyword.trim()) return true;
      const keyword = searchKeyword.toLowerCase();
      return (
        item.title.toLowerCase().includes(keyword) ||
        item.description?.toLowerCase().includes(keyword)
      );
    }),
    sortKeys,
  );

  // Create scene
  const handleCreate = async () => {
    if (!currentWorkspaceId || !createForm.name.trim()) return;

    try {
      setIsCreating(true);
      await projectService.createScene( {
        scope: "SCRIPT",
        scriptId,
        name: createForm.name.trim(),
        description: createForm.description.trim() || undefined,
      });

      setShowCreateForm(false);
      resetCreateForm();
      fetchScenes();
    } catch (err) {
      console.error("Failed to create scene:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsCreating(false);
    }
  };

  // Edit scene - switch to detail view
  const handleEdit = (id: string) => {
    setSelectedId(id);
    setViewMode("detail");
    saveViewMode("scenes", "detail");
  };

  // Delete scene
  const handleDelete = async (id: string) => {
    if (!currentWorkspaceId) return;

    try {
      await projectService.deleteScene( id);
      if (selectedId === id) {
        setSelectedId(items[0]?.id || "");
      }
      fetchScenes();
    } catch (err) {
      console.error("Failed to delete scene:", err);
      toast.danger(getErrorFromException(err, locale));
    }
  };

  if (isLoading && items.length === 0) {
    return <EntityTabSkeleton />;
  }

  if (error) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 text-muted">
        <p>{error}</p>
        <Button variant="ghost" size="sm" onPress={fetchScenes}>
          重新加载
        </Button>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      {/* Toolbar */}
      <Surface variant="secondary" className="rounded-2xl mb-3 flex shrink-0 items-center justify-between gap-3 p-2">
        <div className="flex items-center gap-3">
          {/* View Mode Toggle */}
          <ButtonGroup size="sm" variant="ghost">
            <Button
              variant={viewMode === "list" ? "primary" : undefined}
              isIconOnly
              aria-label="列表视图"
              onPress={() => handleViewModeChange("list")}
            >
              <List className="size-4" />
            </Button>
            <Button
              variant={viewMode === "grid" ? "primary" : undefined}
              isIconOnly
              aria-label="卡片视图"
              onPress={() => handleViewModeChange("grid")}
            >
              <LayoutGrid className="size-4" />
            </Button>
            <Button
              variant={viewMode === "detail" ? "primary" : undefined}
              isIconOnly
              aria-label="详情视图"
              onPress={() => handleViewModeChange("detail")}
            >
              <PanelLeft className="size-4" />
            </Button>
          </ButtonGroup>

          <Separator variant="secondary" orientation="vertical" className="h-5 self-center" />

          {/* Search */}
          <SearchField aria-label="搜索场景" value={searchKeyword} onChange={setSearchKeyword}>
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-36" placeholder="搜索场景..." />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>

          {searchKeyword && (
            <span className="text-xs text-muted">
              {filteredItems.length} / {items.length}
            </span>
          )}

          <Separator variant="secondary" orientation="vertical" className="h-5 self-center" />

          {/* Sort */}
          <SortButtons value={sortKeys} onChange={setSortKeys} />
        </div>

        {/* Right Section */}
        <div className="flex items-center gap-1">
          <Tooltip delay={0}>
            <Button variant="ghost" size="sm" className="size-8 p-0 text-muted hover:text-foreground" onPress={fetchScenes}>
              <RefreshCw className={`size-4 ${isLoading ? "animate-spin" : ""}`} />
            </Button>
            <Tooltip.Content>刷新</Tooltip.Content>
          </Tooltip>

          <Separator orientation="vertical" className="mx-0.5 h-5 self-center" />

          <Button
            variant="ghost"
            size="sm"
            className="gap-1.5 text-xs"
            onPress={() => setShowPicker(true)}
          >
            <Link2 className="size-3.5" />
            关联场景
          </Button>

          <Button
            variant="primary"
            size="sm"
            className="gap-1.5 text-xs"
            onPress={() => setShowCreateForm(true)}
          >
            <Plus className="size-3.5" />
            新增场景
          </Button>
        </div>
      </Surface>

      <EntityPickerModal
        isOpen={showPicker}
        onOpenChange={setShowPicker}
        entityType="scenes"
        scriptId={scriptId}
        onImported={fetchScenes}
      />

      {/* Content */}
      <div className="min-h-0 flex-1">
        {viewMode === "list" && (
          <ScrollShadow className="h-full" hideScrollBar>
            <EntityListView
              items={filteredItems}
              onSelect={handleSelect}
              onEdit={handleEdit}
              onDelete={handleDelete}
            />
          </ScrollShadow>
        )}
        {viewMode === "grid" && (
          <ScrollShadow className="h-full" hideScrollBar>
            <EntityGridView
              items={filteredItems}
              onSelect={handleSelect}
              onEdit={handleEdit}
              onDelete={handleDelete}
            />
          </ScrollShadow>
        )}
        {viewMode === "detail" && (
          <SceneDetailView
            items={filteredItems}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onDelete={handleDelete}
            isSidebarCollapsed={isSidebarCollapsed}
            onToggleSidebar={handleToggleSidebar}
            onRefresh={fetchScenes}
            scriptId={scriptId}
          />
        )}
      </div>

      {/* Create Form Modal */}
      <Modal.Backdrop
        isOpen={showCreateForm}
        onOpenChange={(open) => {
          setShowCreateForm(open);
          if (!open) resetCreateForm();
        }}
        variant="blur"
      >
        <Modal.Container size="sm">
          <Modal.Dialog>
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Icon className="bg-emerald-500/10 text-emerald-500">
                <MapPin className="size-5" />
              </Modal.Icon>
              <Modal.Heading>新增场景</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="overflow-visible">
              <div className="space-y-4">
                <div className="space-y-1.5">
                  <Label className="text-xs font-medium text-muted">名称 *</Label>
                  <Input
                    variant="secondary"
                    value={createForm.name}
                    onChange={(e) => setCreateForm((prev) => ({ ...prev, name: e.target.value }))}
                    className="w-full"
                    placeholder="输入场景名称"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-xs font-medium text-muted">描述</Label>
                  <TextArea
                    variant="secondary"
                    value={createForm.description}
                    onChange={(e) => setCreateForm((prev) => ({ ...prev, description: e.target.value }))}
                    className="min-h-20 w-full"
                    placeholder="场景描述（可选）"
                  />
                </div>
              </div>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" slot="close" isDisabled={isCreating}>
                取消
              </Button>
              <Button
                variant="primary"
                className="gap-1.5"
                onPress={handleCreate}
                isDisabled={isCreating || !createForm.name.trim()}
              >
                {isCreating ? <Loader2 className="size-3.5 animate-spin" /> : <Plus className="size-3.5" />}
                创建
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </div>
  );
}

export default SceneTab;
