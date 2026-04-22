"use client";

import { ScrollShadow, Separator } from "@heroui/react";
import { WorkspaceInfo } from "./workspace-info";
import { NavMenu } from "./nav-menu";
import { QuickLinks } from "./quick-links";
import { StatusFilter } from "./status-filter";
import { OnlineMembers } from "./online-members";
import type { WorkspaceDTO, WorkspaceMemberDTO, ScriptStatus } from "@/lib/api/dto";

interface WorkspaceSidebarProps {
  workspace: WorkspaceDTO | null;
  members: WorkspaceMemberDTO[];
  currentUserId?: string;
  projectCount?: number;
  favoriteCount?: number;
  recentCount?: number;
  trashCount?: number;
  statusFilter: ScriptStatus | "ALL";
  onStatusFilterChange: (value: ScriptStatus | "ALL") => void;
  statusCounts?: {
    all: number;
    draft: number;
    inProgress: number;
    completed: number;
  };
  onInviteMember?: () => void;
}

export function WorkspaceSidebar({
  workspace,
  members,
  currentUserId,
  projectCount,
  favoriteCount,
  recentCount,
  trashCount,
  statusFilter,
  onStatusFilterChange,
  statusCounts,
  onInviteMember,
}: WorkspaceSidebarProps) {
  return (
    <aside className="flex h-full w-60 shrink-0 flex-col border-r border-border bg-surface">
      {/* Workspace Info */}
      <WorkspaceInfo workspace={workspace} />

      <Separator />

      {/* Scrollable Content */}
      <ScrollShadow className="min-h-0 flex-1 py-3" hideScrollBar>
        <div className="flex flex-col gap-6">
          {/* Navigation Menu */}
          <NavMenu
            projectCount={projectCount}
            favoriteCount={favoriteCount}
            recentCount={recentCount}
            trashCount={trashCount}
          />

          <Separator className="mx-3" />

          {/* Quick Links */}
          <QuickLinks />

          <Separator className="mx-3" />

          {/* Status Filter */}
          <StatusFilter
            value={statusFilter}
            onChange={onStatusFilterChange}
            counts={statusCounts}
          />

          <Separator className="mx-3" />

          {/* Online Members */}
          <OnlineMembers
            members={members}
            currentUserId={currentUserId}
            onInvite={onInviteMember}
          />
        </div>
      </ScrollShadow>
    </aside>
  );
}

export { WorkspaceInfo } from "./workspace-info";
export { NavMenu } from "./nav-menu";
export { QuickLinks } from "./quick-links";
export { StatusFilter } from "./status-filter";
export { OnlineMembers } from "./online-members";
