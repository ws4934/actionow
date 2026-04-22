"use client";

import { Avatar, Chip } from "@heroui/react";
import { Building2 } from "lucide-react";
import type { WorkspaceDTO } from "@/lib/api/dto";

interface WorkspaceInfoProps {
  workspace: WorkspaceDTO | null;
}

export function WorkspaceInfo({ workspace }: WorkspaceInfoProps) {
  if (!workspace) return null;

  return (
    <div className="flex flex-col items-center gap-3 px-4 py-5">
      <Avatar size="lg" className="size-16">
        {workspace.logoUrl ? (
          <Avatar.Image src={workspace.logoUrl} alt={workspace.name} />
        ) : null}
        <Avatar.Fallback className="bg-accent/10 text-accent">
          <Building2 className="size-7" />
        </Avatar.Fallback>
      </Avatar>
      <div className="text-center">
        <h2 className="text-sm font-semibold">{workspace.name}</h2>
        <div className="mt-1.5 flex items-center justify-center gap-2">
          <Chip size="sm" variant="soft" color="accent">
            {workspace.planType}
          </Chip>
          <span className="text-xs text-muted">
            {workspace.memberCount} 成员
          </span>
        </div>
      </div>
    </div>
  );
}
