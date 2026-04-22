/**
 * Types for AI Generation Panel
 */

import type {
  ProviderType,
  ExecutionStatus,
  GeneratedAssetDTO,
  ModelProviderListDTO,
  InputSchemaDTO,
} from "@/lib/api/dto/ai.dto";
import type { EntityType, EntityContext } from "@/components/providers/ai-generation-provider";

// Re-export for convenience
export type { ProviderType, ExecutionStatus, GeneratedAssetDTO, EntityType, EntityContext };

// Panel state
export interface AIGenerationPanelState {
  // Provider
  providers: ModelProviderListDTO[];
  selectedProviderId: string | null;
  inputSchema: InputSchemaDTO | null;
  isLoadingProviders: boolean;
  isLoadingSchema: boolean;

  // Form
  prompt: string;
  formValues: Record<string, unknown>;
  formErrors: Record<string, string>;

  // Generation
  isGenerating: boolean;
  executionStatus: ExecutionStatus | null;
  progress: number;
  currentStep?: string;
  generatedAssets: GeneratedAssetDTO[];
  executionError: string | null;
}

// Generation result
export interface GenerationResult {
  success: boolean;
  assets: GeneratedAssetDTO[];
  error?: string;
}

// File upload result
export interface FileUploadResult {
  assetId: string;
  url: string;
  name: string;
  mimeType: string;
  fileSize: number;
}
