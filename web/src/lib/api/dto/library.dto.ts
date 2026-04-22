/**
 * Library (Public Resource) DTOs
 */

export type LibraryAssetType = "IMAGE" | "VIDEO" | "AUDIO" | "DOCUMENT" | "MODEL" | "OTHER";

export interface LibraryBaseDTO {
  id: string;
  name: string;
  description: string | null;
  coverUrl: string | null;
  publishedAt: string | null;
  publishNote: string | null;
  createdAt: string;
}

export interface LibraryCharacterDTO extends LibraryBaseDTO {
  age: number | null;
  gender: string | null;
  characterType: string | null;
}

export interface LibrarySceneDTO extends LibraryBaseDTO {
  sceneType: string | null;
}

export interface LibraryPropDTO extends LibraryBaseDTO {
  propType: string | null;
}

export interface LibraryAssetDTO extends LibraryBaseDTO {
  assetType: LibraryAssetType;
  fileUrl: string | null;
  thumbnailUrl: string | null;
  fileSize: number | null;
  mimeType: string | null;
}

export interface LibraryStyleDTO extends LibraryBaseDTO {}

// ==================== System Admin DTOs ====================

export type SystemLibraryScope = "WORKSPACE" | "SCRIPT" | "SYSTEM";

export interface SystemLibraryBaseDTO extends LibraryBaseDTO {
  scope: SystemLibraryScope;
  publishedBy: string | null;
  createdBy: string;
}

export interface SystemCharacterDTO extends SystemLibraryBaseDTO {
  age: number | null;
  gender: string | null;
  characterType: string | null;
}

export interface SystemSceneDTO extends SystemLibraryBaseDTO {
  sceneType: string | null;
}

export interface SystemPropDTO extends SystemLibraryBaseDTO {
  propType: string | null;
}

export interface SystemAssetDTO extends SystemLibraryBaseDTO {
  assetType: LibraryAssetType;
  fileUrl: string | null;
  thumbnailUrl: string | null;
  fileSize: number | null;
  mimeType: string | null;
}

export interface SystemStyleDTO extends SystemLibraryBaseDTO {}

export interface PublishRequestDTO {
  publishNote?: string;
}
