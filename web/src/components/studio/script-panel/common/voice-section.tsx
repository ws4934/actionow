"use client";

/**
 * Voice Section Component
 * Reusable component for managing voice/audio asset relations on entities.
 * Supports CHARACTER (voice), SCENE (ambient audio), PROP (sound effect).
 */

import { useState, useEffect, useCallback, useRef } from "react";
import { Button, Modal, ScrollShadow, Spinner, toast } from "@heroui/react";
import { Music, Play, Pause, X, Upload, Plus, Trash2, Volume2, Loader2 } from "lucide-react";
import { useTranslations, useLocale } from "next-intl";
import { projectService, getErrorFromException } from "@/lib/api";
import type { EntityAssetRelationDTO, AssetListDTO } from "@/lib/api/dto";
import { useWorkspace } from "@/components/providers/workspace-provider";

interface VoiceSectionProps {
  entityType: "CHARACTER" | "SCENE" | "PROP";
  entityId: string;
  voiceUrl?: string | null;
  voiceAssetId?: string | null;
  scriptId: string;
  onVoiceChanged?: () => void;
}

export function VoiceSection({
  entityType,
  entityId,
  voiceUrl,
  voiceAssetId,
  scriptId,
  onVoiceChanged,
}: VoiceSectionProps) {
  const { currentWorkspaceId } = useWorkspace();
  const t = useTranslations("workspace.studio.common");
  const locale = useLocale();
  const [voiceRelations, setVoiceRelations] = useState<EntityAssetRelationDTO[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isPickerOpen, setIsPickerOpen] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isRemoving, setIsRemoving] = useState(false);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  // Label based on entity type
  const sectionLabel = entityType === "CHARACTER"
    ? t("voice")
    : entityType === "SCENE"
      ? t("voiceAmbient")
      : t("voiceSfx");

  // Fetch voice relations
  const fetchVoiceRelations = useCallback(async () => {
    if (!currentWorkspaceId || !entityId) return;
    try {
      setIsLoading(true);
      const relations = await projectService.getEntityAssetsByType(entityType, entityId, "VOICE");
      setVoiceRelations(relations);
    } catch (err) {
      console.error("Failed to fetch voice relations:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsLoading(false);
    }
  }, [currentWorkspaceId, entityType, entityId]);

  useEffect(() => {
    fetchVoiceRelations();
  }, [fetchVoiceRelations]);

  // Audio playback
  const togglePlay = () => {
    if (!audioRef.current) return;
    if (isPlaying) {
      audioRef.current.pause();
    } else {
      audioRef.current.play();
    }
    setIsPlaying(!isPlaying);
  };

  const handleAudioEnded = () => {
    setIsPlaying(false);
  };

  // Remove voice relation
  const handleRemoveVoice = async () => {
    if (!currentWorkspaceId || voiceRelations.length === 0) return;
    try {
      setIsRemoving(true);
      // Delete the first (primary) voice relation
      await projectService.deleteEntityAssetRelation(voiceRelations[0].id);
      setVoiceRelations([]);
      if (audioRef.current) {
        audioRef.current.pause();
        setIsPlaying(false);
      }
      toast.success(t("voiceRemoved"));
      onVoiceChanged?.();
    } catch (err) {
      console.error("Failed to remove voice:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsRemoving(false);
    }
  };

  // Set voice from picker
  const handleSetVoice = async (assetId: string) => {
    if (!currentWorkspaceId) return;
    try {
      await projectService.createEntityAssetRelation({
        entityType,
        entityId,
        assetId,
        relationType: "VOICE",
      });
      setIsPickerOpen(false);
      toast.success(t("voiceSet"));
      await fetchVoiceRelations();
      onVoiceChanged?.();
    } catch (err) {
      console.error("Failed to set voice:", err);
      toast.danger(getErrorFromException(err, locale));
    }
  };

  const currentVoice = voiceRelations[0];
  const effectiveVoiceUrl = currentVoice?.asset?.fileUrl || voiceUrl;
  const voiceName = currentVoice?.asset?.name;

  return (
    <div className="rounded-xl bg-muted/5 p-3">
      <div className="mb-2 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Volume2 className="size-4 text-accent" />
          <span className="text-xs font-medium">{sectionLabel}</span>
        </div>
        <div className="flex items-center gap-1">
          {effectiveVoiceUrl && (
            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              className="size-6"
              onPress={handleRemoveVoice}
              isDisabled={isRemoving}
              isPending={isRemoving}
            >
              {({isPending}) => isPending ? <Spinner color="current" size="sm" /> : <Trash2 className="size-3 text-danger" />}
            </Button>
          )}
          <Button
            variant="ghost"
            size="sm"
            className="h-6 gap-1 px-2 text-xs"
            onPress={() => setIsPickerOpen(true)}
          >
            <Plus className="size-3" />
            {effectiveVoiceUrl ? t("setVoice") : t("setVoice")}
          </Button>
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-3">
          <Loader2 className="size-4 animate-spin text-muted" />
        </div>
      ) : effectiveVoiceUrl ? (
        <div className="flex items-center gap-2 rounded-lg bg-muted/10 p-2">
          <Button
            variant="ghost"
            size="sm"
            isIconOnly
            className="size-8 shrink-0 rounded-full bg-accent/10"
            onPress={togglePlay}
          >
            {isPlaying ? <Pause className="size-3.5 text-accent" /> : <Play className="size-3.5 text-accent" />}
          </Button>
          <div className="min-w-0 flex-1">
            <p className="truncate text-xs font-medium">{voiceName || sectionLabel}</p>
          </div>
          <audio
            ref={audioRef}
            src={effectiveVoiceUrl}
            onEnded={handleAudioEnded}
            onPause={() => setIsPlaying(false)}
            preload="none"
          />
        </div>
      ) : (
        <p className="py-2 text-center text-xs text-muted">{t("noVoice")}</p>
      )}

      {/* Voice Picker Modal */}
      <VoicePickerModal
        isOpen={isPickerOpen}
        onClose={() => setIsPickerOpen(false)}
        entityType={entityType}
        entityId={entityId}
        scriptId={scriptId}
        onSelect={handleSetVoice}
        sectionLabel={sectionLabel}
      />
    </div>
  );
}

// Voice Picker Modal
function VoicePickerModal({
  isOpen,
  onClose,
  entityType,
  entityId,
  scriptId,
  onSelect,
  sectionLabel,
}: {
  isOpen: boolean;
  onClose: () => void;
  entityType: string;
  entityId: string;
  scriptId: string;
  onSelect: (assetId: string) => void;
  sectionLabel: string;
}) {
  const { currentWorkspaceId } = useWorkspace();
  const t = useTranslations("workspace.studio.common");
  const locale = useLocale();
  const [relatedAudio, setRelatedAudio] = useState<EntityAssetRelationDTO[]>([]);
  const [unattachedAudio, setUnattachedAudio] = useState<AssetListDTO[]>([]);
  const [isLoadingRelated, setIsLoadingRelated] = useState(false);
  const [isLoadingUnattached, setIsLoadingUnattached] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [playingId, setPlayingId] = useState<string | null>(null);
  const audioPreviewRef = useRef<HTMLAudioElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Fetch audio assets when modal opens
  useEffect(() => {
    if (!isOpen || !currentWorkspaceId) return;

    const fetchRelated = async () => {
      try {
        setIsLoadingRelated(true);
        const relations = await projectService.getEntityAssetRelations(entityType, entityId);
        setRelatedAudio(relations.filter((r) => r.asset?.assetType === "AUDIO" && r.relationType !== "VOICE"));
      } catch (err) {
        console.error("Failed to fetch related audio:", err);
        toast.danger(getErrorFromException(err, locale));
      } finally {
        setIsLoadingRelated(false);
      }
    };

    const fetchUnattached = async () => {
      try {
        setIsLoadingUnattached(true);
        const result = await projectService.getUnattachedAssets(scriptId, { assetType: "AUDIO", size: 50 });
        setUnattachedAudio(result.records || []);
      } catch (err) {
        console.error("Failed to fetch unattached audio:", err);
        toast.danger(getErrorFromException(err, locale));
      } finally {
        setIsLoadingUnattached(false);
      }
    };

    fetchRelated();
    fetchUnattached();
  }, [isOpen, currentWorkspaceId, entityType, entityId, scriptId]);

  // Preview audio
  const togglePreview = (url: string | null, id: string) => {
    if (!url) return;
    if (playingId === id) {
      audioPreviewRef.current?.pause();
      setPlayingId(null);
      return;
    }
    if (audioPreviewRef.current) {
      audioPreviewRef.current.src = url;
      audioPreviewRef.current.play();
      setPlayingId(id);
    }
  };

  // Upload new audio
  const handleUpload = async (file: File) => {
    if (!currentWorkspaceId || !scriptId) return;
    try {
      setIsUploading(true);
      const initResponse = await projectService.initAssetUpload({
        name: file.name,
        fileName: file.name,
        mimeType: file.type,
        fileSize: file.size,
        scope: "SCRIPT",
        scriptId,
      });
      await fetch(initResponse.uploadUrl, {
        method: initResponse.method,
        headers: initResponse.headers,
        body: file,
      });
      await projectService.confirmAssetUpload(initResponse.assetId, {
        fileKey: initResponse.fileKey,
        actualFileSize: file.size,
      });
      // Directly set as voice
      onSelect(initResponse.assetId);
    } catch (err) {
      console.error("Failed to upload audio:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsUploading(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    }
  };

  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={(open) => !open && onClose()}>
      <Modal.Container size="sm">
        <Modal.Dialog>
          <Modal.Header>
            <Modal.Heading className="flex items-center gap-2">
              <Music className="size-4 text-accent" />
              {t("selectAudio")} — {sectionLabel}
            </Modal.Heading>
          </Modal.Header>
          <Modal.Body className="max-h-[60vh] p-0">
            {/* Upload button */}
            <div className="border-b border-border/50 px-4 py-3">
              <input
                ref={fileInputRef}
                type="file"
                accept="audio/*"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) handleUpload(file);
                }}
              />
              <Button
                variant="secondary"
                size="sm"
                className="w-full gap-2"
                onPress={() => fileInputRef.current?.click()}
                isDisabled={isUploading}
                isPending={isUploading}
              >
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Upload className="size-3.5" />}{t("uploadAudio")}</>)}
              </Button>
            </div>

            <ScrollShadow className="max-h-[45vh]" hideScrollBar>
              {/* Related Audio Section */}
              {(isLoadingRelated || relatedAudio.length > 0) && (
                <div className="px-4 py-2">
                  <p className="mb-2 text-xs font-medium text-muted">{t("relatedAudio")}</p>
                  {isLoadingRelated ? (
                    <div className="flex items-center justify-center py-4">
                      <Loader2 className="size-4 animate-spin text-muted" />
                    </div>
                  ) : (
                    <div className="space-y-1">
                      {relatedAudio.map((rel) => (
                        <AudioPickerItem
                          key={rel.id}
                          id={rel.asset.id}
                          name={rel.asset.name}
                          fileUrl={rel.asset.fileUrl}
                          isPlaying={playingId === rel.asset.id}
                          onTogglePlay={() => togglePreview(rel.asset.fileUrl, rel.asset.id)}
                          onSelect={() => onSelect(rel.asset.id)}
                        />
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* Unattached Audio Section */}
              {(isLoadingUnattached || unattachedAudio.length > 0) && (
                <div className="px-4 py-2">
                  <p className="mb-2 text-xs font-medium text-muted">{t("unattachedAudio")}</p>
                  {isLoadingUnattached ? (
                    <div className="flex items-center justify-center py-4">
                      <Loader2 className="size-4 animate-spin text-muted" />
                    </div>
                  ) : (
                    <div className="space-y-1">
                      {unattachedAudio.map((asset) => (
                        <AudioPickerItem
                          key={asset.id}
                          id={asset.id}
                          name={asset.name}
                          fileUrl={asset.fileUrl}
                          isPlaying={playingId === asset.id}
                          onTogglePlay={() => togglePreview(asset.fileUrl, asset.id)}
                          onSelect={() => onSelect(asset.id)}
                        />
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* Empty state */}
              {!isLoadingRelated && !isLoadingUnattached && relatedAudio.length === 0 && unattachedAudio.length === 0 && (
                <div className="flex flex-col items-center justify-center py-8 text-muted">
                  <Music className="mb-2 size-8 opacity-20" />
                  <p className="text-xs">{t("noVoice")}</p>
                </div>
              )}
            </ScrollShadow>

            {/* Hidden audio element for preview */}
            <audio
              ref={audioPreviewRef}
              onEnded={() => setPlayingId(null)}
              onPause={() => setPlayingId(null)}
              preload="none"
            />
          </Modal.Body>
          <Modal.Footer>
            <Button variant="ghost" onPress={onClose}>
              <X className="size-4" />
            </Button>
          </Modal.Footer>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}

// Audio item in the picker
function AudioPickerItem({
  id,
  name,
  fileUrl,
  isPlaying,
  onTogglePlay,
  onSelect,
}: {
  id: string;
  name: string;
  fileUrl: string | null;
  isPlaying: boolean;
  onTogglePlay: () => void;
  onSelect: () => void;
}) {
  return (
    <div
      className="group flex cursor-pointer items-center gap-2 rounded-lg p-2 transition-colors hover:bg-muted/10"
      onClick={onSelect}
    >
      <button
        className="flex size-7 shrink-0 items-center justify-center rounded-full bg-accent/10 transition-colors hover:bg-accent/20"
        onClick={(e) => {
          e.stopPropagation();
          onTogglePlay();
        }}
      >
        {isPlaying ? <Pause className="size-3 text-accent" /> : <Play className="size-3 text-accent" />}
      </button>
      <div className="min-w-0 flex-1">
        <p className="truncate text-xs font-medium">{name}</p>
      </div>
      <span className="text-xs text-accent opacity-0 transition-opacity group-hover:opacity-100">
        Select
      </span>
    </div>
  );
}

export default VoiceSection;
