"use client";

import { useEffect, useState } from "react";
import { useRouter, usePathname } from "next/navigation";
import { useLocale } from "next-intl";
import { useAuthStore } from "@/lib/stores/auth-store";

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const locale = useLocale();
  const isLoggedIn = useAuthStore((state) => !!state.tokenBundle?.accessToken);
  const [isHydrated, setIsHydrated] = useState(() =>
    useAuthStore.persist.hasHydrated()
  );

  useEffect(() => {
    const unsub = useAuthStore.persist.onFinishHydration(() => {
      setIsHydrated(true);
    });
    setIsHydrated(useAuthStore.persist.hasHydrated());
    return unsub;
  }, []);

  useEffect(() => {
    if (!isHydrated || !isLoggedIn) return;
    if (pathname.includes("/invite")) return;
    router.replace(`/${locale}/workspace/projects`);
  }, [isHydrated, isLoggedIn, locale, pathname, router]);

  return <>{children}</>;
}
