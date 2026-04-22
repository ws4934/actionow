/**
 * Library (Public Resource) Service
 * Browse, copy, and system admin operations for public library resources
 */

import { api } from "../client";
import type {
  PaginatedResponseDTO,
  LibraryCharacterDTO,
  LibrarySceneDTO,
  LibraryPropDTO,
  LibraryAssetDTO,
  LibraryStyleDTO,
  SystemCharacterDTO,
  SystemSceneDTO,
  SystemPropDTO,
  SystemAssetDTO,
  SystemStyleDTO,
  PublishRequestDTO,
} from "../dto";

const LIBRARY_BASE = "/api/library";
const SYSTEM_LIBRARY_BASE = "/api/system/library";

type QueryParams = Record<string, string | number | boolean | undefined>;

export const libraryService = {
  // ==================== Characters (Browse & Copy) ====================
  queryCharacters: (params?: QueryParams) =>
    api.get<PaginatedResponseDTO<LibraryCharacterDTO>>(`${LIBRARY_BASE}/characters`, { params }),

  getCharacter: (id: string) =>
    api.get<LibraryCharacterDTO>(`${LIBRARY_BASE}/characters/${id}`),

  copyCharacter: (id: string) =>
    api.post<unknown>(`${LIBRARY_BASE}/characters/${id}/copy`, undefined, {
      params: {},
    }),

  // ==================== Scenes (Browse & Copy) ====================
  queryScenes: (params?: QueryParams) =>
    api.get<PaginatedResponseDTO<LibrarySceneDTO>>(`${LIBRARY_BASE}/scenes`, { params }),

  getScene: (id: string) =>
    api.get<LibrarySceneDTO>(`${LIBRARY_BASE}/scenes/${id}`),

  copyScene: (id: string) =>
    api.post<unknown>(`${LIBRARY_BASE}/scenes/${id}/copy`, undefined, {
      params: {},
    }),

  // ==================== Props (Browse & Copy) ====================
  queryProps: (params?: QueryParams) =>
    api.get<PaginatedResponseDTO<LibraryPropDTO>>(`${LIBRARY_BASE}/props`, { params }),

  getProp: (id: string) =>
    api.get<LibraryPropDTO>(`${LIBRARY_BASE}/props/${id}`),

  copyProp: (id: string) =>
    api.post<unknown>(`${LIBRARY_BASE}/props/${id}/copy`, undefined, {
      params: {},
    }),

  // ==================== Assets (Browse & Copy) ====================
  queryAssets: (params?: QueryParams) =>
    api.get<PaginatedResponseDTO<LibraryAssetDTO>>(`${LIBRARY_BASE}/assets`, { params }),

  getAsset: (id: string) =>
    api.get<LibraryAssetDTO>(`${LIBRARY_BASE}/assets/${id}`),

  copyAsset: (id: string) =>
    api.post<unknown>(`${LIBRARY_BASE}/assets/${id}/copy`, undefined, {
      params: {},
    }),

  // ==================== Styles (Browse & Copy) ====================
  queryStyles: (params?: QueryParams) =>
    api.get<PaginatedResponseDTO<LibraryStyleDTO>>(`${LIBRARY_BASE}/styles`, { params }),

  getStyle: (id: string) =>
    api.get<LibraryStyleDTO>(`${LIBRARY_BASE}/styles/${id}`),

  copyStyle: (id: string) =>
    api.post<unknown>(`${LIBRARY_BASE}/styles/${id}/copy`, undefined, {
      params: {},
    }),

  // ==================== System Admin: Characters ====================
  systemQueryCharacters: (params?: QueryParams) =>
    api.get<PaginatedResponseDTO<SystemCharacterDTO>>(`${SYSTEM_LIBRARY_BASE}/characters`, { params }),

  publishCharacter: (id: string, data?: PublishRequestDTO) =>
    api.patch<null>(`${SYSTEM_LIBRARY_BASE}/characters/${id}/publish`, data),

  unpublishCharacter: (id: string) =>
    api.patch<null>(`${SYSTEM_LIBRARY_BASE}/characters/${id}/unpublish`),

  // ==================== System Admin: Scenes ====================
  systemQueryScenes: (params?: QueryParams) =>
    api.get<PaginatedResponseDTO<SystemSceneDTO>>(`${SYSTEM_LIBRARY_BASE}/scenes`, { params }),

  publishScene: (id: string, data?: PublishRequestDTO) =>
    api.patch<null>(`${SYSTEM_LIBRARY_BASE}/scenes/${id}/publish`, data),

  unpublishScene: (id: string) =>
    api.patch<null>(`${SYSTEM_LIBRARY_BASE}/scenes/${id}/unpublish`),

  // ==================== System Admin: Props ====================
  systemQueryProps: (params?: QueryParams) =>
    api.get<PaginatedResponseDTO<SystemPropDTO>>(`${SYSTEM_LIBRARY_BASE}/props`, { params }),

  publishProp: (id: string, data?: PublishRequestDTO) =>
    api.patch<null>(`${SYSTEM_LIBRARY_BASE}/props/${id}/publish`, data),

  unpublishProp: (id: string) =>
    api.patch<null>(`${SYSTEM_LIBRARY_BASE}/props/${id}/unpublish`),

  // ==================== System Admin: Assets ====================
  systemQueryAssets: (params?: QueryParams) =>
    api.get<PaginatedResponseDTO<SystemAssetDTO>>(`${SYSTEM_LIBRARY_BASE}/assets`, { params }),

  publishAsset: (id: string, data?: PublishRequestDTO) =>
    api.patch<null>(`${SYSTEM_LIBRARY_BASE}/assets/${id}/publish`, data),

  unpublishAsset: (id: string) =>
    api.patch<null>(`${SYSTEM_LIBRARY_BASE}/assets/${id}/unpublish`),

  // ==================== System Admin: Styles ====================
  systemQueryStyles: (params?: QueryParams) =>
    api.get<PaginatedResponseDTO<SystemStyleDTO>>(`${SYSTEM_LIBRARY_BASE}/styles`, { params }),

  publishStyle: (id: string, data?: PublishRequestDTO) =>
    api.patch<null>(`${SYSTEM_LIBRARY_BASE}/styles/${id}/publish`, data),

  unpublishStyle: (id: string) =>
    api.patch<null>(`${SYSTEM_LIBRARY_BASE}/styles/${id}/unpublish`),
};
