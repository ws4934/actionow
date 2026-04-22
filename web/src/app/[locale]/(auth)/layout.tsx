import { MonitorBackground } from "@/components/backgrounds/monitor-background";
import { AuthCard } from "@/components/auth/auth-card";
import { AuthGuard } from "@/components/auth/auth-guard";

interface AuthLayoutProps {
  children: React.ReactNode;
}

export default function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <MonitorBackground>
      <AuthCard>
        <AuthGuard>{children}</AuthGuard>
      </AuthCard>
    </MonitorBackground>
  );
}
