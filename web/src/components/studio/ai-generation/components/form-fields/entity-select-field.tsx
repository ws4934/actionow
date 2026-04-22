"use client";

import { useEffect, useMemo, useState } from "react";
import { useTranslations, useLocale} from "next-intl";
import { Description, ErrorMessage, Label, ListBox, Select, Spinner, toast} from "@heroui/react";
import type { Key } from "react";
import type { InputParamDefinition } from "@/lib/api/dto/ai.dto";
import type { EntityType } from "@/lib/api/dto/project.dto";
import { projectService } from "@/lib/api/services/project.service";
import { FieldLabel } from "./field-label";
import { getErrorFromException } from "@/lib/api";
import Image from "@/components/ui/content-image";

type PromptEntityType = Extract<EntityType, "CHARACTER" | "SCENE" | "PROP" | "STORYBOARD">;

interface EntityOption {
  id: string;
  label: string;
  description?: string | null;
  coverUrl?: string | null;
}

interface EntitySelectFieldProps {
  param: InputParamDefinition;
  value: string;
  entityType: PromptEntityType | null;
  scriptId?: string;
  episodeId?: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  error?: string;
}

function isPromptEntityType(value: string): value is PromptEntityType {
  return value === "CHARACTER" || value === "SCENE" || value === "PROP" || value === "STORYBOARD";
}

export function EntitySelectField({
  param,
  value,
  entityType,
  scriptId,
  episodeId,
  onChange,
  disabled,
  error,
}: EntitySelectFieldProps) {
  const locale = useLocale();
  const t = useTranslations("workspace.aiGeneration.dynamicForm");
  const [options, setOptions] = useState<EntityOption[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  const supportedTypes = useMemo(() => {
    const rawTypes = param.componentProps?.supportedTypes;
    if (!Array.isArray(rawTypes)) {
      return null;
    }

    return rawTypes.filter(
      (type): type is PromptEntityType => typeof type === "string" && isPromptEntityType(type)
    );
  }, [param.componentProps]);

  const isTypeSupported = entityType ? !supportedTypes || supportedTypes.includes(entityType) : false;

  useEffect(() => {
    let ignore = false;

    async function loadOptions() {
      if (!entityType || !isTypeSupported) {
        setOptions([]);
        return;
      }

      try {
        setIsLoading(true);

        let nextOptions: EntityOption[] = [];

        switch (entityType) {
          case "CHARACTER": {
            const data = scriptId
              ? await projectService.getCharactersAvailable(scriptId)
              : await projectService.getCharacters();
            nextOptions = data.map((item) => ({
              id: item.id,
              label: item.name,
              description: item.description || item.fixedDesc,
              coverUrl: item.coverUrl,
            }));
            break;
          }
          case "SCENE": {
            const data = scriptId
              ? await projectService.getScenesAvailable(scriptId)
              : await projectService.getScenes();
            nextOptions = data.map((item) => ({
              id: item.id,
              label: item.name,
              description: item.description || item.fixedDesc,
              coverUrl: item.coverUrl,
            }));
            break;
          }
          case "PROP": {
            const data = scriptId
              ? await projectService.getPropsAvailable(scriptId)
              : await projectService.getProps();
            nextOptions = data.map((item) => ({
              id: item.id,
              label: item.name,
              description: item.description || item.fixedDesc,
              coverUrl: item.coverUrl,
            }));
            break;
          }
          case "STORYBOARD": {
            const data = episodeId
              ? await projectService.getStoryboardsByEpisode(episodeId)
              : scriptId
                ? await projectService.getStoryboardsByScript(scriptId)
                : [];
            nextOptions = data.map((item) => ({
              id: item.id,
              label: item.title?.trim() || t("storyboardFallback", { sequence: item.sequence }),
              description: item.synopsis,
              coverUrl: item.coverUrl,
            }));
            break;
          }
        }

        if (!ignore) {
          setOptions(nextOptions);
        }
      } catch (loadError) {
        if (!ignore) {
          console.error("Failed to load entity options:", loadError);
          toast.danger(getErrorFromException(loadError, locale));
          setOptions([]);
        }
      } finally {
        if (!ignore) {
          setIsLoading(false);
        }
      }
    }

    void loadOptions();

    return () => {
      ignore = true;
    };
  }, [entityType, episodeId, isTypeSupported, scriptId, t]);

  const selectedOption = options.find((option) => option.id === value);

  const handleChange = (key: Key | Key[] | null) => {
    if (key !== null && !Array.isArray(key)) {
      onChange(String(key));
    }
  };

  const placeholder = !entityType
    ? t("selectEntityTypeFirst")
    : param.placeholder || t("selectEntity");

  return (
    <Select
      className="w-full"
      value={value || null}
      onChange={handleChange}
      isDisabled={disabled || !entityType || !isTypeSupported}
      isInvalid={!!error}
    >
      <FieldLabel
        label={param.label}
        required={param.required}
        description={param.description}
      />
      <Select.Trigger className="overflow-hidden">
        <Select.Value>
          {({ isPlaceholder }) => (
            <span className="flex min-w-0 items-center gap-2">
              {isLoading && <Spinner size="sm" />}
              <span className="block min-w-0 truncate">
                {isLoading
                  ? t("loadingOptions")
                  : isPlaceholder
                    ? placeholder
                    : selectedOption?.label ?? value}
              </span>
            </span>
          )}
        </Select.Value>
        <Select.Indicator />
      </Select.Trigger>
      <Select.Popover className="w-72">
        {!entityType || !isTypeSupported ? (
          <div className="px-3 py-4 text-sm text-muted">
            {t("selectEntityTypeFirst")}
          </div>
        ) : isLoading ? (
          <div className="flex items-center gap-2 px-3 py-4 text-sm text-muted">
            <Spinner size="sm" />
            <span>{t("loadingOptions")}</span>
          </div>
        ) : options.length === 0 ? (
          <div className="px-3 py-4 text-sm text-muted">
            {t("noOptions")}
          </div>
        ) : (
          <ListBox>
            {options.map((option) => (
              <ListBox.Item key={option.id} id={option.id} textValue={option.label}>
                {option.coverUrl ? (
                  <Image src={option.coverUrl} alt="" width={32} height={32} className="size-8 rounded-md object-cover" />
                ) : null}
                <div className="flex min-w-0 flex-1 flex-col">
                  <Label className="truncate">{option.label}</Label>
                  {option.description ? (
                    <Description className="line-clamp-1 text-xs">{option.description}</Description>
                  ) : null}
                </div>
                <ListBox.ItemIndicator />
              </ListBox.Item>
            ))}
          </ListBox>
        )}
      </Select.Popover>
      {error && <ErrorMessage>{error}</ErrorMessage>}
    </Select>
  );
}
