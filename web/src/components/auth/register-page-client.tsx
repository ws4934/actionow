"use client";

import dynamic from "next/dynamic";
import { AuthFormSkeleton } from "@/components/auth/auth-form-skeleton";

const RegisterForm = dynamic(
  () => import("@/components/auth/register-form").then((mod) => mod.RegisterForm),
  {
    ssr: false,
    loading: () => <AuthFormSkeleton />,
  }
);

interface RegisterPageClientProps {
  initialInviteCode?: string;
  returnUrl?: string;
}

export function RegisterPageClient({
  initialInviteCode,
  returnUrl,
}: RegisterPageClientProps) {
  return <RegisterForm initialInviteCode={initialInviteCode} returnUrl={returnUrl} />;
}
