"use client";

export default function DirectorLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <main className="flex-1 overflow-y-auto bg-background p-6">{children}</main>
  );
}
