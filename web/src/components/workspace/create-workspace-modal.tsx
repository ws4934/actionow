"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import {
  Modal,
  Button,
  Form,
  TextField,
  Input,
  Label,
  TextArea,
  Spinner,
} from "@heroui/react";
import { workspaceService, ApiError, getErrorFromException } from "@/lib/api";
import { toast } from "@heroui/react";
import type { WorkspaceDTO, CreateWorkspaceRequestDTO } from "@/lib/api/dto";

interface CreateWorkspaceModalProps {
  isOpen: boolean;
  onOpenChange: (isOpen: boolean) => void;
  onSuccess: (workspace: WorkspaceDTO) => void;
  isDismissable?: boolean;
}

export function CreateWorkspaceModal({
  isOpen,
  onOpenChange,
  onSuccess,
  isDismissable = true,
}: CreateWorkspaceModalProps) {
  const t = useTranslations("workspace.create");
  const tCommon = useTranslations("common");
  const [isLoading, setIsLoading] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    setIsLoading(true);
    try {
      const data: CreateWorkspaceRequestDTO = {
        name: name.trim(),
        description: description.trim() || undefined,
      };
      const workspace = await workspaceService.createWorkspace(data);
      toast.success(t("success"));
      onSuccess(workspace);
      onOpenChange(false);
      // Reset form
      setName("");
      setDescription("");
    } catch (error) {
      toast.danger(getErrorFromException(error, "zh"));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Modal.Backdrop
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      isDismissable={isDismissable}
      isKeyboardDismissDisabled={!isDismissable}
    >
      <Modal.Container>
        <Modal.Dialog className="overflow-visible sm:max-w-md">
          {isDismissable && <Modal.CloseTrigger />}
          <Modal.Header>
            <Modal.Heading>{t("title")}</Modal.Heading>
          </Modal.Header>
          <Form onSubmit={handleSubmit}>
            <Modal.Body className="space-y-4 overflow-visible">
              <TextField className="w-full" isRequired>
                <Label>{t("name")}</Label>
                <Input
                  placeholder={t("namePlaceholder")}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  autoFocus
                />
              </TextField>
              <TextField className="w-full">
                <Label>{t("description")}</Label>
                <TextArea
                  placeholder={t("descriptionPlaceholder")}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  rows={3}
                />
              </TextField>
            </Modal.Body>
            <Modal.Footer>
              {isDismissable && (
                <Button variant="secondary" slot="close">
                  {tCommon("cancel")}
                </Button>
              )}
              <Button
                type="submit"
                isPending={isLoading}
                isDisabled={!name.trim()}
              >
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{t("submit")}</>)}
              </Button>
            </Modal.Footer>
          </Form>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}
