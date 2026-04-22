"use client";

import { Card } from "@heroui/react";
import { ManagementSidebar } from "@/components/workspace/management-sidebar";

export default function ManagementLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex h-full w-full gap-0 p-3">
      <Card className="h-full shrink-0 overflow-hidden p-0">
        <ManagementSidebar />
      </Card>
      <div className="h-full min-w-0 flex-1 pl-3">
        <Card className="flex h-full flex-col overflow-hidden">
          {children}
        </Card>
      </div>
    </div>
  );
}
