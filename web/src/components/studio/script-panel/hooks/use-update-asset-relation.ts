/**
 * useUpdateAssetRelation Hook
 * Handles updating entity-asset relation (e.g., toggling OFFICIAL/DRAFT status)
 */

import { useState, useCallback } from "react";
import { projectService } from "@/lib/api/services";
import type { EntityAssetRelationDTO, UpdateEntityAssetRelationRequestDTO } from "@/lib/api/dto";

interface UseUpdateAssetRelationOptions {
  workspaceId: string;
  /** Optional callback after relation is updated */
  onSuccess?: (data: EntityAssetRelationDTO) => void;
  /** Optional callback on error */
  onError?: (error: Error) => void;
}

export function useUpdateAssetRelation({
  workspaceId,
  onSuccess,
  onError,
}: UseUpdateAssetRelationOptions) {
  const [isUpdating, setIsUpdating] = useState(false);

  const updateRelation = useCallback(
    async (relationId: string, data: UpdateEntityAssetRelationRequestDTO) => {
      setIsUpdating(true);
      try {
        const result = await projectService.updateEntityAssetRelation( relationId, data);
        onSuccess?.(result);
        return result;
      } catch (error) {
        const err = error instanceof Error ? error : new Error(String(error));
        onError?.(err);
        throw err;
      } finally {
        setIsUpdating(false);
      }
    },
    [workspaceId, onSuccess, onError]
  );

  /**
   * Toggle relation type between OFFICIAL and DRAFT
   */
  const toggleRelationType = useCallback(
    async (relation: EntityAssetRelationDTO) => {
      const newRelationType = relation.relationType === "OFFICIAL" ? "DRAFT" : "OFFICIAL";
      return updateRelation(relation.id, { relationType: newRelationType });
    },
    [updateRelation]
  );

  return {
    updateRelation,
    toggleRelationType,
    isUpdating,
  };
}

export default useUpdateAssetRelation;
