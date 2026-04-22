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
import {
  Ruler,
  Box,
  Palette,
  Package,
  Edit3,
  Sparkles,
} from "lucide-react";
import { projectService, getErrorFromException} from "@/lib/api";
import type { PropDetailDTO } from "@/lib/api/dto";
import { useTranslations, useLocale} from "next-intl";
import {
  type DetailFormHandle,
  useCoverUpload,
  CoverImageSection,
  CollapsibleFieldSection,
  FieldGrid,
} from "./shared";

const PROP_TYPES = [
  { value: "WEAPON", labelKey: "prop.typeWEAPON" },
  { value: "VEHICLE", labelKey: "prop.typeVEHICLE" },
  { value: "FURNITURE", labelKey: "prop.typeFURNITURE" },
  { value: "CLOTHING", labelKey: "prop.typeCLOTHING" },
  { value: "FOOD", labelKey: "prop.typeFOOD" },
  { value: "TOOL", labelKey: "prop.typeTOOL" },
  { value: "DEVICE", labelKey: "prop.typeDEVICE" },
  { value: "OTHER", labelKey: "prop.typeOTHER" },
];

const APPEARANCE_FIELDS = [
  { key: "size", labelKey: "prop.size", placeholderKey: "prop.sizePlaceholder", icon: Ruler },
  { key: "material", labelKey: "prop.material", placeholderKey: "prop.materialPlaceholder", icon: Box },
  { key: "color", labelKey: "prop.color", placeholderKey: "prop.colorPlaceholder", icon: Palette },
  { key: "style", labelKey: "prop.style", placeholderKey: "prop.stylePlaceholder", icon: Sparkles },
  { key: "condition", labelKey: "prop.condition", placeholderKey: "prop.conditionPlaceholder", icon: Package },
  { key: "details", labelKey: "prop.details", placeholderKey: "prop.detailsPlaceholder", icon: Edit3 },
];

const allAppearanceKeys = APPEARANCE_FIELDS.map((f) => f.key);

interface PropDetailFormProps {
  entityId: string;
  workspaceId: string;
  onUpdated?: () => void;
  onFormStateChange?: (canSave: boolean, isSaving: boolean) => void;
}

export const PropDetailForm = forwardRef<DetailFormHandle, PropDetailFormProps>(
  function PropDetailForm({ entityId, workspaceId, onUpdated, onFormStateChange }, ref) {
    const locale = useLocale();
    const t = useTranslations("workspace.materialRoom.detail");
    const [data, setData] = useState<PropDetailDTO | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [isAppearanceOpen, setIsAppearanceOpen] = useState(false);

    const [form, setForm] = useState({ name: "", description: "", propType: "" });
    const [appearance, setAppearance] = useState<Record<string, string>>(() =>
      Object.fromEntries(allAppearanceKeys.map((k) => [k, ""]))
    );

    const reloadData = async () => {
      const detail = await projectService.getProp(entityId);
      setData(detail);
      onUpdated?.();
    };

    const { coverInputRef, isUploading, handleCoverUpload } = useCoverUpload(
      entityId,
      (eid, assetId) => projectService.setPropCover(eid, assetId),
      reloadData,
    );

    useEffect(() => {
      const load = async () => {
        try {
          setIsLoading(true);
          const detail = await projectService.getProp(entityId);
          setData(detail);
          setForm({
            name: detail.name || "",
            description: detail.description || "",
            propType: detail.propType || "",
          });
          const ad = (detail.appearanceData || {}) as Record<string, unknown>;
          setAppearance(
            Object.fromEntries(allAppearanceKeys.map((k) => [k, (ad[k] as string) || ""]))
          );
        } catch (err) {
          console.error("Failed to load prop:", err);
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
          propType: form.propType || null,
          appearanceData: Object.fromEntries(
            Object.entries(appearance).filter(([, v]) => v.trim())
          ),
        };
        await projectService.updateProp(entityId, updateData);
        toast.success(t("saveSuccess"));
        onUpdated?.();
      } catch (err) {
        console.error("Failed to save prop:", err);
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
          <div className="grid grid-cols-2 gap-3">
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
            <Select
              className="w-full"
              placeholder="--"
              variant="secondary"
              value={form.propType || null}
              onChange={(v) => setForm((p) => ({ ...p, propType: (v as string) || "" }))}
            >
              <Label className="text-xs text-muted">{t("propType")}</Label>
              <Select.Trigger>
                <Select.Value />
                <Select.Indicator />
              </Select.Trigger>
              <Select.Popover>
                <ListBox>
                  {PROP_TYPES.map((opt) => (
                    <ListBox.Item key={opt.value} id={opt.value} textValue={t(opt.labelKey)}>
                      {t(opt.labelKey)}
                      <ListBox.ItemIndicator />
                    </ListBox.Item>
                  ))}
                </ListBox>
              </Select.Popover>
            </Select>
          </div>
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
            icon={Sparkles}
            title={t("appearance")}
            hint={t("clickToExpand")}
            isOpen={isAppearanceOpen}
            onToggle={() => setIsAppearanceOpen(!isAppearanceOpen)}
          >
            <FieldGrid
              fields={APPEARANCE_FIELDS}
              values={appearance}
              onChange={(key, value) => setAppearance((p) => ({ ...p, [key]: value }))}
              t={t}
            />
          </CollapsibleFieldSection>
        </div>
      </div>
    );
  }
);
