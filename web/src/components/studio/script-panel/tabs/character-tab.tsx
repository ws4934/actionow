"use client";

/**
 * Character Tab Component
 * Dedicated tab component for managing characters with list/grid/detail views
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
  Select,
  ListBox,
  Spinner,
  Separator,
  SearchField,
  Surface,
  toast,
} from "@heroui/react";
import {
  User,
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
  Ruler,
  Sparkles,
  Heart,
  Image,
  Upload,
  UserCircle,
  Shirt,
  GripVertical,
  Link2,
} from "lucide-react";
import NextImage from "@/components/ui/content-image";
import { projectService, getErrorFromException} from "@/lib/api";
import { commentService } from "@/lib/api/services/comment.service";
import type { CharacterListDTO, CharacterDetailDTO, VersionInfoDTO, EntityAssetRelationDTO, AssetListDTO } from "@/lib/api/dto";
import { useWorkspace } from "@/components/providers/workspace-provider";
import { useAIGeneration } from "@/components/providers/ai-generation-provider";
import { useDebouncedEntityChanges } from "@/lib/websocket";
import { useDragDropActions, createAssetDragData, ASSET_DRAG_TYPE } from "@/lib/stores/drag-drop-store";
import { useCommentPanelStore } from "@/lib/stores/comment-panel-store";
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
import { EditingOverlay } from "../components";
import { useCollaboration } from "../hooks/use-collaboration";
import { useLocale } from "next-intl";

interface CharacterTabProps {
  scriptId: string;
}

// Appearance data field config
const APPEARANCE_FIELDS = {
  basic: [
    { key: "height", label: "身高", placeholder: "如: 175cm" },
    { key: "bodyType", label: "体型", placeholder: "如: 苗条、健壮" },
    { key: "skinTone", label: "肤色", placeholder: "如: 白皙、小麦色" },
  ],
  face: [
    { key: "faceShape", label: "脸型", placeholder: "如: 瓜子脸、方脸" },
    { key: "eyeShape", label: "眼形", placeholder: "如: 杏眼、丹凤眼" },
    { key: "eyeColor", label: "眼睛颜色", placeholder: "如: 黑色、棕色" },
  ],
  hair: [
    { key: "hairStyle", label: "发型", placeholder: "如: 长发、短发、马尾" },
    { key: "hairLength", label: "发长", placeholder: "如: 及腰、齐肩" },
    { key: "hairColor", label: "发色", placeholder: "如: 黑色、栗色" },
  ],
  style: [
    { key: "artStyle", label: "画风", placeholder: "如: 写实、动漫" },
  ],
};

// Character type options
const CHARACTER_TYPES = [
  { value: "PROTAGONIST", label: "主角" },
  { value: "ANTAGONIST", label: "反派" },
  { value: "SUPPORTING", label: "配角" },
  { value: "BACKGROUND", label: "路人" },
];

// Gender options
const GENDER_OPTIONS = [
  { value: "MALE", label: "男" },
  { value: "FEMALE", label: "女" },
  { value: "OTHER", label: "其他" },
];

// Detail Content Component with editable fields (matching storyboard layout)
function CharacterDetailContent({
  data,
  scriptId,
  onUpdate,
  onStartEditing,
  onStopEditing,
  isReadOnly = false,
}: {
  data: CharacterDetailDTO;
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
  const [isEditingAppearance, setIsEditingAppearance] = useState(false);
  const [isAppearanceCollapsed, setIsAppearanceCollapsed] = useState(true); // Collapsed by default
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
    age: data.age?.toString() || "",
    gender: data.gender || "",
    characterType: data.characterType || "",
  });

  // Edit form state - Fixed Description (separate)
  const [fixedDescEdit, setFixedDescEdit] = useState(data.fixedDesc || "");

  // Edit form state - Appearance
  const appearanceData = (data.appearanceData || {}) as Record<string, unknown>;
  const defaultOutfit = (appearanceData.defaultOutfit || {}) as Record<string, unknown>;

  const [appearanceEdit, setAppearanceEdit] = useState({
    height: (appearanceData.height as string) || "",
    bodyType: (appearanceData.bodyType as string) || "",
    skinTone: (appearanceData.skinTone as string) || "",
    faceShape: (appearanceData.faceShape as string) || "",
    eyeShape: (appearanceData.eyeShape as string) || "",
    eyeColor: (appearanceData.eyeColor as string) || "",
    hairStyle: (appearanceData.hairStyle as string) || "",
    hairLength: (appearanceData.hairLength as string) || "",
    hairColor: (appearanceData.hairColor as string) || "",
    artStyle: (appearanceData.artStyle as string) || "",
    outfitStyle: (defaultOutfit.style as string) || "",
    distinguishingFeatures: Array.isArray(appearanceData.distinguishingFeatures)
      ? appearanceData.distinguishingFeatures.join(", ")
      : (appearanceData.distinguishingFeatures as string) || "",
  });

  // Sync with data changes
  useEffect(() => {
    setEditForm({
      name: data.name || "",
      description: data.description || "",
      fixedDesc: data.fixedDesc || "",
      age: data.age?.toString() || "",
      gender: data.gender || "",
      characterType: data.characterType || "",
    });

    setFixedDescEdit(data.fixedDesc || "");

    const appearanceData = (data.appearanceData || {}) as Record<string, unknown>;
    const defaultOutfit = (appearanceData.defaultOutfit || {}) as Record<string, unknown>;

    setAppearanceEdit({
      height: (appearanceData.height as string) || "",
      bodyType: (appearanceData.bodyType as string) || "",
      skinTone: (appearanceData.skinTone as string) || "",
      faceShape: (appearanceData.faceShape as string) || "",
      eyeShape: (appearanceData.eyeShape as string) || "",
      eyeColor: (appearanceData.eyeColor as string) || "",
      hairStyle: (appearanceData.hairStyle as string) || "",
      hairLength: (appearanceData.hairLength as string) || "",
      hairColor: (appearanceData.hairColor as string) || "",
      artStyle: (appearanceData.artStyle as string) || "",
      outfitStyle: (defaultOutfit.style as string) || "",
      distinguishingFeatures: Array.isArray(appearanceData.distinguishingFeatures)
      ? appearanceData.distinguishingFeatures.join(", ")
      : (appearanceData.distinguishingFeatures as string) || "",
    });
  }, [data]);

  // Fetch related assets with auto-refresh for generating items
  useEffect(() => {
    const fetchRelatedAssets = async () => {
      if (!currentWorkspaceId || !data.id) return;
      try {
        setIsLoadingAssets(true);
        const assets = await projectService.getEntityAssetRelations( "CHARACTER", data.id);
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
        const assets = await projectService.getEntityAssetRelations( "CHARACTER", data.id);
        setRelatedAssets(assets);
        // If no more generating assets, interval will be cleared on next effect run
      } catch (err) {
        console.error("Failed to refresh assets:", err);
        toast.danger(getErrorFromException(err, locale));
      }
    }, 3000); // Refresh every 3 seconds

    return () => clearInterval(refreshInterval);
  }, [relatedAssets, currentWorkspaceId, data.id]);

  // Fetch versions
  const fetchVersions = async () => {
    if (!currentWorkspaceId) return;
    try {
      setIsLoadingVersions(true);
      const vers = await projectService.getCharacterVersions( data.id);
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
      await projectService.updateCharacter( data.id, {
        name: editForm.name || undefined,
        description: editForm.description || undefined,
        age: editForm.age ? parseInt(editForm.age) : undefined,
        gender: editForm.gender || undefined,
        characterType: editForm.characterType || undefined,
      });
      setIsEditingInfo(false);
      onStopEditing?.(); // Stop editing when saved
      // Sync with AI generation panel if it's showing this entity
      if (aiEntityId === data.id) {
        updateEntity({
          entityType: "CHARACTER",
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
      console.error("Failed to update character:", err);
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
      await projectService.updateCharacter( data.id, {
        fixedDesc: fixedDescEdit || undefined,
      });
      setIsEditingFixedDesc(false);
      onStopEditing?.();
      // Sync with AI generation panel if it's showing this entity
      if (aiEntityId === data.id) {
        updateEntity({
          entityType: "CHARACTER",
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

  // Save appearance
  const handleSaveAppearance = async () => {
    if (!currentWorkspaceId) return;
    try {
      setIsSaving(true);
      const features = appearanceEdit.distinguishingFeatures
        .split(",")
        .map(f => f.trim())
        .filter(f => f);

      await projectService.updateCharacter( data.id, {
        appearanceData: {
          height: appearanceEdit.height || null,
          bodyType: appearanceEdit.bodyType || null,
          skinTone: appearanceEdit.skinTone || null,
          faceShape: appearanceEdit.faceShape || null,
          eyeShape: appearanceEdit.eyeShape || null,
          eyeColor: appearanceEdit.eyeColor || null,
          hairStyle: appearanceEdit.hairStyle || null,
          hairLength: appearanceEdit.hairLength || null,
          hairColor: appearanceEdit.hairColor || null,
          artStyle: appearanceEdit.artStyle || null,
          defaultOutfit: {
            style: appearanceEdit.outfitStyle || null,
          },
          distinguishingFeatures: features.length > 0 ? features : [],
        },
      });
      setIsEditingAppearance(false);
      onStopEditing?.();
      onUpdate?.();
    } catch (err) {
      console.error("Failed to update appearance:", err);
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
        description: "角色封面图",
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
      await projectService.updateCharacter( data.id, {
        coverAssetId: initResponse.assetId,
      });
      // Sync cover with AI generation panel
      if (aiEntityId === data.id && confirmResult.fileUrl) {
        updateEntity({
          entityType: "CHARACTER",
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
      await projectService.updateCharacter( data.id, {
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
            description: "角色素材",
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
            entityType: "CHARACTER",
            entityId: data.id,
            assetId: initResponse.assetId,
            relationType: "REFERENCE",
          });
        })
      );

      // Refresh assets list
      const assets = await projectService.getEntityAssetRelations( "CHARACTER", data.id);
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
        description: "编辑后的角色素材",
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
        entityType: "CHARACTER",
        entityId: data.id,
        assetId: initResponse.assetId,
        relationType: "REFERENCE",
      });

      // Refresh assets
      const assets = await projectService.getEntityAssetRelations( "CHARACTER", data.id);
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
      await projectService.restoreCharacterVersion( data.id, {
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
        entityType: "CHARACTER",
        entityId: data.id,
        assetId,
        relationType: "DRAFT",
      });
      toast.success("素材关联成功");
      // Refresh related assets
      const assets = await projectService.getEntityAssetRelations("CHARACTER", data.id);
      setRelatedAssets(assets);
      // Remove attached asset from unattached list
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
      {/* Header Card - Collapsible (matching storyboard layout) */}
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
                <h2 className="truncate text-sm font-semibold">{data.name}</h2>
                {data.characterType && (
                  <span className="shrink-0 rounded-full bg-accent/10 px-1.5 py-0.5 text-[10px] text-accent">
                    {CHARACTER_TYPES.find(t => t.value === data.characterType)?.label || data.characterType}
                  </span>
                )}
              </div>
              {isHeaderCollapsed && (
                <p className="mt-0.5 truncate text-xs text-muted">
                  {[
                    data.age && `${data.age}岁`,
                    data.gender && GENDER_OPTIONS.find(g => g.value === data.gender)?.label,
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
                      entityType: "CHARACTER",
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
                  <Tooltip.Content>AI 生成立绘</Tooltip.Content>
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
                  <div className="grid grid-cols-3 gap-3">
                    <div className="space-y-1">
                      <Label className="text-xs text-muted">名称</Label>
                      <Input
                        variant="secondary"
                        value={editForm.name}
                        onChange={(e) => setEditForm((prev) => ({ ...prev, name: e.target.value }))}
                        className="w-full"
                        placeholder="输入角色名称"
                      />
                    </div>
                    <div className="space-y-1">
                      <Label className="text-xs text-muted">年龄</Label>
                      <Input
                        variant="secondary"
                        type="number"
                        value={editForm.age}
                        onChange={(e) => setEditForm((prev) => ({ ...prev, age: e.target.value }))}
                        className="w-full"
                        placeholder="年龄"
                        min="0"
                      />
                    </div>
                    <Select
                      className="w-full"
                      placeholder="未设置"
                      variant="secondary"
                      value={editForm.gender || null}
                      onChange={(value) => setEditForm((prev) => ({ ...prev, gender: value as string || "" }))}
                    >
                      <Label className="text-xs text-muted">性别</Label>
                      <Select.Trigger>
                        <Select.Value />
                        <Select.Indicator />
                      </Select.Trigger>
                      <Select.Popover>
                        <ListBox>
                          {GENDER_OPTIONS.map((opt) => (
                            <ListBox.Item key={opt.value} id={opt.value} textValue={opt.label}>
                              {opt.label}
                              <ListBox.ItemIndicator />
                            </ListBox.Item>
                          ))}
                        </ListBox>
                      </Select.Popover>
                    </Select>
                  </div>
                  <Select
                    fullWidth
                    placeholder="未设置"
                    variant="secondary"
                    value={editForm.characterType || null}
                    onChange={(value) => setEditForm((prev) => ({ ...prev, characterType: value as string || "" }))}
                  >
                    <Label className="text-xs text-muted">角色类型</Label>
                    <Select.Trigger>
                      <Select.Value />
                      <Select.Indicator />
                    </Select.Trigger>
                    <Select.Popover>
                      <ListBox>
                        {CHARACTER_TYPES.map((opt) => (
                          <ListBox.Item key={opt.value} id={opt.value} textValue={opt.label}>
                            {opt.label}
                            <ListBox.ItemIndicator />
                          </ListBox.Item>
                        ))}
                      </ListBox>
                    </Select.Popover>
                  </Select>
                  <div className="space-y-1">
                    <Label className="text-xs text-muted">描述</Label>
                    <Input
                      variant="secondary"
                      value={editForm.description}
                      onChange={(e) => setEditForm((prev) => ({ ...prev, description: e.target.value }))}
                      className="w-full"
                      placeholder="输入角色描述"
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
                  <div className="flex flex-wrap gap-2 text-xs">
                    {data.age && (
                      <span className="inline-flex items-center gap-1.5 rounded-full bg-muted/10 px-2.5 py-1">
                        {data.age} 岁
                      </span>
                    )}
                    {data.gender && (
                      <span className="inline-flex items-center gap-1.5 rounded-full bg-muted/10 px-2.5 py-1">
                        {GENDER_OPTIONS.find(g => g.value === data.gender)?.label || data.gender}
                      </span>
                    )}
                  </div>
                  {data.description && <p className="mt-2 text-sm text-muted">{data.description}</p>}
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
                        entityType: "CHARACTER",
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
                    <Tooltip.Content>{isReadOnly ? "只读模式" : "编辑"}</Tooltip.Content>
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
                  placeholder="输入固定提示词，如外观特征、风格等，将自动填充到 AI 生成的提示词输入框"
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

        {/* Voice Section */}
        {scriptId && (
          <VoiceSection
            entityType="CHARACTER"
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
            {/* Hidden file input for asset upload (supports batch) */}
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

            {/* Header with filter and upload button */}
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <h4 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted">
                  <Image className="size-3.5 text-accent" />
                  参考素材 ({filteredAssets.length})
                </h4>
                {/* Filter buttons */}
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

            {/* Assets content */}
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
                    entityType="CHARACTER"
                    entityId={data.id}
                    refImages={filteredAssets
                      .filter(r => r.asset && r.asset.assetType === "IMAGE" && r.asset.fileUrl)
                      .map(r => r.asset.fileUrl!)}
                    allAssets={relatedAssets}
                    currentCoverAssetId={data.coverAssetId}
                    onDragStart={handleAssetDragStart}
                    onDragEnd={handleAssetDragEnd}
                    onDelete={handleDeleteAssetRelation}
                    onSave={handleSaveEditedImage}
                    onPublishToggled={async () => {
                      // Refresh assets after publish status is toggled
                      if (currentWorkspaceId && data.id) {
                        const assets = await projectService.getEntityAssetRelations( "CHARACTER", data.id);
                        setRelatedAssets(assets);
                        // Also refresh main data to get updated cover
                        onUpdate?.();
                      }
                    }}
                  />
                ))}
              </div>
            )}
          </div>

          {/* Appearance Section - Collapsible */}
          <div className="rounded-xl bg-muted/5">
            <div
              className="flex cursor-pointer items-center justify-between p-4"
              onClick={() => !isEditingAppearance && setIsAppearanceCollapsed(!isAppearanceCollapsed)}
            >
              <h4 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted">
                <Sparkles className="size-3.5 text-accent" />
                外观特征
                {isAppearanceCollapsed && (
                  <span className="ml-1 text-[10px] font-normal normal-case text-muted/50">
                    (点击展开)
                  </span>
                )}
              </h4>
              <div className="flex items-center gap-1">
                {!isAppearanceCollapsed && (
                  isEditingAppearance ? (
                    <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
                      <Button variant="ghost" size="sm" className="h-6 px-2 text-xs" onPress={() => {
                        setIsEditingAppearance(false);
                        onStopEditing?.();
                      }}>
                        取消
                      </Button>
                      <Button variant="tertiary" size="sm" className="h-6 px-2 text-xs" onPress={handleSaveAppearance} isDisabled={isSaving}>
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
                        setIsEditingAppearance(true);
                        onStartEditing?.();
                      }}
                    >
                      <Edit3 className="size-3" />
                    </Button>
                  )
                )}
                <Button variant="ghost" size="sm" isIconOnly className="size-6">
                  {isAppearanceCollapsed ? <ChevronDown className="size-3.5" /> : <ChevronUp className="size-3.5" />}
                </Button>
              </div>
            </div>

            {!isAppearanceCollapsed && (
              <div className="space-y-3 border-t border-muted/10 p-4 pt-3">
                {/* Body */}
                <div className="rounded-lg bg-background/50 p-2.5">
                  <div className="flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-wide text-muted/70">
                    <Ruler className="size-3" />
                    体型
                  </div>
                  <div className="mt-2 grid grid-cols-3 gap-3">
                    {APPEARANCE_FIELDS.basic.map(({ key, label, placeholder }) => (
                      <div key={key}>
                        <Label className="text-[10px] text-muted">{label}</Label>
                        {isEditingAppearance ? (
                          <Input
                            variant="secondary"
                            className="mt-0.5 h-7 text-xs"
                            placeholder={placeholder}
                            value={appearanceEdit[key as keyof typeof appearanceEdit]}
                            onChange={(e) => setAppearanceEdit((prev) => ({ ...prev, [key]: e.target.value }))}
                          />
                        ) : (
                          <p className="mt-0.5 text-sm">
                            {appearanceEdit[key as keyof typeof appearanceEdit] || <span className="text-muted/40">未设置</span>}
                          </p>
                        )}
                      </div>
                    ))}
                  </div>
                </div>

                {/* Face */}
                <div className="rounded-lg bg-background/50 p-2.5">
                  <div className="flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-wide text-muted/70">
                    <UserCircle className="size-3" />
                    面部
                  </div>
                  <div className="mt-2 grid grid-cols-3 gap-3">
                    {APPEARANCE_FIELDS.face.map(({ key, label, placeholder }) => (
                      <div key={key}>
                        <Label className="text-[10px] text-muted">{label}</Label>
                        {isEditingAppearance ? (
                          <Input
                            variant="secondary"
                            className="mt-0.5 h-7 text-xs"
                            placeholder={placeholder}
                            value={appearanceEdit[key as keyof typeof appearanceEdit]}
                            onChange={(e) => setAppearanceEdit((prev) => ({ ...prev, [key]: e.target.value }))}
                          />
                        ) : (
                          <p className="mt-0.5 text-sm">
                            {appearanceEdit[key as keyof typeof appearanceEdit] || <span className="text-muted/40">未设置</span>}
                          </p>
                        )}
                      </div>
                    ))}
                  </div>
                </div>

                {/* Hair */}
                <div className="rounded-lg bg-background/50 p-2.5">
                  <div className="flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-wide text-muted/70">
                    <Sparkles className="size-3" />
                    发型
                  </div>
                  <div className="mt-2 grid grid-cols-3 gap-3">
                    {APPEARANCE_FIELDS.hair.map(({ key, label, placeholder }) => (
                      <div key={key}>
                        <Label className="text-[10px] text-muted">{label}</Label>
                        {isEditingAppearance ? (
                          <Input
                            variant="secondary"
                            className="mt-0.5 h-7 text-xs"
                            placeholder={placeholder}
                            value={appearanceEdit[key as keyof typeof appearanceEdit]}
                            onChange={(e) => setAppearanceEdit((prev) => ({ ...prev, [key]: e.target.value }))}
                          />
                        ) : (
                          <p className="mt-0.5 text-sm">
                            {appearanceEdit[key as keyof typeof appearanceEdit] || <span className="text-muted/40">未设置</span>}
                          </p>
                        )}
                      </div>
                    ))}
                  </div>
                </div>

                {/* Style & Outfit */}
                <div className="rounded-lg bg-background/50 p-2.5">
                  <div className="flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-wide text-muted/70">
                    <Shirt className="size-3" />
                    风格与服装
                  </div>
                  <div className="mt-2 grid grid-cols-2 gap-3">
                    <div>
                      <Label className="text-[10px] text-muted">画风</Label>
                      {isEditingAppearance ? (
                        <Input
                          variant="secondary"
                          className="mt-0.5 h-7 text-xs"
                          placeholder="如: 写实、动漫"
                          value={appearanceEdit.artStyle}
                          onChange={(e) => setAppearanceEdit((prev) => ({ ...prev, artStyle: e.target.value }))}
                        />
                      ) : (
                        <p className="mt-0.5 text-sm">
                          {appearanceEdit.artStyle || <span className="text-muted/40">未设置</span>}
                        </p>
                      )}
                    </div>
                    <div>
                      <Label className="text-[10px] text-muted">默认服装风格</Label>
                      {isEditingAppearance ? (
                        <Input
                          variant="secondary"
                          className="mt-0.5 h-7 text-xs"
                          placeholder="如: 休闲、正装"
                          value={appearanceEdit.outfitStyle}
                          onChange={(e) => setAppearanceEdit((prev) => ({ ...prev, outfitStyle: e.target.value }))}
                        />
                      ) : (
                        <p className="mt-0.5 text-sm">
                          {appearanceEdit.outfitStyle || <span className="text-muted/40">未设置</span>}
                        </p>
                      )}
                    </div>
                  </div>
                </div>

                {/* Distinguishing Features */}
                <div className="rounded-lg bg-background/50 p-2.5">
                  <div className="flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-wide text-muted/70">
                    <Heart className="size-3" />
                    特征标记
                  </div>
                  {isEditingAppearance ? (
                    <Input
                      variant="secondary"
                      className="mt-2 h-7 text-xs"
                      placeholder="多个特征用逗号分隔，如: 戴眼镜, 有疤痕, 纹身"
                      value={appearanceEdit.distinguishingFeatures}
                      onChange={(e) => setAppearanceEdit((prev) => ({ ...prev, distinguishingFeatures: e.target.value }))}
                    />
                  ) : (
                    <div className="mt-1.5 flex flex-wrap gap-1.5">
                      {appearanceEdit.distinguishingFeatures ? (
                        appearanceEdit.distinguishingFeatures.split(",").map((feature, idx) => (
                          <span key={idx} className="rounded-full bg-accent/10 px-2 py-0.5 text-xs text-accent">
                            {feature.trim()}
                          </span>
                        ))
                      ) : (
                        <span className="text-sm text-muted/40">无特征标记</span>
                      )}
                    </div>
                  )}
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
function CharacterDetailView({
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
  const [characterData, setCharacterData] = useState<CharacterDetailDTO | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isReadOnlyMode, setIsReadOnlyMode] = useState(false);

  const selectedItem = items.find((item) => item.id === selectedId) || items[0];

  // Comment panel integration
  const openCommentPanel = useCommentPanelStore((s) => s.open);
  const setCommentTarget = useCommentPanelStore((s) => s.setTarget);
  const isCommentPanelOpen = useCommentPanelStore((s) => s.isOpen);

  // Auto-link: silently update comment target when panel is open and selection changes
  useEffect(() => {
    if (!isCommentPanelOpen || !selectedItem) return;
    setCommentTarget({ type: "CHARACTER", id: selectedItem.id, name: selectedItem.title, scriptId });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedItem?.id, isCommentPanelOpen, scriptId]);

  const handleCommentOpen = useCallback(
    (id: string) => {
      const item = items.find((i) => i.id === id);
      if (!item) return;
      openCommentPanel({ type: "CHARACTER", id: item.id, name: item.title, scriptId });
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
  } = useCollaboration(scriptId, "characters");

  // Use refs to avoid effect re-running due to function reference changes
  const focusEntityRef = useRef(focusEntity);
  const blurEntityRef = useRef(blurEntity);
  useEffect(() => {
    focusEntityRef.current = focusEntity;
    blurEntityRef.current = blurEntity;
  });

  // Get collaboration state for selected entity
  const collaborators = selectedItem?.id
    ? getEntityCollaborators("CHARACTER", selectedItem.id)
    : { editor: null, lockedAt: null, isLockedByOther: false };

  // Focus entity when selection changes
  useEffect(() => {
    if (selectedItem?.id) {
      focusEntityRef.current("CHARACTER", selectedItem.id);
    }
    return () => {
      if (selectedItem?.id) {
        blurEntityRef.current("CHARACTER", selectedItem.id);
      }
    };
  }, [selectedItem?.id]);

  // Fetch character detail when selection changes
  const fetchCharacterDetail = useCallback(async () => {
    if (!currentWorkspaceId || !selectedItem?.id) return;

    try {
      setIsLoading(true);
      const data = await projectService.getCharacter( selectedItem.id);
      setCharacterData(data);
      setIsReadOnlyMode(false); // Reset read-only mode on new selection
    } catch (err) {
      console.error("Failed to fetch character detail:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsLoading(false);
    }
  }, [currentWorkspaceId, selectedItem?.id]);

  useEffect(() => {
    fetchCharacterDetail();
  }, [fetchCharacterDetail]);

  // Auto-sync with AI generation panel when character changes
  useEffect(() => {
    if (activePanel === "ai-generation" && characterData) {
      updateEntity({
        entityType: "CHARACTER",
        entityId: characterData.id,
        entityName: characterData.name,
        entityDescription: characterData.description || undefined,
        entityFixedDesc: characterData.fixedDesc || undefined,
        entityCoverUrl: characterData.coverUrl || undefined,
        scriptId,
      });
    }
  }, [characterData?.id, activePanel, updateEntity, scriptId]);

  if (items.length === 0) {
    return <EmptyState icon={<User className="size-16" />} title="暂无角色" />;
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
        placeholderIcon={<User className="size-5 text-muted/30" />}
        entityTypeLabel="角色"
      />

      {/* Detail Content */}
      <div className="relative min-w-0 flex-1 overflow-hidden rounded-xl">
        {isLoading ? (
          <DetailPaneSkeleton />
        ) : characterData ? (
          <>
            <CharacterDetailContent
              data={characterData}
              scriptId={scriptId}
              onUpdate={fetchCharacterDetail}
              onStartEditing={() => startEditing("CHARACTER", selectedItem!.id)}
              onStopEditing={() => stopEditing("CHARACTER", selectedItem!.id)}
              isReadOnly={isReadOnlyMode}
            />
            {/* Editing Overlay - show when locked by others and not in read-only mode */}
            {collaborators.isLockedByOther && collaborators.editor && !isReadOnlyMode && (
              <EditingOverlay
                editor={collaborators.editor}
                lockedAt={collaborators.lockedAt}
                timeoutMinutes={5}
                onViewReadOnly={() => setIsReadOnlyMode(true)}
                onRefresh={() => refreshEntityState("CHARACTER", selectedItem!.id)}
                onForceEdit={() => startEditing("CHARACTER", selectedItem!.id)}
              />
            )}
          </>
        ) : (
          <div className="flex h-full items-center justify-center text-sm text-muted">
            请选择一个角色
          </div>
        )}
      </div>
    </div>
  );
}

// Character Tab Component
export function CharacterTab({ scriptId }: CharacterTabProps) {
  const locale = useLocale();
  const { currentWorkspaceId } = useWorkspace();
  const [viewMode, setViewMode] = useState<ViewMode>(() => getStoredViewMode("characters"));
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [items, setItems] = useState<EntityItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(() => getStoredSidebarCollapsed("characters"));
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [showPicker, setShowPicker] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [sortKeys, setSortKeys] = useState<SortKey[]>(["updatedAt_desc"]);

  // Create form state
  const [createForm, setCreateForm] = useState({
    name: "",
    description: "",
    age: "",
    gender: "",
    characterType: "",
  });

  const resetCreateForm = () => {
    setCreateForm({ name: "", description: "", age: "", gender: "", characterType: "" });
  };

  // Fetch characters
  const fetchCharacters = useCallback(async () => {
    if (!currentWorkspaceId || !scriptId) return;

    try {
      setIsLoading(true);
      setError(null);

      const characters = await projectService.getCharactersByScript( scriptId);
      const entityItems = characters.map((item) => transformToEntityItem(item, "characters"));

      const countResults = await Promise.allSettled(
        characters.map((c) => commentService.getCommentCount("CHARACTER", c.id))
      );
      countResults.forEach((result, idx) => {
        if (result.status === "fulfilled" && result.value > 0) {
          entityItems[idx].commentCount = result.value;
        }
      });

      setItems(entityItems);
    } catch (err) {
      console.error("Failed to fetch characters:", err);
      toast.danger(getErrorFromException(err, locale));
      setError("加载角色失败");
    } finally {
      setIsLoading(false);
    }
  }, [currentWorkspaceId, scriptId]);

  useEffect(() => {
    fetchCharacters();
  }, [fetchCharacters]);

  // Auto-refresh when characters are changed by other collaborators via WebSocket
  useDebouncedEntityChanges(
    "character",
    fetchCharacters,
    { onDeleted: (id) => { if (selectedId === id) setSelectedId(null); } },
    [fetchCharacters, selectedId]
  );

  const handleSelect = (id: string) => {
    setSelectedId(id);
    if (viewMode !== "detail") {
      setViewMode("detail");
      saveViewMode("characters", "detail");
    }
  };

  const handleViewModeChange = (mode: ViewMode) => {
    setViewMode(mode);
    saveViewMode("characters", mode);
  };

  const handleToggleSidebar = () => {
    const newCollapsed = !isSidebarCollapsed;
    setIsSidebarCollapsed(newCollapsed);
    saveSidebarCollapsed("characters", newCollapsed);
  };

  // Filter + sort items
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

  // Create character
  const handleCreate = async () => {
    if (!currentWorkspaceId || !createForm.name.trim()) return;

    try {
      setIsCreating(true);
      await projectService.createCharacter( {
        scope: "SCRIPT",
        scriptId,
        name: createForm.name.trim(),
        description: createForm.description.trim() || undefined,
        age: createForm.age ? parseInt(createForm.age) : undefined,
        gender: createForm.gender || undefined,
        characterType: createForm.characterType || undefined,
      });

      setShowCreateForm(false);
      resetCreateForm();
      fetchCharacters();
    } catch (err) {
      console.error("Failed to create character:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsCreating(false);
    }
  };

  // Edit character - switch to detail view
  const handleEdit = (id: string) => {
    setSelectedId(id);
    setViewMode("detail");
    saveViewMode("characters", "detail");
  };

  // Delete character
  const handleDelete = async (id: string) => {
    if (!currentWorkspaceId) return;

    try {
      await projectService.deleteCharacter( id);
      if (selectedId === id) {
        setSelectedId(items[0]?.id || "");
      }
      fetchCharacters();
    } catch (err) {
      console.error("Failed to delete character:", err);
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
        <Button variant="ghost" size="sm" onPress={fetchCharacters}>
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
          <SearchField aria-label="搜索角色" value={searchKeyword} onChange={setSearchKeyword}>
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-36" placeholder="搜索角色..." />
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
            <Button variant="ghost" size="sm" className="size-8 p-0 text-muted hover:text-foreground" onPress={fetchCharacters}>
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
            关联角色
          </Button>

          <Button
            variant="primary"
            size="sm"
            className="gap-1.5 text-xs"
            onPress={() => setShowCreateForm(true)}
          >
            <Plus className="size-3.5" />
            新增角色
          </Button>
        </div>
      </Surface>

      <EntityPickerModal
        isOpen={showPicker}
        onOpenChange={setShowPicker}
        entityType="characters"
        scriptId={scriptId}
        onImported={fetchCharacters}
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
          <CharacterDetailView
            items={filteredItems}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onDelete={handleDelete}
            isSidebarCollapsed={isSidebarCollapsed}
            onToggleSidebar={handleToggleSidebar}
            onRefresh={fetchCharacters}
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
              <Modal.Icon className="bg-accent/10 text-accent">
                <User className="size-5" />
              </Modal.Icon>
              <Modal.Heading>新增角色</Modal.Heading>
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
                    placeholder="输入角色名称"
                  />
                </div>
                <div className="grid grid-cols-3 gap-3">
                  <div className="space-y-1.5">
                    <Label className="text-xs font-medium text-muted">年龄</Label>
                    <Input
                      variant="secondary"
                      type="number"
                      value={createForm.age}
                      onChange={(e) => setCreateForm((prev) => ({ ...prev, age: e.target.value }))}
                      className="w-full"
                      placeholder="年龄"
                    />
                  </div>
                  <Select
                    className="w-full"
                    placeholder="未设置"
                    variant="secondary"
                    value={createForm.gender || null}
                    onChange={(value) => setCreateForm((prev) => ({ ...prev, gender: value as string || "" }))}
                  >
                    <Label className="text-xs font-medium text-muted">性别</Label>
                    <Select.Trigger>
                      <Select.Value />
                      <Select.Indicator />
                    </Select.Trigger>
                    <Select.Popover>
                      <ListBox>
                        {GENDER_OPTIONS.map((opt) => (
                          <ListBox.Item key={opt.value} id={opt.value} textValue={opt.label}>
                            {opt.label}
                            <ListBox.ItemIndicator />
                          </ListBox.Item>
                        ))}
                      </ListBox>
                    </Select.Popover>
                  </Select>
                  <Select
                    className="w-full"
                    placeholder="未设置"
                    variant="secondary"
                    value={createForm.characterType || null}
                    onChange={(value) => setCreateForm((prev) => ({ ...prev, characterType: value as string || "" }))}
                  >
                    <Label className="text-xs font-medium text-muted">类型</Label>
                    <Select.Trigger>
                      <Select.Value />
                      <Select.Indicator />
                    </Select.Trigger>
                    <Select.Popover>
                      <ListBox>
                        {CHARACTER_TYPES.map((opt) => (
                          <ListBox.Item key={opt.value} id={opt.value} textValue={opt.label}>
                            {opt.label}
                            <ListBox.ItemIndicator />
                          </ListBox.Item>
                        ))}
                      </ListBox>
                    </Select.Popover>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label className="text-xs font-medium text-muted">描述</Label>
                  <TextArea
                    variant="secondary"
                    value={createForm.description}
                    onChange={(e) => setCreateForm((prev) => ({ ...prev, description: e.target.value }))}
                    className="min-h-20 w-full"
                    placeholder="角色描述（可选）"
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

export default CharacterTab;
