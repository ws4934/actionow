"use client";

/**
 * Entity Tab Component
 * Handles list/grid/detail views for episodes, storyboards, characters, scenes, props, and assets
 */

import { useState, useEffect, useCallback } from "react";
import {
  Button,
  ButtonGroup,
  ScrollShadow,
  Tooltip,
  Modal,
  Input,
  TextArea,
  Label,
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
  Loader2,
  Image as ImageIcon,
} from "lucide-react";
import Image from "@/components/ui/content-image";
import { projectService, getErrorFromException} from "@/lib/api";
import type { EpisodeListDTO } from "@/lib/api/dto";
import { useWorkspace } from "@/components/providers/workspace-provider";
import type { TabKey, ViewMode, EntityItem } from "../types";
import { transformToEntityItem, getStoredViewMode, getStoredSidebarCollapsed, setViewMode as saveViewMode, setSidebarCollapsed as saveSidebarCollapsed } from "../utils";
import { getTabLabel } from "../constants";
import { StatusBadge, EntityListView, EntityGridView, FormSelect, EntityTabSkeleton, SortButtons, sortItems, EmptyState, type SortKey } from "../common";
import { EntityDetailContent } from "./detail-content";
import { useLocale } from "next-intl";

interface EntityTabProps {
  tabKey: TabKey;
  scriptId: string;
}

// Entity Detail View with sidebar
function EntityDetailView({
  items,
  selectedId,
  onSelect,
  isSidebarCollapsed,
  onToggleSidebar,
  tabKey,
  scriptId,
}: {
  items: EntityItem[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  isSidebarCollapsed: boolean;
  onToggleSidebar: () => void;
  tabKey: TabKey;
  scriptId: string;
}) {
  const selectedItem = items.find(item => item.id === selectedId) || items[0];

  if (items.length === 0) {
    return <EmptyState title="暂无数据" />;
  }

  return (
    <div className="flex h-full gap-3">
      {/* Sidebar - Collapsible */}
      <div
        className={`shrink-0 overflow-hidden rounded-xl bg-muted/5 transition-all duration-300 ${
          isSidebarCollapsed ? "w-14" : "w-52"
        }`}
      >
        <div className="flex h-full flex-col">
          {/* Sidebar Toggle */}
          <div className="flex shrink-0 items-center justify-center border-b border-muted/10 p-2">
            <Button
              variant="ghost"
              size="sm"
              className={`gap-1.5 text-xs ${isSidebarCollapsed ? "px-2" : "w-full justify-start px-2"}`}
              onPress={onToggleSidebar}
            >
              {isSidebarCollapsed ? (
                <PanelLeft className="size-4" />
              ) : (
                <>
                  <PanelLeftClose className="size-4" />
                  <span>收起列表</span>
                </>
              )}
            </Button>
          </div>

          {/* Item List */}
          <ScrollShadow className="min-h-0 flex-1 p-1.5" hideScrollBar>
            <div className="space-y-1.5">
              {items.map((item) => (
                <Tooltip key={item.id} delay={0} isDisabled={!isSidebarCollapsed}>
                  <div
                    className={`cursor-pointer overflow-hidden rounded-lg transition-all ${
                      selectedItem?.id === item.id
                        ? "bg-accent/10 ring-1 ring-accent/30"
                        : "hover:bg-muted/10"
                    }`}
                    onClick={() => onSelect(item.id)}
                  >
                    {isSidebarCollapsed ? (
                      <div className="flex aspect-square items-center justify-center p-1">
                        {item.coverUrl ? (
                          <div className="relative size-full overflow-hidden rounded">
                            <Image src={item.coverUrl} alt="" fill className="object-cover" sizes="120px" />
                          </div>
                        ) : (
                          <div className="flex size-full items-center justify-center rounded bg-muted/10">
                            <span className="text-sm font-medium">{item.title.charAt(0)}</span>
                          </div>
                        )}
                      </div>
                    ) : (
                      <div className="flex items-center gap-2 p-2">
                        <div className="relative h-10 w-14 shrink-0 overflow-hidden rounded bg-muted/10">
                          {item.coverUrl ? (
                            <Image src={item.coverUrl} alt="" fill className="object-cover" sizes="120px" />
                          ) : (
                            <div className="flex size-full items-center justify-center">
                              <Film className="size-4 text-muted/30" />
                            </div>
                          )}
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-xs font-medium">{item.title}</p>
                          <div className="mt-0.5">
                            <StatusBadge status={item.status} />
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                  <Tooltip.Content className="max-w-48">
                    <div>
                      <p className="font-medium">{item.title}</p>
                      {item.description && (
                        <p className="mt-1 text-xs text-muted">{item.description}</p>
                      )}
                    </div>
                  </Tooltip.Content>
                </Tooltip>
              ))}
            </div>
          </ScrollShadow>
        </div>
      </div>

      {/* Detail Content */}
      <div className="min-w-0 flex-1 overflow-hidden rounded-xl bg-muted/5">
        {selectedItem ? (
          <EntityDetailContent
            entityId={selectedItem.id}
            tabKey={tabKey}
            scriptId={scriptId}
          />
        ) : (
          <div className="flex h-full items-center justify-center text-sm text-muted">
            请选择一个项目
          </div>
        )}
      </div>
    </div>
  );
}

// Entity Tab Component
export function EntityTab({ tabKey, scriptId }: EntityTabProps) {
  const locale = useLocale();
  const { currentWorkspaceId } = useWorkspace();
  const [viewMode, setViewMode] = useState<ViewMode>(() => getStoredViewMode(tabKey));
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [items, setItems] = useState<EntityItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(() => getStoredSidebarCollapsed(tabKey));
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [sortKeys, setSortKeys] = useState<SortKey[]>(["updatedAt_desc"]);

  // Episodes list for storyboard creation
  const [episodesList, setEpisodesList] = useState<EpisodeListDTO[]>([]);

  // Create form state
  const [createForm, setCreateForm] = useState({
    name: "",
    title: "",
    description: "",
    synopsis: "",
    episodeId: "",
    duration: "",
    gender: "",
    age: "",
    characterType: "",
    propType: "",
  });

  const resetCreateForm = () => {
    setCreateForm({
      name: "",
      title: "",
      description: "",
      synopsis: "",
      episodeId: "",
      duration: "",
      gender: "",
      age: "",
      characterType: "",
      propType: "",
    });
  };

  // Fetch episodes when opening storyboard create form
  useEffect(() => {
    const fetchEpisodes = async () => {
      if (tabKey === "storyboards" && showCreateForm && currentWorkspaceId && scriptId) {
        try {
          const episodes = await projectService.getEpisodesByScript( scriptId);
          setEpisodesList(episodes);
          if (episodes.length > 0) {
            setCreateForm(prev => prev.episodeId ? prev : { ...prev, episodeId: episodes[0].id });
          }
        } catch (err) {
          console.error("Failed to fetch episodes:", err);
          toast.danger(getErrorFromException(err, locale));
        }
      }
    };
    fetchEpisodes();
  }, [tabKey, showCreateForm, currentWorkspaceId, scriptId]);

  // Fetch data
  const fetchData = useCallback(async () => {
    if (!currentWorkspaceId || !scriptId) return;

    try {
      setIsLoading(true);
      setError(null);

      let data: EntityItem[] = [];

      switch (tabKey) {
        case "episodes": {
          const episodes = await projectService.getEpisodesByScript( scriptId);
          data = episodes.map((item) => transformToEntityItem(item, tabKey));
          break;
        }
        case "storyboards": {
          const storyboards = await projectService.getStoryboardsByScript( scriptId);
          data = storyboards.map((item) => transformToEntityItem(item, tabKey));
          break;
        }
        case "characters": {
          const characters = await projectService.getCharactersByScript( scriptId);
          data = characters.map((item) => transformToEntityItem(item, tabKey));
          break;
        }
        case "scenes": {
          const scenes = await projectService.getScenesByScript( scriptId);
          data = scenes.map((item) => transformToEntityItem(item, tabKey));
          break;
        }
        case "props": {
          const props = await projectService.getPropsByScript( scriptId);
          data = props.map((item) => transformToEntityItem(item, tabKey));
          break;
        }
        case "assets": {
          const assets = await projectService.getAssetsByScript( scriptId);
          data = assets.map((item) => transformToEntityItem(item, tabKey));
          break;
        }
      }

      setItems(data);
    } catch (err) {
      console.error(`Failed to fetch ${tabKey}:`, err);
      toast.danger(getErrorFromException(err, locale));
      setError(`加载${getTabLabel(tabKey)}失败`);
    } finally {
      setIsLoading(false);
    }
  }, [currentWorkspaceId, scriptId, tabKey]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleSelect = (id: string) => {
    setSelectedId(id);
    if (viewMode !== "detail") {
      setViewMode("detail");
      saveViewMode(tabKey, "detail");
    }
  };

  const handleViewModeChange = (mode: ViewMode) => {
    setViewMode(mode);
    saveViewMode(tabKey, mode);
  };

  const handleToggleSidebar = () => {
    const newCollapsed = !isSidebarCollapsed;
    setIsSidebarCollapsed(newCollapsed);
    saveSidebarCollapsed(tabKey, newCollapsed);
  };

  const handleSearchChange = (keyword: string) => {
    setSearchKeyword(keyword);
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

  // Handle create
  const handleCreate = async () => {
    if (!currentWorkspaceId || !scriptId) return;

    try {
      setIsCreating(true);

      switch (tabKey) {
        case "episodes":
          if (!createForm.title.trim()) return;
          await projectService.createEpisode( {
            scriptId,
            title: createForm.title.trim(),
            synopsis: createForm.synopsis.trim() || undefined,
          });
          break;

        case "storyboards":
          if (!createForm.episodeId) return;
          await projectService.createStoryboard( {
            episodeId: createForm.episodeId,
            title: createForm.title.trim() || undefined,
            synopsis: createForm.synopsis.trim() || undefined,
            duration: createForm.duration ? parseInt(createForm.duration) : undefined,
          });
          break;

        case "characters":
          if (!createForm.name.trim()) return;
          await projectService.createCharacter( {
            scriptId,
            name: createForm.name.trim(),
            description: createForm.description.trim() || undefined,
            gender: createForm.gender || undefined,
            age: createForm.age ? parseInt(createForm.age) : undefined,
            characterType: createForm.characterType || undefined,
          });
          break;

        case "scenes":
          if (!createForm.name.trim()) return;
          await projectService.createScene( {
            scriptId,
            name: createForm.name.trim(),
            description: createForm.description.trim() || undefined,
          });
          break;

        case "props":
          if (!createForm.name.trim()) return;
          await projectService.createProp( {
            scriptId,
            name: createForm.name.trim(),
            description: createForm.description.trim() || undefined,
            propType: createForm.propType || undefined,
          });
          break;

        case "assets":
          console.log("Asset creation requires file upload");
          break;
      }

      setShowCreateForm(false);
      resetCreateForm();
      fetchData();
    } catch (err) {
      console.error(`Failed to create ${tabKey}:`, err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsCreating(false);
    }
  };

  const isCreateFormValid = () => {
    switch (tabKey) {
      case "episodes":
        return createForm.title.trim().length > 0;
      case "storyboards":
        return createForm.episodeId.length > 0;
      case "characters":
      case "scenes":
      case "props":
        return createForm.name.trim().length > 0;
      default:
        return false;
    }
  };

  // Handle edit - switch to detail view
  const handleEdit = (id: string) => {
    setSelectedId(id);
    setViewMode("detail");
    saveViewMode(tabKey, "detail");
  };

  // Handle copy
  const handleCopy = async (id: string) => {
    if (!currentWorkspaceId) return;
    const item = items.find((i) => i.id === id);
    if (!item) return;

    try {
      switch (tabKey) {
        case "episodes":
          await projectService.createEpisode( {
            scriptId,
            title: `${item.title} (副本)`,
            synopsis: item.description || undefined,
          });
          break;
        case "characters":
          await projectService.createCharacter( {
            scriptId,
            name: `${item.title} (副本)`,
            description: item.description || undefined,
          });
          break;
        case "scenes":
          await projectService.createScene( {
            scriptId,
            name: `${item.title} (副本)`,
            description: item.description || undefined,
          });
          break;
        case "props":
          await projectService.createProp( {
            scriptId,
            name: `${item.title} (副本)`,
            description: item.description || undefined,
          });
          break;
      }
      fetchData();
    } catch (err) {
      console.error(`Failed to copy ${tabKey}:`, err);
      toast.danger(getErrorFromException(err, locale));
    }
  };

  // Handle delete
  const handleDelete = async (id: string) => {
    if (!currentWorkspaceId) return;

    try {
      switch (tabKey) {
        case "episodes":
          await projectService.deleteEpisode( id);
          break;
        case "storyboards":
          await projectService.deleteStoryboard( id);
          break;
        case "characters":
          await projectService.deleteCharacter( id);
          break;
        case "scenes":
          await projectService.deleteScene( id);
          break;
        case "props":
          await projectService.deleteProp( id);
          break;
      }
      // Clear selection if deleted item was selected
      if (selectedId === id) {
        setSelectedId(items[0]?.id || "");
      }
      fetchData();
    } catch (err) {
      console.error(`Failed to delete ${tabKey}:`, err);
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
        <Button variant="ghost" size="sm" onPress={fetchData}>
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
          <SearchField aria-label={`搜索${getTabLabel(tabKey)}`} value={searchKeyword} onChange={handleSearchChange}>
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-36" placeholder={`搜索${getTabLabel(tabKey)}...`} />
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

        {/* Right Section: Refresh + Add */}
        <div className="flex items-center gap-1">
          <Tooltip delay={0}>
            <Button variant="ghost" size="sm" className="size-8 p-0 text-muted hover:text-foreground" onPress={fetchData}>
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
            新增
          </Button>
        </div>
      </Surface>

      {/* Content */}
      <div className="min-h-0 flex-1">
        {viewMode === "list" && (
          <ScrollShadow className="h-full" hideScrollBar>
            <EntityListView items={filteredItems} onSelect={handleSelect} />
          </ScrollShadow>
        )}
        {viewMode === "grid" && (
          <ScrollShadow className="h-full" hideScrollBar>
            <EntityGridView
              items={filteredItems}
              onSelect={handleSelect}
              onEdit={tabKey !== "assets" ? handleEdit : undefined}
              onCopy={tabKey !== "assets" && tabKey !== "storyboards" ? handleCopy : undefined}
              onDelete={tabKey !== "assets" ? handleDelete : undefined}
            />
          </ScrollShadow>
        )}
        {viewMode === "detail" && (
          <EntityDetailView
            items={filteredItems}
            selectedId={selectedId}
            onSelect={setSelectedId}
            isSidebarCollapsed={isSidebarCollapsed}
            onToggleSidebar={handleToggleSidebar}
            tabKey={tabKey}
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
                <Plus className="size-5" />
              </Modal.Icon>
              <Modal.Heading>新增{getTabLabel(tabKey)}</Modal.Heading>
            </Modal.Header>
            <Modal.Body>
              <div className="space-y-4">
                {/* Episode Form */}
                {tabKey === "episodes" && (
                  <>
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
                        className="w-full resize-none"
                        rows={3}
                        placeholder="输入剧集简介（可选）"
                      />
                    </div>
                  </>
                )}

                {/* Storyboard Form */}
                {tabKey === "storyboards" && (
                  <>
                    <div>
                      {episodesList.length === 0 ? (
                        <div className="space-y-1.5">
                          <Label className="text-xs font-medium text-muted">所属剧集 *</Label>
                          <p className="text-sm text-warning">请先创建剧集</p>
                        </div>
                      ) : (
                        <FormSelect
                          label="所属剧集 *"
                          value={createForm.episodeId}
                          onChange={(value) => setCreateForm((prev) => ({ ...prev, episodeId: value }))}
                          options={episodesList.map((ep) => ({
                            value: ep.id,
                            label: `第${ep.sequence}集 - ${ep.title}`,
                          }))}
                          placeholder="选择剧集"
                          className="w-full"
                        />
                      )}
                    </div>
                    <div className="space-y-1.5">
                      <Label className="text-xs font-medium text-muted">标题</Label>
                      <Input
                        variant="secondary"
                        value={createForm.title}
                        onChange={(e) => setCreateForm((prev) => ({ ...prev, title: e.target.value }))}
                        className="w-full"
                        placeholder="输入分镜标题（可选）"
                      />
                    </div>
                    <div className="flex gap-3">
                      <div className="flex-1 space-y-1.5">
                        <Label className="text-xs font-medium text-muted">简介</Label>
                        <Input
                          variant="secondary"
                          value={createForm.synopsis}
                          onChange={(e) => setCreateForm((prev) => ({ ...prev, synopsis: e.target.value }))}
                          className="w-full"
                          placeholder="简介（可选）"
                        />
                      </div>
                      <div className="w-24 space-y-1.5">
                        <Label className="text-xs font-medium text-muted">时长(秒)</Label>
                        <Input
                          variant="secondary"
                          type="number"
                          value={createForm.duration}
                          onChange={(e) => setCreateForm((prev) => ({ ...prev, duration: e.target.value }))}
                          className="w-full"
                          placeholder="秒"
                          min="0"
                        />
                      </div>
                    </div>
                  </>
                )}

                {/* Character Form */}
                {tabKey === "characters" && (
                  <>
                    <div className="space-y-1.5">
                      <Label className="text-xs font-medium text-muted">名称 *</Label>
                      <Input
                        variant="secondary"
                        value={createForm.name}
                        onChange={(e) => setCreateForm((prev) => ({ ...prev, name: e.target.value }))}
                        className="w-full"
                        placeholder="输入角色名称"
                        autoFocus
                      />
                    </div>
                    <div className="flex gap-3">
                      <div className="flex-1">
                        <FormSelect
                          label="性别"
                          value={createForm.gender}
                          onChange={(value) => setCreateForm((prev) => ({ ...prev, gender: value }))}
                          options={[
                            { value: "", label: "未指定" },
                            { value: "男", label: "男" },
                            { value: "女", label: "女" },
                            { value: "其他", label: "其他" },
                          ]}
                          placeholder="选择性别"
                          className="w-full"
                        />
                      </div>
                      <div className="w-24 space-y-1.5">
                        <Label className="text-xs font-medium text-muted">年龄</Label>
                        <Input
                          variant="secondary"
                          type="number"
                          value={createForm.age}
                          onChange={(e) => setCreateForm((prev) => ({ ...prev, age: e.target.value }))}
                          className="w-full"
                          placeholder="岁"
                          min="0"
                        />
                      </div>
                    </div>
                    <FormSelect
                      label="角色类型"
                      value={createForm.characterType}
                      onChange={(value) => setCreateForm((prev) => ({ ...prev, characterType: value }))}
                      options={[
                        { value: "", label: "未指定" },
                        { value: "主角", label: "主角" },
                        { value: "配角", label: "配角" },
                        { value: "路人", label: "路人" },
                        { value: "反派", label: "反派" },
                      ]}
                      placeholder="选择角色类型"
                      className="w-full"
                    />
                    <div className="space-y-1.5">
                      <Label className="text-xs font-medium text-muted">描述</Label>
                      <TextArea
                        variant="secondary"
                        value={createForm.description}
                        onChange={(e) => setCreateForm((prev) => ({ ...prev, description: e.target.value }))}
                        className="w-full resize-none"
                        rows={2}
                        placeholder="输入角色描述（可选）"
                      />
                    </div>
                  </>
                )}

                {/* Scene Form */}
                {tabKey === "scenes" && (
                  <>
                    <div className="space-y-1.5">
                      <Label className="text-xs font-medium text-muted">名称 *</Label>
                      <Input
                        variant="secondary"
                        value={createForm.name}
                        onChange={(e) => setCreateForm((prev) => ({ ...prev, name: e.target.value }))}
                        className="w-full"
                        placeholder="输入场景名称"
                        autoFocus
                      />
                    </div>
                    <div className="space-y-1.5">
                      <Label className="text-xs font-medium text-muted">描述</Label>
                      <TextArea
                        variant="secondary"
                        value={createForm.description}
                        onChange={(e) => setCreateForm((prev) => ({ ...prev, description: e.target.value }))}
                        className="w-full resize-none"
                        rows={3}
                        placeholder="输入场景描述（可选）"
                      />
                    </div>
                  </>
                )}

                {/* Prop Form */}
                {tabKey === "props" && (
                  <>
                    <div className="space-y-1.5">
                      <Label className="text-xs font-medium text-muted">名称 *</Label>
                      <Input
                        variant="secondary"
                        value={createForm.name}
                        onChange={(e) => setCreateForm((prev) => ({ ...prev, name: e.target.value }))}
                        className="w-full"
                        placeholder="输入道具名称"
                        autoFocus
                      />
                    </div>
                    <FormSelect
                      label="道具类型"
                      value={createForm.propType}
                      onChange={(value) => setCreateForm((prev) => ({ ...prev, propType: value }))}
                      options={[
                        { value: "", label: "未指定" },
                        { value: "武器", label: "武器" },
                        { value: "工具", label: "工具" },
                        { value: "装饰", label: "装饰" },
                        { value: "交通工具", label: "交通工具" },
                        { value: "食物", label: "食物" },
                        { value: "其他", label: "其他" },
                      ]}
                      placeholder="选择道具类型"
                      className="w-full"
                    />
                    <div className="space-y-1.5">
                      <Label className="text-xs font-medium text-muted">描述</Label>
                      <TextArea
                        variant="secondary"
                        value={createForm.description}
                        onChange={(e) => setCreateForm((prev) => ({ ...prev, description: e.target.value }))}
                        className="w-full resize-none"
                        rows={3}
                        placeholder="输入道具描述（可选）"
                      />
                    </div>
                  </>
                )}

                {/* Assets */}
                {tabKey === "assets" && (
                  <div className="py-4 text-center">
                    <ImageIcon className="mx-auto size-12 text-muted/30" />
                    <p className="mt-2 text-sm text-muted">素材上传功能开发中</p>
                    <p className="mt-1 text-xs text-muted/70">请使用其他方式上传素材</p>
                  </div>
                )}
              </div>
            </Modal.Body>
            <Modal.Footer>
              <Button
                variant="secondary"
                slot="close"
                isDisabled={isCreating}
              >
                取消
              </Button>
              {tabKey !== "assets" && (
                <Button
                  variant="primary"
                  className="gap-1.5"
                  onPress={handleCreate}
                  isDisabled={isCreating || !isCreateFormValid()}
                >
                  {isCreating ? <Loader2 className="size-3.5 animate-spin" /> : <Plus className="size-3.5" />}
                  创建
                </Button>
              )}
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </div>
  );
}

export default EntityTab;
