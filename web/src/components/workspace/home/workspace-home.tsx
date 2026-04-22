"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { ScrollShadow, Modal, Button, TextField, Input, Label, TextArea, Description, Spinner, toast} from "@heroui/react";
import {
  WelcomeCard,
  CreateButton,
  RecentProjects,
  StatsOverview,
  TeamActivity,
  QuickStart,
  type ActivityItem,
} from "./content";
import { useWorkspace } from "@/components/providers/workspace-provider";
import { authService, projectService, getErrorFromException} from "@/lib/api";
import type { ScriptListDTO, UserDTO } from "@/lib/api/dto";
import { useLocale } from "next-intl";

export function WorkspaceHome() {
  const locale = useLocale();
  const router = useRouter();
  const { currentWorkspaceId } = useWorkspace();

  // State
  const [user, setUser] = useState<UserDTO | null>(null);
  const [projects, setProjects] = useState<ScriptListDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createForm, setCreateForm] = useState({ title: "", synopsis: "" });
  const [isCreating, setIsCreating] = useState(false);

  // Mock data for now (will be replaced with real API calls)
  const [activities] = useState<ActivityItem[]>([
    {
      id: "1",
      userId: "1",
      userName: "张三",
      action: "edit",
      targetType: "script",
      targetName: "剧本A",
      timestamp: new Date(Date.now() - 2 * 60 * 1000).toISOString(),
    },
    {
      id: "2",
      userId: "2",
      userName: "李四",
      action: "create",
      targetType: "character",
      targetName: "新角色",
      timestamp: new Date(Date.now() - 15 * 60 * 1000).toISOString(),
    },
    {
      id: "3",
      userId: "3",
      userName: "王五",
      action: "upload",
      targetType: "asset",
      targetName: "5个素材",
      timestamp: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
    },
  ]);

  // Load data
  const loadData = useCallback(async () => {
    if (!currentWorkspaceId) return;

    setIsLoading(true);
    try {
      const [userData, scriptsRes] = await Promise.all([
        authService.getCurrentUser(),
        projectService.getScripts(),
      ]);
      setUser(userData);
      setProjects(scriptsRes);
    } catch (error) {
      console.error("Failed to load workspace data:", error);
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsLoading(false);
    }
  }, [currentWorkspaceId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Recent projects (sorted by update time)
  const recentProjects = [...projects]
    .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
    .slice(0, 8);

  // Handle create project
  const handleCreateProject = async () => {
    if (!currentWorkspaceId || !createForm.title.trim()) return;

    setIsCreating(true);
    try {
      const newScript = await projectService.createScript( {
        title: createForm.title.trim(),
        synopsis: createForm.synopsis.trim() || undefined,
      });
      setShowCreateModal(false);
      setCreateForm({ title: "", synopsis: "" });
      router.push(`/workspace/projects/${newScript.id}`);
    } catch (error) {
      console.error("Failed to create project:", error);
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <div className="h-full bg-background">
      <ScrollShadow className="h-full" hideScrollBar>
        <div className="mx-auto max-w-6xl px-6 py-8">
          {/* Row 1: Welcome + Create */}
          <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
            <div className="md:col-span-3">
              <WelcomeCard userName={user?.nickname || user?.username || "用户"} />
            </div>
            <div className="md:col-span-1">
              <CreateButton onCreateProject={() => setShowCreateModal(true)} />
            </div>
          </div>

          {/* Row 2: Recent Projects */}
          <div className="mt-6">
            <RecentProjects projects={recentProjects} />
          </div>

          {/* Row 3: Stats + Activity */}
          <div className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-3">
            <div className="md:col-span-1">
              <StatsOverview
                projectCount={projects.length}
                assetCount={156}
                characterCount={24}
                storyboardCount={8}
                storageUsed={7.5 * 1024 * 1024 * 1024}
                storageLimit={10 * 1024 * 1024 * 1024}
              />
            </div>
            <div className="md:col-span-2">
              <TeamActivity activities={activities} />
            </div>
          </div>

          {/* Row 4: Quick Start */}
          <div className="mt-6">
            <QuickStart
              onCreateFromTemplate={() => setShowCreateModal(true)}
              onImport={() => {}}
              onAIGenerate={() => {}}
            />
          </div>
        </div>
      </ScrollShadow>

      {/* Create Project Modal */}
      <Modal.Backdrop isOpen={showCreateModal} onOpenChange={setShowCreateModal}>
        <Modal.Container size="md">
          <Modal.Dialog>
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>新建剧本</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="flex flex-col gap-4">
              <TextField isRequired>
                <Label>剧本标题</Label>
                <Input
                  placeholder="输入剧本标题"
                  value={createForm.title}
                  onChange={(e) => setCreateForm((prev) => ({ ...prev, title: e.target.value }))}
                />
              </TextField>
              <TextField>
                <Label>剧本简介</Label>
                <TextArea
                  placeholder="简要描述你的剧本（可选）"
                  rows={3}
                  value={createForm.synopsis}
                  onChange={(e) => setCreateForm((prev) => ({ ...prev, synopsis: e.target.value }))}
                />
                <Description>简要描述你的剧本，帮助团队成员快速了解项目</Description>
              </TextField>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" slot="close" isDisabled={isCreating}>
                取消
              </Button>
              <Button
                variant="primary"
                onPress={handleCreateProject}
                isPending={isCreating}
                isDisabled={!createForm.title.trim()}
              >
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}创建</>)}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </div>
  );
}

export default WorkspaceHome;
