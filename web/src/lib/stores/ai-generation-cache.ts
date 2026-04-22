/**
 * AI Generation Cache - 统一缓存管理
 * 用于记录用户的模型类型、模型选择、参数配置等
 */

import { create } from "zustand";
import { persist } from "zustand/middleware";
import { ProviderType } from "@/lib/api/dto/ai.dto";
import { createPersistStorage } from "./persist-storage";

// 缓存 key
const CACHE_VERSION = 1;

// 缓存数据结构
export interface AIGenerationCacheData {
  version: number;
  // 最后选择的生成类型
  lastGenerationType: ProviderType;
  // 每种类型对应的最后选择的模型 ID
  lastProviderByType: Record<ProviderType, string | null>;
  // 每个模型对应的参数配置 (providerId -> params)
  paramsByProvider: Record<string, Record<string, unknown>>;
  // 图片生成的风格选择 (scriptId -> styleId)
  styleByScript: Record<string, string>;
  // 分镜的宫格选择
  gridSize: string;
  // 更新时间
  updatedAt: number;
}

interface AIGenerationCacheState extends AIGenerationCacheData {
  setGenerationType: (type: ProviderType) => void;
  setLastProvider: (type: ProviderType, providerId: string) => void;
  setProviderParams: (providerId: string, params: Record<string, unknown>) => void;
  updateProviderParam: (providerId: string, paramName: string, value: unknown) => void;
  setStyleForScript: (scriptId: string, styleId: string) => void;
  setGridSize: (gridSize: string) => void;
  clear: () => void;
}

function buildDefaultCache(): AIGenerationCacheData {
  return {
    version: CACHE_VERSION,
    lastGenerationType: "IMAGE",
    lastProviderByType: {
      IMAGE: null,
      VIDEO: null,
      AUDIO: null,
      TEXT: null,
    },
    paramsByProvider: {},
    styleByScript: {},
    gridSize: "1x1",
    updatedAt: Date.now(),
  };
}

export const useAIGenerationCacheStore = create<AIGenerationCacheState>()(
  persist(
    (set) => ({
      ...buildDefaultCache(),
      setGenerationType: (type) =>
        set((state) => ({
          ...state,
          lastGenerationType: type,
          updatedAt: Date.now(),
        })),
      setLastProvider: (type, providerId) =>
        set((state) => ({
          ...state,
          lastProviderByType: {
            ...state.lastProviderByType,
            [type]: providerId,
          },
          updatedAt: Date.now(),
        })),
      setProviderParams: (providerId, params) =>
        set((state) => ({
          ...state,
          paramsByProvider: {
            ...state.paramsByProvider,
            [providerId]: params,
          },
          updatedAt: Date.now(),
        })),
      updateProviderParam: (providerId, paramName, value) =>
        set((state) => ({
          ...state,
          paramsByProvider: {
            ...state.paramsByProvider,
            [providerId]: {
              ...(state.paramsByProvider[providerId] || {}),
              [paramName]: value,
            },
          },
          updatedAt: Date.now(),
        })),
      setStyleForScript: (scriptId, styleId) =>
        set((state) => ({
          ...state,
          styleByScript: {
            ...state.styleByScript,
            [scriptId]: styleId,
          },
          updatedAt: Date.now(),
        })),
      setGridSize: (gridSize) =>
        set((state) => ({
          ...state,
          gridSize,
          updatedAt: Date.now(),
        })),
      clear: () => set({ ...buildDefaultCache() }),
    }),
    {
      name: "actionow_ai_generation_cache_store",
      storage: createPersistStorage<AIGenerationCacheData>(),
      partialize: (state) => ({
        version: state.version,
        lastGenerationType: state.lastGenerationType,
        lastProviderByType: state.lastProviderByType,
        paramsByProvider: state.paramsByProvider,
        styleByScript: state.styleByScript,
        gridSize: state.gridSize,
        updatedAt: state.updatedAt,
      }),
    }
  )
);

/**
 * AI Generation Cache 管理器
 */
export const aiGenerationCache = {
  /**
   * 获取最后选择的生成类型
   */
  getLastGenerationType(): ProviderType {
    return useAIGenerationCacheStore.getState().lastGenerationType;
  },

  /**
   * 设置生成类型
   */
  setGenerationType(type: ProviderType): void {
    useAIGenerationCacheStore.getState().setGenerationType(type);
  },

  /**
   * 获取指定类型的最后选择的模型 ID
   */
  getLastProvider(type: ProviderType): string | null {
    return useAIGenerationCacheStore.getState().lastProviderByType[type] || null;
  },

  /**
   * 设置指定类型的模型选择
   */
  setLastProvider(type: ProviderType, providerId: string): void {
    useAIGenerationCacheStore.getState().setLastProvider(type, providerId);
  },

  /**
   * 获取指定模型的参数配置
   */
  getProviderParams(providerId: string): Record<string, unknown> {
    return useAIGenerationCacheStore.getState().paramsByProvider[providerId] || {};
  },

  /**
   * 设置指定模型的参数配置
   */
  setProviderParams(providerId: string, params: Record<string, unknown>): void {
    useAIGenerationCacheStore.getState().setProviderParams(providerId, params);
  },

  /**
   * 更新指定模型的单个参数
   */
  updateProviderParam(providerId: string, paramName: string, value: unknown): void {
    useAIGenerationCacheStore.getState().updateProviderParam(providerId, paramName, value);
  },

  /**
   * 获取指定剧本的风格选择
   */
  getStyleForScript(scriptId: string): string {
    return useAIGenerationCacheStore.getState().styleByScript[scriptId] || "";
  },

  /**
   * 设置指定剧本的风格选择
   */
  setStyleForScript(scriptId: string, styleId: string): void {
    useAIGenerationCacheStore.getState().setStyleForScript(scriptId, styleId);
  },

  /**
   * 获取宫格选择
   */
  getGridSize(): string {
    return useAIGenerationCacheStore.getState().gridSize;
  },

  /**
   * 设置宫格选择
   */
  setGridSize(gridSize: string): void {
    useAIGenerationCacheStore.getState().setGridSize(gridSize);
  },

  /**
   * 清除所有缓存
   */
  clear(): void {
    useAIGenerationCacheStore.getState().clear();
  },
};
