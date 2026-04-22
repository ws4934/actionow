"use client";

import { type RefObject } from "react";
import { useTranslations } from "next-intl";
import {
  Button,
  Spinner,
  Tabs,
  SearchField,
  Select,
  ListBox,
} from "@heroui/react";
import {
  RefreshCw,
  Plus,
  Clock,
  ArrowUp,
  ArrowDown,
  ArrowDownAZ,
} from "lucide-react";
import type { EntityType } from "./material-sidebar";

export type SourceMode = "all" | "public" | "workspace" | "script";
export type SortField = "publishedAt" | "name";
export type SortDir = "asc" | "desc";

const SOURCE_TABS: { id: SourceMode; labelKey: string }[] = [
  { id: "all", labelKey: "source.all" },
  { id: "public", labelKey: "source.public" },
  { id: "workspace", labelKey: "source.workspace" },
  { id: "script", labelKey: "source.script" },
];

interface MaterialHeaderProps {
  searchKeyword: string;
  onSearchChange: (keyword: string) => void;
  isRefreshing: boolean;
  onRefresh: () => void;
  sortField: SortField;
  sortDir: SortDir;
  onSortChange: (field: SortField, dir: SortDir) => void;
  isSystemAdmin?: boolean;
  onCreate?: () => void;
  entityType?: EntityType;
  sourceMode: SourceMode;
  onSourceModeChange: (mode: SourceMode) => void;
  scripts?: { id: string; title: string }[];
  selectedScriptId?: string | null;
  onScriptChange?: (scriptId: string) => void;
  /** Ref for spotlight positioning on the source mode selector */
  sourceSelectorRef?: RefObject<HTMLDivElement | null>;
  /** Ref for spotlight positioning on the create button */
  createButtonRef?: RefObject<HTMLDivElement | null>;
  /** Whether a spotlight is currently active */
  spotlightActive?: boolean;
}

export function MaterialHeader({
  searchKeyword,
  onSearchChange,
  isRefreshing,
  onRefresh,
  sortField,
  sortDir,
  onSortChange,
  isSystemAdmin,
  onCreate,
  entityType,
  sourceMode,
  onSourceModeChange,
  scripts,
  selectedScriptId,
  onScriptChange,
  sourceSelectorRef,
  createButtonRef,
  spotlightActive,
}: MaterialHeaderProps) {
  const t = useTranslations("workspace.materialRoom");

  const timeLabel = sourceMode === "public" ? t("sort.publishedAt") : t("sort.createdAt");

  const handleTimeSortPress = () => {
    if (sortField === "publishedAt") {
      onSortChange("publishedAt", sortDir === "desc" ? "asc" : "desc");
    } else {
      onSortChange("publishedAt", "desc");
    }
  };

  const handleNameSortPress = () => {
    if (sortField === "name") {
      onSortChange("name", sortDir === "asc" ? "desc" : "asc");
    } else {
      onSortChange("name", "asc");
    }
  };

  // 创建按钮显示条件：剧组/剧本模式所有人可见，系统模式仅管理员
  const showCreate =
    onCreate &&
    entityType &&
    (sourceMode === "workspace" || sourceMode === "script" || (sourceMode === "public" && isSystemAdmin));

  return (
    <div className="flex shrink-0 items-center justify-between gap-3 pb-4">
      {/* Left: Source Tabs + Script selector */}
      <div
        className={`flex items-center gap-3 ${sourceSelectorRef && spotlightActive ? "relative z-50" : ""}`}
        ref={sourceSelectorRef}
      >
        <Tabs
          selectedKey={sourceMode}
          onSelectionChange={(key) => onSourceModeChange(key as SourceMode)}
        >
          <Tabs.ListContainer>
            <Tabs.List aria-label={t("source.label")}>
              {SOURCE_TABS.map((tab) => (
                <Tabs.Tab
                  key={tab.id}
                  id={tab.id}
                  className="whitespace-nowrap px-3 text-xs"
                >
                  {t(tab.labelKey)}
                  <Tabs.Indicator />
                </Tabs.Tab>
              ))}
            </Tabs.List>
          </Tabs.ListContainer>
        </Tabs>

        {sourceMode === "script" && (
          <Select
            aria-label={t("source.selectScript")}
            className="w-44"
            placeholder={t("source.selectScript")}
            value={selectedScriptId || undefined}
            onChange={(value) => value && onScriptChange?.(String(value))}
            variant="secondary"
          >
            <Select.Trigger>
              <Select.Value />
              <Select.Indicator />
            </Select.Trigger>
            <Select.Popover>
              <ListBox>
                {scripts && scripts.length > 0 ? (
                  scripts.map((s) => (
                    <ListBox.Item key={s.id} id={s.id} textValue={s.title}>
                      {s.title}
                      <ListBox.ItemIndicator />
                    </ListBox.Item>
                  ))
                ) : (
                  <ListBox.Item id="__empty" textValue={t("source.noScripts")} isDisabled>
                    {t("source.noScripts")}
                  </ListBox.Item>
                )}
              </ListBox>
            </Select.Popover>
          </Select>
        )}
      </div>

      {/* Right: Sort buttons + Search + Refresh + Create */}
      <div className="flex items-center gap-2">
        {/* Sort: Time */}
        <Button
          variant={sortField === "publishedAt" ? "secondary" : "ghost"}
          size="sm"
          onPress={handleTimeSortPress}
          className="gap-1 text-xs"
        >
          <Clock className="size-3.5" />
          {timeLabel}
          {sortField === "publishedAt" && (
            sortDir === "desc" ? <ArrowDown className="size-3" /> : <ArrowUp className="size-3" />
          )}
        </Button>

        {/* Sort: Name */}
        <Button
          variant={sortField === "name" ? "secondary" : "ghost"}
          size="sm"
          onPress={handleNameSortPress}
          className="gap-1 text-xs"
        >
          <ArrowDownAZ className="size-3.5" />
          {t("sort.name")}
          {sortField === "name" && (
            sortDir === "asc" ? <ArrowUp className="size-3" /> : <ArrowDown className="size-3" />
          )}
        </Button>

        <SearchField
          aria-label={t("searchPlaceholder")}
          value={searchKeyword}
          onChange={onSearchChange}
          variant="secondary"
        >
          <SearchField.Group>
            <SearchField.SearchIcon />
            <SearchField.Input className="w-36" placeholder={t("searchPlaceholder")} />
            <SearchField.ClearButton />
          </SearchField.Group>
        </SearchField>

        <Button
          variant="ghost"
          size="sm"
          isIconOnly
          isPending={isRefreshing}
          onPress={onRefresh}
        >
          {({ isPending }) =>
            isPending ? <Spinner size="sm" color="current" /> : <RefreshCw className="size-4" />
          }
        </Button>

        {showCreate && (
          <div
            ref={createButtonRef}
            className={createButtonRef && spotlightActive ? "relative z-50" : ""}
          >
            <Button size="sm" onPress={onCreate}>
              <Plus className="size-4" />
              {t("admin.create")}
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
