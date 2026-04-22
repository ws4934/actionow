"use client";

import { useTranslations } from "next-intl";
import { Button, Modal, Separator, Skeleton, Spinner } from "@heroui/react";
import { Copy, Pencil, FileBox, FileVideo, FileAudio, FileText, ImageIcon } from "lucide-react";
import type { EntityType } from "./material-sidebar";
import type { MaterialItem } from "./material-card";

const ASSET_TYPE_ICONS: Record<string, typeof FileBox> = {
  IMAGE: ImageIcon,
  VIDEO: FileVideo,
  AUDIO: FileAudio,
  DOCUMENT: FileText,
};

interface LibraryPreviewModalProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  item: MaterialItem | null;
  entityType: EntityType;
  onCopy?: (item: MaterialItem) => void;
  isCopying?: boolean;
  // Extended detail fields (from the raw library DTO)
  detail?: Record<string, unknown> | null;
  // Admin mode
  isSystemAdmin?: boolean;
  onPublish?: (item: MaterialItem) => void;
  onUnpublish?: (item: MaterialItem) => void;
  onEdit?: (item: MaterialItem) => void;
}

function formatFileSize(bytes: number | null | undefined): string {
  if (!bytes) return "-";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function LibraryPreviewModal({
  isOpen,
  onOpenChange,
  item,
  entityType,
  onCopy,
  isCopying,
  detail,
  isSystemAdmin,
  onPublish,
  onUnpublish,
  onEdit,
}: LibraryPreviewModalProps) {
  const t = useTranslations("workspace.materialRoom");

  if (!item) return null;

  const formatDate = (dateStr: string | null) =>
    dateStr ? new Date(dateStr).toLocaleString() : "-";

  const renderTypeFields = () => {
    if (!detail) return null;

    switch (entityType) {
      case "characters":
        return (
          <>
            {detail.characterType && (
              <InfoRow label={t("preview.characterType")} value={String(detail.characterType)} />
            )}
            {detail.gender && (
              <InfoRow label={t("preview.gender")} value={String(detail.gender)} />
            )}
            {detail.age != null && (
              <InfoRow label={t("preview.age")} value={String(detail.age)} />
            )}
          </>
        );
      case "scenes":
        return detail.sceneType ? (
          <InfoRow label={t("preview.sceneType")} value={String(detail.sceneType)} />
        ) : null;
      case "props":
        return detail.propType ? (
          <InfoRow label={t("preview.propType")} value={String(detail.propType)} />
        ) : null;
      case "assets":
        return (
          <>
            {detail.assetType && (
              <InfoRow label={t("preview.assetType")} value={t(`assetType.${detail.assetType}`)} />
            )}
            {detail.fileSize != null && (
              <InfoRow label={t("preview.fileSize")} value={formatFileSize(detail.fileSize as number)} />
            )}
            {detail.mimeType && (
              <InfoRow label={t("preview.mimeType")} value={String(detail.mimeType)} />
            )}
          </>
        );
      default:
        return null;
    }
  };

  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
      <Modal.Container>
        <Modal.Dialog className="sm:max-w-lg">
          <Modal.CloseTrigger />
          <Modal.Header>
            <Modal.Heading>{t("preview.title")}</Modal.Heading>
          </Modal.Header>
          <Modal.Body className="space-y-4">
            {/* Cover / Media Preview */}
            {entityType === "assets" && item.assetType === "VIDEO" && item.fileUrl ? (
              <div className="overflow-hidden rounded-lg bg-black">
                <video
                  src={item.fileUrl}
                  controls
                  className="aspect-video w-full"
                  preload="metadata"
                  poster={item.coverUrl !== item.fileUrl ? (item.coverUrl ?? undefined) : undefined}
                />
              </div>
            ) : entityType === "assets" && item.assetType === "AUDIO" && item.fileUrl ? (
              <div className="flex flex-col items-center gap-3 rounded-lg bg-muted/5 p-6">
                <FileAudio className="size-12 text-muted/30" />
                <audio src={item.fileUrl} controls className="w-full" preload="metadata" />
              </div>
            ) : item.coverUrl ? (
              <div className="overflow-hidden rounded-lg">
                <img
                  src={item.coverUrl}
                  alt={item.name}
                  className="aspect-video w-full object-cover"
                />
              </div>
            ) : entityType === "assets" && item.assetType ? (
              <div className="flex aspect-video items-center justify-center rounded-lg bg-muted/5">
                {(() => { const Icon = ASSET_TYPE_ICONS[item.assetType!] ?? FileBox; return <Icon className="size-12 text-muted/20" />; })()}
              </div>
            ) : null}

            {/* Name & Description */}
            <div>
              <h3 className="text-lg font-semibold text-foreground">{item.name}</h3>
              {item.description && (
                <p className="mt-1.5 text-sm text-muted">{item.description}</p>
              )}
            </div>

            <Separator />

            {/* Type-specific fields */}
            <div className="space-y-2">
              {detail === undefined ? (
                // Loading state while detail is being fetched
                <>
                  <Skeleton className="h-5 w-full rounded" />
                  <Skeleton className="h-5 w-3/4 rounded" />
                </>
              ) : (
                renderTypeFields()
              )}
              {/* Admin: scope & publish status */}
              {isSystemAdmin && item.scope && (
                <InfoRow
                  label={t("scope.system")}
                  value={t(`scope.${item.scope.toLowerCase()}`)}
                />
              )}
              {isSystemAdmin && (
                <InfoRow
                  label={t("admin.published")}
                  value={
                    <span className="inline-flex items-center gap-1.5">
                      <span className={`inline-block size-2 rounded-full ${item.scope === "SYSTEM" ? "bg-green-400" : "bg-gray-400"}`} />
                      {item.scope === "SYSTEM" ? t("admin.published") : t("admin.unpublished")}
                    </span>
                  }
                />
              )}
              <InfoRow label={t("preview.publishedAt")} value={formatDate(item.publishedAt)} />
              <InfoRow
                label={t("preview.publishNote")}
                value={item.publishNote || t("preview.noPublishNote")}
              />
            </div>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="secondary" slot="close">
              {t("detail.cancel")}
            </Button>
            {isSystemAdmin ? (
              <>
                {onEdit && (
                  <Button variant="secondary" onPress={() => { onEdit(item); onOpenChange(false); }}>
                    <Pencil className="size-4" />
                    {t("card.edit")}
                  </Button>
                )}
                {item.scope === "SYSTEM" ? (
                  onUnpublish && (
                    <Button variant="secondary" onPress={() => onUnpublish(item)}>
                      {t("admin.unpublish")}
                    </Button>
                  )
                ) : (
                  onPublish && (
                    <Button onPress={() => onPublish(item)}>
                      {t("admin.publish")}
                    </Button>
                  )
                )}
              </>
            ) : onEdit ? (
              <Button onPress={() => { onEdit(item); onOpenChange(false); }}>
                <Pencil className="size-4" />
                {t("card.edit")}
              </Button>
            ) : onCopy ? (
              <Button onPress={() => onCopy(item)} isPending={isCopying}>
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Copy className="size-4" />}{t("card.copy")}</>)}
              </Button>
            ) : null}
          </Modal.Footer>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}

function InfoRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between text-sm">
      <span className="text-muted">{label}</span>
      <span className="font-medium text-foreground">{value}</span>
    </div>
  );
}
