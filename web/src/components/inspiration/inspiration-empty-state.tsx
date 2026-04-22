"use client";

import { useTranslations } from "next-intl";
import { Button } from "@heroui/react";
import { Sparkles } from "lucide-react";

interface InspirationEmptyStateProps {
  onTemplateClick: (prompt: string) => void;
}

const TEMPLATES = [
  { key: "portrait", prompt: "A photorealistic portrait of a young woman in golden hour lighting, cinematic depth of field" },
  { key: "landscape", prompt: "A breathtaking mountain landscape at sunrise with misty valleys and warm light" },
  { key: "character", prompt: "An anime-style character with flowing silver hair, detailed armor design, dynamic pose" },
  { key: "concept", prompt: "A futuristic cyberpunk cityscape at night with neon lights and flying vehicles" },
] as const;

export function InspirationEmptyState({ onTemplateClick }: InspirationEmptyStateProps) {
  const t = useTranslations("workspace.inspiration");

  return (
    <div className="flex h-full flex-col items-center justify-center px-8">
      <div className="pointer-events-auto flex flex-col items-center gap-6 text-center">
        <div className="flex size-16 items-center justify-center rounded-2xl bg-surface-2">
          <Sparkles className="size-8 text-accent" />
        </div>
        <div className="space-y-2">
          <h2 className="text-xl font-semibold text-foreground">{t("empty")}</h2>
          <p className="max-w-md text-sm text-muted">{t("emptyHint")}</p>
        </div>
        <div className="flex flex-wrap justify-center gap-2">
          {TEMPLATES.map((tmpl) => (
            <Button
              key={tmpl.key}
              variant="outline"
              size="sm"
              onPress={() => onTemplateClick(tmpl.prompt)}
              className="text-xs"
            >
              {t(`quickTemplate.${tmpl.key}`)}
            </Button>
          ))}
        </div>
      </div>
    </div>
  );
}
