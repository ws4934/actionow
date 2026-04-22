"use client";

import { useState, useEffect } from "react";
import { useTranslations, useLocale } from "next-intl";
import { Button, Avatar, Switch, Skeleton, Card, Spinner, toast } from "@heroui/react";
import { Camera, Globe, Trash2, AlertTriangle, Save, Settings, Info, RefreshCw } from "lucide-react";
import { workspaceService, getErrorFromException } from "@/lib/api";
import { useWorkspace } from "@/components/providers/workspace-provider";
import {
  FormField,
  TextAreaField,
  ListRow,
  ListRowTitle,
  ListRowDescription,
} from "@/components/director";

export default function SettingsPage() {
  const t = useTranslations("workspace.director.studioManagement");
  const locale = useLocale();
  const { currentWorkspace, currentWorkspaceId, setWorkspaces, workspaces } = useWorkspace();
  const [studioName, setStudioName] = useState("");
  const [description, setDescription] = useState("");
  const [isPublic, setIsPublic] = useState(true);
  const [emailNotifications, setEmailNotifications] = useState(true);
  const [memberCanCreateScript, setMemberCanCreateScript] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (currentWorkspace) {
      setStudioName(currentWorkspace.name);
      setDescription(currentWorkspace.description || "");
      const config = currentWorkspace.config || {};
      setIsPublic(config.isPublic !== false);
      setEmailNotifications(config.emailNotifications !== false);
      const permissions = (config.permissions ?? {}) as Record<string, unknown>;
      setMemberCanCreateScript(permissions.memberCanCreateScript !== false);
      setIsLoading(false);
    }
  }, [currentWorkspace]);

  const handleSave = async () => {
    if (!currentWorkspaceId) return;

    try {
      setIsSaving(true);
      const updated = await workspaceService.updateWorkspace( {
        name: studioName,
        description: description || undefined,
        config: {
          ...currentWorkspace?.config,
          isPublic,
          emailNotifications,
        },
      });
      setWorkspaces(workspaces.map((w) => (w.id === updated.id ? updated : w)));
    } catch (error) {
      console.error("Failed to update workspace:", error);
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!currentWorkspaceId) return;

    const confirmed = window.confirm("确定要删除此片场吗？此操作不可恢复。");
    if (!confirmed) return;

    setIsDeleting(true);
    try {
      await workspaceService.deleteWorkspace();
      setWorkspaces(workspaces.filter((w) => w.id !== currentWorkspaceId));
    } catch (error) {
      console.error("Failed to delete workspace:", error);
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsDeleting(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex h-full flex-col">
        <div className="flex shrink-0 items-center justify-between gap-3 pb-4">
          <Skeleton className="h-8 w-48 rounded-md" />
          <Skeleton className="h-8 w-24 rounded-md" />
        </div>
        <div className="min-h-0 flex-1 space-y-4 overflow-auto">
          <Skeleton className="h-56 w-full rounded-xl" />
          <Skeleton className="h-32 w-full rounded-xl" />
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      {/* Toolbar */}
      <div className="flex shrink-0 items-center justify-between gap-3 pb-4">
        <div className="flex items-center gap-2">
          <Settings className="size-4 text-muted" />
          <span className="text-sm font-medium text-foreground">片场设置</span>
        </div>
        <div className="flex items-center gap-2">
          <Button size="sm" onPress={handleSave} isPending={isSaving}>
            {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Save className="size-4" />}保存更改</>)}
          </Button>
          <Button variant="ghost" size="sm" isIconOnly onPress={() => window.location.reload()}>
            <RefreshCw className="size-4" />
          </Button>
        </div>
      </div>

      {/* Content: scrollable area */}
      <div className="min-h-0 flex-1 overflow-auto">
        <div className="grid gap-4" style={{ gridTemplateColumns: "1fr 280px" }}>
          {/* Left: Main Content */}
          <div className="space-y-4">
            {/* Studio Profile */}
            <Card className="w-full" variant="secondary">
              <Card.Content>
                <div className="flex items-start gap-4">
                  <div className="relative shrink-0">
                    <Avatar className="size-16">
                      {currentWorkspace?.logoUrl ? (
                        <Avatar.Image src={currentWorkspace.logoUrl} alt={studioName} />
                      ) : null}
                      <Avatar.Fallback className="text-lg">
                        {studioName.charAt(0).toUpperCase()}
                      </Avatar.Fallback>
                    </Avatar>
                    <Button
                      isIconOnly
                      size="sm"
                      variant="primary"
                      className="absolute -bottom-1 -right-1 size-6 rounded-full border-2 border-surface shadow-md"
                    >
                      <Camera className="size-3" />
                    </Button>
                  </div>

                  <div className="flex-1 space-y-3">
                    <FormField
                      label="片场名称"
                      value={studioName}
                      onChange={setStudioName}
                    />
                    <TextAreaField
                      label="片场简介"
                      value={description}
                      onChange={setDescription}
                      placeholder="介绍一下你的片场..."
                      rows={3}
                    />
                  </div>
                </div>
              </Card.Content>
            </Card>

            {/* Preferences */}
            <Card className="w-full" variant="secondary">
              <Card.Content className="p-0">
                <div className="divide-y divide-border">
                  <ListRow compact right={<Switch isSelected={isPublic} onChange={setIsPublic} />}>
                    <ListRowTitle>公开片场</ListRowTitle>
                    <ListRowDescription>允许其他用户发现并申请加入</ListRowDescription>
                  </ListRow>
                  <ListRow compact right={<Switch isSelected={emailNotifications} onChange={setEmailNotifications} />}>
                    <ListRowTitle>邮件通知</ListRowTitle>
                    <ListRowDescription>接收成员变动、项目更新等通知</ListRowDescription>
                  </ListRow>
                  <ListRow compact right={
                    <Switch
                      isSelected={memberCanCreateScript}
                      onChange={async (checked) => {
                        setMemberCanCreateScript(checked);
                        try {
                          await workspaceService.toggleScriptCreation(checked);
                        } catch (error) {
                          console.error("Failed to toggle script creation:", error);
                          toast.danger(getErrorFromException(error, locale));
                          setMemberCanCreateScript(!checked);
                        }
                      }}
                    />
                  }>
                    <ListRowTitle>成员可创建剧本</ListRowTitle>
                    <ListRowDescription>关闭后仅管理员和创建者可新建剧本</ListRowDescription>
                  </ListRow>
                </div>
              </Card.Content>
            </Card>
          </div>

          {/* Right: Info & Danger */}
          <div className="space-y-3">
            {/* Plan card */}
            <Card className="w-full" variant="secondary">
              <Card.Content className="p-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-muted">当前计划</span>
                  <div className="flex size-7 items-center justify-center rounded-lg bg-accent/10">
                    <Settings className="size-3.5 text-accent" />
                  </div>
                </div>
                <p className="mt-1.5 text-2xl font-bold text-foreground">{currentWorkspace?.planType}</p>
                <p className="mt-1 text-xs text-muted">
                  成员 {currentWorkspace?.memberCount} / {currentWorkspace?.maxMembers}
                </p>
              </Card.Content>
            </Card>

            {/* Workspace info - compact stat rows */}
            <Card className="w-full" variant="secondary">
              <Card.Content className="p-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-muted">片场信息</span>
                  <div className="flex size-7 items-center justify-center rounded-lg bg-muted/10">
                    <Info className="size-3.5 text-muted" />
                  </div>
                </div>
                <div className="mt-3 space-y-1.5">
                {([
                  ["片场 ID", currentWorkspace?.id, true],
                  ["片场标识", currentWorkspace?.slug, false],
                  ["创建时间", currentWorkspace?.createdAt
                    ? new Date(currentWorkspace.createdAt).toLocaleDateString("zh-CN")
                    : "-", false],
                ] as const).map(([label, value, isCode]) => (
                  <div key={label} className="flex items-center justify-between rounded-md bg-surface-secondary px-3 py-1.5 text-xs">
                    <span className="text-muted">{label}</span>
                    {isCode ? (
                      <code className="rounded bg-surface px-1.5 py-0.5 text-[10px]">{value}</code>
                    ) : (
                      <span className="font-medium text-foreground">{value}</span>
                    )}
                  </div>
                ))}
                </div>
              </Card.Content>
            </Card>

            {/* Danger Zone */}
            {currentWorkspace?.myRole === "CREATOR" && (
              <Card className="w-full border-danger/30" variant="secondary">
                <Card.Content className="p-3">
                  <div className="flex items-center gap-2">
                    <AlertTriangle className="size-4 text-danger" />
                    <span className="text-sm font-medium text-danger">危险操作</span>
                  </div>
                  <p className="mt-2 text-xs text-muted">永久删除片场及所有数据，不可恢复</p>
                  <Button variant="danger" size="sm" className="mt-3 w-full" isPending={isDeleting} onPress={handleDelete}>
                    {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Trash2 className="size-3.5" />}删除片场</>)}
                  </Button>
                </Card.Content>
              </Card>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
