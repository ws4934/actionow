"use client";

import { useState, useEffect, forwardRef, useImperativeHandle } from "react";
import {
  Input,
  Label,
  Select,
  ListBox,
  Spinner,
  toast,
} from "@heroui/react";
import { Ruler } from "lucide-react";
import { projectService, getErrorFromException} from "@/lib/api";
import type { CharacterDetailDTO } from "@/lib/api/dto";
import { useTranslations, useLocale} from "next-intl";
import {
  type DetailFormHandle,
  useCoverUpload,
  CoverImageSection,
  CollapsibleFieldSection,
  FieldGrid,
} from "./shared";

export type { DetailFormHandle };

const APPEARANCE_FIELDS = {
  basic: [
    { key: "height", labelKey: "character.height", placeholderKey: "character.heightPlaceholder" },
    { key: "bodyType", labelKey: "character.bodyType", placeholderKey: "character.bodyTypePlaceholder" },
    { key: "skinTone", labelKey: "character.skinTone", placeholderKey: "character.skinTonePlaceholder" },
  ],
  face: [
    { key: "faceShape", labelKey: "character.faceShape", placeholderKey: "character.faceShapePlaceholder" },
    { key: "eyeShape", labelKey: "character.eyeShape", placeholderKey: "character.eyeShapePlaceholder" },
    { key: "eyeColor", labelKey: "character.eyeColor", placeholderKey: "character.eyeColorPlaceholder" },
  ],
  hair: [
    { key: "hairStyle", labelKey: "character.hairStyle", placeholderKey: "character.hairStylePlaceholder" },
    { key: "hairLength", labelKey: "character.hairLength", placeholderKey: "character.hairLengthPlaceholder" },
    { key: "hairColor", labelKey: "character.hairColor", placeholderKey: "character.hairColorPlaceholder" },
  ],
  style: [
    { key: "artStyle", labelKey: "character.artStyle", placeholderKey: "character.artStylePlaceholder" },
  ],
};

const allAppearanceKeys = Object.values(APPEARANCE_FIELDS).flat().map((f) => f.key);

const CHARACTER_TYPES = [
  { value: "PROTAGONIST", labelKey: "character.typePROTAGONIST" },
  { value: "ANTAGONIST", labelKey: "character.typeANTAGONIST" },
  { value: "SUPPORTING", labelKey: "character.typeSUPPORTING" },
  { value: "BACKGROUND", labelKey: "character.typeBACKGROUND" },
];

const GENDER_OPTIONS = [
  { value: "MALE", labelKey: "character.genderMALE" },
  { value: "FEMALE", labelKey: "character.genderFEMALE" },
  { value: "OTHER", labelKey: "character.genderOTHER" },
];

interface CharacterDetailFormProps {
  entityId: string;
  workspaceId: string;
  onUpdated?: () => void;
  onFormStateChange?: (canSave: boolean, isSaving: boolean) => void;
}

export const CharacterDetailForm = forwardRef<DetailFormHandle, CharacterDetailFormProps>(
  function CharacterDetailForm({ entityId, workspaceId, onUpdated, onFormStateChange }, ref) {
    const locale = useLocale();
    const t = useTranslations("workspace.materialRoom.detail");
    const [data, setData] = useState<CharacterDetailDTO | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [isAppearanceOpen, setIsAppearanceOpen] = useState(false);

    const [form, setForm] = useState({
      name: "",
      description: "",
      characterType: "",
      gender: "",
      age: "",
    });

    const [appearance, setAppearance] = useState<Record<string, string>>(() =>
      Object.fromEntries(allAppearanceKeys.map((k) => [k, ""]))
    );

    const reloadData = async () => {
      const detail = await projectService.getCharacter(entityId);
      setData(detail);
      onUpdated?.();
    };

    const { coverInputRef, isUploading, handleCoverUpload } = useCoverUpload(
      entityId,
      (eid, assetId) => projectService.setCharacterCover(eid, assetId),
      reloadData,
    );

    useEffect(() => {
      const load = async () => {
        try {
          setIsLoading(true);
          const detail = await projectService.getCharacter(entityId);
          setData(detail);
          setForm({
            name: detail.name || "",
            description: detail.description || "",
            characterType: detail.characterType || "",
            gender: detail.gender || "",
            age: detail.age?.toString() || "",
          });
          const ad = (detail.appearanceData || {}) as Record<string, unknown>;
          setAppearance(
            Object.fromEntries(allAppearanceKeys.map((k) => [k, (ad[k] as string) || ""]))
          );
        } catch (err) {
          console.error("Failed to load character:", err);
          toast.danger(getErrorFromException(err, locale));
        } finally {
          setIsLoading(false);
        }
      };
      load();
    }, [entityId, workspaceId]);

    const handleSave = async () => {
      if (!data) return;
      try {
        setIsSaving(true);
        const updateData: Record<string, unknown> = {
          name: form.name.trim(),
          description: form.description.trim() || null,
          characterType: form.characterType || null,
          gender: form.gender || null,
          age: form.age ? parseInt(form.age, 10) : null,
          appearanceData: Object.fromEntries(
            Object.entries(appearance).filter(([, v]) => v.trim())
          ),
        };
        await projectService.updateCharacter(entityId, updateData);
        toast.success(t("saveSuccess"));
        onUpdated?.();
      } catch (err) {
        console.error("Failed to save character:", err);
        toast.danger(t("saveFailed"));
      } finally {
        setIsSaving(false);
      }
    };

    useImperativeHandle(ref, () => ({
      save: handleSave,
      canSave: !!form.name.trim(),
      isSaving,
    }), [form.name, isSaving, data, form, appearance]);

    useEffect(() => {
      onFormStateChange?.(!!form.name.trim(), isSaving);
    }, [form.name, isSaving, onFormStateChange]);

    if (isLoading) {
      return (
        <div className="flex h-64 items-center justify-center">
          <Spinner size="lg" />
        </div>
      );
    }

    if (!data) return null;

    return (
      <div className="flex flex-col gap-6 p-1 md:flex-row">
        <CoverImageSection
          coverUrl={data.coverUrl}
          name={data.name}
          coverInputRef={coverInputRef}
          isUploading={isUploading}
          onUpload={handleCoverUpload}
        />

        <div className="min-w-0 flex-1 space-y-3">
          <div className="grid grid-cols-3 gap-3">
            <div className="flex flex-col gap-1">
              <Label className="block text-xs text-muted">{t("name")}</Label>
              <Input
                variant="secondary"
                value={form.name}
                onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
                className="w-full"
                placeholder={t("namePlaceholder")}
              />
            </div>
            <div className="flex flex-col gap-1">
              <Label className="block text-xs text-muted">{t("age")}</Label>
              <Input
                variant="secondary"
                type="number"
                value={form.age}
                onChange={(e) => setForm((p) => ({ ...p, age: e.target.value }))}
                className="w-full"
                placeholder="--"
                min="0"
              />
            </div>
            <Select
              className="w-full"
              placeholder="--"
              variant="secondary"
              value={form.gender || null}
              onChange={(v) => setForm((p) => ({ ...p, gender: (v as string) || "" }))}
            >
              <Label className="text-xs text-muted">{t("gender")}</Label>
              <Select.Trigger>
                <Select.Value />
                <Select.Indicator />
              </Select.Trigger>
              <Select.Popover>
                <ListBox>
                  {GENDER_OPTIONS.map((opt) => (
                    <ListBox.Item key={opt.value} id={opt.value} textValue={t(opt.labelKey)}>
                      {t(opt.labelKey)}
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
            value={form.characterType || null}
            onChange={(v) => setForm((p) => ({ ...p, characterType: (v as string) || "" }))}
          >
            <Label className="text-xs text-muted">{t("characterType")}</Label>
            <Select.Trigger>
              <Select.Value />
              <Select.Indicator />
            </Select.Trigger>
            <Select.Popover>
              <ListBox>
                {CHARACTER_TYPES.map((opt) => (
                  <ListBox.Item key={opt.value} id={opt.value} textValue={t(opt.labelKey)}>
                    {t(opt.labelKey)}
                    <ListBox.ItemIndicator />
                  </ListBox.Item>
                ))}
              </ListBox>
            </Select.Popover>
          </Select>
          <div className="flex flex-col gap-1">
            <Label className="block text-xs text-muted">{t("description")}</Label>
            <Input
              variant="secondary"
              value={form.description}
              onChange={(e) => setForm((p) => ({ ...p, description: e.target.value }))}
              className="w-full"
              placeholder={t("descriptionPlaceholder")}
            />
          </div>

          <CollapsibleFieldSection
            icon={Ruler}
            title={t("appearance")}
            hint={t("clickToExpand")}
            isOpen={isAppearanceOpen}
            onToggle={() => setIsAppearanceOpen(!isAppearanceOpen)}
          >
            {Object.entries(APPEARANCE_FIELDS).map(([group, fields]) => (
              <FieldGrid
                key={group}
                fields={fields}
                values={appearance}
                onChange={(key, value) => setAppearance((p) => ({ ...p, [key]: value }))}
                t={t}
              />
            ))}
          </CollapsibleFieldSection>
        </div>
      </div>
    );
  }
);
