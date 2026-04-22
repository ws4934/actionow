"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useLocale } from "next-intl";
import { useAuthStore } from "@/lib/stores/auth-store";
import { Card } from "@heroui/react";
import { AdminSidebar } from "@/components/admin/admin-sidebar";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const locale = useLocale();
  const router = useRouter();

  const [isHydrated, setIsHydrated] = useState(() =>
    useAuthStore.persist.hasHydrated()
  );
  const isLoggedIn = useAuthStore((state) => !!state.tokenBundle?.accessToken);

  useEffect(() => {
    const unsub1 = useAuthStore.persist.onHydrate(() => setIsHydrated(false));
    const unsub2 = useAuthStore.persist.onFinishHydration(() => setIsHydrated(true));
    setIsHydrated(useAuthStore.persist.hasHydrated());
    return () => {
      unsub1();
      unsub2();
    };
  }, []);

  useEffect(() => {
    if (!isHydrated) return;
    if (isLoggedIn) return;

    const timer = setTimeout(() => {
      const latestToken = useAuthStore.getState().tokenBundle?.accessToken;
      if (!latestToken) {
        router.replace(`/${locale}/login`);
      }
    }, 700);

    return () => clearTimeout(timer);
  }, [isHydrated, isLoggedIn, locale, router]);

  if (!isHydrated) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="size-8 animate-spin rounded-full border-4 border-accent border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="fixed inset-0 flex overflow-hidden bg-background">
      <div className="flex h-full w-full gap-0 p-3">
        <Card className="h-full shrink-0 overflow-hidden p-0">
          <AdminSidebar />
        </Card>
        <div className="h-full min-w-0 flex-1 pl-3">
          <Card className="flex h-full flex-col overflow-hidden">
            {children}
          </Card>
        </div>
      </div>
    </div>
  );
}
