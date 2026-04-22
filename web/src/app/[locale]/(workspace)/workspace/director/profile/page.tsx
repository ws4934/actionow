"use client";

import { useState, useEffect } from "react";
import { useTranslations, useLocale } from "next-intl";
import { useRouter } from "next/navigation";
import { Button, Avatar, Modal, Skeleton, Card, Spinner, toast } from "@heroui/react";
import {
  Camera,
  Shield,
  LogOut,
  Github,
  Chrome,
  ChevronRight,
  Gift,
  Users,
  RefreshCw,
  Link as LinkIcon,
  Copy,
  Check,
  AlertTriangle,
} from "lucide-react";
import { authService, userService, inviteService, clearAuthTokens, resetApiClientSessionState, getErrorFromException } from "@/lib/api";
import { clearSessionCaches } from "@/lib/stores/session-cache";
import type {
  UserDTO,
  PersonalInviteCodeDTO,
  InvitedUserDTO,
  ChangePasswordRequestDTO,
  UpdateProfileRequestDTO,
} from "@/lib/api/dto";
import {
  PageContainer,
  PageHeader,
  TwoColumnLayout,
  MainColumn,
  SideColumn,
  ListRow,
  ListRowTitle,
  ListRowDescription,
  InlineEmptyState,
  FormField,
} from "@/components/director";

function CopyIconButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = async () => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <Button variant="tertiary" size="sm" isIconOnly onPress={handleCopy}>
      {copied ? <Check className="size-4 text-success" /> : <Copy className="size-4" />}
    </Button>
  );
}

export default function ProfilePage() {
  const t = useTranslations("workspace.director.profile");
  const locale = useLocale();
  const router = useRouter();
  const [user, setUser] = useState<UserDTO | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const [inviteCode, setInviteCode] = useState<PersonalInviteCodeDTO | null>(null);
  const [invitedUsers, setInvitedUsers] = useState<InvitedUserDTO[]>([]);
  const [inviteCodeLoading, setInviteCodeLoading] = useState(true);
  const [isRegenerating, setIsRegenerating] = useState(false);

  const [showEditProfile, setShowEditProfile] = useState(false);
  const [showChangePassword, setShowChangePassword] = useState(false);
  const [showInvitedUsers, setShowInvitedUsers] = useState(false);

  const [editNickname, setEditNickname] = useState("");
  const [isSavingProfile, setIsSavingProfile] = useState(false);

  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [isChangingPassword, setIsChangingPassword] = useState(false);
  const [passwordError, setPasswordError] = useState("");
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  useEffect(() => {
    const loadUser = async () => {
      try {
        const data = await authService.getCurrentUser();
        setUser(data);
        setEditNickname(data.nickname || "");
      } catch (error) {
        console.error("Failed to load user:", error);
        toast.danger(getErrorFromException(error, locale));
      } finally {
        setIsLoading(false);
      }
    };
    loadUser();
  }, []);

  useEffect(() => {
    const loadInviteCode = async () => {
      try {
        setInviteCodeLoading(true);
        const codeData = await inviteService.getMyInviteCode();
        setInviteCode(codeData);
      } catch (error) {
        console.error("Failed to load invite code:", error);
        toast.danger(getErrorFromException(error, locale));
      }

      try {
        const usersData = await inviteService.getInvitedUsers({ page: 1, size: 50 });
        setInvitedUsers(usersData.records || []);
      } catch (error) {
        console.error("Failed to load invited users:", error);
        toast.danger(getErrorFromException(error, locale));
      }

      setInviteCodeLoading(false);
    };
    loadInviteCode();
  }, []);

  const handleLogout = async () => {
    try {
      setIsLoggingOut(true);
      await authService.logout();
    } catch {
      // Ignore logout errors
    } finally {
      clearAuthTokens();
      clearSessionCaches();
      resetApiClientSessionState();
      router.replace(`/${locale}/login`);
    }
  };

  const handleCopyInviteLink = async () => {
    if (!inviteCode) return;
    const link = `${window.location.origin}/${locale}/register?code=${inviteCode.code}`;
    await navigator.clipboard.writeText(link);
  };

  const handleRegenerateCode = async () => {
    try {
      setIsRegenerating(true);
      const newCode = await inviteService.regenerateMyInviteCode();
      setInviteCode(newCode);
    } catch (error) {
      console.error("Failed to regenerate invite code:", error);
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsRegenerating(false);
    }
  };

  const handleSaveProfile = async () => {
    try {
      setIsSavingProfile(true);
      const data: UpdateProfileRequestDTO = { nickname: editNickname || undefined };
      const updatedUser = await userService.updateProfile(data);
      setUser(updatedUser);
      setShowEditProfile(false);
    } catch (error) {
      console.error("Failed to update profile:", error);
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsSavingProfile(false);
    }
  };

  const handleChangePassword = async () => {
    setPasswordError("");
    if (newPassword !== confirmPassword) {
      setPasswordError("两次输入的密码不一致");
      return;
    }
    if (newPassword.length < 6) {
      setPasswordError("密码长度不能少于6位");
      return;
    }
    try {
      setIsChangingPassword(true);
      const data: ChangePasswordRequestDTO = { oldPassword, newPassword };
      await userService.changePassword(data);
      setShowChangePassword(false);
      setOldPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch {
      setPasswordError("修改密码失败，请检查当前密码是否正确");
    } finally {
      setIsChangingPassword(false);
    }
  };

  if (isLoading) {
    return (
      <PageContainer>
        <div>
          <Skeleton className="h-8 w-32 rounded-md" />
          <Skeleton className="mt-2 h-4 w-56 rounded-md" />
        </div>
        <TwoColumnLayout>
          <MainColumn>
            <Skeleton className="h-40 w-full rounded-xl" />
            <Skeleton className="h-48 w-full rounded-xl" />
          </MainColumn>
          <SideColumn>
            <Skeleton className="h-32 w-full rounded-xl" />
            <Skeleton className="h-32 w-full rounded-xl" />
            <Skeleton className="h-20 w-full rounded-xl" />
          </SideColumn>
        </TwoColumnLayout>
      </PageContainer>
    );
  }

  return (
    <>
      <PageContainer>
        <PageHeader title={t("title")} description="管理你的个人信息和账号设置" />

        <TwoColumnLayout>
          {/* 左栏：主要内容 */}
          <MainColumn>
            {/* Profile Card */}
            <Card>
              <Card.Content>
                <div className="flex items-start gap-5">
                  <div className="relative shrink-0">
                    <Avatar className="size-20">
                      <Avatar.Image src={user?.avatar || ""} alt={user?.nickname || user?.username || ""} />
                      <Avatar.Fallback className="text-xl">
                        {(user?.nickname || user?.username)?.charAt(0).toUpperCase() || "U"}
                      </Avatar.Fallback>
                    </Avatar>
                    <Button
                      isIconOnly
                      size="sm"
                      variant="primary"
                      className="absolute -bottom-1 -right-1 size-7 rounded-full border-2 border-surface shadow-md"
                    >
                      <Camera className="size-3.5" />
                    </Button>
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between">
                      <div>
                        <h2 className="text-lg font-semibold text-foreground truncate">
                          {user?.nickname || user?.username || "用户"}
                        </h2>
                        <p className="text-sm text-muted">@{user?.username}</p>
                      </div>
                      <Button variant="secondary" size="sm" onPress={() => setShowEditProfile(true)}>
                        编辑资料
                      </Button>
                    </div>
                  </div>
                </div>
                {/* User Info Grid */}
                <div className="mt-4 grid grid-cols-2 gap-3">
                  <div className="rounded-lg bg-surface-secondary px-3 py-2">
                    <p className="text-xs text-muted">邮箱</p>
                    <p className="text-sm text-foreground truncate">
                      {user?.email || "未绑定"}
                      {user?.emailVerified && <span className="ml-1 text-xs text-success">✓</span>}
                    </p>
                  </div>
                  <div className="rounded-lg bg-surface-secondary px-3 py-2">
                    <p className="text-xs text-muted">手机</p>
                    <p className="text-sm text-foreground truncate">
                      {user?.phone || "未绑定"}
                      {user?.phoneVerified && <span className="ml-1 text-xs text-success">✓</span>}
                    </p>
                  </div>
                  <div className="rounded-lg bg-surface-secondary px-3 py-2">
                    <p className="text-xs text-muted">用户ID</p>
                    <p className="text-sm text-foreground font-mono">{user?.id?.slice(0, 8) || "-"}</p>
                  </div>
                  <div className="rounded-lg bg-surface-secondary px-3 py-2">
                    <p className="text-xs text-muted">注册时间</p>
                    <p className="text-sm text-foreground">
                      {user?.createdAt ? new Date(user.createdAt).toLocaleDateString("zh-CN") : "-"}
                    </p>
                  </div>
                </div>
              </Card.Content>
            </Card>

            {/* Account Security */}
            <Card>
              <Card.Header className="pb-2">
                <Card.Title className="text-sm flex items-center gap-2">
                  <Shield className="size-4 text-muted" />
                  {t("security")}
                </Card.Title>
              </Card.Header>
              <Card.Content className="p-0">
                <div className="divide-y divide-border">
                  <button
                    onClick={() => setShowChangePassword(true)}
                    className="flex w-full items-center justify-between px-4 py-3 text-left hover:bg-surface-secondary transition-colors"
                  >
                    <div>
                      <p className="text-sm font-medium text-foreground">{t("changePassword")}</p>
                      <p className="text-xs text-muted">定期更换密码以保护账号安全</p>
                    </div>
                    <ChevronRight className="size-4 text-muted" />
                  </button>
                  <div className="flex items-center justify-between px-4 py-3">
                    <div>
                      <p className="text-sm font-medium text-foreground">{t("bindEmail")}</p>
                      <p className="text-xs text-muted">
                        {user?.email || "未绑定"}
                        {user?.emailVerified && <span className="ml-1 text-success">· 已验证</span>}
                      </p>
                    </div>
                    <ChevronRight className="size-4 text-muted" />
                  </div>
                  <div className="flex items-center justify-between px-4 py-3">
                    <div>
                      <p className="text-sm font-medium text-foreground">{t("bindPhone")}</p>
                      <p className="text-xs text-muted">
                        {user?.phone || "未绑定"}
                        {user?.phoneVerified && <span className="ml-1 text-success">· 已验证</span>}
                      </p>
                    </div>
                    <ChevronRight className="size-4 text-muted" />
                  </div>
                </div>
              </Card.Content>
            </Card>
          </MainColumn>

          {/* 右栏：次要内容 */}
          <SideColumn>
            {/* Invite Code */}
            <Card className="border-accent/30">
              <Card.Header className="pb-2">
                <Card.Title className="text-sm flex items-center gap-2">
                  <Gift className="size-4 text-accent" />
                  我的邀请码
                </Card.Title>
              </Card.Header>
              <Card.Content className="pt-0">
                {inviteCodeLoading ? (
                  <div className="flex items-center justify-center py-4">
                    <div className="size-5 animate-spin rounded-full border-2 border-accent border-t-transparent" />
                  </div>
                ) : inviteCode ? (
                  <div className="space-y-3">
                    <div className="flex items-center gap-2">
                      <code className="flex-1 rounded-lg bg-surface-secondary px-3 py-2 font-mono text-sm font-semibold text-foreground text-center">
                        {inviteCode.code}
                      </code>
                      <CopyIconButton text={inviteCode.code} />
                      <Button variant="tertiary" size="sm" isIconOnly onPress={handleCopyInviteLink}>
                        <LinkIcon className="size-4" />
                      </Button>
                      <Button
                        variant="tertiary"
                        size="sm"
                        isIconOnly
                        onPress={handleRegenerateCode}
                        isDisabled={isRegenerating}
                      >
                        <RefreshCw className={`size-4 ${isRegenerating ? "animate-spin" : ""}`} />
                      </Button>
                    </div>
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-muted">
                        {inviteCode.maxUses <= 0 ? "无限次" : `剩余 ${inviteCode.remainingUses} / ${inviteCode.maxUses} 次`}
                      </span>
                      <button
                        onClick={() => setShowInvitedUsers(true)}
                        className="text-accent hover:underline"
                      >
                        已邀请 {inviteCode.totalInvited} 人
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="py-3 text-center">
                    <p className="text-xs text-muted">暂无邀请码</p>
                    <Button variant="secondary" size="sm" className="mt-2" onPress={handleRegenerateCode}>
                      生成邀请码
                    </Button>
                  </div>
                )}
              </Card.Content>
            </Card>

            {/* OAuth Bindings */}
            <Card>
              <Card.Header className="pb-2">
                <Card.Title className="text-sm">{t("oauthBindings")}</Card.Title>
              </Card.Header>
              <Card.Content className="pt-0 space-y-2">
                <div className="flex items-center justify-between rounded-lg bg-surface-secondary px-3 py-2">
                  <div className="flex items-center gap-2">
                    <div className="flex size-8 items-center justify-center rounded-md bg-[#24292e]">
                      <Github className="size-4 text-white" />
                    </div>
                    <div>
                      <p className="text-sm font-medium text-foreground">GitHub</p>
                      <p className="text-xs text-muted">
                        {user?.oauthProviders?.includes("github") ? "已绑定" : "未绑定"}
                      </p>
                    </div>
                  </div>
                  <Button
                    variant={user?.oauthProviders?.includes("github") ? "tertiary" : "secondary"}
                    size="sm"
                  >
                    {user?.oauthProviders?.includes("github") ? "解绑" : "绑定"}
                  </Button>
                </div>
                <div className="flex items-center justify-between rounded-lg bg-surface-secondary px-3 py-2">
                  <div className="flex items-center gap-2">
                    <div className="flex size-8 items-center justify-center rounded-md bg-white shadow-sm">
                      <Chrome className="size-4 text-[#4285F4]" />
                    </div>
                    <div>
                      <p className="text-sm font-medium text-foreground">Google</p>
                      <p className="text-xs text-muted">
                        {user?.oauthProviders?.includes("google") ? "已绑定" : "未绑定"}
                      </p>
                    </div>
                  </div>
                  <Button
                    variant={user?.oauthProviders?.includes("google") ? "tertiary" : "secondary"}
                    size="sm"
                  >
                    {user?.oauthProviders?.includes("google") ? "解绑" : "绑定"}
                  </Button>
                </div>
              </Card.Content>
            </Card>

            {/* Logout */}
            <Card className="border-danger/30">
              <Card.Header className="flex-row items-center gap-3 pb-2">
                <AlertTriangle className="size-4 text-danger" />
                <Card.Title className="text-sm text-danger">退出登录</Card.Title>
              </Card.Header>
              <Card.Content className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-foreground">退出当前账号</p>
                  <p className="text-xs text-muted">退出后需要重新登录</p>
                </div>
                <Button variant="danger" size="sm" onPress={handleLogout} isPending={isLoggingOut}>
                  {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <LogOut className="mr-1.5 size-3.5" />}退出</>)}
                </Button>
              </Card.Content>
            </Card>
          </SideColumn>
        </TwoColumnLayout>
      </PageContainer>

      {/* Edit Profile Modal */}
      <Modal.Backdrop isOpen={showEditProfile} onOpenChange={setShowEditProfile}>
        <Modal.Container size="sm">
          <Modal.Dialog>
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>编辑资料</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="space-y-4">
              <FormField label="昵称" value={editNickname} onChange={setEditNickname} placeholder="输入昵称" />
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" onPress={() => setShowEditProfile(false)}>取消</Button>
              <Button variant="primary" onPress={handleSaveProfile} isPending={isSavingProfile}>
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}保存</>)}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>

      {/* Change Password Modal */}
      <Modal.Backdrop isOpen={showChangePassword} onOpenChange={setShowChangePassword}>
        <Modal.Container size="sm">
          <Modal.Dialog>
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>修改密码</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="space-y-4">
              <FormField label="当前密码" type="password" value={oldPassword} onChange={setOldPassword} placeholder="输入当前密码" />
              <FormField label="新密码" type="password" value={newPassword} onChange={setNewPassword} placeholder="输入新密码（至少6位）" />
              <FormField label="确认新密码" type="password" value={confirmPassword} onChange={setConfirmPassword} placeholder="再次输入新密码" error={passwordError} />
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" onPress={() => setShowChangePassword(false)}>取消</Button>
              <Button variant="primary" onPress={handleChangePassword} isPending={isChangingPassword} isDisabled={!oldPassword || !newPassword || !confirmPassword}>
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}确认修改</>)}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>

      {/* Invited Users Modal */}
      <Modal.Backdrop isOpen={showInvitedUsers} onOpenChange={setShowInvitedUsers}>
        <Modal.Container size="sm">
          <Modal.Dialog>
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>已邀请的用户</Modal.Heading>
            </Modal.Header>
            <Modal.Body>
              {invitedUsers.length === 0 ? (
                <InlineEmptyState icon={Users} message="暂无邀请记录" />
              ) : (
                <div className="divide-y divide-border">
                  {invitedUsers.map((invitedUser) => (
                    <ListRow
                      key={invitedUser.id}
                      compact
                      left={
                        <Avatar className="size-9">
                          <Avatar.Image src={invitedUser.avatar || ""} alt={invitedUser.nickname || invitedUser.username} />
                          <Avatar.Fallback className="text-xs">
                            {(invitedUser.nickname || invitedUser.username).charAt(0).toUpperCase()}
                          </Avatar.Fallback>
                        </Avatar>
                      }
                    >
                      <ListRowTitle>{invitedUser.nickname || invitedUser.username}</ListRowTitle>
                      <ListRowDescription>
                        注册于 {new Date(invitedUser.registeredAt).toLocaleDateString()}
                      </ListRowDescription>
                    </ListRow>
                  ))}
                </div>
              )}
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" onPress={() => setShowInvitedUsers(false)}>关闭</Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </>
  );
}
