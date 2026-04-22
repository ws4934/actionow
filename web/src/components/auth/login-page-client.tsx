"use client";

import dynamic from "next/dynamic";
import { AuthFormSkeleton } from "@/components/auth/auth-form-skeleton";

const LoginForm = dynamic(
  () => import("@/components/auth/login-form").then((mod) => mod.LoginForm),
  {
    ssr: false,
    loading: () => <AuthFormSkeleton showTabs />,
  }
);

interface LoginPageClientProps {
  returnUrl?: string;
}

export function LoginPageClient({ returnUrl }: LoginPageClientProps) {
  return <LoginForm returnUrl={returnUrl} />;
}
