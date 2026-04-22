import { LoginPageClient } from "@/components/auth/login-page-client";

interface LoginPageProps {
  searchParams: Promise<{ returnUrl?: string }>;
}

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const { returnUrl } = await searchParams;

  return <LoginPageClient returnUrl={returnUrl} />;
}
