"use client";

import { useState, useEffect, forwardRef, useImperativeHandle } from "react";
import {
  Input,
  Label,
  Spinner, toast } from "@heroui/react";
import {
  ImageIcon,
} from "lucide-react";
import { projectService, getErrorFromException} from "@/lib/api";
import { libraryService } from "@/lib/api/services/library.service";
import type { StyleListDTO } from "@/lib/api/dto";
import { useTranslations, useLocale} from "next-intl";
import type { DetailFormHandle } from "./shared";
import Image from "@/components/ui/content-image";

interface StyleDetailFormProps {
  entityId: string;
  workspaceId: string;
  onUpdated?: () => void;
}

export const StyleDetailForm = forwardRef<DetailFormHandle, StyleDetailFormProps>(
  function StyleDetailForm({ entityId, workspaceId }, ref) {
    const t = useTranslations("workspace.materialRoom.detail");
    const locale = useLocale();
    const [data, setData] = useState<StyleListDTO | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    useImperativeHandle(ref, () => ({
      save: async () => {},
      canSave: false,
      isSaving: false,
    }), []);

    useEffect(() => {
      const load = async () => {
        try {
          setIsLoading(true);
          // Try project styles first, fall back to library style
          const styles = await projectService.getStyles();
          let found = styles.find((s) => s.id === entityId);
          if (!found) {
            try {
              const libStyle = await libraryService.getStyle(entityId);
              if (libStyle) {
                found = {
                  id: libStyle.id,
                  name: libStyle.name,
                  description: libStyle.description,
                  coverUrl: libStyle.coverUrl,
                } as StyleListDTO;
              }
            } catch {
              // Style not found in library either
            }
          }
          if (found) setData(found);
        } catch (err) {
          console.error("Failed to load style:", err);
          toast.danger(getErrorFromException(err, locale));
        } finally {
          setIsLoading(false);
        }
      };
      load();
    }, [entityId, workspaceId, locale]);

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
        {/* Left: Cover Image */}
        <div className="md:sticky md:top-0 md:w-2/5 md:shrink-0 md:self-start">
          <div className="relative aspect-[4/3] max-h-[36vh] overflow-hidden rounded-xl bg-muted/10">
            {data.coverUrl ? (
              <Image src={data.coverUrl} alt={data.name} fill className="object-contain" sizes="(min-width: 768px) 33vw, 50vw" />
            ) : (
              <div className="flex size-full items-center justify-center">
                <ImageIcon className="size-12 text-muted/20" />
              </div>
            )}
          </div>
        </div>

        {/* Right: Fields */}
        <div className="min-w-0 flex-1 space-y-3">
          <div className="flex flex-col gap-1">
            <Label className="block text-xs text-muted">{t("name")}</Label>
            <Input
              variant="secondary"
              value={data.name}
              readOnly
            />
          </div>
          <div className="flex flex-col gap-1">
            <Label className="block text-xs text-muted">{t("description")}</Label>
            <Input
              variant="secondary"
              value={data.description || ""}
              readOnly
              placeholder="--"
            />
          </div>
          <p className="text-center text-xs text-muted/50">
            {t("styleReadonly")}
          </p>
        </div>
      </div>
    );
  }
);
