"use client";

/**
 * Episode Tab Component
 * Dedicated tab component for managing episodes with list/grid/detail views
 */

import { useState, useEffect, useCallback, useRef } from "react";
import {
  Button,
  ButtonGroup,
  ScrollShadow,
  Tooltip,
  Modal,
  Input,
  TextArea,
  Label,
  Spinner,
  Separator,
  SearchField,
  Surface,
  toast,
} from "@heroui/react";
import {
  Film,
  LayoutGrid,
  List,
  PanelLeft,
  PanelLeftClose,
  Plus,
  X,
  RefreshCw,
  Clapperboard,
} from "lucide-react";
import { projectService, getErrorFromException} from "@/lib/api";
import { commentService } from "@/lib/api/services/comment.service";
import type { EpisodeListDTO } from "@/lib/api/dto";
import { useWorkspace } from "@/components/providers/workspace-provider";
import { useDebouncedEntityChanges } from "@/lib/websocket";
import type { ViewMode, EntityItem } from "../types";
import {
  transformToEntityItem,
  getStoredViewMode,
  getStoredSidebarCollapsed,
  setViewMode as saveViewMode,
  setSidebarCollapsed as saveSidebarCollapsed,
} from "../utils";
import { StatusBadge, EntityListView, EntityGridView, DetailSidebar, EntityTabSkeleton, DetailPaneSkeleton, SortButtons, sortItems, EmptyState, type SortKey } from "../common";
import { EpisodeDetailContent } from "./detail-content";
import { EditingOverlay } from "../components";
import { useCollaboration } from "../hooks/use-collaboration";
import { useLocale } from "next-intl";

interface EpisodeTabProps {
  scriptId: string;
}

// Episode Detail View with sidebar
function EpisodeDetailView({
  items,
  selectedId,
  onSelect,
  onDelete,
  isSidebarCollapsed,
  onToggleSidebar,
  scriptId,
  onRefresh,
}: {
  items: EntityItem[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
  isSidebarCollapsed: boolean;
  onToggleSidebar: () => void;
  scriptId: string;
  onRefresh: () => void;
}) {
  const { currentWorkspaceId } = useWorkspace();
  const locale = useLocale();
  const [episodeData, setEpisodeData] = useState<import("@/lib/api/dto").EpisodeDetailDTO | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isReadOnlyMode, setIsReadOnlyMode] = useState(false);

  const selectedItem = items.find((item) => item.id === selectedId) || items[0];

  // Collaboration hook
  const {
    getEntityCollaborators,
    refreshEntityState,
    startEditing,
    stopEditing,
    focusEntity,
    blurEntity,
  } = useCollaboration(scriptId, "episodes");

  // Use refs to avoid effect re-running due to function reference changes
  const focusEntityRef = useRef(focusEntity);
  const blurEntityRef = useRef(blurEntity);
  useEffect(() => {
    focusEntityRef.current = focusEntity;
    blurEntityRef.current = blurEntity;
  });

  // Get collaboration state for selected entity
  const collaborators = selectedItem?.id
    ? getEntityCollaborators("EPISODE", selectedItem.id)
    : { editor: null, lockedAt: null, isLockedByOther: false };

  // Focus entity when selection changes
  useEffect(() => {
    if (selectedItem?.id) {
      focusEntityRef.current("EPISODE", selectedItem.id);
    }
    return () => {
      if (selectedItem?.id) {
        blurEntityRef.current("EPISODE", selectedItem.id);
      }
    };
  }, [selectedItem?.id]);

  // Fetch episode detail when selection changes
  const fetchEpisodeDetail = useCallback(async () => {
    if (!currentWorkspaceId || !selectedItem?.id) return;

    try {
      setIsLoading(true);
      const data = await projectService.getEpisode( selectedItem.id);
      setEpisodeData(data);
      setIsReadOnlyMode(false); // Reset read-only mode on new selection
    } catch (err) {
      console.error("Failed to fetch episode detail:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsLoading(false);
    }
  }, [currentWorkspaceId, selectedItem?.id]);

  useEffect(() => {
    fetchEpisodeDetail();
  }, [fetchEpisodeDetail]);

  const handleUpdate = () => {
    fetchEpisodeDetail();
    onRefresh();
  };

  if (items.length === 0) {
    return <EmptyState icon={<Clapperboard className="size-16" />} title="暂无剧集" />;
  }

  return (
    <div className="flex h-full gap-3">
      {/* Sidebar - Collapsible */}
      <DetailSidebar
        items={items}
        selectedId={selectedId}
        onSelect={onSelect}
        onDelete={onDelete}
        isCollapsed={isSidebarCollapsed}
        onToggle={onToggleSidebar}
        placeholderIcon={<Film className="size-5 text-muted/30" />}
        entityTypeLabel="剧集"
      />

      {/* Detail Content */}
      <div className="relative min-w-0 flex-1 overflow-hidden rounded-xl">
        {isLoading ? (
          <DetailPaneSkeleton />
        ) : episodeData ? (
          <>
            <EpisodeDetailContent
              data={episodeData}
              scriptId={scriptId}
              onUpdate={handleUpdate}
              onStartEditing={() => startEditing("EPISODE", selectedItem!.id)}
              onStopEditing={() => stopEditing("EPISODE", selectedItem!.id)}
              isReadOnly={isReadOnlyMode}
            />
            {/* Editing Overlay - show when locked by others and not in read-only mode */}
            {collaborators.isLockedByOther && collaborators.editor && !isReadOnlyMode && (
              <EditingOverlay
                editor={collaborators.editor}
                lockedAt={collaborators.lockedAt}
                timeoutMinutes={5}
                onViewReadOnly={() => setIsReadOnlyMode(true)}
                onRefresh={() => refreshEntityState("EPISODE", selectedItem!.id)}
                onForceEdit={() => startEditing("EPISODE", selectedItem!.id)}
              />
            )}
          </>
        ) : (
          <div className="flex h-full items-center justify-center text-sm text-muted">
            请选择一个剧集
          </div>
        )}
      </div>
    </div>
  );
}

// Episode Tab Component
export function EpisodeTab({ scriptId }: EpisodeTabProps) {
  const locale = useLocale();
  const { currentWorkspaceId } = useWorkspace();
  const [viewMode, setViewMode] = useState<ViewMode>(() => getStoredViewMode("episodes"));
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [items, setItems] = useState<EntityItem[]>([]);
  const [rawEpisodes, setRawEpisodes] = useState<EpisodeListDTO[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(() => getStoredSidebarCollapsed("episodes"));
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [sortKeys, setSortKeys] = useState<SortKey[]>(["sequence_asc"]);

  // Create form state
  const [createForm, setCreateForm] = useState({
    title: "",
    synopsis: "",
  });

  const resetCreateForm = () => {
    setCreateForm({ title: "", synopsis: "" });
  };

  // Fetch episodes
  const fetchEpisodes = useCallback(async () => {
    if (!currentWorkspaceId || !scriptId) return;

    try {
      setIsLoading(true);
      setError(null);

      const episodes = await projectService.getEpisodesByScript( scriptId);
      setRawEpisodes(episodes);
      const entityItems = episodes.map((item) => transformToEntityItem(item, "episodes"));

      // Fetch comment counts in parallel
      const countResults = await Promise.allSettled(
        episodes.map((ep) => commentService.getCommentCount("EPISODE", ep.id))
      );
      countResults.forEach((result, idx) => {
        if (result.status === "fulfilled" && result.value > 0) {
          entityItems[idx].commentCount = result.value;
        }
      });

      setItems(entityItems);
    } catch (err) {
      console.error("Failed to fetch episodes:", err);
      toast.danger(getErrorFromException(err, locale));
      setError("加载剧集失败");
    } finally {
      setIsLoading(false);
    }
  }, [currentWorkspaceId, scriptId]);

  useEffect(() => {
    fetchEpisodes();
  }, [fetchEpisodes]);

  // Auto-refresh when episodes are changed by other collaborators via WebSocket
  useDebouncedEntityChanges(
    "episode",
    fetchEpisodes,
    { onDeleted: (id) => { if (selectedId === id) setSelectedId(null); } },
    [fetchEpisodes, selectedId]
  );

  const handleSelect = (id: string) => {
    setSelectedId(id);
    if (viewMode !== "detail") {
      setViewMode("detail");
      saveViewMode("episodes", "detail");
    }
  };

  const handleViewModeChange = (mode: ViewMode) => {
    setViewMode(mode);
    saveViewMode("episodes", mode);
  };

  const handleToggleSidebar = () => {
    const newCollapsed = !isSidebarCollapsed;
    setIsSidebarCollapsed(newCollapsed);
    saveSidebarCollapsed("episodes", newCollapsed);
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

  // Create episode
  const handleCreate = async () => {
    if (!currentWorkspaceId || !scriptId || !createForm.title.trim()) return;

    try {
      setIsCreating(true);
      await projectService.createEpisode( {
        scriptId,
        title: createForm.title.trim(),
        synopsis: createForm.synopsis.trim() || undefined,
      });

      setShowCreateForm(false);
      resetCreateForm();
      fetchEpisodes();
    } catch (err) {
      console.error("Failed to create episode:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsCreating(false);
    }
  };

  // Edit episode - switch to detail view
  const handleEdit = (id: string) => {
    setSelectedId(id);
    setViewMode("detail");
    saveViewMode("episodes", "detail");
  };

  // Copy episode - fetch full detail then create new episode
  const handleCopy = async (id: string) => {
    if (!currentWorkspaceId) return;

    try {
      // Fetch full episode detail to copy all data
      const episodeDetail = await projectService.getEpisode( id);

      // Create new episode with copied data
      const newEpisode = await projectService.createEpisode( {
        scriptId,
        title: `${episodeDetail.title} (副本)`,
        synopsis: episodeDetail.synopsis || undefined,
      });

      // If the original episode has content, update the new episode with it
      if (episodeDetail.content) {
        await projectService.updateEpisode( newEpisode.id, {
          content: episodeDetail.content,
        });
      }

      fetchEpisodes();
    } catch (err) {
      console.error("Failed to copy episode:", err);
      toast.danger(getErrorFromException(err, locale));
    }
  };

  // Delete episode
  const handleDelete = async (id: string) => {
    if (!currentWorkspaceId) return;

    try {
      await projectService.deleteEpisode( id);
      if (selectedId === id) {
        setSelectedId(items[0]?.id || "");
      }
      fetchEpisodes();
    } catch (err) {
      console.error("Failed to delete episode:", err);
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
        <Button variant="ghost" size="sm" onPress={fetchEpisodes}>
          重新加载
        </Button>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      {/* Toolbar */}
      <Surface variant="secondary" className="rounded-2xl mb-3 flex shrink-0 items-center justify-between gap-3 p-2">
        {/* Left Section: View Mode + Search */}
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
          <SearchField aria-label="搜索剧集" value={searchKeyword} onChange={setSearchKeyword}>
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-36" placeholder="搜索剧集..." />
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
          <SortButtons value={sortKeys} onChange={setSortKeys} fields={["sequence", "updatedAt", "createdAt", "name"]} />
        </div>

        {/* Right Section: Refresh + Add */}
        <div className="flex items-center gap-1">
          <Tooltip delay={0}>
            <Button variant="ghost" size="sm" className="size-8 p-0 text-muted hover:text-foreground" onPress={fetchEpisodes}>
              <RefreshCw className={`size-4 ${isLoading ? "animate-spin" : ""}`} />
            </Button>
            <Tooltip.Content>刷新</Tooltip.Content>
          </Tooltip>

          <Separator orientation="vertical" className="mx-0.5 h-5 self-center" />

          <Button
            variant="primary"
            size="sm"
            className="gap-1.5 text-xs"
            onPress={() => setShowCreateForm(true)}
          >
            <Plus className="size-3.5" />
            新增剧集
          </Button>
        </div>
      </Surface>

      {/* Content */}
      <div className="min-h-0 flex-1">
        {viewMode === "list" && (
          <ScrollShadow className="h-full" hideScrollBar>
            <EntityListView
              items={filteredItems}
              onSelect={handleSelect}
              onEdit={handleEdit}
              onCopy={handleCopy}
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
              onCopy={handleCopy}
              onDelete={handleDelete}
            />
          </ScrollShadow>
        )}
        {viewMode === "detail" && (
          <EpisodeDetailView
            items={filteredItems}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onDelete={handleDelete}
            isSidebarCollapsed={isSidebarCollapsed}
            onToggleSidebar={handleToggleSidebar}
            scriptId={scriptId}
            onRefresh={fetchEpisodes}
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
                <Film className="size-5" />
              </Modal.Icon>
              <Modal.Heading>新增剧集</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="overflow-visible">
              <div className="space-y-4">
                <div className="space-y-1.5">
                  <Label className="text-xs font-medium text-muted">标题 *</Label>
                  <Input
                    variant="secondary"
                    value={createForm.title}
                    onChange={(e) => setCreateForm((prev) => ({ ...prev, title: e.target.value }))}
                    className="w-full"
                    placeholder="输入剧集标题"
                    autoFocus
                  />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-xs font-medium text-muted">简介</Label>
                  <TextArea
                    variant="secondary"
                    value={createForm.synopsis}
                    onChange={(e) => setCreateForm((prev) => ({ ...prev, synopsis: e.target.value }))}
                    className="min-h-20 w-full"
                    placeholder="输入剧集简介（可选）"
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
                isPending={isCreating}
                isDisabled={!createForm.title.trim()}
              >
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Plus className="size-3.5" />}创建</>)}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </div>
  );
}

export default EpisodeTab;
