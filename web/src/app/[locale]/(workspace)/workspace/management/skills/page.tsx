"use client";

import { useState, useEffect, useCallback, useRef, useMemo } from "react";
import { useTranslations } from "next-intl";
import {
  Button,
  Modal,
  TextField,
  Label,
  Input,
  TextArea,
  Chip,
  Spinner,
  SearchField,
  Tooltip,
  toast,
  Table,
  Pagination,
  Select,
  ListBox,
} from "@heroui/react";
import {
  Plus,
  Pencil,
  Trash2,
  ToggleLeft,
  ToggleRight,
  Eye,
  Loader2,
  Zap,
  Upload,
  RefreshCw,
} from "lucide-react";
import { skillService } from "@/lib/api";
import { useWorkspace } from "@/components/providers/workspace-provider";
import type {
  SkillResponseDTO,
  SkillCreateRequestDTO,
  SkillUpdateRequestDTO,
} from "@/lib/api/dto";
// ============================================================================
// Helpers
// ============================================================================

const PAGE_SIZE_OPTIONS = [10, 20, 50] as const;
type PageSize = (typeof PAGE_SIZE_OPTIONS)[number];

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString();
}

// ============================================================================
// Page Component
// ============================================================================

export default function SkillsPage() {
  const t = useTranslations("workspace.materialRoom.skills");
  const { currentWorkspace } = useWorkspace();
  const isAdmin =
    currentWorkspace?.myRole === "CREATOR" || currentWorkspace?.myRole === "ADMIN";

  // ── List state ──────────────────────────────────────────────────────────────
  const [records, setRecords] = useState<SkillResponseDTO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState<PageSize>(20);
  const [keyword, setKeyword] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [togglingName, setTogglingName] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const requestIdRef = useRef(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // ── Modal state ─────────────────────────────────────────────────────────────
  const [showCreate, setShowCreate] = useState(false);
  const [editingSkill, setEditingSkill] = useState<SkillResponseDTO | null>(null);
  const [viewingSkill, setViewingSkill] = useState<SkillResponseDTO | null>(null);
  const [deletingSkill, setDeletingSkill] = useState<SkillResponseDTO | null>(null);

  // ── Form state ───────────────────────────────────────────────────────────────
  const [formName, setFormName] = useState("");
  const [formDisplayName, setFormDisplayName] = useState("");
  const [formDescription, setFormDescription] = useState("");
  const [formContent, setFormContent] = useState("");
  const [formTags, setFormTags] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  // ── Load skills ──────────────────────────────────────────────────────────────
  const loadSkills = useCallback(async (pageNum: number, kw: string, size: number = pageSize) => {
    const reqId = ++requestIdRef.current;
    setIsLoading(true);
    try {
      const result = await skillService.listSkills({
        page: pageNum,
        size,
        keyword: kw.trim() || undefined,
      });
      if (reqId !== requestIdRef.current) return;
      setRecords(result.records);
      setTotal(result.total);
    } catch {
      // silent
    } finally {
      if (reqId === requestIdRef.current) setIsLoading(false);
    }
  }, [pageSize]);

  useEffect(() => {
    loadSkills(page, keyword, pageSize);
  }, [page, keyword, pageSize, loadSkills]);

  // ── Form helpers ─────────────────────────────────────────────────────────────
  const resetForm = () => {
    setFormName("");
    setFormDisplayName("");
    setFormDescription("");
    setFormContent("");
    setFormTags("");
  };

  const openCreate = () => {
    resetForm();
    setShowCreate(true);
  };

  const openEdit = (skill: SkillResponseDTO) => {
    setFormDisplayName(skill.displayName ?? "");
    setFormDescription(skill.description);
    setFormContent(skill.content ?? "");
    setFormTags(skill.tags?.join(", ") ?? "");
    setEditingSkill(skill);
  };

  const parseTags = (raw: string) =>
    raw
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);

  // ── CRUD handlers ────────────────────────────────────────────────────────────
  const handleCreate = async () => {
    if (!formName || !formDescription || !formContent) return;
    setIsSubmitting(true);
    try {
      const data: SkillCreateRequestDTO = {
        name: formName.trim(),
        displayName: formDisplayName.trim() || undefined,
        description: formDescription.trim(),
        content: formContent.trim(),
        tags: formTags ? parseTags(formTags) : undefined,
      };
      await skillService.createSkill(data);
      toast.success(t("create.success"));
      setShowCreate(false);
      resetForm();
      loadSkills(1, keyword);
      setPage(1);
    } catch (err: unknown) {
      toast.danger(err instanceof Error ? err.message : t("create.failed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleUpdate = async () => {
    if (!editingSkill) return;
    setIsSubmitting(true);
    try {
      const data: SkillUpdateRequestDTO = {
        displayName: formDisplayName.trim() || undefined,
        description: formDescription.trim() || undefined,
        content: formContent.trim() || undefined,
        tags: formTags ? parseTags(formTags) : undefined,
      };
      await skillService.updateSkill(editingSkill.name, data);
      toast.success(t("edit.success"));
      setEditingSkill(null);
      loadSkills(page, keyword);
    } catch (err: unknown) {
      toast.danger(err instanceof Error ? err.message : t("edit.failed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!deletingSkill) return;
    setIsSubmitting(true);
    try {
      await skillService.deleteSkill(deletingSkill.name);
      toast.success(t("delete.success"));
      setDeletingSkill(null);
      loadSkills(page, keyword);
    } catch (err: unknown) {
      toast.danger(err instanceof Error ? err.message : t("delete.failed"));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleToggle = async (skill: SkillResponseDTO) => {
    setTogglingName(skill.name);
    try {
      const updated = await skillService.toggleSkill(skill.name);
      setRecords((prev) =>
        prev.map((s) => (s.name === skill.name ? { ...s, enabled: updated.enabled } : s))
      );
      toast.success(t("toggle.success"));
    } catch {
      toast.danger(t("toggle.failed"));
    } finally {
      setTogglingName(null);
    }
  };

  const handleViewDetail = async (skill: SkillResponseDTO) => {
    try {
      const detail = await skillService.getSkill(skill.name);
      setViewingSkill(detail);
    } catch {
      setViewingSkill(skill);
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    // Reset so the same file can be re-uploaded if needed
    e.target.value = "";
    setIsUploading(true);
    try {
      const result = await skillService.importSkill(file);
      if (result.failed === 0) {
        toast.success(t("upload.success", { success: result.success, total: result.total }));
      } else {
        // Partial failure — warn and show first error
        const detail = result.errors[0] ?? "";
        toast.warning(
          t("upload.partialSuccess", { success: result.success, total: result.total }) +
          (detail ? `\n${detail}` : "")
        );
      }
      loadSkills(1, keyword);
      setPage(1);
    } catch (err: unknown) {
      toast.danger(err instanceof Error ? err.message : t("upload.failed"));
    } finally {
      setIsUploading(false);
    }
  };

  // ── Pagination ───────────────────────────────────────────────────────────────
  const totalPages = Math.ceil(total / pageSize);
  const startItem = total === 0 ? 0 : (page - 1) * pageSize + 1;
  const endItem = Math.min(page * pageSize, total);

  const handlePageChange = (p: number) => {
    if (p >= 1 && p <= totalPages) setPage(p);
  };

  const handlePageSizeChange = (size: PageSize) => {
    setPageSize(size);
    setPage(1);
  };

  const pageNumbers = useMemo((): (number | "ellipsis")[] => {
    if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i + 1);
    const pages: (number | "ellipsis")[] = [1];
    if (page > 3) pages.push("ellipsis");
    const start = Math.max(2, page - 1);
    const end = Math.min(totalPages - 1, page + 1);
    for (let i = start; i <= end; i++) pages.push(i);
    if (page < totalPages - 2) pages.push("ellipsis");
    pages.push(totalPages);
    return pages;
  }, [page, totalPages]);

  // ── Render ───────────────────────────────────────────────────────────────────
  return (
    <div className="flex h-full flex-col">
      {/* Toolbar */}
      <div className="flex shrink-0 items-center justify-between gap-3 pb-4">
        {/* Left: Search */}
        <div className="flex items-center gap-2">
          <SearchField
            aria-label={t("searchPlaceholder")}
            value={keyword}
            onChange={(v) => {
              setKeyword(v);
              setPage(1);
            }}
            variant="secondary"
          >
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-36" placeholder={t("searchPlaceholder")} />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
          <span className="text-xs text-muted">
            {t("pagination.total", { total, pages: totalPages })}
          </span>
        </div>

        {/* Right: Actions */}
        <div className="flex items-center gap-2">
          {isAdmin && (
            <>
              {/* Hidden file input for YAML/JSON upload */}
              <input
                ref={fileInputRef}
                type="file"
                accept=".zip"
                className="hidden"
                onChange={handleFileUpload}
              />
              <Tooltip delay={0}>
                <Button
                  variant="secondary"
                  size="sm"
                  isIconOnly
                  isPending={isUploading}
                  onPress={() => fileInputRef.current?.click()}
                  aria-label={t("upload.label")}
                >
                  {({isPending}) => isPending ? <Spinner color="current" size="sm" /> : <Upload className="size-4" />}
                </Button>
                <Tooltip.Content>{t("upload.tooltip")}</Tooltip.Content>
              </Tooltip>
              <Button size="sm" onPress={openCreate}>
                <Plus className="size-4" />
                {t("createSkill")}
              </Button>
            </>
          )}
          <Button
            variant="ghost"
            size="sm"
            isIconOnly
            onPress={() => loadSkills(page, keyword, pageSize)}
          >
            <RefreshCw className="size-4" />
          </Button>
        </div>
      </div>

      {/* Table */}
      <div className="min-h-0 flex-1">
        <Table>
          <Table.ScrollContainer className="h-full overflow-auto">
            <Table.Content aria-label={t("title")}>
              <Table.Header className="sticky top-0 z-10">
                <Table.Column isRowHeader>{t("column.name")}</Table.Column>
                <Table.Column>{t("column.description")}</Table.Column>
                <Table.Column className="w-24">{t("column.scope")}</Table.Column>
                <Table.Column className="w-24">{t("column.status")}</Table.Column>
                <Table.Column className="w-20">{t("column.version")}</Table.Column>
                <Table.Column className="w-32 text-right">{t("column.actions")}</Table.Column>
              </Table.Header>
              <Table.Body
                renderEmptyState={() =>
                  isLoading ? (
                    <div className="flex items-center justify-center py-20">
                      <Spinner size="md" />
                    </div>
                  ) : (
                    <div className="flex flex-col items-center justify-center py-20 text-center">
                      <Zap className="size-10 text-muted/30" />
                      <p className="mt-3 text-sm text-muted">{t("empty")}</p>
                    </div>
                  )
                }
              >
                <Table.Collection items={records}>
                  {(skill) => (
                    <Table.Row id={skill.id}>
                      {/* Name */}
                      <Table.Cell>
                        <div>
                          <span className="font-medium text-foreground">
                            {skill.displayName ?? skill.name}
                          </span>
                          {skill.displayName && (
                            <span className="ml-1.5 font-mono text-xs text-muted">
                              {skill.name}
                            </span>
                          )}
                          {skill.tags && skill.tags.length > 0 && (
                            <div className="mt-1 flex flex-wrap gap-1">
                              {skill.tags.slice(0, 3).map((tag) => (
                                <span
                                  key={tag}
                                  className="rounded bg-muted/10 px-1.5 py-0.5 text-[10px] text-muted"
                                >
                                  {tag}
                                </span>
                              ))}
                            </div>
                          )}
                        </div>
                      </Table.Cell>
                      {/* Description */}
                      <Table.Cell>
                        <span className="line-clamp-2 text-xs text-muted">{skill.description}</span>
                      </Table.Cell>
                      {/* Scope */}
                      <Table.Cell>
                        <Chip
                          size="sm"
                          variant="soft"
                          color={skill.scope === "SYSTEM" ? "accent" : "success"}
                        >
                          {t(`scope.${skill.scope}`)}
                        </Chip>
                      </Table.Cell>
                      {/* Status */}
                      <Table.Cell>
                        <Chip
                          size="sm"
                          variant="soft"
                          color={skill.enabled ? "success" : "default"}
                        >
                          {skill.enabled ? t("status.enabled") : t("status.disabled")}
                        </Chip>
                      </Table.Cell>
                      {/* Version */}
                      <Table.Cell>
                        <span className="text-xs text-muted">v{skill.version}</span>
                      </Table.Cell>
                      {/* Actions */}
                      <Table.Cell className="text-right">
                        <div className="flex items-center justify-end gap-1">
                          <Tooltip delay={0}>
                            <Button
                              variant="ghost"
                              size="sm"
                              isIconOnly
                              className="size-7"
                              aria-label={t("column.view")}
                              onPress={() => handleViewDetail(skill)}
                            >
                              <Eye className="size-3.5" />
                            </Button>
                            <Tooltip.Content>{t("column.view")}</Tooltip.Content>
                          </Tooltip>

                          {isAdmin && skill.scope === "WORKSPACE" && (
                            <>
                              <Tooltip delay={0}>
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  isIconOnly
                                  className="size-7"
                                  aria-label={skill.enabled ? t("status.disabled") : t("status.enabled")}
                                  isPending={togglingName === skill.name}
                                  onPress={() => handleToggle(skill)}
                                >
                                  {({isPending}) => isPending ? <Spinner color="current" size="sm" /> : skill.enabled ? (
                                    <ToggleRight className="size-3.5 text-success" />
                                  ) : (
                                    <ToggleLeft className="size-3.5 text-muted" />
                                  )}
                                </Button>
                                <Tooltip.Content>
                                  {skill.enabled ? t("status.disable") : t("status.enable")}
                                </Tooltip.Content>
                              </Tooltip>
                              <Tooltip delay={0}>
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  isIconOnly
                                  className="size-7"
                                  aria-label={t("edit.title")}
                                  onPress={() => openEdit(skill)}
                                >
                                  <Pencil className="size-3.5" />
                                </Button>
                                <Tooltip.Content>{t("edit.title")}</Tooltip.Content>
                              </Tooltip>
                              <Tooltip delay={0}>
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  isIconOnly
                                  className="size-7 text-danger"
                                  aria-label={t("delete.title")}
                                  onPress={() => setDeletingSkill(skill)}
                                >
                                  <Trash2 className="size-3.5" />
                                </Button>
                                <Tooltip.Content>{t("delete.title")}</Tooltip.Content>
                              </Tooltip>
                            </>
                          )}
                        </div>
                      </Table.Cell>
                    </Table.Row>
                  )}
                </Table.Collection>
              </Table.Body>
            </Table.Content>
          </Table.ScrollContainer>

          <Table.Footer>
            <Pagination size="sm">
              <Pagination.Summary>
                <div className="flex items-center gap-2.5">
                  {total > 0 && (
                    <span className="tabular-nums text-xs text-muted">
                      {startItem}–{endItem} / {total}
                    </span>
                  )}
                  <Select
                    aria-label="每页条数"
                    variant="secondary"
                    value={String(pageSize)}
                    onChange={(value) => handlePageSizeChange(Number(value) as PageSize)}
                  >
                    <Select.Trigger className="h-7 w-24 text-xs">
                      <Select.Value />
                      <Select.Indicator />
                    </Select.Trigger>
                    <Select.Popover>
                      <ListBox>
                        {PAGE_SIZE_OPTIONS.map((s) => (
                          <ListBox.Item key={s} id={String(s)} textValue={`${s} 条/页`}>
                            {s} 条/页
                            <ListBox.ItemIndicator />
                          </ListBox.Item>
                        ))}
                      </ListBox>
                    </Select.Popover>
                  </Select>
                </div>
              </Pagination.Summary>
              {totalPages > 1 && (
                <Pagination.Content>
                  <Pagination.Item>
                    <Pagination.Previous
                      isDisabled={page === 1}
                      onPress={() => handlePageChange(page - 1)}
                    >
                      <Pagination.PreviousIcon />
                    </Pagination.Previous>
                  </Pagination.Item>
                  {pageNumbers.map((p, i) =>
                    p === "ellipsis" ? (
                      <Pagination.Item key={`e-${i}`}>
                        <Pagination.Ellipsis />
                      </Pagination.Item>
                    ) : (
                      <Pagination.Item key={p}>
                        <Pagination.Link
                          isActive={p === page}
                          onPress={() => handlePageChange(p as number)}
                        >
                          {p}
                        </Pagination.Link>
                      </Pagination.Item>
                    )
                  )}
                  <Pagination.Item>
                    <Pagination.Next
                      isDisabled={page >= totalPages}
                      onPress={() => handlePageChange(page + 1)}
                    >
                      <Pagination.NextIcon />
                    </Pagination.Next>
                  </Pagination.Item>
                </Pagination.Content>
              )}
            </Pagination>
          </Table.Footer>
        </Table>
      </div>

      {/* ── Create Modal ─────────────────────────────────────────────────────── */}
      <Modal.Backdrop isOpen={showCreate} onOpenChange={setShowCreate}>
        <Modal.Container>
          <Modal.Dialog className="max-w-2xl">
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>{t("create.title")}</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="space-y-4 overflow-visible">
              <TextField className="w-full" isRequired value={formName} onChange={setFormName}>
                <Label>{t("create.nameLabel")}</Label>
                <Input placeholder={t("create.namePlaceholder")} />
                <p className="mt-1 text-xs text-muted">{t("create.nameHint")}</p>
              </TextField>
              <TextField className="w-full" value={formDisplayName} onChange={setFormDisplayName}>
                <Label>{t("create.displayNameLabel")}</Label>
                <Input placeholder={t("create.displayNamePlaceholder")} />
              </TextField>
              <TextField className="w-full" isRequired value={formDescription} onChange={setFormDescription}>
                <Label>{t("create.descriptionLabel")}</Label>
                <Input placeholder={t("create.descriptionPlaceholder")} />
              </TextField>
              <div className="space-y-1">
                <Label>{t("create.contentLabel")} *</Label>
                <TextArea
                  className="w-full"
                  rows={8}
                  placeholder={t("create.contentPlaceholder")}
                  value={formContent}
                  onChange={(e) => setFormContent(e.target.value)}
                />
              </div>
              <TextField className="w-full" value={formTags} onChange={setFormTags}>
                <Label>{t("create.tagsLabel")}</Label>
                <Input placeholder={t("create.tagsPlaceholder")} />
              </TextField>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" slot="close">
                {t("cancel")}
              </Button>
              <Button
                onPress={handleCreate}
                isPending={isSubmitting}
                isDisabled={!formName || !formDescription || !formContent}
              >
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{t("create.submit")}</>)}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>

      {/* ── Edit Modal ───────────────────────────────────────────────────────── */}
      <Modal.Backdrop isOpen={!!editingSkill} onOpenChange={(o) => { if (!o) setEditingSkill(null); }}>
        <Modal.Container>
          <Modal.Dialog className="max-w-2xl">
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>{t("edit.title")}: {editingSkill?.name}</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="space-y-4 overflow-visible">
              <TextField className="w-full" value={formDisplayName} onChange={setFormDisplayName}>
                <Label>{t("create.displayNameLabel")}</Label>
                <Input />
              </TextField>
              <TextField className="w-full" value={formDescription} onChange={setFormDescription}>
                <Label>{t("create.descriptionLabel")}</Label>
                <Input />
              </TextField>
              <div className="space-y-1">
                <Label>{t("create.contentLabel")}</Label>
                <TextArea
                  className="w-full"
                  rows={8}
                  value={formContent}
                  onChange={(e) => setFormContent(e.target.value)}
                />
              </div>
              <TextField className="w-full" value={formTags} onChange={setFormTags}>
                <Label>{t("create.tagsLabel")}</Label>
                <Input placeholder={t("create.tagsPlaceholder")} />
              </TextField>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" onPress={() => setEditingSkill(null)}>
                {t("cancel")}
              </Button>
              <Button onPress={handleUpdate} isPending={isSubmitting}>
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{t("edit.submit")}</>)}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>

      {/* ── View Detail Modal ────────────────────────────────────────────────── */}
      <Modal.Backdrop isOpen={!!viewingSkill} onOpenChange={(o) => { if (!o) setViewingSkill(null); }}>
        <Modal.Container>
          <Modal.Dialog className="max-w-2xl">
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>
                {viewingSkill?.displayName ?? viewingSkill?.name}
              </Modal.Heading>
            </Modal.Header>
            <Modal.Body className="space-y-4 max-h-[60vh] overflow-y-auto">
              {viewingSkill && (
                <>
                  <div className="flex items-center gap-4 text-sm">
                    <Chip size="sm" variant="soft" color={viewingSkill.scope === "SYSTEM" ? "accent" : "success"}>
                      {t(`scope.${viewingSkill.scope}`)}
                    </Chip>
                    <Chip size="sm" variant="soft" color={viewingSkill.enabled ? "success" : "default"}>
                      {viewingSkill.enabled ? t("status.enabled") : t("status.disabled")}
                    </Chip>
                    <span className="text-xs text-muted">v{viewingSkill.version}</span>
                    <span className="text-xs text-muted">{formatDate(viewingSkill.createdAt)}</span>
                  </div>
                  <div>
                    <Label className="text-xs text-muted">{t("view.nameLabel")}</Label>
                    <p className="font-mono text-sm">{viewingSkill.name}</p>
                  </div>
                  <div>
                    <Label className="text-xs text-muted">{t("create.descriptionLabel")}</Label>
                    <p className="text-sm">{viewingSkill.description}</p>
                  </div>
                  {viewingSkill.content && (
                    <div>
                      <Label className="text-xs text-muted">{t("create.contentLabel")}</Label>
                      <pre className="mt-1 max-h-64 overflow-y-auto rounded-lg bg-muted/8 p-3 text-xs leading-relaxed whitespace-pre-wrap">
                        {viewingSkill.content}
                      </pre>
                    </div>
                  )}
                  {viewingSkill.tags && viewingSkill.tags.length > 0 && (
                    <div>
                      <Label className="text-xs text-muted">{t("create.tagsLabel")}</Label>
                      <div className="mt-1.5 flex flex-wrap gap-1.5">
                        {viewingSkill.tags.map((tag) => (
                          <Chip key={tag} size="sm" variant="soft">
                            {tag}
                          </Chip>
                        ))}
                      </div>
                    </div>
                  )}
                </>
              )}
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" slot="close">
                {t("close")}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>

      {/* ── Delete Confirm Modal ─────────────────────────────────────────────── */}
      <Modal.Backdrop isOpen={!!deletingSkill} onOpenChange={(o) => { if (!o) setDeletingSkill(null); }}>
        <Modal.Container>
          <Modal.Dialog className="max-w-sm">
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>{t("delete.title")}</Modal.Heading>
            </Modal.Header>
            <Modal.Body>
              <p className="text-sm text-muted">
                {t("delete.message", { name: deletingSkill?.name ?? "" })}
              </p>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" onPress={() => setDeletingSkill(null)}>
                {t("cancel")}
              </Button>
              <Button variant="danger" onPress={handleDelete} isPending={isSubmitting}>
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{t("delete.confirm")}</>)}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </div>
  );
}
