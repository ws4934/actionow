"use client";

/**
 * AssetPreviewModal
 * Unified two-column asset preview modal.
 * Left: media preview (image / video / audio).
 * Right: scrollable info panel — name, chips, prompt, description, actions, metadata.
 * No close button — dismiss via backdrop click or ESC.
 */

import { useState, useCallback } from "react";
import { Modal, Button, Chip, Disclosure, ScrollShadow, toast } from "@heroui/react";
import { useLocale } from "next-intl";
import { getErrorFromException } from "@/lib/api";
import {
  Download,
  Music,
  FileText,
  Sparkles,
  X,
  Copy,
  Check,
  ChevronDown,
  Hash,
  Link2,
} from "lucide-react";

// ── Types ────────────────────────────────────────────────────────────────────

export interface AssetPreviewInfo {
  id: string;
  name?: string | null;
  description?: string | null;
  assetType?: string | null;
  fileUrl?: string | null;
  thumbnailUrl?: string | null;
  mimeType?: string | null;
  fileSize?: number | null;
  source?: string | null;
  generationStatus?: string | null;
  versionNumber?: number | null;
  scope?: string | null;
  createdAt?: string | null;
  createdByUsername?: string | null;
  createdByNickname?: string | null;
  extraInfo?: Record<string, unknown> | null;
}

export interface AssetPreviewRelation {
  relationType?: string | null;
  sequence?: number | null;
  extraInfo?: Record<string, unknown> | null;
}

export interface AssetPreviewModalProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  asset: AssetPreviewInfo | null;
  relation?: AssetPreviewRelation | null;
  /** Extra action buttons rendered in the action bar */
  actions?: React.ReactNode;
}

// ── Helpers ──────────────────────────────────────────────────────────────────

export function formatFileSize(bytes: number | null | undefined): string {
  if (!bytes) return "-";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return "-";
  return new Date(dateStr).toLocaleString("zh-CN");
}

function getSourceLabel(source: string | null | undefined): string {
  switch (source) {
    case "AI_GENERATED": return "AI生成";
    case "UPLOAD": return "上传";
    case "EXTERNAL": return "外部";
    default: return source || "-";
  }
}

function getStatusLabel(status: string | null | undefined): string {
  switch (status) {
    case "COMPLETED": return "已完成";
    case "GENERATING": return "生成中";
    case "FAILED": return "失败";
    case "DRAFT": return "草稿";
    default: return status || "-";
  }
}

function getStatusColor(status: string | null | undefined): "success" | "warning" | "danger" | "default" {
  switch (status) {
    case "COMPLETED": return "success";
    case "GENERATING": return "warning";
    case "FAILED": return "danger";
    default: return "default";
  }
}

function getRelationLabel(type: string | null | undefined): string {
  switch (type) {
    case "OFFICIAL": return "正式";
    case "REFERENCE": return "参考";
    case "VOICE": return "语音";
    default: return "草稿";
  }
}

function extractPromptInfo(
  extraInfo?: Record<string, unknown> | null,
  relationExtra?: Record<string, unknown> | null,
) {
  const ei = extraInfo || {};
  const re = relationExtra || {};
  return {
    prompt: (ei.prompt || ei.positive_prompt || re.prompt) as string | undefined,
    negativePrompt: (ei.negative_prompt || ei.negativePrompt || re.negative_prompt) as string | undefined,
    model: (ei.model || ei.modelName || ei.checkpoint) as string | undefined,
    seed: ei.seed as string | number | undefined,
    steps: ei.steps as number | undefined,
    cfgScale: (ei.cfg_scale || ei.cfgScale || ei.guidance_scale) as number | undefined,
    sampler: (ei.sampler || ei.sampler_name) as string | undefined,
    width: ei.width as number | undefined,
    height: ei.height as number | undefined,
  };
}

// ── Component ────────────────────────────────────────────────────────────────

export function AssetPreviewModal({
  isOpen,
  onOpenChange,
  asset,
  relation,
  actions,
}: AssetPreviewModalProps) {
  const locale = useLocale();
  const [copiedField, setCopiedField] = useState<string | null>(null);

  const handleCopy = useCallback(async (text: string, field: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedField(field);
      setTimeout(() => setCopiedField(null), 2000);
    } catch (err) {
      console.error("Failed to copy:", err);
      toast.danger(getErrorFromException(err, locale));
    }
  }, []);

  if (!asset) return null;

  const assetType = asset.assetType;
  const fileUrl = asset.fileUrl ?? "";
  const isImage = assetType === "IMAGE" || asset.mimeType?.startsWith("image/");
  const isVideo = assetType === "VIDEO" || asset.mimeType?.startsWith("video/");
  const isAudio = assetType === "AUDIO" || asset.mimeType?.startsWith("audio/");
  const displayName = asset.name || "未命名素材";

  const promptInfo = extractPromptInfo(asset.extraInfo, relation?.extraInfo);
  const hasPromptInfo = promptInfo.prompt || promptInfo.negativePrompt || promptInfo.model;
  const hasExtendedInfo = asset.source || asset.generationStatus || asset.createdAt || asset.createdByNickname || asset.createdByUsername;

  // Chips to show
  const chips: { label: string; color?: "success" | "warning" | "danger" | "default" }[] = [];
  if (assetType) chips.push({ label: assetType });
  if (asset.source) chips.push({ label: getSourceLabel(asset.source) });
  if (asset.generationStatus) chips.push({ label: getStatusLabel(asset.generationStatus), color: getStatusColor(asset.generationStatus) });
  if (asset.versionNumber != null) chips.push({ label: `v${asset.versionNumber || 1}` });
  if (promptInfo.width && promptInfo.height) chips.push({ label: `${promptInfo.width} × ${promptInfo.height}` });
  if (asset.fileSize) chips.push({ label: formatFileSize(asset.fileSize) });
  if (relation?.relationType) chips.push({ label: getRelationLabel(relation.relationType) });

  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
      <Modal.Container>
        <Modal.Dialog className="h-[55vh] overflow-hidden sm:max-w-4xl">
          {/* Two-column layout */}
          <div className="flex h-full">
            {/* ── Left: Media Preview ── */}
            <div className="flex min-h-[240px] flex-1 items-center justify-center bg-black/5 p-4 dark:bg-white/5">
              {isImage && fileUrl ? (
                <img
                  src={fileUrl}
                  alt={displayName}
                  className="max-h-full max-w-full rounded-lg object-contain"
                />
              ) : isVideo && fileUrl ? (
                <video
                  src={fileUrl}
                  className="max-h-full max-w-full rounded-lg"
                  controls
                  autoPlay
                  muted
                />
              ) : isAudio && fileUrl ? (
                <div className="flex w-full max-w-xs flex-col items-center gap-4">
                  <div className="flex size-16 items-center justify-center rounded-full bg-accent/10">
                    <Music className="size-8 text-accent" />
                  </div>
                  <p className="max-w-full truncate text-xs font-medium">{displayName}</p>
                  <audio src={fileUrl} controls autoPlay className="w-full" />
                </div>
              ) : (
                <div className="flex size-20 items-center justify-center rounded-xl bg-muted/10">
                  <FileText className="size-8 text-muted/30" />
                </div>
              )}
            </div>

            {/* ── Right: Info Panel ── */}
            <ScrollShadow className="w-[280px] shrink-0 border-l border-muted/10">
              <div className="space-y-3 p-3.5">
                {/* Name + Chips */}
                <div>
                  <h3 className="text-sm font-semibold leading-snug">{displayName}</h3>
                  {chips.length > 0 && (
                    <div className="mt-1.5 flex flex-wrap gap-1">
                      {chips.map((c, i) => (
                        <Chip key={i} size="sm" variant="secondary" color={c.color}>
                          {c.label === getSourceLabel("AI_GENERATED") && <Sparkles className="mr-0.5 size-3" />}
                          {c.label}
                        </Chip>
                      ))}
                    </div>
                  )}
                </div>

                {/* Prompt Section */}
                {hasPromptInfo && (
                  <div className="space-y-2 rounded-lg bg-muted/5 p-2.5">
                    {(promptInfo.model || promptInfo.sampler || promptInfo.steps || promptInfo.cfgScale || promptInfo.seed) && (
                      <div className="flex flex-wrap gap-1 text-[11px] text-muted">
                        {promptInfo.model && <span className="rounded bg-muted/10 px-1.5 py-0.5">{promptInfo.model}</span>}
                        {promptInfo.sampler && <span className="rounded bg-muted/10 px-1.5 py-0.5">{promptInfo.sampler}</span>}
                        {promptInfo.steps && <span className="rounded bg-muted/10 px-1.5 py-0.5">步数 {promptInfo.steps}</span>}
                        {promptInfo.cfgScale && <span className="rounded bg-muted/10 px-1.5 py-0.5">CFG {promptInfo.cfgScale}</span>}
                        {promptInfo.seed && <span className="rounded bg-muted/10 px-1.5 py-0.5">种子 {promptInfo.seed}</span>}
                      </div>
                    )}
                    {promptInfo.prompt && (
                      <div className="space-y-0.5">
                        <div className="flex items-center justify-between">
                          <span className="flex items-center gap-1 text-[11px] font-medium">
                            <Sparkles className="size-3 text-accent" />
                            提示词
                          </span>
                          <Button isIconOnly size="sm" variant="ghost" className="size-5" onPress={() => handleCopy(promptInfo.prompt!, "prompt")}>
                            {copiedField === "prompt" ? <Check className="size-3 text-success" /> : <Copy className="size-3" />}
                          </Button>
                        </div>
                        <p className="whitespace-pre-wrap text-xs leading-snug text-foreground">{promptInfo.prompt}</p>
                      </div>
                    )}
                    {promptInfo.negativePrompt && (
                      <div className="space-y-0.5 border-t border-muted/10 pt-2">
                        <div className="flex items-center justify-between">
                          <span className="flex items-center gap-1 text-[11px] font-medium text-danger">
                            <X className="size-3" />
                            反向提示词
                          </span>
                          <Button isIconOnly size="sm" variant="ghost" className="size-5" onPress={() => handleCopy(promptInfo.negativePrompt!, "negativePrompt")}>
                            {copiedField === "negativePrompt" ? <Check className="size-3 text-success" /> : <Copy className="size-3" />}
                          </Button>
                        </div>
                        <p className="whitespace-pre-wrap text-xs leading-snug text-muted">{promptInfo.negativePrompt}</p>
                      </div>
                    )}
                  </div>
                )}

                {/* Description */}
                {asset.description && asset.description !== promptInfo.prompt && (
                  <div className="space-y-0.5">
                    <div className="flex items-center justify-between">
                      <span className="text-[11px] font-medium text-muted">描述</span>
                      <Button isIconOnly size="sm" variant="ghost" className="size-5" onPress={() => handleCopy(asset.description!, "description")}>
                        {copiedField === "description" ? <Check className="size-3 text-success" /> : <Copy className="size-3" />}
                      </Button>
                    </div>
                    <p className="text-xs leading-snug text-foreground">{asset.description}</p>
                  </div>
                )}

                {/* More Info - Collapsible */}
                {hasExtendedInfo && (
                  <Disclosure>
                    <Disclosure.Heading>
                      <Disclosure.Trigger className="flex w-full items-center justify-between rounded-md bg-muted/5 px-2.5 py-1.5 text-[11px] font-medium text-muted transition-colors hover:bg-muted/10">
                        <span>更多信息</span>
                        <Disclosure.Indicator>
                          <ChevronDown className="size-3" />
                        </Disclosure.Indicator>
                      </Disclosure.Trigger>
                    </Disclosure.Heading>
                    <Disclosure.Content>
                      <Disclosure.Body className="space-y-2 pt-2">
                        <div className="grid grid-cols-1 gap-y-1 text-[11px]">
                          {asset.createdAt && (
                            <div className="flex items-center justify-between">
                              <span className="text-muted">创建时间</span>
                              <span>{formatDate(asset.createdAt)}</span>
                            </div>
                          )}
                          {(asset.createdByNickname || asset.createdByUsername) && (
                            <div className="flex items-center justify-between">
                              <span className="text-muted">创建者</span>
                              <span>{asset.createdByNickname || asset.createdByUsername}</span>
                            </div>
                          )}
                          {asset.mimeType && (
                            <div className="flex items-center justify-between">
                              <span className="text-muted">MIME类型</span>
                              <span>{asset.mimeType}</span>
                            </div>
                          )}
                          {asset.scope && (
                            <div className="flex items-center justify-between">
                              <span className="text-muted">作用域</span>
                              <span>{asset.scope === "SCRIPT" ? "项目" : "工作空间"}</span>
                            </div>
                          )}
                        </div>

                        {/* IDs */}
                        <div className="space-y-1 border-t border-muted/10 pt-2">
                          <div className="flex items-center justify-between text-[11px]">
                            <span className="flex items-center gap-1 text-muted">
                              <Hash className="size-2.5" />
                              素材ID
                            </span>
                            <div className="flex items-center gap-1">
                              <code className="rounded bg-muted/10 px-1 py-0.5 text-[10px]">{asset.id.slice(0, 12)}…</code>
                              <Button isIconOnly size="sm" variant="ghost" className="size-4" onPress={() => handleCopy(asset.id, "id")}>
                                {copiedField === "id" ? <Check className="size-2.5 text-success" /> : <Copy className="size-2.5" />}
                              </Button>
                            </div>
                          </div>
                          {fileUrl && (
                            <div className="flex items-center justify-between text-[11px]">
                              <span className="flex items-center gap-1 text-muted">
                                <Link2 className="size-2.5" />
                                文件URL
                              </span>
                              <div className="flex items-center gap-1">
                                <code className="max-w-28 truncate rounded bg-muted/10 px-1 py-0.5 text-[10px]">{fileUrl}</code>
                                <Button isIconOnly size="sm" variant="ghost" className="size-4" onPress={() => handleCopy(fileUrl, "url")}>
                                  {copiedField === "url" ? <Check className="size-2.5 text-success" /> : <Copy className="size-2.5" />}
                                </Button>
                              </div>
                            </div>
                          )}
                        </div>
                      </Disclosure.Body>
                    </Disclosure.Content>
                  </Disclosure>
                )}

                {/* Action Buttons */}
                <div className="flex flex-wrap gap-1.5 border-t border-muted/10 pt-3">
                  {actions}
                  {fileUrl && (
                    <Button
                      size="sm"
                      variant="secondary"
                      onPress={() => {
                        const link = document.createElement("a");
                        link.href = fileUrl;
                        link.download = asset.name || "download";
                        link.click();
                      }}
                    >
                      <Download className="size-3.5" />
                      下载
                    </Button>
                  )}
                </div>
              </div>
            </ScrollShadow>
          </div>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}
