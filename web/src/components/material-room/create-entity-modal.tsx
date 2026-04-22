"use client";

import { useState, useRef } from "react";
import { useTranslations, useLocale} from "next-intl";
import {
  Button,
  Modal,
  Label,
  Input,
  TextArea,
  Select,
  ListBox,
  Spinner,
  toast,
} from "@heroui/react";
import { Upload, Loader2, X, FileIcon, ImageIcon } from "lucide-react";
import { projectService, getErrorFromException} from "@/lib/api";
import type { EntityScope } from "@/lib/api/dto/project.dto";
import type { EntityType } from "./material-sidebar";
import type { SourceMode } from "./material-header";
import Image from "@/components/ui/content-image";

const MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

// Character type options
const CHARACTER_TYPES = [
  { value: "PROTAGONIST", label: "主角", labelEn: "Protagonist" },
  { value: "ANTAGONIST", label: "反派", labelEn: "Antagonist" },
  { value: "SUPPORTING", label: "配角", labelEn: "Supporting" },
  { value: "BACKGROUND", label: "路人", labelEn: "Background" },
];

// Gender options
const GENDER_OPTIONS = [
  { value: "MALE", label: "男", labelEn: "Male" },
  { value: "FEMALE", label: "女", labelEn: "Female" },
  { value: "OTHER", label: "其他", labelEn: "Other" },
];

// Prop type options
const PROP_TYPES = [
  { value: "WEAPON", label: "武器", labelEn: "Weapon" },
  { value: "VEHICLE", label: "载具", labelEn: "Vehicle" },
  { value: "FURNITURE", label: "家具", labelEn: "Furniture" },
  { value: "CLOTHING", label: "服装", labelEn: "Clothing" },
  { value: "FOOD", label: "食物", labelEn: "Food" },
  { value: "TOOL", label: "工具", labelEn: "Tool" },
  { value: "DEVICE", label: "设备", labelEn: "Device" },
  { value: "OTHER", label: "其他", labelEn: "Other" },
];

/** Upload a file via 3-step flow: init → OSS → confirm. Returns assetId. */
async function uploadFile(
  file: File,
  scope: EntityScope,
  scriptId?: string | null,
): Promise<string> {
  const initRes = await projectService.initAssetUpload({
    name: file.name,
    fileName: file.name,
    mimeType: file.type,
    fileSize: file.size,
    scope,
    ...(scope === "SCRIPT" && scriptId ? { scriptId } : {}),
  });
  await fetch(initRes.uploadUrl, {
    method: initRes.method,
    headers: initRes.headers,
    body: file,
  });
  await projectService.confirmAssetUpload(initRes.assetId, {
    fileKey: initRes.fileKey,
    actualFileSize: file.size,
  });
  return initRes.assetId;
}

interface CreateEntityModalProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  entityType: EntityType;
  workspaceId: string;
  onCreated: () => void;
  sourceMode: SourceMode;
  selectedScriptId?: string | null;
}

export function CreateEntityModal({
  isOpen,
  onOpenChange,
  entityType,
  workspaceId: _workspaceId,
  onCreated,
  sourceMode,
  selectedScriptId,
}: CreateEntityModalProps) {
  const locale = useLocale();
  const t = useTranslations("workspace.materialRoom.create");
  const tc = useTranslations("common");

  // Common fields
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [isCreating, setIsCreating] = useState(false);

  // Cover upload
  const coverInputRef = useRef<HTMLInputElement>(null);
  const [coverFile, setCoverFile] = useState<File | null>(null);
  const [coverPreview, setCoverPreview] = useState<string | null>(null);
  const [isCoverUploading, setIsCoverUploading] = useState(false);
  const [coverAssetId, setCoverAssetId] = useState<string | null>(null);

  // Asset file upload (for entityType === "assets")
  const assetInputRef = useRef<HTMLInputElement>(null);
  const [assetFile, setAssetFile] = useState<File | null>(null);
  const [isAssetUploading, setIsAssetUploading] = useState(false);

  // Character fields
  const [characterType, setCharacterType] = useState<string | null>(null);
  const [gender, setGender] = useState<string | null>(null);
  const [age, setAge] = useState("");

  // Prop fields
  const [propType, setPropType] = useState<string | null>(null);

  // Style fields
  const [fixedDesc, setFixedDesc] = useState("");
  const [artDirection, setArtDirection] = useState("");

  // Silence unused var warnings for destructured prop
  void _workspaceId;
  void isAssetUploading;

  const scope: EntityScope =
    sourceMode === "public" ? "SYSTEM" :
    sourceMode === "script" ? "SCRIPT" :
    "WORKSPACE";

  const resetForm = () => {
    setName("");
    setDescription("");
    setCharacterType(null);
    setGender(null);
    setAge("");
    setPropType(null);
    setFixedDesc("");
    setArtDirection("");
    setCoverFile(null);
    setCoverPreview(null);
    setCoverAssetId(null);
    setIsCoverUploading(false);
    setAssetFile(null);
    setIsAssetUploading(false);
    if (coverInputRef.current) coverInputRef.current.value = "";
    if (assetInputRef.current) assetInputRef.current.value = "";
  };

  // Cover image selection & upload
  const handleCoverSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > MAX_FILE_SIZE) {
      toast.danger(t("coverUploadHint"));
      return;
    }
    setCoverFile(file);
    setCoverPreview(URL.createObjectURL(file));

    // Upload immediately
    setIsCoverUploading(true);
    try {
      const assetId = await uploadFile(file, scope, selectedScriptId);
      setCoverAssetId(assetId);
    } catch (err) {
      console.error("Cover upload failed:", err);
      toast.danger("Upload failed");
      setCoverFile(null);
      setCoverPreview(null);
    } finally {
      setIsCoverUploading(false);
    }
  };

  const removeCover = () => {
    setCoverFile(null);
    setCoverPreview(null);
    setCoverAssetId(null);
    if (coverInputRef.current) coverInputRef.current.value = "";
  };
  void coverFile;

  // Asset file selection (for assets type)
  const handleAssetFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > MAX_FILE_SIZE) {
      toast.danger(t("uploadFileHint"));
      return;
    }
    setAssetFile(file);
    if (!name.trim()) setName(file.name.replace(/\.[^.]+$/, ""));
  };

  // Set cover on entity after creation
  const setCoverOnEntity = async (entityId: string, assetId: string) => {
    try {
      switch (entityType) {
        case "characters":
          await projectService.setCharacterCover(entityId, assetId);
          break;
        case "scenes":
          await projectService.setSceneCover(entityId, assetId);
          break;
        case "props":
          await projectService.setPropCover(entityId, assetId);
          break;
        case "styles":
          await projectService.setStyleCover(entityId, assetId);
          break;
      }
    } catch {
      // Non-critical: entity created successfully, cover link failed
    }
  };

  const handleCreate = async () => {
    if (entityType === "assets") {
      // Asset creation = file upload
      if (!assetFile) return;
      setIsCreating(true);
      try {
        setIsAssetUploading(true);
        await uploadFile(assetFile, scope);
        resetForm();
        onOpenChange(false);
        onCreated();
      } catch (error) {
        console.error("Failed to upload asset:", error);
        toast.danger(getErrorFromException(error, locale));
      } finally {
        setIsCreating(false);
        setIsAssetUploading(false);
      }
      return;
    }

    if (!name.trim()) return;
    try {
      setIsCreating(true);
      const baseData: Record<string, unknown> = {
        name: name.trim(),
        description: description.trim() || undefined,
        scope,
        ...(scope === "SCRIPT" && selectedScriptId ? { scriptId: selectedScriptId } : {}),
      };

      let createdId: string | undefined;

      switch (entityType) {
        case "characters": {
          if (characterType) baseData.characterType = characterType;
          if (gender) baseData.gender = gender;
          if (age) baseData.age = parseInt(age, 10);
          const res = await projectService.createCharacter(baseData);
          createdId = (res as { id?: string })?.id;
          break;
        }
        case "scenes": {
          const res = await projectService.createScene(baseData);
          createdId = (res as { id?: string })?.id;
          break;
        }
        case "props": {
          if (propType) baseData.propType = propType;
          const res = await projectService.createProp(baseData);
          createdId = (res as { id?: string })?.id;
          break;
        }
        case "styles": {
          if (fixedDesc.trim()) baseData.fixedDesc = fixedDesc.trim();
          if (artDirection.trim()) {
            baseData.styleParams = { artDirection: artDirection.trim() };
          }
          if (coverAssetId) baseData.coverAssetId = coverAssetId;
          const res = await projectService.createStyle(baseData);
          createdId = (res as { id?: string })?.id;
          break;
        }
      }

      // Link cover to created entity (if cover was uploaded and entity supports it)
      if (createdId && coverAssetId && entityType !== "styles") {
        await setCoverOnEntity(createdId, coverAssetId);
      }

      resetForm();
      onOpenChange(false);
      onCreated();
    } catch (error) {
      console.error("Failed to create entity:", error);
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsCreating(false);
    }
  };

  const getTitle = () => {
    const titles: Record<EntityType, string> = {
      characters: t("character"),
      scenes: t("scene"),
      props: t("prop"),
      assets: t("asset"),
      styles: t("style"),
    };
    return titles[entityType];
  };

  const isAssetMode = entityType === "assets";
  const canSubmit = isAssetMode ? !!assetFile : !!name.trim();

  // Render a field wrapper that ensures Label sits above Input with proper spacing.
  // We avoid TextField's full layout here so we can nest fields in grids cleanly.
  const Field = ({ label, children }: { label: string; children: React.ReactNode }) => (
    <div className="flex min-w-0 flex-col gap-1">
      <Label className="block text-xs text-muted">{label}</Label>
      {children}
    </div>
  );

  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={(open) => { if (!open) resetForm(); onOpenChange(open); }}>
      <Modal.Container>
        <Modal.Dialog className="max-h-[85vh] sm:max-w-3xl">
          <Modal.CloseTrigger />
          <Modal.Header>
            <Modal.Heading>{getTitle()}</Modal.Heading>
          </Modal.Header>
          <Modal.Body>
            {/* Padding-1 inside the scroll container so the focused input's box-shadow ring isn't clipped by overflow */}
            <div className="space-y-3 p-1">
              {/* ─── Asset Mode: file upload card ─── */}
              {isAssetMode ? (
                <div className="rounded-2xl bg-muted/5 p-4">
                  <Label className="mb-3 block text-xs text-muted">{t("uploadFile")}</Label>
                  <input
                    ref={assetInputRef}
                    type="file"
                    className="hidden"
                    onChange={handleAssetFileSelect}
                  />
                  {assetFile ? (
                    <div className="relative flex flex-col items-center justify-center rounded-xl border border-border bg-background/50 p-6">
                      <FileIcon className="mb-2 size-10 text-muted" />
                      <p className="w-full truncate text-center text-sm font-medium">{assetFile.name}</p>
                      <p className="text-xs text-muted">
                        {(assetFile.size / 1024 / 1024).toFixed(1)} MB
                      </p>
                      <Button
                        variant="ghost"
                        size="sm"
                        isIconOnly
                        className="absolute right-2 top-2 size-6 rounded-full"
                        onPress={() => { setAssetFile(null); if (assetInputRef.current) assetInputRef.current.value = ""; }}
                      >
                        <X className="size-3" />
                      </Button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={() => assetInputRef.current?.click()}
                      className="flex w-full cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-border bg-background/30 py-10 text-muted transition-colors hover:border-accent hover:text-accent"
                    >
                      <Upload className="size-8" />
                      <span className="px-4 text-center text-xs">{t("uploadFileHint")}</span>
                    </button>
                  )}
                </div>
              ) : (
                <>
                  {/* ─── Cover + Basic Info (same layout as edit modal) ─── */}
                  <div className="rounded-2xl bg-muted/5 p-4">
                    <div className="flex gap-4">
                      {/* Cover Thumbnail */}
                      <div className="relative h-24 w-20 shrink-0 overflow-hidden rounded-lg bg-muted/10">
                        <input
                          ref={coverInputRef}
                          type="file"
                          accept="image/*"
                          className="hidden"
                          onChange={handleCoverSelect}
                        />
                        {coverPreview ? (
                          <Image src={coverPreview} alt="cover" fill unoptimized className="object-cover" sizes="80px" />
                        ) : (
                          <div className="flex size-full items-center justify-center">
                            <ImageIcon className="size-6 text-muted/30" />
                          </div>
                        )}
                        {isCoverUploading ? (
                          <div className="absolute inset-0 flex items-center justify-center bg-black/50">
                            <Spinner size="sm" />
                          </div>
                        ) : (
                          <>
                            <div className="absolute inset-0 flex items-center justify-center bg-black/40 opacity-0 transition-opacity hover:opacity-100">
                              <Button
                                size="sm"
                                variant="secondary"
                                isIconOnly
                                className="size-7"
                                onPress={() => coverInputRef.current?.click()}
                              >
                                <Upload className="size-3.5" />
                              </Button>
                            </div>
                            {coverPreview && (
                              <Button
                                variant="ghost"
                                size="sm"
                                isIconOnly
                                className="absolute right-0.5 top-0.5 size-5 rounded-full bg-black/60 text-white hover:bg-black/80"
                                onPress={removeCover}
                              >
                                <X className="size-3" />
                              </Button>
                            )}
                          </>
                        )}
                      </div>

                      {/* Fields */}
                      <div className="min-w-0 flex-1 space-y-3">
                        {entityType === "characters" ? (
                          <>
                            <div className="grid grid-cols-3 gap-3">
                              <Field label={t("name")}>
                                <Input
                                  variant="secondary"
                                  value={name}
                                  onChange={(e) => setName(e.target.value)}
                                  className="w-full"
                                  placeholder={t("namePlaceholder")}
                                />
                              </Field>
                              <Field label={t("age")}>
                                <Input
                                  variant="secondary"
                                  type="number"
                                  min="0"
                                  value={age}
                                  onChange={(e) => setAge(e.target.value)}
                                  className="w-full"
                                  placeholder={t("agePlaceholder")}
                                />
                              </Field>
                              <Select
                                className="w-full"
                                placeholder="--"
                                variant="secondary"
                                value={gender}
                                onChange={(value) => setGender(value as string | null)}
                              >
                                <Label className="block text-xs text-muted">{t("gender")}</Label>
                                <Select.Trigger>
                                  <Select.Value />
                                  <Select.Indicator />
                                </Select.Trigger>
                                <Select.Popover>
                                  <ListBox>
                                    {GENDER_OPTIONS.map((opt) => (
                                      <ListBox.Item key={opt.value} id={opt.value} textValue={opt.label}>
                                        {opt.label}
                                        <ListBox.ItemIndicator />
                                      </ListBox.Item>
                                    ))}
                                  </ListBox>
                                </Select.Popover>
                              </Select>
                            </div>
                            <Select
                              className="w-full"
                              placeholder="--"
                              variant="secondary"
                              value={characterType}
                              onChange={(value) => setCharacterType(value as string | null)}
                            >
                              <Label className="block text-xs text-muted">{t("characterType")}</Label>
                              <Select.Trigger>
                                <Select.Value />
                                <Select.Indicator />
                              </Select.Trigger>
                              <Select.Popover>
                                <ListBox>
                                  {CHARACTER_TYPES.map((opt) => (
                                    <ListBox.Item key={opt.value} id={opt.value} textValue={opt.label}>
                                      {opt.label}
                                      <ListBox.ItemIndicator />
                                    </ListBox.Item>
                                  ))}
                                </ListBox>
                              </Select.Popover>
                            </Select>
                          </>
                        ) : entityType === "props" ? (
                          <div className="grid grid-cols-2 gap-3">
                            <Field label={t("name")}>
                              <Input
                                variant="secondary"
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                className="w-full"
                                placeholder={t("namePlaceholder")}
                              />
                            </Field>
                            <Select
                              className="w-full"
                              placeholder="--"
                              variant="secondary"
                              value={propType}
                              onChange={(value) => setPropType(value as string | null)}
                            >
                              <Label className="block text-xs text-muted">{t("propType")}</Label>
                              <Select.Trigger>
                                <Select.Value />
                                <Select.Indicator />
                              </Select.Trigger>
                              <Select.Popover>
                                <ListBox>
                                  {PROP_TYPES.map((opt) => (
                                    <ListBox.Item key={opt.value} id={opt.value} textValue={opt.label}>
                                      {opt.label}
                                      <ListBox.ItemIndicator />
                                    </ListBox.Item>
                                  ))}
                                </ListBox>
                              </Select.Popover>
                            </Select>
                          </div>
                        ) : (
                          <Field label={t("name")}>
                            <Input
                              variant="secondary"
                              value={name}
                              onChange={(e) => setName(e.target.value)}
                              className="w-full"
                              placeholder={t("namePlaceholder")}
                            />
                          </Field>
                        )}

                        <Field label={t("description")}>
                          <TextArea
                            variant="secondary"
                            rows={2}
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            className="w-full"
                            placeholder={t("descriptionPlaceholder")}
                          />
                        </Field>
                      </div>
                    </div>
                  </div>

                  {/* ─── Style-specific extra fields ─── */}
                  {entityType === "styles" && (
                    <div className="space-y-3 rounded-2xl bg-muted/5 p-4">
                      <Field label={t("fixedDesc")}>
                        <TextArea
                          variant="secondary"
                          rows={2}
                          value={fixedDesc}
                          onChange={(e) => setFixedDesc(e.target.value)}
                          className="w-full"
                          placeholder={t("fixedDescPlaceholder")}
                        />
                      </Field>
                      <Field label={t("artDirection")}>
                        <Input
                          variant="secondary"
                          value={artDirection}
                          onChange={(e) => setArtDirection(e.target.value)}
                          className="w-full"
                          placeholder={t("artDirectionPlaceholder")}
                        />
                      </Field>
                    </div>
                  )}
                </>
              )}
            </div>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="secondary" slot="close">{tc("cancel")}</Button>
            <Button
              onPress={handleCreate}
              isPending={isCreating}
              isDisabled={!canSubmit || isCoverUploading}
            >
              {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{t("submit")}</>)}
            </Button>
          </Modal.Footer>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}
