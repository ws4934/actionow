"use client";

import {
  createContext,
  useContext,
  useState,
  useCallback,
  type ReactNode,
} from "react";
import type { ProviderType } from "@/lib/api/dto/ai.dto";
import { usePreferencesStore } from "@/lib/stores/preferences-store";

// Entity types that can trigger AI generation (only storyboard, character, scene, prop, asset)
export type EntityType = "CHARACTER" | "SCENE" | "PROP" | "STORYBOARD" | "ASSET";

// Associated cover from storyboard-related entities
export interface AssociatedCover {
  entityType: string; // "CHARACTER" | "SCENE" | "PROP"
  entityName: string;
  coverAssetId: string;
  coverUrl: string;
}

// Entity context for AI generation
export interface EntityContext {
  entityType: EntityType;
  entityId: string;
  entityName: string;
  entityDescription?: string;
  entityFixedDesc?: string;
  entityCoverUrl?: string;
  scriptId?: string;
  episodeId?: string;
  associatedCovers?: AssociatedCover[];
}

// Active panel type
export type ActivePanelType = "agent" | "ai-generation";

interface AIGenerationContextValue {
  // Current entity information
  entityType: EntityType | null;
  entityId: string | null;
  entityName: string;
  entityDescription?: string;
  entityFixedDesc?: string;
  entityCoverUrl?: string;
  scriptId?: string;
  episodeId?: string;
  associatedCovers?: AssociatedCover[];

  // Generation type
  generationType: ProviderType;
  setGenerationType: (type: ProviderType) => void;

  // Panel state
  isPanelOpen: boolean;
  activePanel: ActivePanelType;
  isLocked: boolean;

  // Methods
  openPanel: (entity: EntityContext, type?: ProviderType) => void;
  closePanel: () => void;
  updateEntity: (entity: EntityContext) => void;
  clearEntity: () => void;
  switchToAgentPanel: () => void;
  switchToAIGenerationPanel: () => void;
  togglePanel: () => void;
  setIsLocked: (locked: boolean) => void;
}

const AIGenerationContext = createContext<AIGenerationContextValue | null>(null);

export function AIGenerationProvider({ children }: { children: ReactNode }) {
  // Entity state
  const [entityType, setEntityType] = useState<EntityType | null>(null);
  const [entityId, setEntityId] = useState<string | null>(null);
  const [entityName, setEntityName] = useState<string>("");
  const [entityDescription, setEntityDescription] = useState<string | undefined>();
  const [entityFixedDesc, setEntityFixedDesc] = useState<string | undefined>();
  const [entityCoverUrl, setEntityCoverUrl] = useState<string | undefined>();
  const [scriptId, setScriptId] = useState<string | undefined>();
  const [episodeId, setEpisodeId] = useState<string | undefined>();
  const [associatedCovers, setAssociatedCovers] = useState<AssociatedCover[] | undefined>();

  // Generation type (default to IMAGE)
  const [generationType, setGenerationType] = useState<ProviderType>("IMAGE");

  // Panel state
  const [isPanelOpen, setIsPanelOpen] = useState(false);
  const [activePanel, setActivePanelState] = useState<ActivePanelType>(() => {
    if (typeof window !== "undefined") {
      return usePreferencesStore.getState().activePanel ?? "agent";
    }
    return "agent";
  });
  const [isLocked, setIsLocked] = useState(false);

  const setActivePanel = useCallback((panel: ActivePanelType) => {
    setActivePanelState(panel);
    usePreferencesStore.getState().setActivePanel(panel);
  }, []);

  // Open the AI generation panel with entity context
  const openPanel = useCallback((entity: EntityContext, type?: ProviderType) => {
    setEntityType(entity.entityType);
    setEntityId(entity.entityId);
    setEntityName(entity.entityName);
    setEntityDescription(entity.entityDescription);
    setEntityFixedDesc(entity.entityFixedDesc);
    setEntityCoverUrl(entity.entityCoverUrl);
    setScriptId(entity.scriptId);
    setEpisodeId(entity.episodeId);
    setAssociatedCovers(entity.associatedCovers);

    if (type) {
      setGenerationType(type);
    }

    setIsPanelOpen(true);
    setActivePanel("ai-generation");
  }, [setActivePanel]);

  // Close the panel
  const closePanel = useCallback(() => {
    setIsPanelOpen(false);
  }, []);

  // Update entity context without closing panel (respects lock state)
  const updateEntity = useCallback((entity: EntityContext) => {
    // If locked, don't update entity context
    if (isLocked) return;

    setEntityType(entity.entityType);
    setEntityId(entity.entityId);
    setEntityName(entity.entityName);
    setEntityDescription(entity.entityDescription);
    setEntityFixedDesc(entity.entityFixedDesc);
    setEntityCoverUrl(entity.entityCoverUrl);
    setScriptId(entity.scriptId);
    setEpisodeId(entity.episodeId);
    setAssociatedCovers(entity.associatedCovers);
  }, [isLocked]);

  // Clear entity context (switch to script mode), preserving scriptId/episodeId
  const clearEntity = useCallback(() => {
    setEntityType(null);
    setEntityId(null);
    setEntityName("");
    setEntityDescription(undefined);
    setEntityFixedDesc(undefined);
    setEntityCoverUrl(undefined);
    setAssociatedCovers(undefined);
  }, []);

  // Switch to agent panel
  const switchToAgentPanel = useCallback(() => {
    setActivePanel("agent");
  }, [setActivePanel]);

  // Switch to AI generation panel
  const switchToAIGenerationPanel = useCallback(() => {
    setActivePanel("ai-generation");
  }, [setActivePanel]);

  // Toggle between panels
  const togglePanel = useCallback(() => {
    setActivePanelState((prev) => {
      const next = prev === "agent" ? "ai-generation" : "agent";
      usePreferencesStore.getState().setActivePanel(next);
      return next;
    });
  }, []);

  return (
    <AIGenerationContext.Provider
      value={{
        entityType,
        entityId,
        entityName,
        entityDescription,
        entityFixedDesc,
        entityCoverUrl,
        scriptId,
        episodeId,
        associatedCovers,
        generationType,
        setGenerationType,
        isPanelOpen,
        activePanel,
        isLocked,
        openPanel,
        closePanel,
        updateEntity,
        clearEntity,
        switchToAgentPanel,
        switchToAIGenerationPanel,
        togglePanel,
        setIsLocked,
      }}
    >
      {children}
    </AIGenerationContext.Provider>
  );
}

export function useAIGeneration() {
  const context = useContext(AIGenerationContext);
  if (!context) {
    throw new Error("useAIGeneration must be used within an AIGenerationProvider");
  }
  return context;
}

// Helper to get generation type label
export function getGenerationTypeLabel(type: ProviderType): string {
  switch (type) {
    case "IMAGE":
      return "图片";
    case "VIDEO":
      return "视频";
    case "AUDIO":
      return "音频";
    case "TEXT":
      return "文本";
    default:
      return type;
  }
}

// Helper to get entity type label
export function getEntityTypeLabel(type: EntityType): string {
  switch (type) {
    case "CHARACTER":
      return "角色";
    case "SCENE":
      return "场景";
    case "PROP":
      return "道具";
    case "STORYBOARD":
      return "分镜";
    case "ASSET":
      return "素材";
    default:
      return type;
  }
}
