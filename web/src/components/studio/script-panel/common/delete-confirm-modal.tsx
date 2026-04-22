"use client";

/**
 * Shared Delete Confirmation Modal
 */

import { useState } from "react";
import { Button, Modal, Spinner } from "@heroui/react";
import { useTranslations } from "next-intl";

interface DeleteConfirmModalProps {
  title: string | null;
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void | Promise<void>;
}

export function DeleteConfirmModal({ title, isOpen, onClose, onConfirm }: DeleteConfirmModalProps) {
  const t = useTranslations("workspace.studio.common");
  const [isDeleting, setIsDeleting] = useState(false);

  const handleConfirm = async () => {
    setIsDeleting(true);
    try {
      await onConfirm();
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={(open) => !open && onClose()}>
      <Modal.Container size="sm">
        <Modal.Dialog>
          <Modal.CloseTrigger />
          <Modal.Header>
            <Modal.Heading>{t("confirmDelete")}</Modal.Heading>
          </Modal.Header>
          <Modal.Body>
            <p className="text-sm text-muted">
              {t("confirmDeleteMessage", { title: title ?? "" })}
            </p>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="secondary" slot="close" isDisabled={isDeleting}>
              {t("cancel")}
            </Button>
            <Button variant="danger" isPending={isDeleting} onPress={handleConfirm}>
              {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{t("delete")}</>)}
            </Button>
          </Modal.Footer>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}
