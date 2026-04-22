/**
 * useSetEntityCover Hook
 * Handles entity cover setting with auto-cover logic when toggling publish status
 */

import { useState, useCallback } from "react";
import { projectService } from "@/lib/api/services";
import type { EntityAssetRelationDTO } from "@/lib/api/dto";

type CoverEntityType = "CHARACTER" | "SCENE" | "PROP" | "STORYBOARD" | "SCRIPT";

interface UseSetEntityCoverOptions {
  workspaceId: string;
  entityType: CoverEntityType;
  entityId: string;
  /** Optional callback after cover is set */
  onSuccess?: () => void;
  /** Optional callback on error */
  onError?: (error: Error) => void;
}

export function useSetEntityCover({
  workspaceId,
  entityType,
  entityId,
  onSuccess,
  onError,
}: UseSetEntityCoverOptions) {
  const [isSettingCover, setIsSettingCover] = useState(false);

  const setCover = useCallback(
    async (assetId: string) => {
      setIsSettingCover(true);
      try {
        switch (entityType) {
          case "CHARACTER":
            await projectService.setCharacterCover( entityId, assetId);
            break;
          case "SCENE":
            await projectService.setSceneCover( entityId, assetId);
            break;
          case "PROP":
            await projectService.setPropCover( entityId, assetId);
            break;
          case "STORYBOARD":
            await projectService.setStoryboardCover( entityId, assetId);
            break;
          case "SCRIPT":
            await projectService.setScriptCover( entityId, assetId);
            break;
          default:
            throw new Error(`Unknown entity type: ${entityType}`);
        }
        onSuccess?.();
      } catch (error) {
        onError?.(error instanceof Error ? error : new Error(String(error)));
      } finally {
        setIsSettingCover(false);
      }
    },
    [workspaceId, entityType, entityId, onSuccess, onError]
  );

  /**
   * Handle auto-cover logic when toggling publish status:
   * - When setting to OFFICIAL: auto-set as cover
   * - When setting to DRAFT: find next official asset as cover
   *
   * @param asset - The asset being toggled
   * @param isSettingToOfficial - Whether we're setting to OFFICIAL (true) or DRAFT (false)
   * @param currentCoverAssetId - Current cover asset ID
   * @param allAssets - All assets for this entity
   */
  const handleAutoCoverOnPublish = useCallback(
    async (
      asset: EntityAssetRelationDTO,
      isSettingToOfficial: boolean,
      currentCoverAssetId: string | null | undefined,
      allAssets: EntityAssetRelationDTO[]
    ) => {
      if (isSettingToOfficial) {
        // Setting to OFFICIAL: auto-set as cover
        if (asset.asset && currentCoverAssetId !== asset.asset.id) {
          await setCover(asset.asset.id);
        }
      } else {
        // Setting to DRAFT: find another OFFICIAL asset to be cover
        const nextOfficialAsset = allAssets.find(
          (a) => a.asset?.id !== asset.asset?.id && a.relationType === "OFFICIAL"
        );

        if (nextOfficialAsset?.asset) {
          if (currentCoverAssetId !== nextOfficialAsset.asset.id) {
            await setCover(nextOfficialAsset.asset.id);
          }
        } else {
          // No official asset found, use first available asset
          const firstAsset = allAssets.find((a) => a.asset?.id !== asset.asset?.id);
          if (firstAsset?.asset && currentCoverAssetId !== firstAsset.asset.id) {
            await setCover(firstAsset.asset.id);
          }
        }
      }
    },
    [setCover]
  );

  return {
    setCover,
    handleAutoCoverOnPublish,
    isSettingCover,
  };
}

export default useSetEntityCover;
