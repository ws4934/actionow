"use client";

import NextImage from "next/image";
import { Tooltip } from "@heroui/react";
import { X } from "lucide-react";
import type { EntityCategoryKey, LinkedEntity } from "../types";
import { ENTITY_CATEGORIES } from "../constants";

interface EntityChipBarProps {
  linkedEntities: LinkedEntity[];
  onRemove: (category: EntityCategoryKey) => void;
}

export function EntityChipBar({ linkedEntities, onRemove }: EntityChipBarProps) {
  if (linkedEntities.length === 0) return null;

  return (
    <div className="flex flex-wrap gap-1.5 px-3 pb-2">
      {linkedEntities.map((entity) => {
        const catDef = ENTITY_CATEGORIES.find((c) => c.key === entity.category);
        if (!catDef) return null;
        const Icon = catDef.icon;

        return (
          <Tooltip key={entity.category} delay={0}>
            <Tooltip.Trigger>
              <span
                className="inline-flex items-center gap-1.5 rounded-full bg-accent/10 py-0.5 pl-0.5 pr-1 text-xs text-accent"
              >
                {entity.coverUrl ? (
                  <NextImage
                    src={entity.coverUrl}
                    alt={entity.name}
                    width={20}
                    height={20}
                    className="size-5 shrink-0 rounded-full object-cover"
                    unoptimized
                  />
                ) : (
                  <span className="flex size-5 shrink-0 items-center justify-center">
                    <Icon className="size-3" />
                  </span>
                )}
                <span className="max-w-24 truncate">{entity.name}</span>
                <button
                  onClick={() => onRemove(entity.category)}
                  className="ml-0.5 rounded-full p-0.5 transition-colors hover:bg-accent/20"
                >
                  <X className="size-3" />
                </button>
              </span>
            </Tooltip.Trigger>
            <Tooltip.Content>{entity.name}</Tooltip.Content>
          </Tooltip>
        );
      })}
    </div>
  );
}
