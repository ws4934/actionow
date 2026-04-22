/**
 * Project (Script) DTOs
 */

export type ScriptStatus = "DRAFT" | "IN_PROGRESS" | "COMPLETED" | "ARCHIVED";
export type EntityStatus = "DRAFT" | "IN_PROGRESS" | "COMPLETED" | "FAILED" | "GENERATING";
export type SaveMode = "NEW_VERSION" | "OVERWRITE" | "NEW_ENTITY";
export type EntityScope = "SYSTEM" | "WORKSPACE" | "SCRIPT";
export type AssetType = "IMAGE" | "VIDEO" | "AUDIO" | "DOCUMENT";
export type AssetSource = "UPLOAD" | "AI_GENERATED" | "EXTERNAL";
export type GenerationStatus = "DRAFT" | "GENERATING" | "COMPLETED" | "FAILED";

export interface ScriptListDTO {
  id: string;
  workspaceId: string;
  title: string;
  status: ScriptStatus;
  synopsis: string | null;
  coverAssetId: string | null;
  coverUrl: string | null;
  episodeCount: number;
  versionNumber: number;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  createdByUsername: string;
  createdByNickname: string | null;
}

export interface ScriptDetailDTO extends ScriptListDTO {
  content: string | null;
  docAssetId: string | null;
  currentVersionId: string;
  extraInfo: Record<string, unknown>;
}

export interface CreateScriptRequestDTO {
  title: string;
  synopsis?: string;
  coverAssetId?: string;
}

export interface UpdateScriptRequestDTO {
  title?: string;
  synopsis?: string;
  content?: string;
  coverAssetId?: string;
  saveMode?: SaveMode;
}

export interface ScriptQueryParams {
  status?: ScriptStatus;
  keyword?: string;
  createdBy?: string;
  pageNum?: number;
  pageSize?: number;
  orderBy?: "created_at" | "updated_at" | "title";
  orderDir?: "asc" | "desc";
}

// Episode DTOs
export interface EpisodeListDTO {
  id: string;
  scriptId: string;
  title: string;
  sequence: number;
  status: EntityStatus;
  synopsis: string | null;
  coverAssetId: string | null;
  coverUrl: string | null;
  storyboardCount: number;
  versionNumber: number;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  createdByUsername: string;
  createdByNickname: string | null;
}

export interface EpisodeDetailDTO extends EpisodeListDTO {
  content: string | null;
  docAssetId: string | null;
  currentVersionId: string;
  extraInfo: Record<string, unknown>;
}

// Storyboard DTOs
export interface StoryboardListDTO {
  id: string;
  episodeId: string;
  scriptId: string;
  title: string | null;
  sequence: number;
  status: EntityStatus;
  synopsis: string | null;
  duration: number | null;
  coverAssetId: string | null;
  coverUrl: string | null;
  versionNumber: number;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
}

// Storyboard visual description
export interface StoryboardVisualDesc {
  lighting: string | null;
  shotType: string | null;
  transition: {
    type: string | null;
  } | null;
  cameraAngle: string | null;
  colorGrading: string | null;
  visualEffects: string | null;
  cameraMovement: string | null;
}

// Storyboard audio description
export interface StoryboardAudioDesc {
  bgm: {
    mood: string | null;
    genre: string | null;
    tempo: string | null;
  } | null;
  narration: string | null;
  soundEffects: string[];
}

// Storyboard related character
export interface StoryboardCharacter {
  relationId: string;
  characterId: string;
  characterName: string;
  characterType: string | null;
  coverUrl: string | null;
  sequence: number;
  position: string | null;
  positionDetail: string | null;
  action: string | null;
  expression: string | null;
  outfitOverride: string | null;
}

// Storyboard related prop
export interface StoryboardProp {
  relationId: string;
  propId: string;
  propName: string;
  propType: string | null;
  coverUrl: string | null;
  sequence: number;
  position: string | null;
  positionDetail: string | null;
  state: string | null;
}

// Storyboard dialogue
export interface StoryboardDialogue {
  id: string;
  characterId: string | null;
  characterName: string | null;
  dialogueType: string;
  content: string;
  sequence: number;
  emotion: string | null;
  voiceStyle: string | null;
}

// Storyboard related scene
export interface StoryboardScene {
  relationId: string;
  sceneId: string;
  sceneName: string;
  coverUrl: string | null;
  description: string | null;
}

export interface StoryboardDetailDTO extends StoryboardListDTO {
  visualDesc: StoryboardVisualDesc | null;
  audioDesc: StoryboardAudioDesc | null;
  currentVersionId: string;
  extraInfo: Record<string, unknown>;
  createdByUsername?: string;
  createdByNickname?: string | null;
  scene: StoryboardScene | null;
  characters: StoryboardCharacter[];
  props: StoryboardProp[];
  dialogues: StoryboardDialogue[];
  styleId: string | null;
  styleName: string | null;
}

// Character DTOs
export interface CharacterListDTO {
  id: string;
  workspaceId: string;
  scope: EntityScope;
  scriptId: string | null;
  name: string;
  description: string | null;
  fixedDesc: string | null;
  age: number | null;
  gender: string | null;
  characterType: string | null;
  coverAssetId: string | null;
  coverUrl: string | null;
  versionNumber: number;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  createdByUsername: string;
  createdByNickname: string | null;
  voiceAssetId: string | null;
  voiceUrl: string | null;
}

export interface CharacterDetailDTO extends CharacterListDTO {
  appearanceData: Record<string, unknown> | null;
  referenceAssetId: string | null;
  voiceSeedId: string | null;
  currentVersionId: string;
  extraInfo: Record<string, unknown>;
}

// Scene DTOs
export interface SceneListDTO {
  id: string;
  workspaceId: string;
  scope: EntityScope;
  scriptId: string | null;
  name: string;
  description: string | null;
  fixedDesc: string | null;
  coverAssetId: string | null;
  coverUrl: string | null;
  versionNumber: number;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  createdByUsername: string;
  createdByNickname: string | null;
  voiceAssetId: string | null;
  voiceUrl: string | null;
}

export interface SceneDetailDTO extends SceneListDTO {
  environment: Record<string, unknown> | null;
  atmosphere: Record<string, unknown> | null;
  referenceAssetId: string | null;
  currentVersionId: string;
  extraInfo: Record<string, unknown>;
}

// Prop DTOs
export interface PropListDTO {
  id: string;
  workspaceId: string;
  scope: EntityScope;
  scriptId: string | null;
  name: string;
  description: string | null;
  fixedDesc: string | null;
  propType: string | null;
  coverAssetId: string | null;
  coverUrl: string | null;
  versionNumber: number;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  createdByUsername: string;
  createdByNickname: string | null;
  voiceAssetId: string | null;
  voiceUrl: string | null;
}

export interface PropDetailDTO extends PropListDTO {
  appearanceData: Record<string, unknown> | null;
  referenceAssetId: string | null;
  currentVersionId: string;
  extraInfo: Record<string, unknown>;
}

// Asset DTOs
export interface AssetListDTO {
  id: string;
  workspaceId: string;
  scope: EntityScope;
  scriptId: string | null;
  name: string;
  description: string | null;
  assetType: AssetType;
  source: AssetSource;
  generationStatus: GenerationStatus;
  fileUrl: string | null;
  thumbnailUrl: string | null;
  fileSize: number | null;
  mimeType: string | null;
  versionNumber: number;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  createdByUsername?: string;
  createdByNickname?: string | null;
}

// Trash Asset DTO (includes deletedAt field)
export interface TrashAssetDTO extends AssetListDTO {
  fileKey: string | null;
  deletedAt: string;
  trashPath: string | null;
}

export interface AssetDetailDTO extends AssetListDTO {
  currentVersionId: string;
  extraInfo: Record<string, unknown>;
}

// Version DTOs
export type EntityType = "SCRIPT" | "EPISODE" | "STORYBOARD" | "CHARACTER" | "SCENE" | "PROP" | "ASSET" | "STYLE";
export type ChangeType = "ADDED" | "REMOVED" | "MODIFIED";

export interface VersionInfoDTO {
  id: string;
  entityId: string;
  entityType: EntityType;
  versionNumber: number;
  changeSummary: string | null;
  createdAt: string;
  createdBy: string;
  isCurrent: boolean;
}

export interface ScriptVersionDetailDTO {
  id: string;
  scriptId: string;
  versionNumber: number;
  changeSummary: string | null;
  createdBy: string;
  createdAt: string;
  isCurrent: boolean;
  title: string;
  status: ScriptStatus;
  synopsis: string | null;
  content: string | null;
  coverAssetId: string | null;
  docAssetId: string | null;
  extraInfo: Record<string, unknown>;
}

export interface EpisodeVersionDetailDTO {
  id: string;
  episodeId: string;
  versionNumber: number;
  changeSummary: string | null;
  createdBy: string;
  createdAt: string;
  isCurrent: boolean;
  title: string;
  status: EntityStatus;
  synopsis: string | null;
  content: string | null;
  coverAssetId: string | null;
  docAssetId: string | null;
  extraInfo: Record<string, unknown>;
}

export interface FieldDiffDTO {
  fieldName: string;
  fieldLabel: string;
  oldValue: string | null;
  newValue: string | null;
  changeType: ChangeType;
}

export interface VersionDiffDTO {
  entityId: string;
  versionNumber1: number;
  versionNumber2: number;
  fieldDiffs: FieldDiffDTO[];
}

export interface RestoreVersionRequestDTO {
  versionNumber: number;
  reason?: string;
}

// Upload DTOs
export type UploadStatus = "PENDING" | "UPLOADING" | "COMPLETED" | "FAILED";

export interface AssetUploadInitRequestDTO {
  name: string;
  fileName: string;
  mimeType: string;
  fileSize: number;
  scope?: EntityScope;
  scriptId?: string;
  description?: string;
}

export interface AssetUploadInitResponseDTO {
  assetId: string;
  name: string;
  assetType: AssetType;
  uploadStatus: UploadStatus;
  uploadUrl: string;
  method: string;
  headers: Record<string, string>;
  fileKey: string;
  expiresAt: string;
}

export interface AssetUploadConfirmRequestDTO {
  fileKey: string;
  actualFileSize?: number;
  metaInfo?: Record<string, unknown>;
}

// Entity-Asset Relation DTOs
export type RelationType = "REFERENCE" | "OFFICIAL" | "DRAFT" | "VOICE";

export interface EntityAssetRelationDTO {
  id: string;
  entityType: EntityType;
  entityId: string;
  relationType: RelationType;
  description: string | null;
  sequence: number;
  extraInfo: Record<string, unknown>;
  asset: AssetListDTO;
  createdAt: string;
  createdBy: string;
}

export interface UpdateEntityAssetRelationRequestDTO {
  relationType?: RelationType;
  description?: string;
  sequence?: number;
  extraInfo?: Record<string, unknown>;
}

// Style DTOs
export interface StyleListDTO {
  id: string;
  workspaceId: string;
  scope: EntityScope;
  scriptId: string | null;
  name: string;
  description: string | null;
  coverAssetId: string | null;
  coverUrl: string | null;
  versionNumber: number;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  createdByUsername: string | null;
  createdByNickname: string | null;
}
