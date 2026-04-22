"use client";

import { usePathname } from "next/navigation";
import Link from "next/link";
import { Home, FolderOpen, Star, Clock, Trash2 } from "lucide-react";

interface NavItem {
  icon: typeof Home;
  label: string;
  href: string;
  count?: number;
}

interface NavMenuProps {
  projectCount?: number;
  favoriteCount?: number;
  recentCount?: number;
  trashCount?: number;
}

export function NavMenu({
  projectCount = 0,
  favoriteCount = 0,
  recentCount = 0,
  trashCount = 0,
}: NavMenuProps) {
  const pathname = usePathname();

  const navItems: NavItem[] = [
    { icon: Home, label: "首页", href: "/workspace" },
    { icon: FolderOpen, label: "所有项目", href: "/workspace/projects", count: projectCount },
    { icon: Star, label: "收藏项目", href: "/workspace/favorites", count: favoriteCount },
    { icon: Clock, label: "最近浏览", href: "/workspace/recent", count: recentCount },
    { icon: Trash2, label: "回收站", href: "/workspace/trash", count: trashCount },
  ];

  return (
    <div className="px-3">
      <h3 className="mb-2 px-2 text-xs font-medium uppercase tracking-wide text-muted">
        导航
      </h3>
      <nav className="flex flex-col gap-0.5">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.href ||
            (item.href !== "/workspace" && pathname.startsWith(item.href));

          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors ${
                isActive
                  ? "bg-accent/10 text-accent"
                  : "text-foreground/80 hover:bg-muted/10 hover:text-foreground"
              }`}
            >
              <Icon className="size-4" />
              <span className="flex-1">{item.label}</span>
              {item.count !== undefined && item.count > 0 && (
                <span className={`text-xs ${isActive ? "text-accent" : "text-muted"}`}>
                  ({item.count})
                </span>
              )}
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
