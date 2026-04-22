"use client";

import { useState, useEffect, forwardRef, useImperativeHandle } from "react";
import {
  Input,
  Label,
  Spinner,
  toast,
} from "@heroui/react";
import {
  Sun,
  Volume2,
  Clock,
  Cloud,
  Thermometer,
  Lightbulb,
  MapPin as MapPinIcon,
} from "lucide-react";
import { projectService, getErrorFromException} from "@/lib/api";
import type { SceneDetailDTO } from "@/lib/api/dto";
import { useTranslations, useLocale} from "next-intl";
import {
  type DetailFormHandle,
  useCoverUpload,
  CoverImageSection,
  CollapsibleFieldSection,
  FieldGrid,
} from "./shared";

const ENVIRONMENT_FIELDS = [
  { key: "location", labelKey: "scene.location", placeholderKey: "scene.locationPlaceholder", icon: MapPinIcon },
  { key: "timeOfDay", labelKey: "scene.timeOfDay", placeholderKey: "scene.timeOfDayPlaceholder", icon: Clock },
  { key: "weather", labelKey: "scene.weather", placeholderKey: "scene.weatherPlaceholder", icon: Cloud },
  { key: "season", labelKey: "scene.season", placeholderKey: "scene.seasonPlaceholder", icon: Sun },
  { key: "temperature", labelKey: "scene.temperature", placeholderKey: "scene.temperaturePlaceholder", icon: Thermometer },
  { key: "lighting", labelKey: "scene.lighting", placeholderKey: "scene.lightingPlaceholder", icon: Lightbulb },
];

const ATMOSPHERE_FIELDS = [
  { key: "mood", labelKey: "scene.mood", placeholderKey: "scene.moodPlaceholder" },
  { key: "ambiance", labelKey: "scene.ambiance", placeholderKey: "scene.ambiancePlaceholder" },
  { key: "soundscape", labelKey: "scene.soundscape", placeholderKey: "scene.soundscapePlaceholder" },
];

interface SceneDetailFormProps {
  entityId: string;
  workspaceId: string;
  onUpdated?: () => void;
  onFormStateChange?: (canSave: boolean, isSaving: boolean) => void;
}

export const SceneDetailForm = forwardRef<DetailFormHandle, SceneDetailFormProps>(
  function SceneDetailForm({ entityId, workspaceId, onUpdated, onFormStateChange }, ref) {
    const locale = useLocale();
    const t = useTranslations("workspace.materialRoom.detail");
    const [data, setData] = useState<SceneDetailDTO | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [isEnvironmentOpen, setIsEnvironmentOpen] = useState(false);
    const [isAtmosphereOpen, setIsAtmosphereOpen] = useState(false);

    const [form, setForm] = useState({ name: "", description: "" });
    const [environment, setEnvironment] = useState<Record<string, string>>(() =>
      Object.fromEntries(ENVIRONMENT_FIELDS.map((f) => [f.key, ""]))
    );
    const [atmosphere, setAtmosphere] = useState<Record<string, string>>(() =>
      Object.fromEntries(ATMOSPHERE_FIELDS.map((f) => [f.key, ""]))
    );

    const reloadData = async () => {
      const detail = await projectService.getScene(entityId);
      setData(detail);
      onUpdated?.();
    };

    const { coverInputRef, isUploading, handleCoverUpload } = useCoverUpload(
      entityId,
      (eid, assetId) => projectService.setSceneCover(eid, assetId),
      reloadData,
    );

    useEffect(() => {
      const load = async () => {
        try {
          setIsLoading(true);
          const detail = await projectService.getScene(entityId);
          setData(detail);
          setForm({
            name: detail.name || "",
            description: detail.description || "",
          });
          const env = (detail.environment || {}) as Record<string, unknown>;
          setEnvironment(
            Object.fromEntries(ENVIRONMENT_FIELDS.map((f) => [f.key, (env[f.key] as string) || ""]))
          );
          const atm = (detail.atmosphere || {}) as Record<string, unknown>;
          setAtmosphere(
            Object.fromEntries(ATMOSPHERE_FIELDS.map((f) => [f.key, (atm[f.key] as string) || ""]))
          );
        } catch (err) {
          console.error("Failed to load scene:", err);
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
          environment: Object.fromEntries(
            Object.entries(environment).filter(([, v]) => v.trim())
          ),
          atmosphere: Object.fromEntries(
            Object.entries(atmosphere).filter(([, v]) => v.trim())
          ),
        };
        await projectService.updateScene(entityId, updateData);
        toast.success(t("saveSuccess"));
        onUpdated?.();
      } catch (err) {
        console.error("Failed to save scene:", err);
        toast.danger(t("saveFailed"));
      } finally {
        setIsSaving(false);
      }
    };

    useImperativeHandle(ref, () => ({
      save: handleSave,
      canSave: !!form.name.trim(),
      isSaving,
    }), [form.name, isSaving, data, form, environment, atmosphere]);

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
            icon={Sun}
            title={t("environment")}
            hint={t("clickToExpand")}
            isOpen={isEnvironmentOpen}
            onToggle={() => setIsEnvironmentOpen(!isEnvironmentOpen)}
          >
            <FieldGrid
              fields={ENVIRONMENT_FIELDS}
              values={environment}
              onChange={(key, value) => setEnvironment((p) => ({ ...p, [key]: value }))}
              t={t}
            />
          </CollapsibleFieldSection>

          <CollapsibleFieldSection
            icon={Volume2}
            title={t("atmosphere")}
            hint={t("clickToExpand")}
            isOpen={isAtmosphereOpen}
            onToggle={() => setIsAtmosphereOpen(!isAtmosphereOpen)}
          >
            <FieldGrid
              fields={ATMOSPHERE_FIELDS}
              values={atmosphere}
              onChange={(key, value) => setAtmosphere((p) => ({ ...p, [key]: value }))}
              t={t}
            />
          </CollapsibleFieldSection>
        </div>
      </div>
    );
  }
);
