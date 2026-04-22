"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Dropdown, Label, Button, Separator, Avatar } from "@heroui/react";
import { ChevronDown, Plus, Check } from "lucide-react";
import type { WorkspaceDTO } from "@/lib/api/dto";

interface WorkspaceSwitcherProps {
  workspaces: WorkspaceDTO[];
  currentWorkspaceId: string | null;
  onWorkspaceChange: (workspaceId: string) => void;
  onCreateWorkspace?: () => void;
}

export function WorkspaceSwitcher({
  workspaces,
  currentWorkspaceId,
  onWorkspaceChange,
  onCreateWorkspace,
}: WorkspaceSwitcherProps) {
  const t = useTranslations("workspace.switcher");
  const [isOpen, setIsOpen] = useState(false);

  const currentWorkspace = workspaces.find((w) => w.id === currentWorkspaceId);

  const handleCreateWorkspace = () => {
    if (onCreateWorkspace) {
      onCreateWorkspace();
    }
    setIsOpen(false);
  };

  return (
    <Dropdown isOpen={isOpen} onOpenChange={setIsOpen}>
      <Button
        variant="ghost"
        size="sm"
        aria-label={t("title")}
      >
        <Avatar className="size-5">
          {currentWorkspace?.logoUrl ? (
            <Avatar.Image src={currentWorkspace.logoUrl} alt={currentWorkspace.name} />
          ) : null}
          <Avatar.Fallback className="bg-accent/20 text-xs text-accent">
            {currentWorkspace?.name?.charAt(0).toUpperCase() || "W"}
          </Avatar.Fallback>
        </Avatar>
        <span className="max-w-[100px] truncate">
          {currentWorkspace?.name || t("title")}
        </span>
        <ChevronDown
          className={`size-3.5 text-muted transition-transform duration-200 ${isOpen ? "rotate-180" : ""}`}
        />
      </Button>
      <Dropdown.Popover placement="bottom start" className="w-56">
        <Dropdown.Menu
          selectedKeys={currentWorkspaceId ? new Set([currentWorkspaceId]) : new Set()}
          selectionMode="single"
          onAction={(key) => {
            if (key === "create") {
              handleCreateWorkspace();
            } else {
              onWorkspaceChange(key as string);
              setIsOpen(false);
            }
          }}
        >
          {workspaces.length === 0 ? (
            <Dropdown.Item id="empty" textValue={t("noWorkspaces")} isDisabled>
              <Label className="text-muted">{t("noWorkspaces")}</Label>
            </Dropdown.Item>
          ) : (
            workspaces.map((workspace) => (
              <Dropdown.Item key={workspace.id} id={workspace.id} textValue={workspace.name}>
                <Avatar size="sm" className="size-7">
                  {workspace.logoUrl ? (
                    <Avatar.Image src={workspace.logoUrl} alt={workspace.name} />
                  ) : null}
                  <Avatar.Fallback className="bg-accent/20 text-xs text-accent">
                    {workspace.name.charAt(0).toUpperCase()}
                  </Avatar.Fallback>
                </Avatar>
                <div className="flex flex-col">
                  <Label>{workspace.name}</Label>
                  {workspace.myRole && (
                    <span className="text-xs text-muted capitalize">
                      {workspace.myRole}
                    </span>
                  )}
                </div>
                {workspace.id === currentWorkspaceId && (
                  <Check className="ml-auto size-4 shrink-0 text-accent" />
                )}
              </Dropdown.Item>
            ))
          )}
        </Dropdown.Menu>
        <Separator className="my-1" />
        <div className="px-1.5 pb-1.5">
          <Button
            variant="ghost"
            size="sm"
            fullWidth
            className="justify-start"
            onPress={handleCreateWorkspace}
          >
            <Plus className="size-4" />
            {t("create")}
          </Button>
        </div>
      </Dropdown.Popover>
    </Dropdown>
  );
}
