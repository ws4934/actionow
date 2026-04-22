"use client";

/**
 * Prop Tab Component
 * Dedicated tab component for managing props with list/grid/detail views
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
  Package,
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
  Palette,
  Box,
  Sparkles,
  Image,
  Upload,
  GripVertical,
  Link2,
} from "lucide-react";
import NextImage from "@/components/ui/content-image";
import { projectService, getErrorFromException} from "@/lib/api";
import { commentService } from "@/lib/api/services/comment.service";
import type { PropListDTO, PropDetailDTO, VersionInfoDTO, EntityAssetRelationDTO, AssetListDTO } from "@/lib/api/dto";
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

interface PropTabProps {
  scriptId: string;
}

// Prop type options
const PROP_TYPES = [
  { value: "WEAPON", label: "武器" },
  { value: "VEHICLE", label: "载具" },
  { value: "FURNITURE", label: "家具" },
  { value: "CLOTHING", label: "服装" },
  { value: "FOOD", label: "食物" },
  { value: "TOOL", label: "工具" },
  { value: "DEVICE", label: "设备" },
  { value: "OTHER", label: "其他" },
];

// Appearance field config
const APPEARANCE_FIELDS = [
  { key: "size", label: "尺寸", placeholder: "如: 小型、中型、大型" },
  { key: "material", label: "材质", placeholder: "如: 金属、木头、塑料" },
  { key: "color", label: "颜色", placeholder: "如: 红色、黑色、银色" },
  { key: "style", label: "风格", placeholder: "如: 现代、古典、科幻" },
  { key: "condition", label: "状态", placeholder: "如: 全新、老旧、破损" },
  { key: "details", label: "细节特征", placeholder: "如: 雕花、磨损痕迹" },
];

// Detail Content Component with editable fields (matching storyboard/character layout)
function PropDetailContent({
  data,
  scriptId,
  onUpdate,
  onStartEditing,
  onStopEditing,
  isReadOnly = false,
}: {
  data: PropDetailDTO;
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
  const [isAppearanceCollapsed, setIsAppearanceCollapsed] = useState(true);
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
    propType: data.propType || "",
  });

  // Edit form state - Fixed Description (separate)
  const [fixedDescEdit, setFixedDescEdit] = useState(data.fixedDesc || "");

  // Edit form state - Appearance
  const appearanceData = (data.appearanceData || {}) as Record<string, unknown>;
  const [appearanceEdit, setAppearanceEdit] = useState({
    size: (appearanceData.size as string) || "",
    material: (appearanceData.material as string) || "",
    color: (appearanceData.color as string) || "",
    style: (appearanceData.style as string) || "",
    condition: (appearanceData.condition as string) || "",
    details: (appearanceData.details as string) || "",
  });

  // Sync with data changes
  useEffect(() => {
    setEditForm({
      name: data.name || "",
      description: data.description || "",
      fixedDesc: data.fixedDesc || "",
      propType: data.propType || "",
    });

    setFixedDescEdit(data.fixedDesc || "");

    const appearanceData = (data.appearanceData || {}) as Record<string, unknown>;
    setAppearanceEdit({
      size: (appearanceData.size as string) || "",
      material: (appearanceData.material as string) || "",
      color: (appearanceData.color as string) || "",
      style: (appearanceData.style as string) || "",
      condition: (appearanceData.condition as string) || "",
      details: (appearanceData.details as string) || "",
    });
  }, [data]);

  // Fetch related assets with auto-refresh for generating items
  useEffect(() => {
    const fetchRelatedAssets = async () => {
      if (!currentWorkspaceId || !data.id) return;
      try {
        setIsLoadingAssets(true);
        const assets = await projectService.getEntityAssetRelations( "PROP", data.id);
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
        const assets = await projectService.getEntityAssetRelations( "PROP", data.id);
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
      const vers = await projectService.getPropVersions( data.id);
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
      await projectService.updateProp( data.id, {
        name: editForm.name || undefined,
        description: editForm.description || undefined,
        propType: editForm.propType || undefined,
      });
      setIsEditingInfo(false);
      onStopEditing?.();
      // Sync with AI generation panel if it's showing this entity
      if (aiEntityId === data.id) {
        updateEntity({
          entityType: "PROP",
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
      console.error("Failed to update prop:", err);
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
      await projectService.updateProp( data.id, {
        fixedDesc: fixedDescEdit || undefined,
      });
      setIsEditingFixedDesc(false);
      onStopEditing?.();
      // Sync with AI generation panel if it's showing this entity
      if (aiEntityId === data.id) {
        updateEntity({
          entityType: "PROP",
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
      await projectService.updateProp( data.id, {
        appearanceData: {
          size: appearanceEdit.size || null,
          material: appearanceEdit.material || null,
          color: appearanceEdit.color || null,
          style: appearanceEdit.style || null,
          condition: appearanceEdit.condition || null,
          details: appearanceEdit.details || null,
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
        description: "道具封面图",
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
      await projectService.updateProp( data.id, {
        coverAssetId: initResponse.assetId,
      });
      // Sync cover with AI generation panel
      if (aiEntityId === data.id && confirmResult.fileUrl) {
        updateEntity({
          entityType: "PROP",
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
      await projectService.updateProp( data.id, {
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
            description: "道具参考素材",
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
            entityType: "PROP",
            entityId: data.id,
            assetId: initResponse.assetId,
            relationType: "REFERENCE",
          });
        })
      );

      // Refresh assets list
      const assets = await projectService.getEntityAssetRelations( "PROP", data.id);
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
        description: "编辑后的道具素材",
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
        entityType: "PROP",
        entityId: data.id,
        assetId: initResponse.assetId,
        relationType: "REFERENCE",
      });

      // Refresh assets
      const assets = await projectService.getEntityAssetRelations( "PROP", data.id);
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
      await projectService.restorePropVersion( data.id, {
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
        entityType: "PROP",
        entityId: data.id,
        assetId,
        relationType: "DRAFT",
      });
      toast.success("素材关联成功");
      const assets = await projectService.getEntityAssetRelations("PROP", data.id);
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
                <Package className="size-4 shrink-0 text-amber-500" />
                <h2 className="truncate text-sm font-semibold">{data.name}</h2>
                {data.propType && (
                  <span className="shrink-0 rounded-full bg-amber-500/10 px-1.5 py-0.5 text-[10px] text-amber-600">
                    {PROP_TYPES.find(t => t.value === data.propType)?.label || data.propType}
                  </span>
                )}
              </div>
              {isHeaderCollapsed && (
                <p className="mt-0.5 truncate text-xs text-muted">
                  {[
                    appearanceEdit.material,
                    appearanceEdit.color,
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
                      entityType: "PROP",
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
                  <Tooltip.Content>AI 生成道具图</Tooltip.Content>
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
                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1">
                      <Label className="text-xs text-muted">名称</Label>
                      <Input
                        variant="secondary"
                        value={editForm.name}
                        onChange={(e) => setEditForm((prev) => ({ ...prev, name: e.target.value }))}
                        className="w-full"
                        placeholder="输入道具名称"
                      />
                    </div>
                    <Select
                      className="w-full"
                      placeholder="未设置"
                      variant="secondary"
                      value={editForm.propType || null}
                      onChange={(value) => setEditForm((prev) => ({ ...prev, propType: value as string || "" }))}
                    >
                      <Label className="text-xs text-muted">类型</Label>
                      <Select.Trigger>
                        <Select.Value />
                        <Select.Indicator />
                      </Select.Trigger>
                      <Select.Popover>
                        <ListBox>
                          {PROP_TYPES.map((opt) => (
                            <ListBox.Item key={opt.value} id={opt.value} textValue={opt.label}>
                              {opt.label}
                              <ListBox.ItemIndicator />
                            </ListBox.Item>
                          ))}
                        </ListBox>
                      </Select.Popover>
                    </Select>
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs text-muted">描述</Label>
                    <Input
                      variant="secondary"
                      value={editForm.description}
                      onChange={(e) => setEditForm((prev) => ({ ...prev, description: e.target.value }))}
                      className="w-full"
                      placeholder="输入道具描述"
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
                        entityType: "PROP",
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
                  placeholder="输入固定提示词，如外观特征、材质等，将自动填充到 AI 生成的提示词输入框"
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

        {/* Voice Section - Sound Effect */}
        {scriptId && (
          <VoiceSection
            entityType="PROP"
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
                    entityType="PROP"
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
              <div className="border-t border-muted/10 p-4 pt-3">
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  {APPEARANCE_FIELDS.map(({ key, label, placeholder }) => (
                    <div key={key} className="rounded-lg bg-background/50 p-2.5">
                      <div className="flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-wide text-muted/70">
                        {key === "size" && <Ruler className="size-3" />}
                        {key === "material" && <Box className="size-3" />}
                        {key === "color" && <Palette className="size-3" />}
                        {key === "style" && <Sparkles className="size-3" />}
                        {key === "condition" && <Package className="size-3" />}
                        {key === "details" && <Edit3 className="size-3" />}
                        {label}
                      </div>
                      {isEditingAppearance ? (
                        <Input
                          variant="secondary"
                          className="mt-1 h-7 text-xs"
                          placeholder={placeholder}
                          value={appearanceEdit[key as keyof typeof appearanceEdit]}
                          onChange={(e) => setAppearanceEdit((prev) => ({ ...prev, [key]: e.target.value }))}
                        />
                      ) : (
                        <p className="mt-1 text-sm text-foreground">
                          {appearanceEdit[key as keyof typeof appearanceEdit] || (
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

// Prop Detail View with sidebar
function PropDetailView({
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
  const [propData, setPropData] = useState<PropDetailDTO | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isReadOnlyMode, setIsReadOnlyMode] = useState(false);

  const selectedItem = items.find((item) => item.id === selectedId) || items[0];

  // Comment panel integration
  const openCommentPanel = useCommentPanelStore((s) => s.open);
  const setCommentTarget = useCommentPanelStore((s) => s.setTarget);
  const isCommentPanelOpen = useCommentPanelStore((s) => s.isOpen);

  useEffect(() => {
    if (!isCommentPanelOpen || !selectedItem) return;
    setCommentTarget({ type: "PROP", id: selectedItem.id, name: selectedItem.title, scriptId });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedItem?.id, isCommentPanelOpen, scriptId]);

  const handleCommentOpen = useCallback(
    (id: string) => {
      const item = items.find((i) => i.id === id);
      if (!item) return;
      openCommentPanel({ type: "PROP", id: item.id, name: item.title, scriptId });
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
  } = useCollaboration(scriptId, "props");

  // Use refs to avoid effect re-running due to function reference changes
  const focusEntityRef = useRef(focusEntity);
  const blurEntityRef = useRef(blurEntity);
  useEffect(() => {
    focusEntityRef.current = focusEntity;
    blurEntityRef.current = blurEntity;
  });

  // Get collaboration state for selected entity
  const collaborators = selectedItem?.id
    ? getEntityCollaborators("PROP", selectedItem.id)
    : { editor: null, lockedAt: null, isLockedByOther: false };

  // Focus entity when selection changes
  useEffect(() => {
    if (selectedItem?.id) {
      focusEntityRef.current("PROP", selectedItem.id);
    }
    return () => {
      if (selectedItem?.id) {
        blurEntityRef.current("PROP", selectedItem.id);
      }
    };
  }, [selectedItem?.id]);

  // Fetch prop detail when selection changes
  const fetchPropDetail = useCallback(async () => {
    if (!currentWorkspaceId || !selectedItem?.id) return;

    try {
      setIsLoading(true);
      const data = await projectService.getProp( selectedItem.id);
      setPropData(data);
      setIsReadOnlyMode(false); // Reset read-only mode on new selection
    } catch (err) {
      console.error("Failed to fetch prop detail:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsLoading(false);
    }
  }, [currentWorkspaceId, selectedItem?.id]);

  useEffect(() => {
    fetchPropDetail();
  }, [fetchPropDetail]);

  // Auto-sync with AI generation panel when prop changes
  useEffect(() => {
    if (activePanel === "ai-generation" && propData) {
      updateEntity({
        entityType: "PROP",
        entityId: propData.id,
        entityName: propData.name,
        entityDescription: propData.description || undefined,
        entityFixedDesc: propData.fixedDesc || undefined,
        entityCoverUrl: propData.coverUrl || undefined,
        scriptId,
      });
    }
  }, [propData?.id, activePanel, updateEntity, scriptId]);

  if (items.length === 0) {
    return <EmptyState icon={<Package className="size-16" />} title="暂无道具" />;
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
        placeholderIcon={<Package className="size-5 text-amber-500/30" />}
        entityTypeLabel="道具"
      />

      {/* Detail Content */}
      <div className="relative min-w-0 flex-1 overflow-hidden rounded-xl">
        {isLoading ? (
          <DetailPaneSkeleton />
        ) : propData ? (
          <>
            <PropDetailContent
              data={propData}
              scriptId={scriptId}
              onUpdate={fetchPropDetail}
              onStartEditing={() => startEditing("PROP", selectedItem!.id)}
              onStopEditing={() => stopEditing("PROP", selectedItem!.id)}
              isReadOnly={isReadOnlyMode}
            />
            {/* Editing Overlay - show when locked by others and not in read-only mode */}
            {collaborators.isLockedByOther && collaborators.editor && !isReadOnlyMode && (
              <EditingOverlay
                editor={collaborators.editor}
                lockedAt={collaborators.lockedAt}
                timeoutMinutes={5}
                onViewReadOnly={() => setIsReadOnlyMode(true)}
                onRefresh={() => refreshEntityState("PROP", selectedItem!.id)}
                onForceEdit={() => startEditing("PROP", selectedItem!.id)}
              />
            )}
          </>
        ) : (
          <div className="flex h-full items-center justify-center text-sm text-muted">
            请选择一个道具
          </div>
        )}
      </div>
    </div>
  );
}

// Prop Tab Component
export function PropTab({ scriptId }: PropTabProps) {
  const locale = useLocale();
  const { currentWorkspaceId } = useWorkspace();
  const [viewMode, setViewMode] = useState<ViewMode>(() => getStoredViewMode("props"));
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [items, setItems] = useState<EntityItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(() => getStoredSidebarCollapsed("props"));
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [showPicker, setShowPicker] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [sortKeys, setSortKeys] = useState<SortKey[]>(["updatedAt_desc"]);

  // Create form state
  const [createForm, setCreateForm] = useState({
    name: "",
    description: "",
    propType: "",
  });

  const resetCreateForm = () => {
    setCreateForm({ name: "", description: "", propType: "" });
  };

  // Fetch props
  const fetchProps = useCallback(async () => {
    if (!currentWorkspaceId || !scriptId) return;

    try {
      setIsLoading(true);
      setError(null);

      const props = await projectService.getPropsByScript( scriptId);
      const entityItems = props.map((item) => transformToEntityItem(item, "props"));

      const countResults = await Promise.allSettled(
        props.map((p) => commentService.getCommentCount("PROP", p.id))
      );
      countResults.forEach((result, idx) => {
        if (result.status === "fulfilled" && result.value > 0) {
          entityItems[idx].commentCount = result.value;
        }
      });

      setItems(entityItems);
    } catch (err) {
      console.error("Failed to fetch props:", err);
      toast.danger(getErrorFromException(err, locale));
      setError("加载道具失败");
    } finally {
      setIsLoading(false);
    }
  }, [currentWorkspaceId, scriptId]);

  useEffect(() => {
    fetchProps();
  }, [fetchProps]);

  // Auto-refresh when props are changed by other collaborators via WebSocket
  useDebouncedEntityChanges(
    "prop",
    fetchProps,
    { onDeleted: (id) => { if (selectedId === id) setSelectedId(null); } },
    [fetchProps, selectedId]
  );

  const handleSelect = (id: string) => {
    setSelectedId(id);
    if (viewMode !== "detail") {
      setViewMode("detail");
      saveViewMode("props", "detail");
    }
  };

  const handleViewModeChange = (mode: ViewMode) => {
    setViewMode(mode);
    saveViewMode("props", mode);
  };

  const handleToggleSidebar = () => {
    const newCollapsed = !isSidebarCollapsed;
    setIsSidebarCollapsed(newCollapsed);
    saveSidebarCollapsed("props", newCollapsed);
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

  // Create prop
  const handleCreate = async () => {
    if (!currentWorkspaceId || !createForm.name.trim()) return;

    try {
      setIsCreating(true);
      await projectService.createProp( {
        scope: "SCRIPT",
        scriptId,
        name: createForm.name.trim(),
        description: createForm.description.trim() || undefined,
        propType: createForm.propType || undefined,
      });

      setShowCreateForm(false);
      resetCreateForm();
      fetchProps();
    } catch (err) {
      console.error("Failed to create prop:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsCreating(false);
    }
  };

  // Edit prop - switch to detail view
  const handleEdit = (id: string) => {
    setSelectedId(id);
    setViewMode("detail");
    saveViewMode("props", "detail");
  };

  // Delete prop
  const handleDelete = async (id: string) => {
    if (!currentWorkspaceId) return;

    try {
      await projectService.deleteProp( id);
      if (selectedId === id) {
        setSelectedId(items[0]?.id || "");
      }
      fetchProps();
    } catch (err) {
      console.error("Failed to delete prop:", err);
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
        <Button variant="ghost" size="sm" onPress={fetchProps}>
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
          <SearchField aria-label="搜索道具" value={searchKeyword} onChange={setSearchKeyword}>
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-36" placeholder="搜索道具..." />
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
            <Button variant="ghost" size="sm" className="size-8 p-0 text-muted hover:text-foreground" onPress={fetchProps}>
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
            关联道具
          </Button>

          <Button
            variant="primary"
            size="sm"
            className="gap-1.5 text-xs"
            onPress={() => setShowCreateForm(true)}
          >
            <Plus className="size-3.5" />
            新增道具
          </Button>
        </div>
      </Surface>

      <EntityPickerModal
        isOpen={showPicker}
        onOpenChange={setShowPicker}
        entityType="props"
        scriptId={scriptId}
        onImported={fetchProps}
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
          <PropDetailView
            items={filteredItems}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onDelete={handleDelete}
            isSidebarCollapsed={isSidebarCollapsed}
            onToggleSidebar={handleToggleSidebar}
            onRefresh={fetchProps}
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
              <Modal.Icon className="bg-amber-500/10 text-amber-500">
                <Package className="size-5" />
              </Modal.Icon>
              <Modal.Heading>新增道具</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="overflow-visible">
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <Label className="text-xs font-medium text-muted">名称 *</Label>
                    <Input
                      variant="secondary"
                      value={createForm.name}
                      onChange={(e) => setCreateForm((prev) => ({ ...prev, name: e.target.value }))}
                      className="w-full"
                      placeholder="输入道具名称"
                    />
                  </div>
                  <Select
                    className="w-full"
                    placeholder="未设置"
                    variant="secondary"
                    value={createForm.propType || null}
                    onChange={(value) => setCreateForm((prev) => ({ ...prev, propType: value as string || "" }))}
                  >
                    <Label className="text-xs font-medium text-muted">类型</Label>
                    <Select.Trigger>
                      <Select.Value />
                      <Select.Indicator />
                    </Select.Trigger>
                    <Select.Popover>
                      <ListBox>
                        {PROP_TYPES.map((opt) => (
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
                    placeholder="道具描述（可选）"
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

export default PropTab;
