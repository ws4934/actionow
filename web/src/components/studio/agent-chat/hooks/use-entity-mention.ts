"use client";

import { useState, useCallback, useRef } from "react";
import { projectService, getErrorFromException} from "@/lib/api";
import { useWorkspace } from "@/components/providers/workspace-provider";
import type { EntityCategoryKey, LinkedEntity } from "../types";
import { ENTITY_CATEGORIES } from "../constants";
import { toast } from "@heroui/react";
import { useLocale } from "next-intl";

interface EntityItem {
  id: string;
  name: string;
  coverUrl?: string | null;
}

type EntityCache = Partial<Record<EntityCategoryKey, EntityItem[]>>;

/**
 * Scan backward from cursorPos to find the nearest unmatched `@`.
 * Returns { atIndex, query } or null if no valid mention context.
 */
function findMentionContext(value: string, cursorPos: number) {
  // Search backward from cursor for `@`
  let i = cursorPos - 1;
  while (i >= 0) {
    if (value[i] === "@") {
      const query = value.slice(i + 1, cursorPos);
      // Allow spaces within the search query (entity names can contain spaces)
      // Only break on newline
      if (!query.includes("\n")) {
        return { atIndex: i, query };
      }
      return null;
    }
    if (value[i] === "\n") {
      return null; // hit newline before finding @
    }
    i--;
  }
  return null;
}

export function useEntityMention(scriptId: string) {
  const locale = useLocale();
  const { currentWorkspaceId } = useWorkspace();

  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [activeCategory, setActiveCategory] = useState<EntityCategoryKey>("character");
  const [highlightIndex, setHighlightIndex] = useState(0);
  const [linkedEntities, setLinkedEntities] = useState<LinkedEntity[]>([]);
  const [isLoadingEntities, setIsLoadingEntities] = useState(false);

  const entityCacheRef = useRef<EntityCache>({});
  // Store the index of the `@` character itself (not the position after it)
  const mentionAtIndexRef = useRef<number>(0);
  const fetchAbortRef = useRef<AbortController | null>(null);
  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Fetch entities for a category
  const fetchCategory = useCallback(async (category: EntityCategoryKey) => {
    if (!currentWorkspaceId || !scriptId) return;
    if (entityCacheRef.current[category]) {
      setIsLoadingEntities(false);
      return;
    }

    // Cancel previous in-flight request
    fetchAbortRef.current?.abort();
    const controller = new AbortController();
    fetchAbortRef.current = controller;

    setIsLoadingEntities(true);
    try {
      const sId = scriptId;
      let items: EntityItem[] = [];

      switch (category) {
        case "character": {
          const data = await projectService.getCharactersAvailable(sId);
          items = data.map((c) => ({ id: c.id, name: c.name, coverUrl: c.coverUrl }));
          break;
        }
        case "scene": {
          const data = await projectService.getScenesAvailable(sId);
          items = data.map((s) => ({ id: s.id, name: s.name, coverUrl: s.coverUrl }));
          break;
        }
        case "prop": {
          const data = await projectService.getPropsAvailable(sId);
          items = data.map((p) => ({ id: p.id, name: p.name, coverUrl: p.coverUrl }));
          break;
        }
        case "style": {
          const data = await projectService.getAvailableStyles(sId);
          items = data.map((s) => ({ id: s.id, name: s.name, coverUrl: s.coverUrl }));
          break;
        }
        case "episode": {
          const data = await projectService.getEpisodesByScript(sId);
          items = data.map((e) => ({ id: e.id, name: e.title, coverUrl: e.coverUrl }));
          break;
        }
        case "storyboard": {
          const data = await projectService.getStoryboardsByScript(sId);
          items = data.map((s) => ({ id: s.id, name: s.title || s.id, coverUrl: s.coverUrl }));
          break;
        }
      }

      if (!controller.signal.aborted) {
        entityCacheRef.current[category] = items;
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        console.error(`Failed to fetch ${category} entities:`, error);
        toast.danger(getErrorFromException(error, locale));
        entityCacheRef.current[category] = [];
      }
    } finally {
      if (!controller.signal.aborted) {
        setIsLoadingEntities(false);
      }
    }
  }, [currentWorkspaceId, scriptId]);

  // Open mention popup — atIndex is the index of the `@` char
  const openMention = useCallback((atIndex: number) => {
    mentionAtIndexRef.current = atIndex;
    setIsOpen(true);
    setSearchQuery("");
    setHighlightIndex(0);
    setActiveCategory("character");
    fetchCategory("character");
  }, [fetchCategory]);

  // Close mention popup
  const closeMention = useCallback(() => {
    setIsOpen(false);
    setSearchQuery("");
    setHighlightIndex(0);
  }, []);

  // Change active category
  const changeCategory = useCallback((category: EntityCategoryKey) => {
    setActiveCategory(category);
    setHighlightIndex(0);
    fetchCategory(category);
  }, [fetchCategory]);

  // Update search from the current input value + cursor position.
  // Scans backward from cursor to find the `@` — resilient to edits before `@`.
  // Debounced to avoid excessive filtering on fast typing.
  const updateSearchFromInput = useCallback((inputValue: string, cursorPos: number) => {
    const ctx = findMentionContext(inputValue, cursorPos);
    if (!ctx) {
      if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
      closeMention();
      return;
    }
    mentionAtIndexRef.current = ctx.atIndex;
    if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    searchDebounceRef.current = setTimeout(() => {
      setSearchQuery(ctx.query);
      setHighlightIndex(0);
    }, 80);
  }, [closeMention]);

  // Get filtered entities based on search query and active category
  const getFilteredEntities = useCallback(() => {
    const items = entityCacheRef.current[activeCategory] || [];
    if (!searchQuery) return items;
    const q = searchQuery.toLowerCase();
    return items.filter((item) => item.name.toLowerCase().includes(q));
  }, [activeCategory, searchQuery]);

  // Select an entity
  const selectEntity = useCallback((entity: LinkedEntity) => {
    setLinkedEntities((prev) => {
      const filtered = prev.filter((e) => e.category !== entity.category);
      return [...filtered, entity];
    });
    closeMention();
  }, [closeMention]);

  // Remove entity by category
  const removeEntity = useCallback((category: EntityCategoryKey) => {
    setLinkedEntities((prev) => prev.filter((e) => e.category !== category));
  }, []);

  // Clear all entities
  const clearAllEntities = useCallback(() => {
    setLinkedEntities([]);
  }, []);

  // Get the index of the `@` character for text replacement
  const getMentionAtIndex = useCallback(() => {
    return mentionAtIndexRef.current;
  }, []);

  // Handle keyboard navigation (ArrowUp, ArrowDown, Tab, Escape — Enter handled by parent)
  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    const filtered = getFilteredEntities();

    switch (e.key) {
      case "ArrowDown":
        setHighlightIndex((prev) => (prev + 1) % Math.max(filtered.length, 1));
        break;
      case "ArrowUp":
        setHighlightIndex((prev) => (prev - 1 + Math.max(filtered.length, 1)) % Math.max(filtered.length, 1));
        break;
      case "Tab": {
        e.preventDefault();
        const catKeys = ENTITY_CATEGORIES.map((c) => c.key);
        const curIdx = catKeys.indexOf(activeCategory);
        const nextIdx = e.shiftKey
          ? (curIdx - 1 + catKeys.length) % catKeys.length
          : (curIdx + 1) % catKeys.length;
        changeCategory(catKeys[nextIdx]);
        break;
      }
      case "Escape":
        closeMention();
        break;
    }
  }, [getFilteredEntities, activeCategory, changeCategory, closeMention]);

  return {
    isOpen,
    searchQuery,
    activeCategory,
    highlightIndex,
    linkedEntities,
    isLoadingEntities,
    openMention,
    closeMention,
    changeCategory,
    updateSearchFromInput,
    getFilteredEntities,
    selectEntity,
    removeEntity,
    clearAllEntities,
    getMentionAtIndex,
    handleKeyDown,
  };
}
