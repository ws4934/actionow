"use client";

import { Toast } from "@heroui/react";

interface ToastProviderProps {
  children: React.ReactNode;
}

export function ToastProvider({ children }: ToastProviderProps) {
  return (
    <>
      <Toast.Provider placement="top end" className="z-[100]" />
      {children}
    </>
  );
}
