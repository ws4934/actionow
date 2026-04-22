"use client";

import { useTranslations } from "next-intl";
import { Button, Modal, Spinner } from "@heroui/react";

interface ConfirmModalProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  message: string;
  confirmLabel?: string;
  onConfirm: () => void;
  isPending?: boolean;
  variant?: "danger" | "primary";
}

export function ConfirmModal({
  isOpen,
  onOpenChange,
  title,
  message,
  confirmLabel,
  onConfirm,
  isPending,
  variant = "danger",
}: ConfirmModalProps) {
  const tc = useTranslations("common");

  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
      <Modal.Container>
        <Modal.Dialog className="sm:max-w-sm">
          <Modal.CloseTrigger />
          <Modal.Header>
            <Modal.Heading>{title}</Modal.Heading>
          </Modal.Header>
          <Modal.Body>
            <p className="text-sm text-muted">{message}</p>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="secondary" slot="close">{tc("cancel")}</Button>
            <Button variant={variant} onPress={onConfirm} isPending={isPending}>
              {({isPending: pending}) => (<>{pending ? <Spinner color="current" size="sm" /> : null}{confirmLabel || tc("confirm")}</>)}
            </Button>
          </Modal.Footer>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}
