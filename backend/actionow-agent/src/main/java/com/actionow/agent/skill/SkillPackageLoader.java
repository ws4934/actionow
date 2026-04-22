package com.actionow.agent.skill;

import com.actionow.agent.dto.response.SkillImportResult;
import com.actionow.agent.entity.AgentSkillEntity;
import com.actionow.agent.mapper.AgentSkillMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Skill 包加载器
 *
 * <p>支持两种 ZIP 包格式：
 *
 * <h3>格式 1：扁平文件（Actionow 默认）</h3>
 * <pre>{@code
 * script_expert.md
 * character_expert.md
 * }</pre>
 *
 * <h3>格式 2：SAA 标准目录结构</h3>
 * <pre>{@code
 * script_expert/
 *   SKILL.md          # YAML frontmatter + 正文内容
 *   references/       # 参考资料（可选）
 *     world_building.md
 *   examples/         # 使用示例（可选）
 *     basic_usage.md
 * }</pre>
 *
 * <p>文件名（去掉 .md 后缀）或目录名作为 Skill 的 {@code name} 字段。
 * 操作为 upsert：name 已存在则更新内容和版本，不存在则插入。
 *
 * @author Actionow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillPackageLoader {

    private final AgentSkillMapper skillMapper;

    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final String FRONTMATTER_DELIMITER = "---";
    private static final long MAX_DOWNLOAD_BYTES = 50 * 1024 * 1024;  // 50 MB（URL 下载）
    private static final long MAX_DECOMPRESSED_BYTES = 100 * 1024 * 1024; // 100 MB（解压后总大小上限）
    private static final long MAX_ENTRY_BYTES = 5 * 1024 * 1024;     // 5 MB（单个条目上限）
    private static final int MAX_ENTRIES = 200;                        // 最大条目数

    /**
     * 从指定 URL 下载 Skill ZIP 包并将所有 Skill upsert 到数据库（SYSTEM scope）。
     *
     * @param packageUrl ZIP 包的公开地址或预签名地址
     * @return 成功导入的 Skill 数量
     * @throws IOException 下载或解析失败
     */
    public int downloadAndApply(String packageUrl) throws IOException, InterruptedException {
        log.info("开始下载 Skill 包: {}", maskUrl(packageUrl));
        byte[] zipBytes = downloadBytes(packageUrl);
        log.info("Skill 包下载完成，大小: {} KB", zipBytes.length / 1024);

        SkillImportResult result = applyFromBytes(zipBytes, "SYSTEM", null, null);
        return result.getSuccess();
    }

    /**
     * 从 ZIP 字节数组解析并导入 Skill。
     *
     * @param zipBytes    ZIP 文件内容
     * @param scope       作用域（"SYSTEM" 或 "WORKSPACE"）
     * @param workspaceId 工作空间 ID（scope=WORKSPACE 时必填）
     * @param userId      操作者 ID（scope=WORKSPACE 时记录 creatorId）
     * @return 导入结果
     */
    public SkillImportResult applyFromBytes(byte[] zipBytes, String scope, String workspaceId, String userId) {
        List<SkillDefinition> definitions;
        try {
            definitions = parseZip(zipBytes);
        } catch (IOException e) {
            log.error("解析 ZIP 包失败: {}", e.getMessage());
            return SkillImportResult.builder()
                    .total(0).success(0).failed(1)
                    .errors(List.of("ZIP 包解析失败: " + e.getMessage()))
                    .build();
        }

        log.info("解析到 {} 个 Skill 定义, scope={}, workspaceId={}", definitions.size(), scope, workspaceId);

        List<String> errors = new ArrayList<>();
        int success = 0;
        for (SkillDefinition def : definitions) {
            try {
                upsert(def, scope, workspaceId, userId);
                success++;
            } catch (Exception e) {
                String msg = def.name + ": " + e.getMessage();
                errors.add(msg);
                log.error("导入 Skill [{}] 失败: {}", def.name, e.getMessage());
            }
        }

        log.info("Skill 包导入完成: 成功 {}/{}, scope={}", success, definitions.size(), scope);
        return SkillImportResult.builder()
                .total(definitions.size())
                .success(success)
                .failed(errors.size())
                .errors(errors)
                .build();
    }

    // ==================== ZIP 解析 ====================

    /**
     * 解析 ZIP 包，支持扁平文件和 SAA 目录两种格式。
     */
    private List<SkillDefinition> parseZip(byte[] zipBytes) throws IOException {
        // Phase 1: 读取所有条目到内存（带解压炸弹防护）
        Map<String, String> fileContents = new LinkedHashMap<>();
        long totalDecompressed = 0;
        int entryCount = 0;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    if (++entryCount > MAX_ENTRIES) {
                        throw new IOException("ZIP 包条目数超过上限: " + MAX_ENTRIES);
                    }
                    String path = normalizePath(entry.getName());
                    String content = readEntryAsString(zis, MAX_ENTRY_BYTES);
                    totalDecompressed += content.length();
                    if (totalDecompressed > MAX_DECOMPRESSED_BYTES) {
                        throw new IOException("ZIP 解压后总大小超过上限: " + MAX_DECOMPRESSED_BYTES / 1024 / 1024 + " MB");
                    }
                    fileContents.put(path, content);
                }
                zis.closeEntry();
            }
        }

        // Phase 2: 识别并构建 Skill 定义
        return buildSkillDefinitions(fileContents);
    }

    /**
     * 从文件内容映射中构建 Skill 定义列表。
     * 优先识别 SAA 目录格式（SKILL.md），其次识别扁平 .md 文件。
     */
    @SuppressWarnings("unchecked")
    private List<SkillDefinition> buildSkillDefinitions(Map<String, String> fileContents) {
        Yaml yaml = new Yaml();
        List<SkillDefinition> result = new ArrayList<>();
        Set<String> processedSkills = new HashSet<>();

        // Pass 1: SAA 目录格式 — 查找 */SKILL.md
        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            String path = entry.getKey();
            String fileName = getFileName(path);

            if (!"SKILL.md".equalsIgnoreCase(fileName)) continue;

            // 提取 Skill 名称（目录名）
            String parentPath = getParentPath(path);
            String skillName = getLastSegment(parentPath);

            if (skillName == null || !skillName.matches("^[a-z][a-z0-9_]{1,63}$")) {
                log.warn("跳过非法 Skill 目录名: {}", path);
                continue;
            }

            try {
                SkillDefinition def = parseMdFile(skillName, entry.getValue(), yaml);
                // 从子目录合并 references/ 和 examples/
                mergeSubdirectoryContent(def, parentPath, fileContents);
                result.add(def);
                processedSkills.add(skillName);
                log.debug("解析 SAA 目录格式 Skill: {}", skillName);
            } catch (Exception e) {
                log.warn("解析 Skill [{}] 的 SKILL.md 失败: {}", skillName, e.getMessage());
            }
        }

        // Pass 2: 扁平文件格式 — 根级 *.md 文件
        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            String path = entry.getKey();
            if (!path.endsWith(".md")) continue;

            String fileName = getFileName(path);
            if ("SKILL.md".equalsIgnoreCase(fileName)) continue;

            // 仅处理根级（无子目录）或一级子目录内的 .md 文件
            String skillName = fileName.substring(0, fileName.length() - 3);
            if (processedSkills.contains(skillName)) continue;

            if (!skillName.matches("^[a-z][a-z0-9_]{1,63}$")) {
                log.warn("跳过非法 Skill 文件名: {}", path);
                continue;
            }

            try {
                SkillDefinition def = parseMdFile(skillName, entry.getValue(), yaml);
                result.add(def);
                processedSkills.add(skillName);
                log.debug("解析扁平格式 Skill: {}", skillName);
            } catch (Exception e) {
                log.warn("解析 Skill 文件 [{}] 失败: {}", path, e.getMessage());
            }
        }

        return result;
    }

    /**
     * 从 SAA 标准子目录中读取 references/ 和 examples/ 内容，合并到 SkillDefinition。
     * 子目录中的 .md 文件会被转换为 JSON 对象 {title, content} 并追加到对应列表。
     */
    @SuppressWarnings("unchecked")
    private void mergeSubdirectoryContent(SkillDefinition def, String skillDirPath,
                                           Map<String, String> fileContents) {
        String refPrefix = skillDirPath + "/references/";
        String exPrefix = skillDirPath + "/examples/";

        // 收集 references/
        List<Map<String, Object>> dirRefs = new ArrayList<>();
        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            if (entry.getKey().startsWith(refPrefix) && entry.getKey().endsWith(".md")) {
                String refName = getFileName(entry.getKey());
                String title = refName.substring(0, refName.length() - 3);
                dirRefs.add(Map.of("title", title, "content", entry.getValue()));
            }
        }
        if (!dirRefs.isEmpty()) {
            if (def.references == null) def.references = new ArrayList<>();
            def.references.addAll(dirRefs);
            log.debug("Skill {} 从 references/ 目录合并 {} 个参考资料", def.name, dirRefs.size());
        }

        // 收集 examples/
        List<Map<String, Object>> dirExamples = new ArrayList<>();
        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            if (entry.getKey().startsWith(exPrefix) && entry.getKey().endsWith(".md")) {
                String exName = getFileName(entry.getKey());
                String title = exName.substring(0, exName.length() - 3);
                dirExamples.add(Map.of("title", title, "content", entry.getValue()));
            }
        }
        if (!dirExamples.isEmpty()) {
            if (def.examples == null) def.examples = new ArrayList<>();
            def.examples.addAll(dirExamples);
            log.debug("Skill {} 从 examples/ 目录合并 {} 个使用示例", def.name, dirExamples.size());
        }
    }

    // ==================== Markdown 解析 ====================

    @SuppressWarnings("unchecked")
    private SkillDefinition parseMdFile(String name, String raw, Yaml yaml) {
        SkillDefinition def = new SkillDefinition();
        def.name = name;

        // 检测 YAML frontmatter (--- ... ---)
        if (raw.startsWith(FRONTMATTER_DELIMITER)) {
            int endIdx = raw.indexOf(FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length());
            if (endIdx > 0) {
                String frontmatter = raw.substring(FRONTMATTER_DELIMITER.length(), endIdx).strip();
                String body = raw.substring(endIdx + FRONTMATTER_DELIMITER.length()).strip();

                Map<String, Object> meta = yaml.load(frontmatter);
                if (meta != null) {
                    def.displayName = (String) meta.get("displayName");
                    // SAA 标准用 name 字段（如果 displayName 为空则取 name）
                    if (def.displayName == null) {
                        def.displayName = (String) meta.get("name");
                    }
                    def.description = (String) meta.get("description");
                    def.enabled = meta.get("enabled") == null || Boolean.TRUE.equals(meta.get("enabled"));

                    Object toolIds = meta.get("groupedToolIds");
                    if (toolIds instanceof List) {
                        def.groupedToolIds = (List<String>) toolIds;
                    }

                    Object schema = meta.get("outputSchema");
                    if (schema instanceof Map) {
                        def.outputSchema = (Map<String, Object>) schema;
                    }

                    Object tags = meta.get("tags");
                    if (tags instanceof List) {
                        def.tags = (List<String>) tags;
                    }

                    Object refs = meta.get("references");
                    if (refs instanceof List) {
                        def.references = new ArrayList<>((List<Map<String, Object>>) refs);
                    }

                    Object examples = meta.get("examples");
                    if (examples instanceof List) {
                        def.examples = new ArrayList<>((List<Map<String, Object>>) examples);
                    }
                }
                def.content = body;
            } else {
                // 没有结束分隔符，整体作为 content
                def.content = raw;
            }
        } else {
            def.content = raw;
        }

        if (def.description == null) {
            def.description = name;
        }

        return def;
    }

    // ==================== 数据库操作 ====================

    /**
     * Scope 感知的 upsert。
     * SYSTEM scope：按 name 查找 → 更新或插入
     * WORKSPACE scope：按 name + workspaceId 查找 → 更新或插入
     */
    private void upsert(SkillDefinition def, String scope, String workspaceId, String userId) {
        AgentSkillEntity existing;
        if ("WORKSPACE".equals(scope) && workspaceId != null) {
            existing = skillMapper.selectByNameAndWorkspace(def.name, workspaceId);
        } else {
            existing = skillMapper.selectByName(def.name);
        }

        if (existing != null) {
            // 更新
            if (def.displayName != null) existing.setDisplayName(def.displayName);
            if (def.description != null) existing.setDescription(def.description);
            if (def.content != null) existing.setContent(def.content);
            if (def.groupedToolIds != null) existing.setGroupedToolIds(def.groupedToolIds);
            if (def.outputSchema != null) existing.setOutputSchema(def.outputSchema);
            if (def.tags != null) existing.setTags(def.tags);
            if (def.references != null) existing.setReferences(def.references);
            if (def.examples != null) existing.setExamples(def.examples);
            existing.setEnabled(def.enabled);
            existing.setVersion(existing.getVersion() + 1);
            existing.setUpdatedAt(LocalDateTime.now());
            skillMapper.updateById(existing);
            log.debug("更新 Skill: name={}, scope={}, version={}", def.name, scope, existing.getVersion());
        } else {
            // 插入
            AgentSkillEntity entity = new AgentSkillEntity();
            entity.setName(def.name);
            entity.setDisplayName(def.displayName);
            entity.setDescription(def.description);
            entity.setContent(def.content);
            entity.setGroupedToolIds(def.groupedToolIds != null ? def.groupedToolIds : List.of());
            entity.setOutputSchema(def.outputSchema);
            entity.setTags(def.tags != null ? def.tags : List.of());
            entity.setReferences(def.references != null ? def.references : List.of());
            entity.setExamples(def.examples != null ? def.examples : List.of());
            entity.setEnabled(def.enabled);
            entity.setVersion(1);
            entity.setScope(scope != null ? scope : "SYSTEM");
            entity.setWorkspaceId(workspaceId);
            entity.setCreatorId(userId);
            entity.setDeleted(0);
            skillMapper.insert(entity);
            log.debug("插入 Skill: name={}, scope={}", def.name, scope);
        }
    }

    // ==================== 工具方法 ====================

    private byte[] downloadBytes(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(DOWNLOAD_TIMEOUT)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DOWNLOAD_TIMEOUT)
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("下载 Skill 包失败，HTTP 状态码: " + response.statusCode());
        }
        byte[] body = response.body();
        if (body.length > MAX_DOWNLOAD_BYTES) {
            throw new IOException("Skill 包超过大小限制: " + body.length / 1024 + " KB（上限 50 MB）");
        }
        return body;
    }

    private String readEntryAsString(ZipInputStream zis, long maxBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        long total = 0;
        int len;
        while ((len = zis.read(buf)) != -1) {
            total += len;
            if (total > maxBytes) {
                throw new IOException("单个 ZIP 条目超过大小上限: " + maxBytes / 1024 + " KB");
            }
            baos.write(buf, 0, len);
        }
        return baos.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 规范化 ZIP 条目路径：去除前导 '/'，统一分隔符 */
    private String normalizePath(String zipEntryName) {
        String path = zipEntryName.replace('\\', '/');
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    /** 获取路径中的文件名部分 */
    private String getFileName(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    /** 获取路径的父目录 */
    private String getParentPath(String path) {
        int idx = path.lastIndexOf('/');
        return idx > 0 ? path.substring(0, idx) : "";
    }

    /** 获取路径最后一个段（目录名或文件名） */
    private String getLastSegment(String path) {
        if (path == null || path.isEmpty()) return null;
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    /** 隐藏 URL 中的查询参数（可能含签名密钥） */
    private String maskUrl(String url) {
        int queryIdx = url.indexOf('?');
        return queryIdx > 0 ? url.substring(0, queryIdx) + "?..." : url;
    }

    // ==================== 内部数据类 ====================

    private static class SkillDefinition {
        String name;
        String displayName;
        String description;
        String content;
        List<String> groupedToolIds;
        Map<String, Object> outputSchema;
        List<String> tags;
        List<Map<String, Object>> references;
        List<Map<String, Object>> examples;
        boolean enabled = true;
    }
}
