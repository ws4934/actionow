"use client";

import { useTranslations } from "next-intl";
import { Description, ErrorMessage, Label, ListBox, Select } from "@heroui/react";
import type { Key } from "react";
import type { InputParamDefinition } from "@/lib/api/dto/ai.dto";
import type { StyleListDTO } from "@/lib/api/dto/project.dto";
import { FieldLabel } from "./field-label";
import Image from "@/components/ui/content-image";

interface StyleSelectFieldProps {
  param: InputParamDefinition;
  value: string;
  styles: StyleListDTO[];
  onChange: (value: string) => void;
  disabled?: boolean;
  error?: string;
}

export function StyleSelectField({
  param,
  value,
  styles,
  onChange,
  disabled,
  error,
}: StyleSelectFieldProps) {
  const t = useTranslations("workspace.aiGeneration");
  const selectedStyle = styles.find((style) => style.id === value);

  const handleChange = (key: Key | Key[] | null) => {
    if (key === null) {
      onChange("");
      return;
    }

    if (!Array.isArray(key)) {
      onChange(String(key));
    }
  };

  return (
    <Select
      className="w-full"
      value={value || null}
      onChange={handleChange}
      isDisabled={disabled}
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
            <span className="block min-w-0 truncate">
              {isPlaceholder
                ? param.placeholder || t("style.label")
                : selectedStyle?.name ?? t("style.noStyle")}
            </span>
          )}
        </Select.Value>
        <Select.Indicator />
      </Select.Trigger>
      <Select.Popover className="w-72">
        <ListBox>
          <ListBox.Item key="__none__" id="" textValue={t("style.noStyle")}>
            <span className="text-muted">{t("style.noStyle")}</span>
            <ListBox.ItemIndicator />
          </ListBox.Item>
          {styles.map((style) => (
            <ListBox.Item key={style.id} id={style.id} textValue={style.name}>
              {style.coverUrl ? (
                <Image src={style.coverUrl} alt="" width={32} height={32} className="size-8 rounded-md object-cover" />
              ) : null}
              <div className="flex min-w-0 flex-1 flex-col">
                <Label className="truncate">{style.name}</Label>
                {style.description ? (
                  <Description className="line-clamp-1 text-xs">{style.description}</Description>
                ) : null}
              </div>
              <ListBox.ItemIndicator />
            </ListBox.Item>
          ))}
        </ListBox>
      </Select.Popover>
      {error && <ErrorMessage>{error}</ErrorMessage>}
    </Select>
  );
}
