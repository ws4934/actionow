import { RegisterPageClient } from "@/components/auth/register-page-client";

interface RegisterPageProps {
  searchParams: Promise<{ code?: string; returnUrl?: string }>;
}

export default async function RegisterPage({ searchParams }: RegisterPageProps) {
  const { code, returnUrl } = await searchParams;

  return <RegisterPageClient initialInviteCode={code} returnUrl={returnUrl} />;
}
