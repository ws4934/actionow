"use client";

import { useState, useEffect, useMemo, useCallback } from "react";
import { useTranslations, useLocale } from "next-intl";
import {
  Button,
  Avatar,
  Modal,
  Skeleton,
  Card,
  Select,
  ListBox,
  Chip,
  Popover,
  toast,
  Tooltip,
  SearchField,
  Spinner,
} from "@heroui/react";
import {
  Users,
  Search,
  UserPlus,
  Crown,
  Shield,
  User,
  UserX,
  Ticket,
  X,
  Copy,
  Check,
  Plus,
  Coins,
  RotateCcw,
  Infinity,
  AlertCircle,
  Lock,
  Loader2,
  Clock,
  RefreshCw,
} from "lucide-react";
import {
  workspaceService,
  inviteService,
  walletService,
  projectService,
  scriptPermissionService,
  getErrorFromException,
} from "@/lib/api";
import { useWorkspace } from "@/components/providers/workspace-provider";
import type {
  WorkspaceMemberDTO,
  WorkspaceInvitationDTO,
  WorkspaceRole,
  CreateInvitationRequestDTO,
  PersonalInviteCodeDTO,
  QuotaDTO,
  SetQuotaRequestDTO,
  ResetCycle,
  ScriptListDTO,
} from "@/lib/api/dto";
import type {
  ScriptPermissionDTO,
  ScriptPermissionType,
  GrantScriptPermissionRequest,
  InviteCollaboratorRequest,
} from "@/lib/api/dto";
import {
  FormField,
  TextAreaField,
  StatusBadge,
} from "@/components/director";

// ── Constants ────────────────────────────────────────────────────────────────

const roleConfig: Record<WorkspaceRole, {
  label: string;
  icon: typeof Crown;
  color: string;
}> = {
  CREATOR: { label: "创建者", icon: Crown, color: "text-warning" },
  ADMIN: { label: "管理员", icon: Shield, color: "text-accent" },
  MEMBER: { label: "成员", icon: User, color: "text-foreground" },
  GUEST: { label: "访客", icon: UserX, color: "text-muted" },
};

const statusConfig: Record<number, { label: string; variant: "warning" | "success" | "danger" | "default" }> = {
  0: { label: "待使用", variant: "warning" },
  1: { label: "已接受", variant: "success" },
  2: { label: "已过期", variant: "danger" },
  3: { label: "已禁用", variant: "default" },
};

const resetCycleConfig: Record<ResetCycle, string> = {
  DAILY: "每日",
  WEEKLY: "每周",
  MONTHLY: "每月",
  NEVER: "永不重置",
};

const permissionTypeConfig: Record<ScriptPermissionType, {
  label: string;
  color: "default" | "warning" | "accent" | "success" | "danger";
}> = {
  VIEW: { label: "查看", color: "default" },
  EDIT: { label: "编辑", color: "accent" },
  ADMIN: { label: "管理", color: "warning" },
};

// ── Helper Components ────────────────────────────────────────────────────────

function CopyButton({ text, label }: { text: string; label?: string }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = async () => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <button
      onClick={handleCopy}
      className="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs text-muted transition-colors hover:bg-surface-secondary hover:text-foreground"
    >
      {copied ? <Check className="size-3 text-success" /> : <Copy className="size-3" />}
      {label && <span>{copied ? "已复制" : label}</span>}
    </button>
  );
}

// ── Main Page ────────────────────────────────────────────────────────────────

export default function MembersPage() {
  const t = useTranslations("workspace.director.studioManagement");
  const tPerm = useTranslations("workspace.management.permissions");
  const locale = useLocale();
  const { currentWorkspaceId, currentWorkspace } = useWorkspace();
  const [searchQuery, setSearchQuery] = useState("");

  // Members
  const [members, setMembers] = useState<WorkspaceMemberDTO[]>([]);
  const [membersLoading, setMembersLoading] = useState(true);

  // Invitations
  const [invitations, setInvitations] = useState<WorkspaceInvitationDTO[]>([]);
  const [invitationsLoading, setInvitationsLoading] = useState(true);
  const [personalInviteCode, setPersonalInviteCode] = useState<PersonalInviteCodeDTO | null>(null);
  const [showCreateInvitation, setShowCreateInvitation] = useState(false);
  const [inviteRole, setInviteRole] = useState<"ADMIN" | "MEMBER">("MEMBER");
  const [inviteEmails, setInviteEmails] = useState("");
  const [inviteMaxUses, setInviteMaxUses] = useState(10);
  const [inviteExpireHours, setInviteExpireHours] = useState(168);
  const [isCreatingInvitation, setIsCreatingInvitation] = useState(false);

  // Quota
  const [quotas, setQuotas] = useState<QuotaDTO[]>([]);
  const [quotasLoading, setQuotasLoading] = useState(true);
  const [showQuotaModal, setShowQuotaModal] = useState(false);
  const [selectedMemberForQuota, setSelectedMemberForQuota] = useState<WorkspaceMemberDTO | null>(null);
  const [quotaLimit, setQuotaLimit] = useState<number>(-1);
  const [quotaResetCycle, setQuotaResetCycle] = useState<ResetCycle>("MONTHLY");
  const [isUnlimited, setIsUnlimited] = useState(true);
  const [isSavingQuota, setIsSavingQuota] = useState(false);
  const [memberToRemove, setMemberToRemove] = useState<WorkspaceMemberDTO | null>(null);
  const [isRemovingMember, setIsRemovingMember] = useState(false);
  const [disablingInvId, setDisablingInvId] = useState<string | null>(null);
  const [resettingQuotaUserId, setResettingQuotaUserId] = useState<string | null>(null);

  // Script permissions
  const [scripts, setScripts] = useState<ScriptListDTO[]>([]);
  const [scriptsLoading, setScriptsLoading] = useState(true);
  const [selectedScriptId, setSelectedScriptId] = useState<string | null>(null);
  const [permissions, setPermissions] = useState<ScriptPermissionDTO[]>([]);
  const [permissionsLoading, setPermissionsLoading] = useState(false);
  const [savingPermUserId, setSavingPermUserId] = useState<string | null>(null);

  // Invite external user
  const [showInviteExtPopover, setShowInviteExtPopover] = useState(false);
  const [extUserId, setExtUserId] = useState("");
  const [extPermType, setExtPermType] = useState<ScriptPermissionType>("VIEW");
  const [isInvitingExt, setIsInvitingExt] = useState(false);

  // ── Derived ────────────────────────────────────────────────────────────────

  const quotaMap = useMemo(() => {
    const map = new Map<string, QuotaDTO>();
    quotas.forEach((q) => map.set(q.userId, q));
    return map;
  }, [quotas]);

  const permissionMap = useMemo(
    () => new Map(permissions.map((p) => [p.userId, p])),
    [permissions]
  );

  const isCreator = currentWorkspace?.myRole === "CREATOR";
  const isAdmin = currentWorkspace?.myRole === "ADMIN" || isCreator;

  const filteredMembers = members.filter(
    (member) =>
      (member.nickname || "").toLowerCase().includes(searchQuery.toLowerCase()) ||
      member.username.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const membersByRole: Record<WorkspaceRole, WorkspaceMemberDTO[]> = {
    CREATOR: filteredMembers.filter((m) => m.role === "CREATOR"),
    ADMIN: filteredMembers.filter((m) => m.role === "ADMIN"),
    MEMBER: filteredMembers.filter((m) => m.role === "MEMBER"),
    GUEST: filteredMembers.filter((m) => m.role === "GUEST"),
  };

  const activeInvitations = invitations.filter((inv) => inv.status === 0);
  const usedInvitations = invitations.filter((inv) => inv.status !== 0);

  // External collaborators: have script permission but not workspace members
  const externalCollaborators = useMemo(
    () => permissions.filter((p) => !members.some((m) => m.userId === p.userId)),
    [permissions, members]
  );

  const isExpired = (expiresAt: string | null) => {
    if (!expiresAt) return false;
    return new Date(expiresAt) < new Date();
  };

  // ── Data Loading ───────────────────────────────────────────────────────────

  useEffect(() => {
    if (!currentWorkspaceId) return;
    const loadMembers = async () => {
      try {
        setMembersLoading(true);
        const response = await workspaceService.getMembers({ current: 1, size: 100 });
        setMembers(response.records);
      } catch (error) {
        console.error("Failed to load members:", error);
        toast.danger(getErrorFromException(error, locale));
      } finally {
        setMembersLoading(false);
      }
    };
    loadMembers();
  }, [currentWorkspaceId]);

  useEffect(() => {
    if (!currentWorkspaceId) return;
    const loadInvitations = async () => {
      try {
        setInvitationsLoading(true);
        const [invitationsResponse, personalCode] = await Promise.all([
          workspaceService.getInvitations({ current: 1, size: 50 }),
          inviteService.getMyInviteCode().catch(() => null),
        ]);
        setInvitations(invitationsResponse.records);
        setPersonalInviteCode(personalCode);
      } catch (error) {
        console.error("Failed to load invitations:", error);
        toast.danger(getErrorFromException(error, locale));
      } finally {
        setInvitationsLoading(false);
      }
    };
    loadInvitations();
  }, [currentWorkspaceId]);

  useEffect(() => {
    if (!currentWorkspaceId) return;
    const loadQuotas = async () => {
      try {
        setQuotasLoading(true);
        const quotasResponse = await walletService.getAllQuotas();
        setQuotas(quotasResponse);
      } catch (error) {
        console.error("Failed to load quotas:", error);
        toast.danger(getErrorFromException(error, locale));
      } finally {
        setQuotasLoading(false);
      }
    };
    loadQuotas();
  }, [currentWorkspaceId]);

  useEffect(() => {
    if (!currentWorkspaceId) return;
    const loadScripts = async () => {
      try {
        setScriptsLoading(true);
        const data = await projectService.getScripts();
        setScripts(data);
      } catch (error) {
        console.error("Failed to load scripts:", error);
        toast.danger(getErrorFromException(error, locale));
      } finally {
        setScriptsLoading(false);
      }
    };
    loadScripts();
  }, [currentWorkspaceId]);

  useEffect(() => {
    if (!selectedScriptId) {
      setPermissions([]);
      return;
    }
    const loadPermissions = async () => {
      try {
        setPermissionsLoading(true);
        const data = await scriptPermissionService.getPermissions(selectedScriptId);
        setPermissions(data);
      } catch (error) {
        console.error("Failed to load permissions:", error);
        toast.danger(getErrorFromException(error, locale));
      } finally {
        setPermissionsLoading(false);
      }
    };
    loadPermissions();
  }, [selectedScriptId]);

  // ── Handlers ───────────────────────────────────────────────────────────────

  const getInviteLink = (invitation: WorkspaceInvitationDTO) => {
    const personalCode = personalInviteCode?.code || "";
    const params = new URLSearchParams();
    params.set("workspace_code", invitation.code);
    if (personalCode) params.set("invite_code", personalCode);
    return `${window.location.origin}/${locale}/invite?${params.toString()}`;
  };

  const handleCreateInvitation = async () => {
    if (!currentWorkspaceId) return;
    try {
      setIsCreatingInvitation(true);
      const emails = inviteEmails.split(/[,\n]/).map((e) => e.trim()).filter(Boolean);
      const data: CreateInvitationRequestDTO = {
        role: inviteRole,
        emails: emails.length > 0 ? emails : undefined,
        maxUses: inviteMaxUses,
        expireHours: inviteExpireHours,
      };
      const newInvitation = await workspaceService.createInvitation(data);
      setInvitations([newInvitation, ...invitations]);
      setShowCreateInvitation(false);
      setInviteEmails("");
      setInviteRole("MEMBER");
    } catch (error) {
      console.error("Failed to create invitation:", error);
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsCreatingInvitation(false);
    }
  };

  const handleDisableInvitation = async (invitationId: string) => {
    if (!currentWorkspaceId) return;
    try {
      setDisablingInvId(invitationId);
      await workspaceService.disableInvitation(invitationId);
      setInvitations(invitations.map((inv) => (inv.id === invitationId ? { ...inv, status: 3 } : inv)));
    } catch (error) {
      console.error("Failed to disable invitation:", error);
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setDisablingInvId(null);
    }
  };

  const handleRemoveMember = async () => {
    if (!currentWorkspaceId || !memberToRemove) return;
    try {
      setIsRemovingMember(true);
      await workspaceService.removeMember(memberToRemove.id);
      setMembers((prev) => prev.filter((member) => member.id !== memberToRemove.id));
      toast.success("成员已移除");
      setMemberToRemove(null);
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsRemovingMember(false);
    }
  };

  // Quota management
  const handleOpenQuotaModal = (member: WorkspaceMemberDTO) => {
    const existingQuota = quotaMap.get(member.userId);
    setSelectedMemberForQuota(member);
    if (existingQuota) {
      setIsUnlimited(existingQuota.limitAmount === -1);
      setQuotaLimit(existingQuota.limitAmount === -1 ? 10000 : existingQuota.limitAmount);
      setQuotaResetCycle(existingQuota.resetCycle);
    } else {
      setIsUnlimited(true);
      setQuotaLimit(10000);
      setQuotaResetCycle("MONTHLY");
    }
    setShowQuotaModal(true);
  };

  const handleSaveQuota = async () => {
    if (!currentWorkspaceId || !selectedMemberForQuota) return;
    setIsSavingQuota(true);
    try {
      const data: SetQuotaRequestDTO = {
        limitAmount: isUnlimited ? -1 : quotaLimit,
        resetCycle: quotaResetCycle,
      };
      const updatedQuota = await walletService.setQuota(selectedMemberForQuota.userId, data);
      setQuotas((prev) => {
        const existing = prev.find((q) => q.userId === selectedMemberForQuota.userId);
        if (existing) return prev.map((q) => (q.userId === selectedMemberForQuota.userId ? updatedQuota : q));
        return [...prev, updatedQuota];
      });
      toast.success("配额设置已保存");
      setShowQuotaModal(false);
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsSavingQuota(false);
    }
  };

  const handleResetQuota = async (userId: string) => {
    if (!currentWorkspaceId) return;
    if (!confirm("确定要重置该成员的已用配额吗？")) return;
    try {
      setResettingQuotaUserId(userId);
      const updatedQuota = await walletService.resetQuota(userId);
      setQuotas((prev) => prev.map((q) => (q.userId === userId ? updatedQuota : q)));
      toast.success("配额已重置");
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setResettingQuotaUserId(null);
    }
  };

  // Permission change (inline)
  const handlePermissionChange = useCallback(async (userId: string, newType: ScriptPermissionType | "") => {
    if (!selectedScriptId) return;

    const existing = permissionMap.get(userId);
    if (existing?.grantSource === "SCRIPT_OWNER") {
      toast.warning(tPerm("ownerProtected"));
      return;
    }

    try {
      setSavingPermUserId(userId);
      if (newType === "") {
        if (!existing) return;
        await scriptPermissionService.revokePermission(selectedScriptId, userId);
        setPermissions((prev) => prev.filter((p) => p.userId !== userId));
        toast.success("权限已撤销");
      } else {
        const data: GrantScriptPermissionRequest = { userId, permissionType: newType };
        const newPermission = await scriptPermissionService.grantPermission(selectedScriptId, data);
        setPermissions((prev) => {
          const next = prev.filter((p) => p.userId !== newPermission.userId);
          return [...next, newPermission];
        });
        toast.success(`已设为${permissionTypeConfig[newType].label}权限`);
      }
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setSavingPermUserId(null);
    }
  }, [selectedScriptId, permissionMap, locale, tPerm]);

  // Invite external collaborator
  const handleInviteExternal = async () => {
    if (!selectedScriptId || !extUserId.trim()) return;
    try {
      setIsInvitingExt(true);
      const data: InviteCollaboratorRequest = { userId: extUserId.trim(), permissionType: extPermType };
      const newPerm = await scriptPermissionService.inviteCollaborator(selectedScriptId, data);
      setPermissions((prev) => [...prev, newPerm]);
      setExtUserId("");
      setExtPermType("VIEW");
      setShowInviteExtPopover(false);
      toast.success("协作者已邀请");
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsInvitingExt(false);
    }
  };

  // ── Loading State ──────────────────────────────────────────────────────────

  const isLoading = membersLoading || invitationsLoading || quotasLoading;

  if (isLoading) {
    return (
      <div className="flex h-full flex-col">
        <div className="flex shrink-0 items-center justify-between gap-3 pb-4">
          <Skeleton className="h-8 w-64 rounded-md" />
          <div className="flex gap-2">
            <Skeleton className="h-8 w-36 rounded-md" />
            <Skeleton className="h-8 w-28 rounded-md" />
          </div>
        </div>
        <div className="min-h-0 flex-1 space-y-4 overflow-auto">
          <Skeleton className="h-32 w-full rounded-xl" />
          <Skeleton className="h-48 w-full rounded-xl" />
          <Skeleton className="h-32 w-full rounded-xl" />
        </div>
      </div>
    );
  }

  // ── Sub-components ─────────────────────────────────────────────────────────

  const InvitationCard = ({ invitation, isActive: isActiveInv }: { invitation: WorkspaceInvitationDTO; isActive: boolean }) => {
    const config = statusConfig[invitation.status] || statusConfig[0];
    const roleLabel = invitation.role === "ADMIN" ? "管理员" : "成员";

    return (
      <div className={`rounded-md p-2 transition-all ${isActiveInv ? "bg-surface-secondary" : "bg-surface opacity-60"}`}>
        <div className="flex items-center justify-between">
          <code className="rounded bg-surface px-1.5 py-0.5 font-mono text-xs text-foreground">
            {invitation.code}
          </code>
          <div className="flex items-center gap-1">
            <CopyButton text={invitation.code} />
            <CopyButton text={getInviteLink(invitation)} label="链接" />
            {isActiveInv && isAdmin && (
              <button
                onClick={() => handleDisableInvitation(invitation.id)}
                disabled={disablingInvId === invitation.id}
                className="rounded p-1 text-muted hover:bg-surface hover:text-danger disabled:opacity-50"
              >
                <X className={`size-3 ${disablingInvId === invitation.id ? "animate-spin" : ""}`} />
              </button>
            )}
          </div>
        </div>
        <div className="mt-1 flex items-center justify-between text-xs text-muted">
          <span>{roleLabel} · {invitation.usedCount}/{invitation.maxUses}次</span>
          <StatusBadge variant={config.variant}>{config.label}</StatusBadge>
        </div>
      </div>
    );
  };

  // Inline permission select for a member row
  const PermissionSelect = ({ userId, perm }: { userId: string; perm: ScriptPermissionDTO | undefined }) => {
    const isOwner = perm?.grantSource === "SCRIPT_OWNER";
    const isSaving = savingPermUserId === userId;
    const expired = perm ? isExpired(perm.expiresAt) : false;
    const currentValue = perm ? perm.permissionType : "";

    if (!selectedScriptId) return null;

    if (permissionsLoading) {
      return <Skeleton className="h-8 w-24 rounded-lg" />;
    }

    if (isOwner) {
      return (
        <div className="flex items-center gap-1 rounded-md bg-warning/10 px-2 py-1">
          <Lock className="size-3 text-warning" />
          <Chip size="sm" variant="soft" color="warning">{permissionTypeConfig[perm!.permissionType].label}</Chip>
        </div>
      );
    }

    return (
      <div className="flex items-center gap-1.5">
        {isSaving && <Loader2 className="size-3.5 animate-spin text-accent" />}
        {expired && (
          <Tooltip delay={0}>
            <Tooltip.Trigger>
              <Clock className="size-3.5 text-danger" />
            </Tooltip.Trigger>
            <Tooltip.Content>
              <span className="text-xs">{tPerm("expired")}</span>
            </Tooltip.Content>
          </Tooltip>
        )}
        <Select
          aria-label="权限"
          className="w-24"
          variant="secondary"
          placeholder="无权限"
          value={currentValue}
          onChange={(value) => handlePermissionChange(userId, (value as ScriptPermissionType | ""))}
          isDisabled={isSaving}
        >
          <Select.Trigger className="h-7 text-xs">
            <Select.Value />
            <Select.Indicator />
          </Select.Trigger>
          <Select.Popover>
            <ListBox>
              <ListBox.Item id="" textValue="无权限">
                <span className="text-muted">无权限</span>
                <ListBox.ItemIndicator />
              </ListBox.Item>
              {(["VIEW", "EDIT", "ADMIN"] as ScriptPermissionType[]).map((type) => (
                <ListBox.Item key={type} id={type} textValue={permissionTypeConfig[type].label}>
                  {permissionTypeConfig[type].label}
                  <ListBox.ItemIndicator />
                </ListBox.Item>
              ))}
            </ListBox>
          </Select.Popover>
        </Select>
      </div>
    );
  };

  // Member card component
  const MemberCard = ({ member, showRemove }: { member: WorkspaceMemberDTO; showRemove: boolean }) => {
    const config = roleConfig[member.role];
    const Icon = config.icon;
    const quota = quotaMap.get(member.userId);
    const hasQuota = quota && quota.limitAmount !== -1;
    const usagePercent = hasQuota ? Math.min(quota.usagePercentage, 100) : 0;
    const isOverLimit = hasQuota && quota.usagePercentage >= 100;
    const perm = permissionMap.get(member.userId);

    const hasActions = isAdmin || showRemove;

    return (
      <div className="flex flex-col rounded-xl border border-border bg-surface p-3 transition-all hover:border-accent/50">
        {/* Top: Avatar + Info + Actions */}
        <div className="flex items-start gap-3">
          <Avatar className="size-10 shrink-0">
            <Avatar.Image src={member.avatar || ""} alt={member.nickname || member.username} />
            <Avatar.Fallback className="text-xs">
              {(member.nickname || member.username).charAt(0).toUpperCase()}
            </Avatar.Fallback>
          </Avatar>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-1.5">
              <span className="truncate text-sm font-medium text-foreground">
                {member.nickname || member.username}
              </span>
              {member.role !== "MEMBER" && member.role !== "GUEST" && (
                <Icon className={`size-3.5 shrink-0 ${config.color}`} />
              )}
            </div>
            <div className="flex items-center gap-1.5">
              <span className="text-xs text-muted">@{member.username}</span>
              <Chip size="sm" variant="soft" color={
                member.role === "CREATOR" ? "warning" :
                member.role === "ADMIN" ? "accent" :
                "default"
              }>
                {config.label}
              </Chip>
            </div>
          </div>
          {/* Actions — top right */}
          {hasActions && (
            <div className="flex shrink-0 items-center gap-0.5">
              {isAdmin && (
                <Tooltip delay={0}>
                  <Button variant="ghost" size="sm" isIconOnly className="size-6" onPress={() => handleOpenQuotaModal(member)}>
                    <Coins className="size-3 text-accent" />
                  </Button>
                  <Tooltip.Content>配额设置</Tooltip.Content>
                </Tooltip>
              )}
              {isAdmin && quota && quota.usedAmount > 0 && (
                <Tooltip delay={0}>
                  <button
                    onClick={() => handleResetQuota(member.userId)}
                    disabled={resettingQuotaUserId === member.userId}
                    className="rounded p-1 text-muted hover:bg-surface-secondary hover:text-accent disabled:opacity-50"
                  >
                    <RotateCcw className={`size-3 ${resettingQuotaUserId === member.userId ? "animate-spin" : ""}`} />
                  </button>
                  <Tooltip.Content>重置配额</Tooltip.Content>
                </Tooltip>
              )}
              {showRemove && (
                <Button variant="ghost" size="sm" isIconOnly className="size-6" onPress={() => setMemberToRemove(member)}>
                  <X className="size-3 text-danger" />
                </Button>
              )}
            </div>
          )}
        </div>

        {/* Quota */}
        {quota && (
          <div className="mt-2.5 flex items-center gap-1.5">
            <Coins className="size-3 shrink-0 text-muted" />
            {hasQuota ? (
              <>
                <div className="flex-1">
                  <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-tertiary">
                    <div
                      className={`h-full rounded-full transition-all ${
                        isOverLimit ? "bg-danger" : usagePercent > 80 ? "bg-warning" : "bg-accent"
                      }`}
                      style={{ width: `${usagePercent}%` }}
                    />
                  </div>
                </div>
                <span className={`text-[10px] font-medium tabular-nums ${isOverLimit ? "text-danger" : "text-muted"}`}>
                  {quota.usedAmount.toLocaleString()}/{quota.limitAmount.toLocaleString()}
                </span>
              </>
            ) : (
              <span className="flex items-center gap-0.5 text-[10px] text-muted">
                <Infinity className="size-3" /> 无限制
              </span>
            )}
          </div>
        )}

        {/* Script Permission */}
        {selectedScriptId && (
          <div className="mt-2">
            <PermissionSelect userId={member.userId} perm={perm} />
          </div>
        )}
      </div>
    );
  };

  // Role section header + card grid
  const RoleSection = ({
    role,
    members: roleMembers,
    showRemove,
  }: {
    role: WorkspaceRole;
    members: WorkspaceMemberDTO[];
    showRemove: boolean;
  }) => {
    if (roleMembers.length === 0) return null;
    const config = roleConfig[role];
    const Icon = config.icon;

    return (
      <div>
        <div className="mb-2 flex items-center gap-2">
          <Icon className={`size-3.5 ${config.color}`} />
          <span className="text-xs font-medium text-muted">{config.label}</span>
          <span className="text-[10px] text-muted">({roleMembers.length})</span>
        </div>
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-3 xl:grid-cols-4">
          {roleMembers.map((member) => (
            <MemberCard
              key={member.id}
              member={member}
              showRemove={showRemove && role !== "CREATOR"}
            />
          ))}
        </div>
      </div>
    );
  };

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <>
      <div className="flex h-full flex-col">
        {/* Toolbar */}
        <div className="flex shrink-0 items-center justify-between gap-3 pb-4">
          {/* Left: Search + Script selector */}
          <div className="flex items-center gap-2">
            <SearchField
              aria-label="搜索成员"
              value={searchQuery}
              onChange={setSearchQuery}
              variant="secondary"
            >
              <SearchField.Group>
                <SearchField.SearchIcon />
                <SearchField.Input className="w-36" placeholder="搜索成员..." />
                <SearchField.ClearButton />
              </SearchField.Group>
            </SearchField>
            <Select
              aria-label="选择剧本查看权限"
              className="w-44 shrink-0"
              placeholder="选择剧本"
              variant="secondary"
              value={selectedScriptId}
              onChange={(value) => setSelectedScriptId((value as string) || null)}
              isDisabled={scriptsLoading}
            >
              <Select.Trigger className="text-xs">
                <Select.Value />
                <Select.Indicator />
              </Select.Trigger>
              <Select.Popover>
                <ListBox>
                  {scripts.map((script) => (
                    <ListBox.Item key={script.id} id={script.id} textValue={script.title}>
                      {script.title}
                      <ListBox.ItemIndicator />
                    </ListBox.Item>
                  ))}
                </ListBox>
              </Select.Popover>
            </Select>
          </div>

          {/* Right: External invite + Invite + Refresh */}
          <div className="flex items-center gap-2">
            {selectedScriptId && (
              <Popover isOpen={showInviteExtPopover} onOpenChange={setShowInviteExtPopover}>
                <Popover.Trigger>
                  <Button variant="secondary" size="sm" className="shrink-0 gap-1 text-xs">
                    <UserPlus className="size-3.5" />
                    外部协作
                  </Button>
                </Popover.Trigger>
                <Popover.Content placement="bottom end" className="w-64 p-0">
                  <Popover.Dialog className="p-3">
                    <div className="space-y-2.5">
                      <div>
                        <label className="mb-1 block text-xs font-medium text-foreground">用户 ID</label>
                        <input
                          type="text"
                          value={extUserId}
                          onChange={(e) => setExtUserId(e.target.value)}
                          onKeyDown={(e) => e.key === "Enter" && handleInviteExternal()}
                          placeholder="输入用户 ID"
                          className="w-full rounded-lg border border-border bg-surface px-3 py-1.5 text-sm text-foreground placeholder:text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                        />
                      </div>
                      <div className="flex gap-1">
                        {(["VIEW", "EDIT", "ADMIN"] as ScriptPermissionType[]).map((type) => (
                          <button
                            key={type}
                            onClick={() => setExtPermType(type)}
                            className={`flex-1 rounded-md border px-2 py-1 text-xs font-medium transition-colors ${
                              extPermType === type
                                ? "border-accent bg-accent/10 text-accent"
                                : "border-border text-muted hover:border-accent/50"
                            }`}
                          >
                            {permissionTypeConfig[type].label}
                          </button>
                        ))}
                      </div>
                      <Button
                        size="sm"
                        className="w-full"
                        onPress={handleInviteExternal}
                        isDisabled={!extUserId.trim()}
                        isPending={isInvitingExt}
                      >
                        {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}邀请</>)}
                      </Button>
                    </div>
                  </Popover.Dialog>
                </Popover.Content>
              </Popover>
            )}

            {isAdmin && (
              <Button size="sm" onPress={() => setShowCreateInvitation(true)}>
                <UserPlus className="size-4" />
                邀请成员
              </Button>
            )}

            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              onPress={() => {
                if (!currentWorkspaceId) return;
                workspaceService.getMembers({ current: 1, size: 100 }).then((r) => setMembers(r.records));
              }}
            >
              <RefreshCw className="size-4" />
            </Button>
          </div>
        </div>

        {/* Content: scrollable area */}
        <div className="min-h-0 flex-1 overflow-auto">
          <div className="grid gap-4" style={{ gridTemplateColumns: "1fr 280px" }}>
            {/* Left: Members grid */}
            <div className="space-y-5">
              <RoleSection role="CREATOR" members={membersByRole.CREATOR} showRemove={false} />
              <RoleSection role="ADMIN" members={membersByRole.ADMIN} showRemove={isCreator} />
              <RoleSection role="MEMBER" members={membersByRole.MEMBER} showRemove={isAdmin} />
              <RoleSection role="GUEST" members={membersByRole.GUEST} showRemove={isAdmin} />

              {filteredMembers.length === 0 && (
                <div className="py-8 text-center">
                  <Users className="mx-auto size-10 text-muted/30" />
                  <p className="mt-3 text-sm font-medium text-foreground">
                    {searchQuery ? "没有找到匹配的成员" : "暂无成员"}
                  </p>
                </div>
              )}

              {/* External collaborators */}
              {externalCollaborators.length > 0 && (
                <div>
                  <div className="mb-2 flex items-center gap-2">
                    <UserPlus className="size-3.5 text-muted" />
                    <span className="text-xs font-medium text-muted">外部协作者</span>
                    <span className="text-[10px] text-muted">({externalCollaborators.length})</span>
                  </div>
                  <div className="grid grid-cols-2 gap-3 lg:grid-cols-3 xl:grid-cols-4">
                    {externalCollaborators.map((perm) => {
                      const isOwner = perm.grantSource === "SCRIPT_OWNER";
                      const isSaving = savingPermUserId === perm.userId;
                      const expired = isExpired(perm.expiresAt);

                      return (
                        <div
                          key={perm.userId}
                          className="group relative flex flex-col rounded-xl border border-dashed border-border bg-surface p-3 transition-all hover:border-accent/50"
                        >
                          <div className="flex items-start gap-3">
                            <Avatar className="size-10 shrink-0">
                              <Avatar.Image src={perm.avatar || ""} alt={perm.nickname || perm.username || ""} />
                              <Avatar.Fallback className="text-xs">
                                {(perm.nickname || perm.username || "?").charAt(0).toUpperCase()}
                              </Avatar.Fallback>
                            </Avatar>
                            <div className="min-w-0 flex-1">
                              <div className="flex items-center gap-1.5">
                                <span className="truncate text-sm font-medium text-foreground">
                                  {perm.nickname || perm.username || perm.userId}
                                </span>
                              </div>
                              {perm.username && (
                                <span className="text-xs text-muted">@{perm.username}</span>
                              )}
                            </div>
                            <Chip size="sm" variant="soft" color="default" className="shrink-0">外部</Chip>
                          </div>
                          <div className="mt-2 flex items-center gap-1.5">
                            {isSaving && <Loader2 className="size-3.5 animate-spin text-accent" />}
                            {expired && <Clock className="size-3.5 text-danger" />}
                            {isOwner ? (
                              <div className="flex items-center gap-1 rounded-md bg-warning/10 px-2 py-1">
                                <Lock className="size-3 text-warning" />
                                <Chip size="sm" variant="soft" color="warning">
                                  {permissionTypeConfig[perm.permissionType].label}
                                </Chip>
                              </div>
                            ) : (
                              <Select
                                aria-label="权限"
                                className="w-full"
                                variant="secondary"
                                placeholder="无权限"
                                value={perm.permissionType}
                                onChange={(value) => handlePermissionChange(perm.userId, (value as ScriptPermissionType | ""))}
                                isDisabled={isSaving}
                              >
                                <Select.Trigger className="h-7 text-xs">
                                  <Select.Value />
                                  <Select.Indicator />
                                </Select.Trigger>
                                <Select.Popover>
                                  <ListBox>
                                    <ListBox.Item id="" textValue="无权限">
                                      <span className="text-muted">无权限</span>
                                      <ListBox.ItemIndicator />
                                    </ListBox.Item>
                                    {(["VIEW", "EDIT", "ADMIN"] as ScriptPermissionType[]).map((type) => (
                                      <ListBox.Item key={type} id={type} textValue={permissionTypeConfig[type].label}>
                                        {permissionTypeConfig[type].label}
                                        <ListBox.ItemIndicator />
                                      </ListBox.Item>
                                    ))}
                                  </ListBox>
                                </Select.Popover>
                              </Select>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* Permission info footer (only when script selected) */}
              {selectedScriptId && (
                <div className="flex items-center gap-2 rounded-lg bg-surface-secondary px-3 py-2 text-[11px] text-muted">
                  <AlertCircle className="size-3 shrink-0 text-accent" />
                  <span>
                    <strong className="text-foreground">查看</strong> 只读 ·{" "}
                    <strong className="text-foreground">编辑</strong> 可修改 ·{" "}
                    <strong className="text-foreground">管理</strong> 完全控制
                  </span>
                </div>
              )}
            </div>

            {/* Right: Stats & Invitations */}
            <div className="space-y-3">
              {/* Member count card - compact, wallet-style */}
              <Card className="w-full" variant="secondary">
                <Card.Content className="p-3">
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-muted">成员总数</span>
                    <div className="flex size-7 items-center justify-center rounded-lg bg-accent/10">
                      <Users className="size-3.5 text-accent" />
                    </div>
                  </div>
                  <p className="mt-1.5 text-2xl font-bold text-foreground">{members.length}</p>
                  <p className="mt-1 text-xs text-muted">
                    上限 {currentWorkspace?.maxMembers || "∞"}
                  </p>
                </Card.Content>
              </Card>

              {/* Role breakdown - compact stats */}
              <Card className="w-full" variant="secondary">
                <Card.Content className="p-3">
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-muted">角色分布</span>
                    <div className="flex size-7 items-center justify-center rounded-lg bg-muted/10">
                      <Shield className="size-3.5 text-muted" />
                    </div>
                  </div>
                  <div className="mt-3 space-y-1.5">
                  {([
                    ["CREATOR", "创建者", "text-warning", membersByRole.CREATOR.length],
                    ["ADMIN", "管理员", "text-accent", membersByRole.ADMIN.length],
                    ["MEMBER", "成员", "text-foreground", membersByRole.MEMBER.length],
                    ...(membersByRole.GUEST.length > 0 ? [["GUEST", "访客", "text-muted", membersByRole.GUEST.length] as const] : []),
                  ] as const).map(([key, label, color, count]) => (
                    <div key={key} className="flex items-center justify-between rounded-md bg-surface-secondary px-3 py-1.5 text-xs">
                      <span className="text-muted">{label}</span>
                      <span className={`font-medium ${color}`}>{count}</span>
                    </div>
                  ))}
                  </div>
                </Card.Content>
              </Card>

              {/* Active invitations card */}
              {isAdmin && (
                <Card className="w-full" variant="secondary">
                  <Card.Content className="p-3">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <Ticket className="size-4 text-accent" />
                        <span className="text-sm font-medium text-foreground">邀请码</span>
                        <span className="rounded-full bg-accent/10 px-1.5 py-0.5 text-[10px] font-medium text-accent">
                          {activeInvitations.length}
                        </span>
                      </div>
                      <Button variant="ghost" size="sm" isIconOnly onPress={() => setShowCreateInvitation(true)}>
                        <Plus className="size-4" />
                      </Button>
                    </div>
                    {invitations.length === 0 ? (
                      <p className="mt-3 text-center text-xs text-muted">暂无邀请码</p>
                    ) : (
                      <div className="mt-3 space-y-1.5">
                        {activeInvitations.slice(0, 3).map((inv) => (
                          <InvitationCard key={inv.id} invitation={inv} isActive />
                        ))}
                        {activeInvitations.length > 3 && (
                          <p className="text-center text-[11px] text-muted">
                            还有 {activeInvitations.length - 3} 个有效邀请码
                          </p>
                        )}
                        {usedInvitations.length > 0 && (
                          <div className="border-t border-border pt-1.5">
                            {usedInvitations.slice(0, 2).map((inv) => (
                              <InvitationCard key={inv.id} invitation={inv} isActive={false} />
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </Card.Content>
                </Card>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Create Invitation Modal */}
      <Modal.Backdrop isOpen={showCreateInvitation} onOpenChange={setShowCreateInvitation}>
        <Modal.Container size="sm">
          <Modal.Dialog>
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>创建邀请</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="space-y-4">
              <div>
                <label className="mb-1.5 block text-sm font-medium text-foreground">邀请角色</label>
                <div className="flex gap-2">
                  {(["MEMBER", "ADMIN"] as const).map((role) => (
                    <button
                      key={role}
                      onClick={() => setInviteRole(role)}
                      className={`flex-1 rounded-lg border px-4 py-2.5 text-sm font-medium transition-colors ${
                        inviteRole === role
                          ? "border-accent bg-accent/10 text-accent"
                          : "border-border bg-surface text-muted hover:border-accent/50"
                      }`}
                    >
                      {role === "MEMBER" ? "成员" : "管理员"}
                    </button>
                  ))}
                </div>
              </div>

              <TextAreaField
                label="邮箱地址（可选）"
                value={inviteEmails}
                onChange={setInviteEmails}
                placeholder="user@example.com, user2@example.com"
                rows={2}
                hint="多个邮箱用逗号分隔，留空则创建通用邀请码"
              />

              <div className="grid grid-cols-2 gap-4">
                <FormField
                  label="最大使用次数"
                  type="number"
                  value={inviteMaxUses.toString()}
                  onChange={(v) => setInviteMaxUses(Number(v))}
                />
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-foreground">有效期</label>
                  <select
                    value={inviteExpireHours}
                    onChange={(e) => setInviteExpireHours(Number(e.target.value))}
                    className="w-full rounded-lg border border-border bg-surface px-3 py-2.5 text-sm text-foreground focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  >
                    <option value={24}>1 天</option>
                    <option value={72}>3 天</option>
                    <option value={168}>7 天</option>
                    <option value={720}>30 天</option>
                  </select>
                </div>
              </div>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" onPress={() => setShowCreateInvitation(false)}>
                取消
              </Button>
              <Button onPress={handleCreateInvitation} isPending={isCreatingInvitation}>
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}创建邀请</>)}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>

      {/* Remove Member Modal */}
      <Modal.Backdrop isOpen={!!memberToRemove} onOpenChange={(open) => { if (!open) setMemberToRemove(null); }}>
        <Modal.Container size="sm">
          <Modal.Dialog>
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>移除成员</Modal.Heading>
            </Modal.Header>
            <Modal.Body>
              {memberToRemove && (
                <div className="space-y-4">
                  <div className="flex items-start gap-3 rounded-lg bg-danger/5 p-3">
                    <AlertCircle className="mt-0.5 size-4 shrink-0 text-danger" />
                    <div className="space-y-1">
                      <p className="text-sm font-medium text-foreground">确定要移除该成员吗？</p>
                      <p className="text-sm text-muted">
                        {memberToRemove.nickname || memberToRemove.username} 将失去当前工作空间访问权限。
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3 rounded-lg bg-surface-secondary p-3">
                    <Avatar className="size-10 shrink-0">
                      <Avatar.Image src={memberToRemove.avatar || ""} alt={memberToRemove.nickname || memberToRemove.username} />
                      <Avatar.Fallback className="text-sm">
                        {(memberToRemove.nickname || memberToRemove.username).charAt(0).toUpperCase()}
                      </Avatar.Fallback>
                    </Avatar>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-foreground">
                        {memberToRemove.nickname || memberToRemove.username}
                      </p>
                      <p className="truncate text-xs text-muted">@{memberToRemove.username}</p>
                    </div>
                  </div>
                </div>
              )}
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" onPress={() => setMemberToRemove(null)}>取消</Button>
              <Button
                variant="primary"
                className="bg-danger text-danger-foreground hover:bg-danger/90"
                onPress={handleRemoveMember}
                isPending={isRemovingMember}
              >
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}确认移除</>)}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>

      {/* Quota Settings Modal */}
      <Modal.Backdrop isOpen={showQuotaModal} onOpenChange={setShowQuotaModal}>
        <Modal.Container size="sm">
          <Modal.Dialog>
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>配额设置</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="space-y-4">
              {selectedMemberForQuota && (
                <>
                  <div className="flex items-center gap-3 rounded-lg bg-surface-secondary p-3">
                    <Avatar className="size-10 shrink-0">
                      <Avatar.Image
                        src={selectedMemberForQuota.avatar || ""}
                        alt={selectedMemberForQuota.nickname || selectedMemberForQuota.username}
                      />
                      <Avatar.Fallback className="text-sm">
                        {(selectedMemberForQuota.nickname || selectedMemberForQuota.username).charAt(0).toUpperCase()}
                      </Avatar.Fallback>
                    </Avatar>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-foreground">
                        {selectedMemberForQuota.nickname || selectedMemberForQuota.username}
                      </p>
                      <p className="truncate text-xs text-muted">@{selectedMemberForQuota.username}</p>
                    </div>
                  </div>

                  {quotaMap.get(selectedMemberForQuota.userId) && (
                    <div className="rounded-lg border border-border bg-surface p-3">
                      <div className="flex items-center justify-between text-sm">
                        <span className="text-muted">当前已用</span>
                        <span className="font-medium text-foreground">
                          {quotaMap.get(selectedMemberForQuota.userId)?.usedAmount.toLocaleString()} 积分
                        </span>
                      </div>
                    </div>
                  )}

                  <div>
                    <label className="mb-1.5 block text-sm font-medium text-foreground">配额类型</label>
                    <div className="flex gap-2">
                      <button
                        onClick={() => setIsUnlimited(true)}
                        className={`flex-1 rounded-lg border px-4 py-2.5 text-sm font-medium transition-colors ${
                          isUnlimited
                            ? "border-accent bg-accent/10 text-accent"
                            : "border-border bg-surface text-muted hover:border-accent/50"
                        }`}
                      >
                        <Infinity className="mx-auto mb-1 size-4" />
                        无限制
                      </button>
                      <button
                        onClick={() => setIsUnlimited(false)}
                        className={`flex-1 rounded-lg border px-4 py-2.5 text-sm font-medium transition-colors ${
                          !isUnlimited
                            ? "border-accent bg-accent/10 text-accent"
                            : "border-border bg-surface text-muted hover:border-accent/50"
                        }`}
                      >
                        <Coins className="mx-auto mb-1 size-4" />
                        限额
                      </button>
                    </div>
                  </div>

                  {!isUnlimited && (
                    <FormField
                      label="配额上限（积分）"
                      type="number"
                      value={quotaLimit.toString()}
                      onChange={(v) => setQuotaLimit(Math.max(0, Number(v)))}
                    />
                  )}

                  <div>
                    <label className="mb-1.5 block text-sm font-medium text-foreground">重置周期</label>
                    <select
                      value={quotaResetCycle}
                      onChange={(e) => setQuotaResetCycle(e.target.value as ResetCycle)}
                      className="w-full rounded-lg border border-border bg-surface px-3 py-2.5 text-sm text-foreground focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                    >
                      <option value="DAILY">每日重置</option>
                      <option value="WEEKLY">每周重置</option>
                      <option value="MONTHLY">每月重置</option>
                      <option value="NEVER">永不重置</option>
                    </select>
                    <p className="mt-1.5 text-xs text-muted">
                      {quotaResetCycle === "NEVER"
                        ? "配额不会自动重置，需手动操作"
                        : `已用配额将在${resetCycleConfig[quotaResetCycle]}自动归零`}
                    </p>
                  </div>

                  {!isUnlimited && quotaLimit > 0 && (
                    <div className="flex items-start gap-2 rounded-lg bg-warning/10 p-3 text-xs text-warning">
                      <AlertCircle className="size-4 shrink-0" />
                      <span>当用户超出配额限制时，将无法使用 AI 生成功能，直到配额重置或管理员调整。</span>
                    </div>
                  )}
                </>
              )}
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" onPress={() => setShowQuotaModal(false)}>取消</Button>
              <Button onPress={handleSaveQuota} isPending={isSavingQuota}>
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}保存设置</>)}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </>
  );
}
