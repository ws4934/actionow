import { InviteContent } from "@/components/auth/invite-content";

interface InvitePageProps {
  searchParams: Promise<{ workspace_code?: string; invite_code?: string }>;
}

export default async function InvitePage({ searchParams }: InvitePageProps) {
  const { workspace_code, invite_code } = await searchParams;

  return (
    <InviteContent
      workspaceCode={workspace_code ?? ""}
      personalCode={invite_code ?? ""}
    />
  );
}
