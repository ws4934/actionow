"use client";

import { create } from "zustand";

// ============================================================================
// Types
// ============================================================================

export interface AssetDragData {
  assetId: string;
  url: string;
  name: string;
  mimeType: string;
  fileSize?: number;
  assetType: "IMAGE" | "VIDEO" | "AUDIO" | "DOCUMENT";
}

export interface DragDropStoreState {
  draggedAsset: AssetDragData | null;
  isDragging: boolean;
}

export interface DragDropStoreActions {
  setDraggedAsset: (asset: AssetDragData | null) => void;
  startDrag: (asset: AssetDragData) => void;
  endDrag: () => void;
  clearDrag: () => void;
}

export type DragDropStore = DragDropStoreState & DragDropStoreActions;

// ============================================================================
// Constants
// ============================================================================

const INITIAL_STATE: DragDropStoreState = {
  draggedAsset: null,
  isDragging: false,
};

export const ASSET_DRAG_TYPE = "application/x-asset-drag";

// ============================================================================
// Selectors
// ============================================================================

export const dragDropSelectors = {
  isDragging: (state: DragDropStore) => state.isDragging,
  draggedAsset: (state: DragDropStore) => state.draggedAsset,
};

// ============================================================================
// Store
// ============================================================================

export const useDragDropStore = create<DragDropStore>((set) => ({
  ...INITIAL_STATE,

  setDraggedAsset: (asset) => set({ draggedAsset: asset }),

  startDrag: (asset) =>
    set({
      draggedAsset: asset,
      isDragging: true,
    }),

  endDrag: () => set({ ...INITIAL_STATE }),

  clearDrag: () => set({ ...INITIAL_STATE }),
}));

export function useDragDropState() {
  const isDragging = useDragDropStore(dragDropSelectors.isDragging);
  const draggedAsset = useDragDropStore(dragDropSelectors.draggedAsset);

  return {
    isDragging,
    draggedAsset,
  };
}

export function useDragDropActions() {
  const setDraggedAsset = useDragDropStore((state) => state.setDraggedAsset);
  const startDrag = useDragDropStore((state) => state.startDrag);
  const endDrag = useDragDropStore((state) => state.endDrag);
  const clearDrag = useDragDropStore((state) => state.clearDrag);

  return {
    setDraggedAsset,
    startDrag,
    endDrag,
    clearDrag,
  };
}

// Helper to create drag data from asset
export function createAssetDragData(asset: {
  id?: string;
  assetId?: string;
  fileUrl?: string | null;
  url?: string | null;
  name?: string | null;
  fileName?: string | null;
  mimeType?: string | null;
  fileSize?: number | null;
  assetType?: string | null;
}): AssetDragData {
  return {
    assetId: asset.assetId || asset.id || "",
    url: asset.fileUrl || asset.url || "",
    name: asset.name || asset.fileName || "未命名",
    mimeType: asset.mimeType || "application/octet-stream",
    fileSize: asset.fileSize ?? undefined,
    assetType: (asset.assetType as AssetDragData["assetType"]) || "IMAGE",
  };
}

