/**
 * Project (Script) Service
 * Handles project-related API calls
 */

import { api } from "../client";
import type {
  ScriptListDTO,
  ScriptDetailDTO,
  CreateScriptRequestDTO,
  UpdateScriptRequestDTO,
  PaginatedResponseDTO,
  EpisodeListDTO,
  EpisodeDetailDTO,
  StoryboardListDTO,
  StoryboardDetailDTO,
  CharacterListDTO,
  CharacterDetailDTO,
  SceneListDTO,
  SceneDetailDTO,
  PropListDTO,
  PropDetailDTO,
  AssetListDTO,
  AssetDetailDTO,
  TrashAssetDTO,
  VersionInfoDTO,
  ScriptVersionDetailDTO,
  EpisodeVersionDetailDTO,
  VersionDiffDTO,
  RestoreVersionRequestDTO,
  AssetUploadInitRequestDTO,
  AssetUploadInitResponseDTO,
  AssetUploadConfirmRequestDTO,
  EntityAssetRelationDTO,
  UpdateEntityAssetRelationRequestDTO,
  StyleListDTO,
} from "../dto";

const PROJECT_BASE = "/api/project";

export const projectService = {
  // ==================== Scripts ====================
  /** Create a new script */
  createScript: (data: CreateScriptRequestDTO) =>
    api.post<ScriptDetailDTO>(`${PROJECT_BASE}/scripts`, data, {
      params: {},
    }),

  /** Update a script */
  updateScript: (scriptId: string, data: UpdateScriptRequestDTO) =>
    api.put<ScriptDetailDTO>(`${PROJECT_BASE}/scripts/${scriptId}`, data, {
      params: {},
    }),

  /** Delete a script */
  deleteScript: (scriptId: string) =>
    api.delete<null>(`${PROJECT_BASE}/scripts/${scriptId}`, undefined, {
      params: {},
    }),

  /** Get script detail */
  getScript: (scriptId: string) =>
    api.get<ScriptDetailDTO>(`${PROJECT_BASE}/scripts/${scriptId}`, {
      params: {},
    }),

  /** Get all scripts (non-paginated) */
  getScripts: (status?: string) =>
    api.get<ScriptListDTO[]>(`${PROJECT_BASE}/scripts`, {
      params: status ? { status } : {},
    }),

  /** Query scripts with pagination */
  queryScripts: (params?: Record<string, string | number | boolean | undefined>) =>
    api.get<PaginatedResponseDTO<ScriptListDTO>>(`${PROJECT_BASE}/scripts/query`, {
      params: { ...params },
    }),

  /** Update script status */
  updateScriptStatus: (scriptId: string, status: string) =>
    api.patch<ScriptDetailDTO>(
      `${PROJECT_BASE}/scripts/${scriptId}/status`,
      undefined,
      {
        params: { status },
      }
    ),

  /** Archive a script */
  archiveScript: (scriptId: string) =>
    api.post<null>(`${PROJECT_BASE}/scripts/${scriptId}/archive`, undefined, {
      params: {},
    }),

  // ==================== Script Versions ====================
  /** Get script version list */
  getScriptVersions: (scriptId: string) =>
    api.get<VersionInfoDTO[]>(`${PROJECT_BASE}/scripts/${scriptId}/versions`, {
      params: {},
    }),

  /** Get script version detail */
  getScriptVersion: (scriptId: string, versionNumber: number) =>
    api.get<ScriptVersionDetailDTO>(`${PROJECT_BASE}/scripts/${scriptId}/versions/${versionNumber}`, {
      params: {},
    }),

  /** Get script current version */
  getScriptCurrentVersion: (scriptId: string) =>
    api.get<ScriptVersionDetailDTO>(`${PROJECT_BASE}/scripts/${scriptId}/versions/current`, {
      params: {},
    }),

  /** Restore script to a specific version */
  restoreScriptVersion: (scriptId: string, data: RestoreVersionRequestDTO) =>
    api.post<number>(`${PROJECT_BASE}/scripts/${scriptId}/versions/restore`, data, {
      params: {},
    }),

  /** Compare two script versions */
  compareScriptVersions: (scriptId: string, version1: number, version2: number) =>
    api.get<VersionDiffDTO>(`${PROJECT_BASE}/scripts/${scriptId}/versions/diff`, {
      params: { version1, version2 },
    }),

  // ==================== Episodes ====================
  /** Get episodes for a script */
  getEpisodesByScript: (scriptId: string) =>
    api.get<EpisodeListDTO[]>(`${PROJECT_BASE}/episodes/script/${scriptId}`, {
      params: {},
    }),

  /** Get episode detail */
  getEpisode: (episodeId: string) =>
    api.get<EpisodeDetailDTO>(`${PROJECT_BASE}/episodes/${episodeId}`, {
      params: {},
    }),

  /** Create episode */
  createEpisode: (data: { scriptId: string; title: string; synopsis?: string; sequence?: number }) =>
    api.post<EpisodeDetailDTO>(`${PROJECT_BASE}/episodes`, data, {
      params: {},
    }),

  /** Update episode */
  updateEpisode: (episodeId: string, data: Record<string, unknown>) =>
    api.put<EpisodeDetailDTO>(`${PROJECT_BASE}/episodes/${episodeId}`, data, {
      params: {},
    }),

  /** Delete episode */
  deleteEpisode: (episodeId: string) =>
    api.delete<null>(`${PROJECT_BASE}/episodes/${episodeId}`, undefined, {
      params: {},
    }),

  // ==================== Episode Versions ====================
  /** Get episode versions */
  getEpisodeVersions: (episodeId: string) =>
    api.get<VersionInfoDTO[]>(`${PROJECT_BASE}/episodes/${episodeId}/versions`, {
      params: {},
    }),

  /** Get specific episode version */
  getEpisodeVersion: (episodeId: string, versionNumber: number) =>
    api.get<EpisodeVersionDetailDTO>(`${PROJECT_BASE}/episodes/${episodeId}/versions/${versionNumber}`, {
      params: {},
    }),

  /** Get current episode version */
  getEpisodeCurrentVersion: (episodeId: string) =>
    api.get<EpisodeVersionDetailDTO>(`${PROJECT_BASE}/episodes/${episodeId}/versions/current`, {
      params: {},
    }),

  /** Restore episode to specific version */
  restoreEpisodeVersion: (episodeId: string, data: RestoreVersionRequestDTO) =>
    api.post<EpisodeDetailDTO>(`${PROJECT_BASE}/episodes/${episodeId}/versions/restore`, data, {
      params: {},
    }),

  /** Compare two episode versions */
  compareEpisodeVersions: (episodeId: string, version1: number, version2: number) =>
    api.get<VersionDiffDTO>(`${PROJECT_BASE}/episodes/${episodeId}/versions/diff`, {
      params: { version1, version2 },
    }),

  // ==================== Storyboards ====================
  /** Get storyboards for a script */
  getStoryboardsByScript: (scriptId: string) =>
    api.get<StoryboardListDTO[]>(`${PROJECT_BASE}/storyboards/script/${scriptId}`, {
      params: {},
    }),

  /** Get storyboards for an episode */
  getStoryboardsByEpisode: (episodeId: string) =>
    api.get<StoryboardListDTO[]>(`${PROJECT_BASE}/storyboards/episode/${episodeId}`, {
      params: {},
    }),

  /** Get storyboard detail */
  getStoryboard: (storyboardId: string) =>
    api.get<StoryboardDetailDTO>(`${PROJECT_BASE}/storyboards/${storyboardId}`, {
      params: {},
    }),

  /** Create storyboard */
  createStoryboard: (data: { episodeId: string; title?: string; synopsis?: string; duration?: number }) =>
    api.post<StoryboardDetailDTO>(`${PROJECT_BASE}/storyboards`, data, {
      params: {},
    }),

  /** Update storyboard */
  updateStoryboard: (storyboardId: string, data: Record<string, unknown>) =>
    api.put<StoryboardDetailDTO>(`${PROJECT_BASE}/storyboards/${storyboardId}`, data, {
      params: {},
    }),

  /** Delete storyboard */
  deleteStoryboard: (storyboardId: string) =>
    api.delete<null>(`${PROJECT_BASE}/storyboards/${storyboardId}`, undefined, {
      params: {},
    }),

  // ==================== Storyboard Versions ====================
  /** Get storyboard versions */
  getStoryboardVersions: (storyboardId: string) =>
    api.get<VersionInfoDTO[]>(`${PROJECT_BASE}/storyboards/${storyboardId}/versions`, {
      params: {},
    }),

  /** Get specific storyboard version */
  getStoryboardVersion: (storyboardId: string, versionNumber: number) =>
    api.get<StoryboardDetailDTO>(`${PROJECT_BASE}/storyboards/${storyboardId}/versions/${versionNumber}`, {
      params: {},
    }),

  /** Restore storyboard to specific version */
  restoreStoryboardVersion: (storyboardId: string, data: RestoreVersionRequestDTO) =>
    api.post<StoryboardDetailDTO>(`${PROJECT_BASE}/storyboards/${storyboardId}/versions/restore`, data, {
      params: {},
    }),

  /** Compare two storyboard versions */
  compareStoryboardVersions: (storyboardId: string, version1: number, version2: number) =>
    api.get<VersionDiffDTO>(`${PROJECT_BASE}/storyboards/${storyboardId}/versions/diff`, {
      params: { version1, version2 },
    }),

  // ==================== Storyboard Relations ====================
  /** Add character to storyboard */
  addStoryboardCharacter: (storyboardId: string, data: { characterId: string; action?: string; expression?: string; position?: string }) =>
    api.post<{ id: string }>(`${PROJECT_BASE}/entity-relations`, {
      sourceType: "STORYBOARD",
      sourceId: storyboardId,
      targetType: "CHARACTER",
      targetId: data.characterId,
      relationType: "appears_in",
    }, {
      params: {},
    }),

  /** Remove character from storyboard */
  removeStoryboardCharacter: (_storyboardId: string, relationId: string) =>
    api.delete<null>(`${PROJECT_BASE}/entity-relations/${relationId}`, undefined, {
      params: {},
    }),

  /** Set scene for storyboard */
  setStoryboardScene: (storyboardId: string, data: { sceneId: string }) =>
    api.post<{ id: string }>(`${PROJECT_BASE}/entity-relations`, {
      sourceType: "STORYBOARD",
      sourceId: storyboardId,
      targetType: "SCENE",
      targetId: data.sceneId,
      relationType: "takes_place_in",
    }, {
      params: {},
    }),

  /** Remove scene from storyboard */
  removeStoryboardScene: (_storyboardId: string, relationId: string) =>
    api.delete<null>(`${PROJECT_BASE}/entity-relations/${relationId}`, undefined, {
      params: {},
    }),

  /** Add prop to storyboard */
  addStoryboardProp: (storyboardId: string, data: { propId: string; state?: string; position?: string }) =>
    api.post<{ id: string }>(`${PROJECT_BASE}/entity-relations`, {
      sourceType: "STORYBOARD",
      sourceId: storyboardId,
      targetType: "PROP",
      targetId: data.propId,
      relationType: "uses",
    }, {
      params: {},
    }),

  /** Remove prop from storyboard */
  removeStoryboardProp: (_storyboardId: string, relationId: string) =>
    api.delete<null>(`${PROJECT_BASE}/entity-relations/${relationId}`, undefined, {
      params: {},
    }),

  // ==================== Characters ====================
  /** Get characters available for a script (workspace + script level) */
  getCharactersByScript: (scriptId: string) =>
    api.get<CharacterListDTO[]>(`${PROJECT_BASE}/characters/script/${scriptId}`, {
      params: {},
    }),

  /** Get characters available for a script (workspace + script level) */
  getCharactersAvailable: (scriptId: string) =>
    api.get<CharacterListDTO[]>(`${PROJECT_BASE}/characters/available/${scriptId}`, {
      params: {},
    }),

  /** Get workspace-level characters */
  getCharacters: () =>
    api.get<CharacterListDTO[]>(`${PROJECT_BASE}/characters`, {
      params: {},
    }),

  /** Query characters with pagination */
  queryCharacters: (params?: Record<string, string | number | boolean | undefined>) =>
    api.get<PaginatedResponseDTO<CharacterListDTO>>(`${PROJECT_BASE}/characters/query`, {
      params: { ...params },
    }),

  /** Get character detail */
  getCharacter: (characterId: string) =>
    api.get<CharacterDetailDTO>(`${PROJECT_BASE}/characters/${characterId}`, {
      params: {},
    }),

  /** Create character */
  createCharacter: (data: Record<string, unknown>) =>
    api.post<CharacterDetailDTO>(`${PROJECT_BASE}/characters`, data, {
      params: {},
    }),

  /** Update character */
  updateCharacter: (characterId: string, data: Record<string, unknown>) =>
    api.put<CharacterDetailDTO>(`${PROJECT_BASE}/characters/${characterId}`, data, {
      params: {},
    }),

  /** Delete character */
  deleteCharacter: (characterId: string) =>
    api.delete<null>(`${PROJECT_BASE}/characters/${characterId}`, undefined, {
      params: {},
    }),

  // Character Versions
  /** Get character versions */
  getCharacterVersions: (characterId: string) =>
    api.get<VersionInfoDTO[]>(`${PROJECT_BASE}/characters/${characterId}/versions`, {
      params: {},
    }),

  /** Restore character version */
  restoreCharacterVersion: (characterId: string, data: { versionNumber: number; reason?: string }) =>
    api.post<CharacterDetailDTO>(`${PROJECT_BASE}/characters/${characterId}/versions/restore`, data, {
      params: {},
    }),

  // ==================== Scenes ====================
  /** Get scenes available for a script (workspace + script level) */
  getScenesByScript: (scriptId: string) =>
    api.get<SceneListDTO[]>(`${PROJECT_BASE}/scenes/script/${scriptId}`, {
      params: {},
    }),

  /** Get scenes available for a script (workspace + script level) */
  getScenesAvailable: (scriptId: string) =>
    api.get<SceneListDTO[]>(`${PROJECT_BASE}/scenes/available/${scriptId}`, {
      params: {},
    }),

  /** Get workspace-level scenes */
  getScenes: () =>
    api.get<SceneListDTO[]>(`${PROJECT_BASE}/scenes`, {
      params: {},
    }),

  /** Query scenes with pagination */
  queryScenes: (params?: Record<string, string | number | boolean | undefined>) =>
    api.get<PaginatedResponseDTO<SceneListDTO>>(`${PROJECT_BASE}/scenes/query`, {
      params: { ...params },
    }),

  /** Get scene detail */
  getScene: (sceneId: string) =>
    api.get<SceneDetailDTO>(`${PROJECT_BASE}/scenes/${sceneId}`, {
      params: {},
    }),

  /** Create scene */
  createScene: (data: Record<string, unknown>) =>
    api.post<SceneDetailDTO>(`${PROJECT_BASE}/scenes`, data, {
      params: {},
    }),

  /** Update scene */
  updateScene: (sceneId: string, data: Record<string, unknown>) =>
    api.put<SceneDetailDTO>(`${PROJECT_BASE}/scenes/${sceneId}`, data, {
      params: {},
    }),

  /** Delete scene */
  deleteScene: (sceneId: string) =>
    api.delete<null>(`${PROJECT_BASE}/scenes/${sceneId}`, undefined, {
      params: {},
    }),

  /** Get scene versions */
  getSceneVersions: (sceneId: string) =>
    api.get<VersionInfoDTO[]>(`${PROJECT_BASE}/scenes/${sceneId}/versions`, {
      params: {},
    }),

  /** Restore scene version */
  restoreSceneVersion: (sceneId: string, data: { versionNumber: number; reason?: string }) =>
    api.post<SceneDetailDTO>(`${PROJECT_BASE}/scenes/${sceneId}/versions/restore`, data, {
      params: {},
    }),

  // ==================== Props ====================
  /** Get props available for a script (workspace + script level) */
  getPropsByScript: (scriptId: string) =>
    api.get<PropListDTO[]>(`${PROJECT_BASE}/props/script/${scriptId}`, {
      params: {},
    }),

  /** Get props available for a script (workspace + script level) */
  getPropsAvailable: (scriptId: string) =>
    api.get<PropListDTO[]>(`${PROJECT_BASE}/props/available/${scriptId}`, {
      params: {},
    }),

  /** Get workspace-level props */
  getProps: () =>
    api.get<PropListDTO[]>(`${PROJECT_BASE}/props`, {
      params: {},
    }),

  /** Query props with pagination */
  queryProps: (params?: Record<string, string | number | boolean | undefined>) =>
    api.get<PaginatedResponseDTO<PropListDTO>>(`${PROJECT_BASE}/props/query`, {
      params: { ...params },
    }),

  /** Get prop detail */
  getProp: (propId: string) =>
    api.get<PropDetailDTO>(`${PROJECT_BASE}/props/${propId}`, {
      params: {},
    }),

  /** Create prop */
  createProp: (data: Record<string, unknown>) =>
    api.post<PropDetailDTO>(`${PROJECT_BASE}/props`, data, {
      params: {},
    }),

  /** Update prop */
  updateProp: (propId: string, data: Record<string, unknown>) =>
    api.put<PropDetailDTO>(`${PROJECT_BASE}/props/${propId}`, data, {
      params: {},
    }),

  /** Delete prop */
  deleteProp: (propId: string) =>
    api.delete<null>(`${PROJECT_BASE}/props/${propId}`, undefined, {
      params: {},
    }),

  /** Get prop versions */
  getPropVersions: (propId: string) =>
    api.get<VersionInfoDTO[]>(`${PROJECT_BASE}/props/${propId}/versions`, {
      params: {},
    }),

  /** Restore prop version */
  restorePropVersion: (propId: string, data: { versionNumber: number; reason?: string }) =>
    api.post<PropDetailDTO>(`${PROJECT_BASE}/props/${propId}/versions/restore`, data, {
      params: {},
    }),

  // ==================== Assets ====================
  /** Get assets for a script */
  getAssetsByScript: (scriptId: string) =>
    api.get<AssetListDTO[]>(`${PROJECT_BASE}/assets/script/${scriptId}`, {
      params: {},
    }),

  /** Get workspace-level assets */
  getAssets: (assetType?: string) =>
    api.get<AssetListDTO[]>(`${PROJECT_BASE}/assets`, {
      params: { ...(assetType ? { assetType } : {}) },
    }),

  /** Get asset detail */
  getAsset: (assetId: string) =>
    api.get<AssetDetailDTO>(`${PROJECT_BASE}/assets/${assetId}`, {
      params: {},
    }),

  /** Create asset */
  createAsset: (data: Record<string, unknown>) =>
    api.post<AssetDetailDTO>(`${PROJECT_BASE}/assets`, data, {
      params: {},
    }),

  /** Update asset */
  updateAsset: (assetId: string, data: Record<string, unknown>) =>
    api.put<AssetDetailDTO>(`${PROJECT_BASE}/assets/${assetId}`, data, {
      params: {},
    }),

  /** Delete asset */
  deleteAsset: (assetId: string) =>
    api.delete<null>(`${PROJECT_BASE}/assets/${assetId}`, undefined, {
      params: {},
    }),

  // ==================== Asset Trash ====================
  /** Get trash assets with pagination */
  getTrashAssets: (params?: { pageNum?: number; pageSize?: number; assetType?: string }) =>
    api.get<PaginatedResponseDTO<TrashAssetDTO>>(`${PROJECT_BASE}/assets/trash`, {
      params: { ...params },
    }),

  /** Restore asset from trash */
  restoreAsset: (assetId: string) =>
    api.post<AssetDetailDTO>(`${PROJECT_BASE}/assets/${assetId}/restore`, undefined, {
      params: {},
    }),

  /** Permanently delete asset */
  permanentDeleteAsset: (assetId: string) =>
    api.delete<null>(`${PROJECT_BASE}/assets/${assetId}/permanent`, undefined, {
      params: {},
    }),

  /** Empty trash (delete all trashed assets) */
  emptyTrash: () =>
    api.delete<{ count: number }>(`${PROJECT_BASE}/assets/trash`, undefined, {
      params: {},
    }),

  // ==================== Asset Upload ====================
  /** Initialize asset upload - get presigned URL */
  initAssetUpload: (data: AssetUploadInitRequestDTO) =>
    api.post<AssetUploadInitResponseDTO>(`${PROJECT_BASE}/assets/upload/init`, data, {
      params: {},
    }),

  /** Confirm asset upload after uploading to OSS */
  confirmAssetUpload: (assetId: string, data: AssetUploadConfirmRequestDTO) =>
    api.post<AssetDetailDTO>(`${PROJECT_BASE}/assets/${assetId}/upload/confirm`, data, {
      params: {},
    }),

  // ==================== Entity-Asset Relations ====================
  /** Get entity-asset relations for an entity */
  getEntityAssetRelations: (entityType: string, entityId: string) =>
    api.get<EntityAssetRelationDTO[]>(`${PROJECT_BASE}/entity-assets/${entityType}/${entityId}`, {
      params: {},
    }),

  /** Create entity-asset relation */
  createEntityAssetRelation: (
    data: { entityType: string; entityId: string; assetId: string; relationType: string }
  ) =>
    api.post<EntityAssetRelationDTO>(`${PROJECT_BASE}/entity-assets/relations`, data, {
      params: {},
    }),

  /** Update entity-asset relation */
  updateEntityAssetRelation: (relationId: string, data: UpdateEntityAssetRelationRequestDTO) =>
    api.put<EntityAssetRelationDTO>(`${PROJECT_BASE}/entity-assets/relations/${relationId}`, data, {
      params: {},
    }),

  /** Delete entity-asset relation */
  deleteEntityAssetRelation: (relationId: string) =>
    api.delete<null>(`${PROJECT_BASE}/entity-assets/relations/${relationId}`, undefined, {
      params: {},
    }),

  /** Get entity-asset relations by relation type */
  getEntityAssetsByType: (entityType: string, entityId: string, relationType: string) =>
    api.get<EntityAssetRelationDTO[]>(`${PROJECT_BASE}/entity-assets/${entityType}/${entityId}/by-type/${relationType}`, {
      params: {},
    }),

  // ==================== Unattached Assets ====================
  /** Get unattached assets for a script */
  getUnattachedAssets: (scriptId: string, params?: { assetType?: string; page?: number; size?: number }) =>
    api.get<PaginatedResponseDTO<AssetListDTO>>(`${PROJECT_BASE}/assets/script/${scriptId}/unattached`, {
      params: { ...params },
    }),

  /** Mount asset to entity */
  mountAsset: (data: { entityType: string; entityId: string; assetId: string; relationType?: string; description?: string; sequence?: number }) =>
    api.post<EntityAssetRelationDTO>(`${PROJECT_BASE}/entity-assets/mount`, data, {
      params: {},
    }),

  /** Unmount asset from entity */
  unmountAsset: (data: { entityType: string; entityId: string; assetId: string; relationType?: string }) =>
    api.post<null>(`${PROJECT_BASE}/entity-assets/unmount`, data, {
      params: {},
    }),

  /** Copy asset */
  copyAsset: (assetId: string, data: { targetScriptId?: string; targetEntityType?: string; targetEntityId?: string; relationType?: string }) =>
    api.post<AssetDetailDTO>(`${PROJECT_BASE}/assets/${assetId}/copy`, data, {
      params: {},
    }),

  // ==================== Entity Cover ====================
  /** Set character cover */
  setCharacterCover: (characterId: string, assetId: string) =>
    api.put<null>(`${PROJECT_BASE}/characters/${characterId}/cover`, null, {
      params: { assetId },
    }),

  /** Set scene cover */
  setSceneCover: (sceneId: string, assetId: string) =>
    api.put<null>(`${PROJECT_BASE}/scenes/${sceneId}/cover`, null, {
      params: { assetId },
    }),

  /** Set prop cover */
  setPropCover: (propId: string, assetId: string) =>
    api.put<null>(`${PROJECT_BASE}/props/${propId}/cover`, null, {
      params: { assetId },
    }),

  /** Set storyboard cover */
  setStoryboardCover: (storyboardId: string, assetId: string) =>
    api.put<null>(`${PROJECT_BASE}/storyboards/${storyboardId}/cover`, null, {
      params: { assetId },
    }),

  /** Set script cover */
  setScriptCover: (scriptId: string, assetId: string) =>
    api.put<null>(`${PROJECT_BASE}/scripts/${scriptId}/cover`, null, {
      params: { assetId },
    }),

  // ==================== Styles ====================
  /** Get all styles in workspace */
  getStyles: () =>
    api.get<StyleListDTO[]>(`${PROJECT_BASE}/styles`, {
      params: {},
    }),

  /** Query styles with pagination */
  queryStyles: (params?: Record<string, string | number | boolean | undefined>) =>
    api.get<PaginatedResponseDTO<StyleListDTO>>(`${PROJECT_BASE}/styles/query`, {
      params: { ...params },
    }),

  /** Get styles for a specific script */
  getScriptStyles: (scriptId: string) =>
    api.get<StyleListDTO[]>(`${PROJECT_BASE}/styles/script/${scriptId}`, {
      params: {},
    }),

  /** Get available styles for a script (workspace + script level) */
  getAvailableStyles: (scriptId: string, params?: { keyword?: string; limit?: number }) =>
    api.get<StyleListDTO[]>(`${PROJECT_BASE}/styles/available/${scriptId}`, {
      params: { ...params },
    }),

  /** Create a new style */
  createStyle: (data: Record<string, unknown>) =>
    api.post<StyleListDTO>(`${PROJECT_BASE}/styles`, data, {
      params: {},
    }),

  /** Set style cover image */
  setStyleCover: (styleId: string, assetId: string) =>
    api.put<void>(`${PROJECT_BASE}/styles/${styleId}/cover`, { assetId }, {
      params: {},
    }),
};

export default projectService;
