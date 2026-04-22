"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Users } from "lucide-react";
import { useOnboardingStore } from "@/lib/stores/onboarding-store";
import { workspaceService, getErrorFromException } from "@/lib/api";
import { toast } from "@heroui/react";
import type { WorkspaceDTO } from "@/lib/api/dto";

interface CreateWorkspaceStepProps {
  onWorkspaceCreated: (workspace: WorkspaceDTO) => void;
}

export function CreateWorkspaceStep({ onWorkspaceCreated }: CreateWorkspaceStepProps) {
  const t = useTranslations("onboarding.createWorkspace");
  const { nextStep } = useOnboardingStore();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async () => {
    if (!name.trim() || isLoading) return;

    setIsLoading(true);
    try {
      const workspace = await workspaceService.createWorkspace({
        name: name.trim(),
        description: description.trim() || undefined,
      });
      onWorkspaceCreated(workspace);
      nextStep();
    } catch (error) {
      toast.danger(getErrorFromException(error, "zh"));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="onboarding-step-enter flex h-full flex-col items-center justify-center px-8 py-10 text-center md:px-12">
      <div className="mb-6 flex size-20 items-center justify-center rounded-full bg-accent/10">
        <Users className="size-10 text-accent" />
      </div>

      <h1 className="mb-3 text-3xl font-bold tracking-tight md:text-4xl">
        {t("title")}
      </h1>
      <p className="mb-8 max-w-md text-lg text-foreground/60">
        {t("subtitle")}
      </p>

      <div className="flex w-full max-w-sm flex-col gap-4 text-left">
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-foreground/70">
            {t("name")}
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t("namePlaceholder")}
            autoFocus
            className="rounded-lg border border-foreground/10 bg-surface/50 px-4 py-2.5 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/30 focus:border-accent"
            onKeyDown={(e) => {
              if (e.key === "Enter" && name.trim()) {
                void handleSubmit();
              }
            }}
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-foreground/70">
            {t("description")}
          </label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder={t("descriptionPlaceholder")}
            rows={3}
            className="resize-none rounded-lg border border-foreground/10 bg-surface/50 px-4 py-2.5 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/30 focus:border-accent"
          />
        </div>
      </div>

      <button
        type="button"
        onClick={() => void handleSubmit()}
        disabled={!name.trim() || isLoading}
        className="mt-8 rounded-lg bg-accent px-8 py-3 font-black uppercase tracking-wider text-accent-foreground transition-transform enabled:hover:scale-[1.02] enabled:active:scale-[0.98] disabled:opacity-40"
      >
        {isLoading ? t("creating") : t("create")}
      </button>
    </div>
  );
}
