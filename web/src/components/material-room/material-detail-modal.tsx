"use client";

import { useRef, useState, useCallback } from "react";
import { Modal, Button, Spinner } from "@heroui/react";
import { useTranslations } from "next-intl";
import type { EntityType } from "./material-sidebar";
import { CharacterDetailForm } from "./character-detail-form";
import type { DetailFormHandle } from "./shared";
import { SceneDetailForm } from "./scene-detail-form";
import { PropDetailForm } from "./prop-detail-form";
import { StyleDetailForm } from "./style-detail-form";
import { AssetDetailForm } from "./asset-detail-form";

interface MaterialDetailModalProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  entityType: EntityType;
  entityId: string | null;
  entityName: string;
  workspaceId: string;
  onUpdated: () => void;
}

export function MaterialDetailModal({
  isOpen,
  onOpenChange,
  entityType,
  entityId,
  entityName,
  workspaceId,
  onUpdated,
}: MaterialDetailModalProps) {
  const tc = useTranslations("common");
  const t = useTranslations("workspace.materialRoom.detail");
  const formRef = useRef<DetailFormHandle>(null);
  const [formState, setFormState] = useState({ canSave: false, isSaving: false });

  const onFormStateChange = useCallback((canSave: boolean, isSaving: boolean) => {
    setFormState({ canSave, isSaving });
  }, []);

  if (!entityId) return null;

  const isReadonly = entityType === "styles";

  const renderForm = () => {
    switch (entityType) {
      case "characters":
        return (
          <CharacterDetailForm
            ref={formRef}
            entityId={entityId}
            workspaceId={workspaceId}
            onUpdated={onUpdated}
            onFormStateChange={onFormStateChange}
          />
        );
      case "scenes":
        return (
          <SceneDetailForm
            ref={formRef}
            entityId={entityId}
            workspaceId={workspaceId}
            onUpdated={onUpdated}
            onFormStateChange={onFormStateChange}
          />
        );
      case "props":
        return (
          <PropDetailForm
            ref={formRef}
            entityId={entityId}
            workspaceId={workspaceId}
            onUpdated={onUpdated}
            onFormStateChange={onFormStateChange}
          />
        );
      case "styles":
        return (
          <StyleDetailForm
            ref={formRef}
            entityId={entityId}
            workspaceId={workspaceId}
            onUpdated={onUpdated}
          />
        );
      case "assets":
        return (
          <AssetDetailForm
            ref={formRef}
            entityId={entityId}
            workspaceId={workspaceId}
            onUpdated={onUpdated}
            onFormStateChange={onFormStateChange}
          />
        );
      default:
        return null;
    }
  };

  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
      <Modal.Container>
        <Modal.Dialog className="max-h-[85vh] sm:max-w-5xl">
          <Modal.CloseTrigger />
          <Modal.Header>
            <Modal.Heading>{entityName}</Modal.Heading>
          </Modal.Header>
          <Modal.Body className="overflow-y-auto">
            {renderForm()}
          </Modal.Body>
          <Modal.Footer>
            <Button variant="secondary" slot="close">{tc("cancel")}</Button>
            {!isReadonly && (
              <Button
                onPress={() => formRef.current?.save()}
                isPending={formState.isSaving}
                isDisabled={!formState.canSave}
              >
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{t("save")}</>)}
              </Button>
            )}
          </Modal.Footer>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}
