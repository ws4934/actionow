"use client";

/**
 * Entity Picker Modal
 * Lets users pick a library entity (character/scene/prop) from the material
 * room and import it into the current script by fetching the detail and
 * calling the project create API.
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { Modal, SearchField, Spinner, toast } from "@heroui/react";
import { FormSelect } from "./form-select";
import { useLocale, useTranslations } from "next-intl";
import { Users, MapPin, Package, Copy } from "lucide-react";

import { libraryService } from "@/lib/api/services/library.service";
import { projectService } from "@/lib/api/services/project.service";
import { useWorkspace } from "@/components/providers/workspace-provider";
import { getErrorFromException } from "@/lib/api";
import { EntityCard, type EntityCardAction } from "@/components/ui/entity-card";
import type {
  LibraryCharacterDTO,
  LibrarySceneDTO,
  LibraryPropDTO,
  CharacterListDTO,
  SceneListDTO,
  PropListDTO,
  CharacterDetailDTO,
  SceneDetailDTO,
  PropDetailDTO,
  EntityScope,
} from "@/lib/api/dto";
import { EmptyState } from "./empty-state";

type PickerEntityType = "characters" | "scenes" | "props";
type SourceMode = "all" | "public" | "workspace";

const ENTITY_ICONS: Record<PickerEntityType, typeof Users> = {
  characters: Users,
  scenes: MapPin,
  props: Package,
};

const TITLE: Record<PickerEntityType, string> = {
  characters: "关联角色",
  scenes: "关联场景",
  props: "关联道具",
};

interface PickerItem {
  id: string;
  name: string;
  description: string | null;
  coverUrl: string | null;
  createdAt: string | null;
  createdByNickname: string | null;
  createdByUsername: string | null;
  /** Present when item comes from the project service (workspace scope). */
  scope?: EntityScope;
}

interface EntityPickerModalProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  entityType: PickerEntityType;
  scriptId: string;
  onImported?: () => void;
}

function toLibraryItem(e: LibraryCharacterDTO | LibrarySceneDTO | LibraryPropDTO): PickerItem {
  return {
    id: e.id,
    name: e.name,
    description: e.description,
    coverUrl: e.coverUrl ?? null,
    createdAt: e.createdAt,
    createdByNickname: null,
    createdByUsername: null,
  };
}

function toProjectItem(e: CharacterListDTO | SceneListDTO | PropListDTO): PickerItem {
  return {
    id: e.id,
    name: e.name,
    description: e.description,
    coverUrl: e.coverUrl ?? null,
    createdAt: e.createdAt,
    createdByNickname: e.createdByNickname ?? null,
    createdByUsername: e.createdByUsername ?? null,
    scope: e.scope,
  };
}

export function EntityPickerModal({
  isOpen,
  onOpenChange,
  entityType,
  scriptId,
  onImported,
}: EntityPickerModalProps) {
  const locale = useLocale();
  const t = useTranslations("workspace.studio.common");
  const tMaterial = useTranslations("workspace.materialRoom");
  const { currentWorkspaceId } = useWorkspace();

  const [sourceMode, setSourceMode] = useState<SourceMode>("all");
  const [searchKeyword, setSearchKeyword] = useState("");
  const [items, setItems] = useState<PickerItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [copyingId, setCopyingId] = useState<string | null>(null);

  const requestIdRef = useRef(0);

  const load = useCallback(async () => {
    const reqId = ++requestIdRef.current;
    setIsLoading(true);
    try {
      const params: Record<string, string | number | boolean | undefined> = {
        pageNum: 1,
        pageSize: 40,
        orderBy: sourceMode === "public" || sourceMode === "all" ? "publishedAt" : "createdAt",
        orderDir: "desc",
      };
      if (searchKeyword.trim()) params.keyword = searchKeyword.trim();

      const loadPublic = async (): Promise<PickerItem[]> => {
        try {
          switch (entityType) {
            case "characters":
              return (await libraryService.queryCharacters(params)).records.map(toLibraryItem);
            case "scenes":
              return (await libraryService.queryScenes(params)).records.map(toLibraryItem);
            case "props":
              return (await libraryService.queryProps(params)).records.map(toLibraryItem);
          }
        } catch {
          return [];
        }
      };

      const loadWorkspace = async (): Promise<PickerItem[]> => {
        if (!currentWorkspaceId) return [];
        try {
          const p = { ...params, orderBy: "createdAt", scope: "WORKSPACE" };
          switch (entityType) {
            case "characters":
              return (await projectService.queryCharacters(p)).records.map(toProjectItem);
            case "scenes":
              return (await projectService.queryScenes(p)).records.map(toProjectItem);
            case "props":
              return (await projectService.queryProps(p)).records.map(toProjectItem);
          }
        } catch {
          return [];
        }
      };

      let next: PickerItem[] = [];
      if (sourceMode === "all") {
        const [pub, ws] = await Promise.all([loadPublic(), loadWorkspace()]);
        const seen = new Set<string>();
        next = [...ws, ...pub].filter((i) => (seen.has(i.id) ? false : (seen.add(i.id), true)));
      } else if (sourceMode === "public") {
        next = await loadPublic();
      } else {
        next = await loadWorkspace();
      }

      if (reqId === requestIdRef.current) {
        setItems(next);
      }
    } catch (error) {
      if (reqId === requestIdRef.current) {
        toast.danger(getErrorFromException(error, locale));
      }
    } finally {
      if (reqId === requestIdRef.current) {
        setIsLoading(false);
      }
    }
  }, [entityType, sourceMode, searchKeyword, currentWorkspaceId, locale]);

  useEffect(() => {
    if (!isOpen) return;
    load();
  }, [isOpen, sourceMode, load]);

  useEffect(() => {
    if (!isOpen) return;
    const timer = setTimeout(() => load(), 300);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchKeyword]);

  useEffect(() => {
    if (!isOpen) {
      setSearchKeyword("");
      setSourceMode("all");
      setItems([]);
      setCopyingId(null);
    }
  }, [isOpen]);

  const handleImport = async (item: PickerItem) => {
    if (copyingId) return;
    setCopyingId(item.id);
    try {
      const isPublic = !item.scope;
      let detail: CharacterDetailDTO | SceneDetailDTO | PropDetailDTO;
      if (entityType === "characters") {
        detail = isPublic
          ? await libraryService.getCharacter(item.id) as unknown as CharacterDetailDTO
          : await projectService.getCharacter(item.id);
      } else if (entityType === "scenes") {
        detail = isPublic
          ? await libraryService.getScene(item.id) as unknown as SceneDetailDTO
          : await projectService.getScene(item.id);
      } else {
        detail = isPublic
          ? await libraryService.getProp(item.id) as unknown as PropDetailDTO
          : await projectService.getProp(item.id);
      }

      if (entityType === "characters") {
        const d = detail as CharacterDetailDTO;
        await projectService.createCharacter({
          scope: "SCRIPT",
          scriptId,
          name: d.name,
          description: d.description ?? undefined,
          fixedDesc: d.fixedDesc ?? undefined,
          age: d.age ?? undefined,
          gender: d.gender ?? undefined,
          characterType: d.characterType ?? undefined,
          coverAssetId: d.coverAssetId ?? undefined,
          voiceAssetId: d.voiceAssetId ?? undefined,
          appearanceData: d.appearanceData ?? undefined,
          referenceAssetId: d.referenceAssetId ?? undefined,
          voiceSeedId: d.voiceSeedId ?? undefined,
          extraInfo: d.extraInfo ?? undefined,
        });
      } else if (entityType === "scenes") {
        const d = detail as SceneDetailDTO;
        await projectService.createScene({
          scope: "SCRIPT",
          scriptId,
          name: d.name,
          description: d.description ?? undefined,
          fixedDesc: d.fixedDesc ?? undefined,
          coverAssetId: d.coverAssetId ?? undefined,
          voiceAssetId: d.voiceAssetId ?? undefined,
          environment: d.environment ?? undefined,
          atmosphere: d.atmosphere ?? undefined,
          referenceAssetId: d.referenceAssetId ?? undefined,
          extraInfo: d.extraInfo ?? undefined,
        });
      } else {
        const d = detail as PropDetailDTO;
        await projectService.createProp({
          scope: "SCRIPT",
          scriptId,
          name: d.name,
          description: d.description ?? undefined,
          fixedDesc: d.fixedDesc ?? undefined,
          propType: d.propType ?? undefined,
          coverAssetId: d.coverAssetId ?? undefined,
          voiceAssetId: d.voiceAssetId ?? undefined,
          appearanceData: d.appearanceData ?? undefined,
          referenceAssetId: d.referenceAssetId ?? undefined,
          extraInfo: d.extraInfo ?? undefined,
        });
      }

      toast.success(tMaterial("copySuccess"));
      onImported?.();
      onOpenChange(false);
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setCopyingId(null);
    }
  };

  const EmptyIcon = ENTITY_ICONS[entityType];
  const FallbackIcon = ENTITY_ICONS[entityType];

  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
      <Modal.Container>
        <Modal.Dialog className="h-[85vh] max-h-[900px] sm:max-w-5xl">
          <Modal.CloseTrigger />
          <Modal.Header>
            <Modal.Heading>{TITLE[entityType]}</Modal.Heading>
          </Modal.Header>

          <Modal.Body className="flex min-h-0 flex-1 flex-col gap-4 overflow-hidden">
            <div className="flex shrink-0 items-center gap-3">
              <FormSelect
                label=""
                value={sourceMode}
                onChange={(v) => setSourceMode(v as SourceMode)}
                options={[
                  { value: "all", label: "全部" },
                  { value: "public", label: "公共库" },
                  { value: "workspace", label: "工作区" },
                ]}
                className="w-32 shrink-0"
              />

              <SearchField
                aria-label="搜索"
                value={searchKeyword}
                onChange={setSearchKeyword}
                className="flex-1"
              >
                <SearchField.Group>
                  <SearchField.SearchIcon />
                  <SearchField.Input placeholder={t("search") as unknown as string} />
                  <SearchField.ClearButton />
                </SearchField.Group>
              </SearchField>
            </div>

            <div className="min-h-0 flex-1 overflow-auto">
              {isLoading && items.length === 0 ? (
                <div className="flex h-full items-center justify-center">
                  <Spinner />
                </div>
              ) : items.length === 0 ? (
                <EmptyState
                  icon={<EmptyIcon className="size-16" />}
                  title={t("noData")}
                />
              ) : (
                <div className="grid grid-cols-2 gap-4 p-0.5 md:grid-cols-3 lg:grid-cols-4">
                  {items.map((item) => {
                    const authorName = item.createdByNickname || item.createdByUsername;
                    const actions: EntityCardAction[] = [
                      {
                        id: "copy-to-script",
                        label: "复制到剧本",
                        icon: Copy,
                        onAction: () => handleImport(item),
                      },
                    ];
                    return (
                      <EntityCard
                        key={item.id}
                        title={item.name}
                        description={item.description}
                        coverUrl={item.coverUrl}
                        fallbackIcon={<FallbackIcon className="size-12 text-muted/20" />}
                        actions={actions}
                        isActionPending={copyingId === item.id}
                        footerLeft={authorName ? <span className="truncate">{authorName}</span> : null}
                        onClick={() => handleImport(item)}
                      />
                    );
                  })}
                </div>
              )}
            </div>
          </Modal.Body>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}

export default EntityPickerModal;
