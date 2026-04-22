#!/usr/bin/env python3

from __future__ import annotations

import re
from collections import defaultdict, OrderedDict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple


ROOT = Path(__file__).resolve().parents[1]
DOCS_DIR = ROOT / "docs" / "apis"
DATE = "2026-03-12"

MODULE_ORDER = [
    "actionow-common",
    "actionow-gateway",
    "actionow-user",
    "actionow-workspace",
    "actionow-wallet",
    "actionow-billing",
    "actionow-project",
    "actionow-ai",
    "actionow-task",
    "actionow-collab",
    "actionow-system",
    "actionow-canvas",
    "actionow-agent",
]

SIMPLE_TYPES = {
    "String",
    "Integer",
    "Long",
    "Boolean",
    "Double",
    "Float",
    "BigDecimal",
    "BigInteger",
    "Object",
    "Map",
    "HashMap",
    "LinkedHashMap",
    "List",
    "ArrayList",
    "Set",
    "HashSet",
    "Page",
    "IPage",
    "Result",
    "Void",
    "MultipartFile",
    "Resource",
    "InputStream",
    "OutputStream",
    "LocalDate",
    "LocalDateTime",
    "Instant",
    "Date",
    "byte[]",
    "byte",
    "int",
    "long",
    "double",
    "float",
    "boolean",
    "short",
    "char",
}

ANNOTATION_IGNORE = {
    "RestController",
    "RequiredArgsConstructor",
    "Slf4j",
    "Data",
    "Builder",
    "NoArgsConstructor",
    "AllArgsConstructor",
    "EqualsAndHashCode",
    "ToString",
    "SuppressWarnings",
    "Validated",
    "Schema",
}

AUTH_ANNOTATION_DESC = {
    "IgnoreAuth": "无需登录",
    "RequireLogin": "要求登录",
    "RequireWorkspaceMember": "要求工作空间成员权限",
    "RequireSystemTenant": "要求系统租户权限",
    "RequireAdmin": "要求管理员权限",
}


@dataclass
class JavaField:
    name: str
    type_name: str
    description: str = ""
    required: Optional[bool] = None
    constraints: str = ""
    annotations: List[str] = field(default_factory=list)


@dataclass
class JavaModel:
    name: str
    path: Path
    package: str
    kind: str
    description: str = ""
    fields: List[JavaField] = field(default_factory=list)
    enum_values: List[Tuple[str, str]] = field(default_factory=list)


@dataclass
class ParameterInfo:
    source: str
    name: str
    type_name: str
    required: str = "-"
    default: str = "-"
    description: str = ""
    constraints: str = ""
    raw: str = ""


@dataclass
class EndpointInfo:
    summary: str
    description: str
    http_method: str
    path: str
    external_paths: List[str]
    auth: str
    endpoint_type: str
    controller_name: str
    controller_tag: str
    method_name: str
    method_signature: str
    request_body_type: Optional[str]
    response_type: str
    path_params: List[ParameterInfo] = field(default_factory=list)
    query_params: List[ParameterInfo] = field(default_factory=list)
    header_params: List[ParameterInfo] = field(default_factory=list)
    misc_params: List[ParameterInfo] = field(default_factory=list)
    source_file: Optional[Path] = None


@dataclass
class ControllerInfo:
    name: str
    path: Path
    base_path: str
    tag_name: str
    tag_description: str
    description: str
    auth: str
    endpoints: List[EndpointInfo] = field(default_factory=list)
    import_map: Dict[str, str] = field(default_factory=dict)
    package: str = ""


@dataclass
class GatewayRoute:
    route_id: str
    target_service: str
    external_pattern: str
    route_type: str
    stripped_prefix: str
    internal_prefix: str


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def normalize_space(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


def parse_javadoc(block: str) -> Tuple[str, Dict[str, str]]:
    if not block:
        return "", {}
    lines = []
    params: Dict[str, str] = {}
    for raw in block.splitlines():
        line = raw.strip()
        line = re.sub(r"^/\*\*?", "", line)
        line = re.sub(r"\*/$", "", line)
        line = re.sub(r"^\*", "", line).strip()
        if not line:
            continue
        if line.startswith("@param"):
            parts = line.split(None, 2)
            if len(parts) >= 3:
                params[parts[1]] = parts[2].strip()
            continue
        if line.startswith("@"):
            continue
        lines.append(line)
    return normalize_space(" ".join(lines)), params


def annotation_name(annotation: str) -> str:
    match = re.match(r"@([A-Za-z0-9_]+)", annotation.strip())
    return match.group(1) if match else annotation.strip()


def parse_annotation_value(annotation: str, key: str) -> Optional[str]:
    body_match = re.search(r"\((.*)\)", annotation, re.S)
    if not body_match:
        return None
    body = body_match.group(1).strip()
    explicit = re.search(rf"\b{re.escape(key)}\s*=\s*\"([^\"]+)\"", body)
    if explicit:
        return explicit.group(1)
    if key in {"value", "path"}:
        first = re.match(r"\s*\"([^\"]+)\"", body)
        if first:
            return first.group(1)
    return None


def parse_annotation_bool(annotation: str, key: str) -> Optional[bool]:
    match = re.search(rf"\b{re.escape(key)}\s*=\s*(true|false)", annotation)
    if not match:
        return None
    return match.group(1) == "true"


def parse_schema_attrs(annotation: str) -> Dict[str, str]:
    attrs = {}
    body_match = re.search(r"\((.*)\)", annotation, re.S)
    if not body_match:
        return attrs
    body = body_match.group(1)
    for key in ("description", "example", "name"):
        match = re.search(rf"\b{key}\s*=\s*\"([^\"]+)\"", body)
        if match:
            attrs[key] = match.group(1)
    required_mode = re.search(r"requiredMode\s*=\s*Schema.RequiredMode.([A-Z_]+)", body)
    if required_mode:
        attrs["requiredMode"] = required_mode.group(1)
    return attrs


def strip_generic(type_name: str) -> str:
    return re.sub(r"<.*>", "", type_name).strip()


def split_top_level(text: str, sep: str = ",") -> List[str]:
    parts = []
    current = []
    angle = paren = brace = bracket = 0
    for ch in text:
        if ch == "<":
            angle += 1
        elif ch == ">":
            angle = max(0, angle - 1)
        elif ch == "(":
            paren += 1
        elif ch == ")":
            paren = max(0, paren - 1)
        elif ch == "{":
            brace += 1
        elif ch == "}":
            brace = max(0, brace - 1)
        elif ch == "[":
            bracket += 1
        elif ch == "]":
            bracket = max(0, bracket - 1)
        if ch == sep and angle == 0 and paren == 0 and brace == 0 and bracket == 0:
            part = "".join(current).strip()
            if part:
                parts.append(part)
            current = []
            continue
        current.append(ch)
    tail = "".join(current).strip()
    if tail:
        parts.append(tail)
    return parts


def extract_type_names(type_text: str) -> List[str]:
    names = []
    for name in re.findall(r"\b([A-Z][A-Za-z0-9_]*)\b", type_text or ""):
        if name not in SIMPLE_TYPES and name not in names:
            names.append(name)
    return names


def field_required(annotations: List[str]) -> Optional[bool]:
    for ann in annotations:
        name = annotation_name(ann)
        if name in {"NotBlank", "NotNull", "NotEmpty", "Positive", "PositiveOrZero", "Min", "Max"}:
            return True
        if name == "Schema":
            attrs = parse_schema_attrs(ann)
            if attrs.get("requiredMode") == "REQUIRED":
                return True
    return None


def format_constraints(annotations: List[str]) -> str:
    constraints = []
    for ann in annotations:
        name = annotation_name(ann)
        if name == "Size":
            min_v = re.search(r"min\s*=\s*(\d+)", ann)
            max_v = re.search(r"max\s*=\s*(\d+)", ann)
            if min_v or max_v:
                constraints.append(f"长度 {min_v.group(1) if min_v else '-'}~{max_v.group(1) if max_v else '-'}")
        elif name == "Pattern":
            regexp = re.search(r"regexp\s*=\s*\"([^\"]+)\"", ann)
            if regexp:
                constraints.append(f"正则 {regexp.group(1)}")
        elif name == "Email":
            constraints.append("邮箱格式")
        elif name in {"Min", "Max"}:
            val = re.search(r"\((\d+)\)", ann)
            if not val:
                val = re.search(r"value\s*=\s*(\d+)", ann)
            if val:
                constraints.append(f"{name} {val.group(1)}")
        elif name in {"NotBlank", "NotNull", "NotEmpty"}:
            constraints.append("必填")
        elif name == "Schema":
            attrs = parse_schema_attrs(ann)
            if attrs.get("example"):
                constraints.append(f"示例 {attrs['example']}")
    return "；".join(constraints)


def collect_java_index() -> Dict[str, List[Path]]:
    index: Dict[str, List[Path]] = defaultdict(list)
    for path in ROOT.glob("actionow-*/src/main/java/**/*.java"):
        index[path.stem].append(path)
    for path in ROOT.glob("actionow-common/*/src/main/java/**/*.java"):
        index[path.stem].append(path)
    return index


def parse_application_info(module_dir: Path) -> Tuple[str, Optional[str]]:
    path = module_dir / "src/main/resources/application.yml"
    if not path.exists():
        return module_dir.name, None
    text = read_text(path)
    name_match = re.search(r"application:\s*\n\s*name:\s*([\w-]+)", text)
    if not name_match:
        name_match = re.search(r"name:\s*([\w-]+)", text)
    port_match = re.search(r"server:\s*\n\s*port:\s*(\d+)", text)
    return (name_match.group(1) if name_match else module_dir.name, port_match.group(1) if port_match else None)


def parse_gateway_routes() -> Dict[str, List[GatewayRoute]]:
    path = ROOT / "actionow-gateway/src/main/resources/application.yml"
    text = read_text(path)
    routes: List[GatewayRoute] = []
    blocks = re.split(r"\n\s*- id:\s*", "\n" + text)
    for block in blocks[1:]:
        lines = block.splitlines()
        route_id = lines[0].strip()
        uri_match = re.search(r"\n\s*uri:\s*([^\n]+)", "\n" + block)
        if not uri_match:
            continue
        uri = uri_match.group(1).strip()
        target_match = re.search(r"actionow-[\w-]+", uri)
        if not target_match:
            continue
        target = target_match.group(0)
        if route_id.endswith("-docs"):
            continue
        pred_match = re.search(r"predicates:\s*\n\s*- Path=([^\n]+)", block)
        if not pred_match:
            continue
        path_expr = pred_match.group(1).strip().split(",")[0]
        pattern = path_expr.strip()
        strip_match = re.search(r"StripPrefix=(\d+)", block)
        rewrite_match = re.search(r"RewritePath=([^,]+),\s*/\$\{segment\}", block)
        if strip_match:
            strip = int(strip_match.group(1))
            segs = [seg for seg in pattern.split("/") if seg and seg != "**"]
            stripped = "/" + "/".join(segs[:strip]) if segs[:strip] else ""
            internal = "/" + "/".join(segs[strip:]) if segs[strip:] else ""
            routes.append(GatewayRoute(route_id, target, pattern, "strip", stripped, internal))
        elif rewrite_match:
            prefix = rewrite_match.group(1).strip()
            routes.append(GatewayRoute(route_id, target, pattern, "rewrite", prefix, ""))
        else:
            routes.append(GatewayRoute(route_id, target, pattern, "direct", "", pattern.replace("/**", "")))
    grouped: Dict[str, List[GatewayRoute]] = defaultdict(list)
    for route in routes:
        grouped[route.target_service].append(route)
    return grouped


def parse_gateway_whitelist() -> List[str]:
    path = ROOT / "actionow-gateway/src/main/resources/application.yml"
    lines = read_text(path).splitlines()
    items: List[str] = []
    in_whitelist = False
    whitelist_indent = None
    for line in lines:
        if re.match(r"\s*whitelist:\s*$", line):
            in_whitelist = True
            whitelist_indent = len(line) - len(line.lstrip())
            continue
        if in_whitelist:
            indent = len(line) - len(line.lstrip())
            stripped = line.strip()
            if stripped.startswith("- "):
                items.append(stripped[2:].strip())
                continue
            if stripped and indent <= (whitelist_indent or 0):
                break
    return items


def compute_external_paths(internal_path: str, routes: List[GatewayRoute]) -> List[str]:
    results = []
    for route in routes:
        if route.route_type in {"strip", "rewrite"}:
            if route.internal_prefix and not internal_path.startswith(route.internal_prefix):
                continue
            ext = f"{route.stripped_prefix}{internal_path}"
            ext = re.sub(r"//+", "/", ext)
            if ext not in results:
                results.append(ext)
        elif route.route_type == "direct":
            if route.internal_prefix and internal_path.startswith(route.internal_prefix):
                results.append(internal_path)
    return results


def resolve_model_path(type_name: str, import_map: Dict[str, str], current_package: str, current_module: str, java_index: Dict[str, List[Path]]) -> Optional[Path]:
    type_name = strip_generic(type_name).replace("[]", "")
    if type_name in SIMPLE_TYPES or not re.match(r"^[A-Za-z_][A-Za-z0-9_]*$", type_name):
        return None
    if type_name in import_map:
        fqcn = import_map[type_name]
        candidate = ROOT / "/".join(fqcn.split("."))
        candidate = candidate.with_suffix(".java")
        if candidate.exists():
            return candidate
    current_pkg_candidate = ROOT / "/".join(current_package.split(".")) / f"{type_name}.java"
    if current_pkg_candidate.exists():
        return current_pkg_candidate
    candidates = java_index.get(type_name, [])
    if not candidates:
        return None
    for candidate in candidates:
        if current_module in str(candidate):
            return candidate
    return candidates[0]


def parse_java_model(path: Path, cache: Dict[Path, JavaModel]) -> Optional[JavaModel]:
    if path in cache:
        return cache[path]
    if not path.exists():
        return None
    text = read_text(path)
    package_match = re.search(r"package\s+([\w.]+);", text)
    package = package_match.group(1) if package_match else ""
    class_match = re.search(r"(public\s+)?(class|enum)\s+(\w+)", text)
    if not class_match:
        return None
    kind = class_match.group(2)
    name = class_match.group(3)
    description_match = re.search(r"/\*\*(.*?)\*/\s*(?:@[^\n]+\s*)*(public\s+)?(?:class|enum)\s+" + re.escape(name), text, re.S)
    description = parse_javadoc(description_match.group(1))[0] if description_match else ""
    model = JavaModel(name=name, path=path, package=package, kind=kind, description=description)
    cache[path] = model
    lines = text.splitlines()
    brace_depth = 0
    pending_javadoc = ""
    pending_annotations: List[str] = []
    in_javadoc = False
    javadoc_lines: List[str] = []
    enum_collecting = False
    for idx, line in enumerate(lines):
        stripped = line.strip()
        if in_javadoc:
            javadoc_lines.append(line)
            if "*/" in stripped:
                pending_javadoc = "\n".join(javadoc_lines)
                javadoc_lines = []
                in_javadoc = False
            continue
        if stripped.startswith("/**"):
            in_javadoc = True
            javadoc_lines = [line]
            if "*/" in stripped:
                pending_javadoc = "\n".join(javadoc_lines)
                javadoc_lines = []
                in_javadoc = False
            continue
        if stripped.startswith("@"):
            ann = stripped
            open_paren = ann.count("(") - ann.count(")")
            next_idx = idx + 1
            while open_paren > 0 and next_idx < len(lines):
                ann += " " + lines[next_idx].strip()
                open_paren = ann.count("(") - ann.count(")")
                next_idx += 1
            pending_annotations.append(ann)
            continue
        if kind == "enum" and brace_depth == 1:
            if stripped and not stripped.startswith("//") and not stripped.startswith("private"):
                if not enum_collecting:
                    enum_collecting = True
                    enum_text = stripped
                    if ";" in enum_text:
                        enum_collecting = False
                else:
                    enum_text += stripped
            if stripped.endswith(";") or stripped == ";":
                enum_collecting = False
        if brace_depth == 1 and re.match(r"private\s+(?!static)([\w<>, ?\[\].]+)\s+(\w+)\s*;", stripped):
            match = re.match(r"private\s+(?!static)([\w<>, ?\[\].]+)\s+(\w+)\s*;", stripped)
            if match:
                description_text, _ = parse_javadoc(pending_javadoc)
                schema_ann = next((a for a in pending_annotations if annotation_name(a) == "Schema"), None)
                if schema_ann:
                    attrs = parse_schema_attrs(schema_ann)
                    if attrs.get("description"):
                        description_text = attrs["description"]
                field = JavaField(
                    name=match.group(2),
                    type_name=normalize_space(match.group(1)),
                    description=description_text,
                    required=field_required(pending_annotations),
                    constraints=format_constraints(pending_annotations),
                    annotations=pending_annotations[:],
                )
                model.fields.append(field)
                pending_javadoc = ""
                pending_annotations = []
        brace_depth += line.count("{") - line.count("}")
    if kind == "enum":
        body_match = re.search(r"enum\s+\w+\s*\{(.*?)\n\}", text, re.S)
        if body_match:
            body = body_match.group(1)
            before_semicolon = body.split(";", 1)[0]
            for chunk in split_top_level(before_semicolon):
                name_match = re.match(r"([A-Z0-9_]+)", chunk.strip())
                if name_match:
                    model.enum_values.append((name_match.group(1), ""))
    return model


def extract_imports(text: str) -> Tuple[Dict[str, str], str]:
    imports = {}
    package_match = re.search(r"package\s+([\w.]+);", text)
    package = package_match.group(1) if package_match else ""
    for match in re.finditer(r"import\s+([\w.*]+);", text):
        fqcn = match.group(1)
        if fqcn.endswith(".*"):
            continue
        imports[fqcn.split(".")[-1]] = fqcn
    return imports, package


def mapping_from_annotations(annotations: List[str]) -> Tuple[str, str]:
    for ann in annotations:
        name = annotation_name(ann)
        if name in {"GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping"}:
            method = name.replace("Mapping", "").upper()
            path = parse_annotation_value(ann, "value") or parse_annotation_value(ann, "path") or ""
            return method, path
        if name == "RequestMapping":
            method_match = re.search(r"RequestMethod\.([A-Z]+)", ann)
            method = method_match.group(1) if method_match else "REQUEST"
            path = parse_annotation_value(ann, "value") or parse_annotation_value(ann, "path") or ""
            return method, path
    return "", ""


def detect_auth(annotations: List[str], fallback: str = "未显式声明") -> str:
    auth_items = []
    for ann in annotations:
        name = annotation_name(ann)
        if name in AUTH_ANNOTATION_DESC:
            desc = AUTH_ANNOTATION_DESC[name]
            raw = ann.strip()
            auth_items.append(f"`{raw}`（{desc}）")
    return "；".join(auth_items) if auth_items else fallback


def parse_parameter(raw: str, param_docs: Dict[str, str]) -> Optional[ParameterInfo]:
    text = normalize_space(raw)
    if not text:
        return None
    annotations = re.findall(r"@[^@]+?(?=(?:\s+@)|\s+(?:final\s+)?[\w<\[].+\s+\w+$|$)", text)
    cleaned = text
    for ann in annotations:
        cleaned = cleaned.replace(ann, " ")
    cleaned = normalize_space(cleaned.replace("final ", ""))
    if " " not in cleaned:
        return None
    type_name, name = cleaned.rsplit(" ", 1)
    source = "misc"
    required = "-"
    default = "-"
    desc = param_docs.get(name, "")
    constraints = ""
    for ann in annotations:
        ann_name = annotation_name(ann)
        if ann_name == "PathVariable":
            source = "path"
            pname = parse_annotation_value(ann, "value") or parse_annotation_value(ann, "name") or name
            required = "是"
            name = pname
        elif ann_name == "RequestParam":
            source = "query"
            pname = parse_annotation_value(ann, "value") or parse_annotation_value(ann, "name") or name
            name = pname
            req = parse_annotation_bool(ann, "required")
            def_val = parse_annotation_value(ann, "defaultValue")
            required = "否" if (req is False or def_val is not None) else "是"
            default = def_val or "-"
        elif ann_name == "RequestHeader":
            source = "header"
            pname = parse_annotation_value(ann, "value") or parse_annotation_value(ann, "name") or name
            name = pname
            req = parse_annotation_bool(ann, "required")
            required = "否" if req is False else "是"
        elif ann_name == "RequestBody":
            source = "body"
            req = parse_annotation_bool(ann, "required")
            required = "否" if req is False else "是"
    return ParameterInfo(source=source, name=name, type_name=type_name.strip(), required=required, default=default, description=desc, constraints=constraints, raw=text)


def merge_path(base: str, sub: str) -> str:
    if not base:
        return sub or "/"
    if not sub:
        return base or "/"
    path = f"{base.rstrip('/')}/{sub.lstrip('/')}"
    return re.sub(r"//+", "/", path)


def parse_controller(path: Path, gateway_routes: Dict[str, List[GatewayRoute]], java_index: Dict[str, List[Path]]) -> ControllerInfo:
    text = read_text(path)
    imports, package = extract_imports(text)
    module_name = path.parts[0]
    class_name = path.stem
    class_tag = class_name
    class_tag_desc = ""
    class_desc = ""
    class_path = ""
    class_auth = "未显式声明"
    controller = ControllerInfo(name=class_name, path=path, base_path="", tag_name=class_name, tag_description="", description="", auth="未显式声明", import_map=imports, package=package)
    lines = text.splitlines()
    brace_depth = 0
    pending_javadoc = ""
    pending_annotations: List[str] = []
    in_javadoc = False
    javadoc_lines: List[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        if in_javadoc:
            javadoc_lines.append(line)
            if "*/" in stripped:
                pending_javadoc = "\n".join(javadoc_lines)
                javadoc_lines = []
                in_javadoc = False
            i += 1
            continue

        if stripped.startswith("/**"):
            in_javadoc = True
            javadoc_lines = [line]
            if "*/" in stripped:
                pending_javadoc = "\n".join(javadoc_lines)
                javadoc_lines = []
                in_javadoc = False
            i += 1
            continue

        if stripped.startswith("@") and brace_depth in {0, 1}:
            ann = stripped
            balance = ann.count("(") - ann.count(")")
            j = i + 1
            while balance > 0 and j < len(lines):
                ann += " " + lines[j].strip()
                balance = ann.count("(") - ann.count(")")
                j += 1
            pending_annotations.append(ann)
            i = j
            continue

        if brace_depth == 0 and re.search(r"\bclass\s+\w+", stripped):
            class_desc, _ = parse_javadoc(pending_javadoc)
            tag_ann = next((a for a in pending_annotations if annotation_name(a) == "Tag"), None)
            if tag_ann:
                class_tag = parse_annotation_value(tag_ann, "name") or class_name
                class_tag_desc = parse_annotation_value(tag_ann, "description") or ""
            req_map = next((a for a in pending_annotations if annotation_name(a) == "RequestMapping"), None)
            if req_map:
                class_path = parse_annotation_value(req_map, "value") or parse_annotation_value(req_map, "path") or ""
            class_auth = detect_auth(pending_annotations)
            controller.base_path = class_path or "/"
            controller.tag_name = class_tag
            controller.tag_description = class_tag_desc
            controller.description = class_desc
            controller.auth = class_auth
            pending_javadoc = ""
            pending_annotations = []

        elif brace_depth == 1 and stripped.startswith("public "):
            sig_lines = [stripped]
            paren_balance = stripped.count("(") - stripped.count(")")
            j = i + 1
            if not (paren_balance <= 0 and "{" in stripped):
                while j < len(lines):
                    sig_lines.append(lines[j].strip())
                    paren_balance += lines[j].count("(") - lines[j].count(")")
                    if paren_balance <= 0 and "{" in lines[j]:
                        break
                    j += 1
            else:
                j = i
            sig_clean = normalize_space(" ".join(sig_lines).split("{")[0].strip())
            method_desc, param_docs = parse_javadoc(pending_javadoc)
            method_annotations = pending_annotations[:]
            method, sub_path = mapping_from_annotations(method_annotations)
            if method:
                operation_ann = next((a for a in method_annotations if annotation_name(a) == "Operation"), None)
                summary = parse_annotation_value(operation_ann, "summary") if operation_ann else ""
                match = re.search(r"public\s+([\w<>, ?\[\].]+)\s+(\w+)\s*\((.*)\)\s*$", sig_clean)
                if match:
                    if not summary:
                        summary = method_desc or match.group(2)
                    return_type = normalize_space(match.group(1))
                    method_name = match.group(2)
                    params_text = match.group(3)
                    params = [parse_parameter(p, param_docs) for p in split_top_level(params_text)]
                    params = [p for p in params if p]
                    request_body = next((p.type_name for p in params if p.source == "body"), None)
                    full_path = merge_path(class_path or "/", sub_path or "")
                    route_key = module_name if module_name in gateway_routes else module_name.replace("-v2", "")
                    external_paths = compute_external_paths(full_path, gateway_routes.get(route_key, []))
                    endpoint = EndpointInfo(
                        summary=summary,
                        description=method_desc,
                        http_method=method,
                        path=full_path,
                        external_paths=external_paths,
                        auth=detect_auth(method_annotations, fallback=class_auth),
                        endpoint_type="内部接口" if full_path.startswith("/internal") or "Internal" in class_name else "业务接口",
                        controller_name=class_name,
                        controller_tag=class_tag,
                        method_name=method_name,
                        method_signature=sig_clean,
                        request_body_type=request_body,
                        response_type=return_type,
                        source_file=path,
                    )
                    for param in params:
                        if param.source == "path":
                            endpoint.path_params.append(param)
                        elif param.source == "query":
                            endpoint.query_params.append(param)
                        elif param.source == "header":
                            endpoint.header_params.append(param)
                        elif param.source == "misc":
                            endpoint.misc_params.append(param)
                    controller.endpoints.append(endpoint)
            pending_javadoc = ""
            pending_annotations = []
            for k in range(i, min(j + 1, len(lines))):
                brace_depth += lines[k].count("{") - lines[k].count("}")
            i = j + 1
            continue

        elif brace_depth == 1 and stripped:
            pending_javadoc = ""
            pending_annotations = []

        brace_depth += line.count("{") - line.count("}")
        i += 1
    return controller


def result_inner_type(response_type: str) -> str:
    match = re.match(r"Result<(.+)>", response_type)
    return normalize_space(match.group(1)) if match else response_type


def markdown_table(headers: List[str], rows: List[List[str]]) -> str:
    lines = ["| " + " | ".join(headers) + " |", "|" + "|".join(["---"] * len(headers)) + "|"]
    for row in rows:
        safe_row = [cell.replace("\n", "<br>") if isinstance(cell, str) else str(cell) for cell in row]
        lines.append("| " + " | ".join(safe_row) + " |")
    return "\n".join(lines)


def render_param_table(params: List[ParameterInfo]) -> str:
    if not params:
        return "无\n"
    rows = []
    for p in params:
        rows.append([
            f"`{p.name}`",
            f"`{p.type_name}`",
            p.required,
            p.default,
            p.description or "-",
            p.constraints or "-",
        ])
    return markdown_table(["参数名", "类型", "必填", "默认值", "说明", "约束"], rows) + "\n"


def render_model_ref(type_name: Optional[str], model_cache: Dict[Path, JavaModel], import_map: Dict[str, str], package: str, module_name: str, java_index: Dict[str, List[Path]]) -> str:
    if not type_name:
        return "无\n"
    inner = normalize_space(type_name)
    model_names = extract_type_names(inner)
    rows = []
    if not model_names:
        rows.append([f"`{inner}`", "基础类型或动态结构"])
    else:
        for name in model_names:
            model_path = resolve_model_path(name, import_map, package, module_name, java_index)
            if model_path:
                rows.append([f"`{name}`", f"见当前文档的“模型定义”章节；源码：`{model_path.relative_to(ROOT).as_posix()}`"])
            else:
                rows.append([f"`{name}`", "未解析到源码定义，请结合方法签名核对"])
    return markdown_table(["模型", "说明"], rows) + "\n"


def render_model_definition(model: JavaModel) -> str:
    lines = [f"### `{model.name}`", "", f"- 源码：`{model.path.as_posix()}`", f"- 类型：`{model.kind}`"]
    if model.description:
        lines.append(f"- 说明：{model.description}")
    lines.append("")
    if model.kind == "enum":
        rows = [[f"`{name}`", desc or "-"] for name, desc in model.enum_values] or [["-", "-"]]
        lines.append(markdown_table(["枚举值", "说明"], rows))
    else:
        rows = []
        for field in model.fields:
            rows.append([
                f"`{field.name}`",
                f"`{field.type_name}`",
                "是" if field.required is True else "否" if field.required is False else "-",
                field.description or "-",
                field.constraints or "-",
            ])
        if rows:
            lines.append(markdown_table(["字段", "类型", "必填", "说明", "约束"], rows))
        else:
            lines.append("未解析到字段，建议直接查看源码定义。")
    lines.append("")
    return "\n".join(lines)


def collect_models_for_controller(controller: ControllerInfo, java_index: Dict[str, List[Path]], cache: Dict[Path, JavaModel]) -> OrderedDict[str, JavaModel]:
    models: OrderedDict[str, JavaModel] = OrderedDict()
    module_name = controller.path.parts[0]
    for endpoint in controller.endpoints:
        type_candidates = []
        if endpoint.request_body_type:
            type_candidates.append(endpoint.request_body_type)
        type_candidates.append(result_inner_type(endpoint.response_type))
        for type_name in type_candidates:
            for model_name in extract_type_names(type_name):
                model_path = resolve_model_path(model_name, controller.import_map, controller.package, module_name, java_index)
                if not model_path:
                    continue
                model = parse_java_model(model_path, cache)
                if model and model.name not in models:
                    models[model.name] = model
    return models


def render_module_doc(module_name: str, service_name: str, port: Optional[str], controllers: List[ControllerInfo], gateway_routes: Dict[str, List[GatewayRoute]], java_index: Dict[str, List[Path]], model_cache: Dict[Path, JavaModel]) -> str:
    total_endpoints = sum(len(c.endpoints) for c in controllers)
    lines = [f"# {module_name} API 文档", "", f"- 模块名称：`{module_name}`", f"- 服务名称：`{service_name}`"]
    lines.append(f"- 服务端口：`{port}`" if port else "- 服务端口：-")
    lines.extend([
        f"- 控制器数量：`{len(controllers)}`",
        f"- 接口数量：`{total_endpoints}`",
        f"- 文档生成时间：`{DATE}`",
        "- 生成依据：仅基于当前仓库源码中的 Controller / DTO / Gateway 配置自动整理，未参考历史 API 文档",
        "",
        "## 1. 模块概览",
        "",
    ])
    if module_name == "actionow-common":
        lines.extend([
            "该模块是公共基础库，当前源码中未定义对外 `Controller`，因此没有独立 HTTP API。",
            "",
            "## 2. 代码级结论",
            "",
            "- 仅提供公共能力子模块，例如 `actionow-common-core`、`actionow-common-web`、`actionow-common-security`、`actionow-common-file` 等。",
            "- 业务接口由其他服务模块暴露，本模块不单独对外发布 REST API。",
            "",
        ])
        return "\n".join(lines)
    if module_name == "actionow-gateway":
        lines.append("该模块本身未定义业务 Controller，但负责统一 API 入口、路由转发、白名单和文档聚合。")
        lines.extend(["", "## 2. 网关路由", ""])
        route_rows = []
        all_routes = [route for routes in gateway_routes.values() for route in routes]
        for route in all_routes:
            route_rows.append([f"`{route.route_id}`", f"`{route.external_pattern}`", f"`{route.target_service}`", f"`{route.route_type}`", f"`{route.stripped_prefix or '-'}`", f"`{route.internal_prefix or '-'}`"])
        if route_rows:
            lines.append(markdown_table(["路由ID", "外部路径模式", "目标服务", "转换类型", "外部前缀", "服务内前缀"], route_rows))
        lines.extend(["", "## 3. 白名单路径", ""])
        whitelist_items = parse_gateway_whitelist()
        if whitelist_items:
            for item in whitelist_items:
                lines.append(f"- `{item}`")
        else:
            lines.append("未解析到白名单路径。")
        return "\n".join(lines) + "\n"
    route_notes = gateway_routes.get(module_name, [])
    if route_notes:
        lines.append("当前模块存在网关入口，文档中的“网关路径”字段已按 `actionow-gateway` 路由规则回推。")
    else:
        lines.append("当前模块未在网关配置中解析到明确路由，以下路径为服务内 Controller 路径。")
    lines.extend(["", "## 2. 统一约定", "", "### 2.1 统一响应结构", "", "所有控制器返回值以 `Result<T>` 为主，标准字段如下：", ""])
    lines.append(markdown_table(["字段", "类型", "说明"], [["`code`", "`String`", "业务状态码"], ["`message`", "`String`", "响应消息"], ["`data`", "`T`", "业务数据"], ["`requestId`", "`String`", "请求追踪 ID"], ["`timestamp`", "`Long`", "毫秒时间戳"]]))
    lines.extend(["", "### 2.2 鉴权注解", ""])
    auth_rows = []
    auth_seen = set()
    for controller in controllers:
        for candidate in [controller.auth] + [endpoint.auth for endpoint in controller.endpoints]:
            for ann in re.findall(r"`(@[^`]+)`", candidate):
                name = annotation_name(ann)
                if name not in auth_seen:
                    auth_seen.add(name)
                    auth_rows.append([f"`{ann}`", AUTH_ANNOTATION_DESC.get(name, "按源码注解解释")])
    if auth_rows:
        lines.append(markdown_table(["源码注解", "说明"], auth_rows))
    else:
        lines.append("未解析到特殊鉴权注解。")
    lines.extend(["", "## 3. 接口目录", ""])
    catalog_rows = []
    seq = 1
    for controller in controllers:
        for endpoint in controller.endpoints:
            catalog_rows.append([
                str(seq),
                endpoint.controller_tag,
                f"`{endpoint.http_method}`",
                f"`{endpoint.path}`",
                f"`{endpoint.external_paths[0]}`" if endpoint.external_paths else "-",
                endpoint.summary,
                endpoint.endpoint_type,
            ])
            seq += 1
    if catalog_rows:
        lines.append(markdown_table(["序号", "分组", "方法", "服务内路径", "网关路径", "接口说明", "接口类型"], catalog_rows))
    else:
        lines.append("当前模块未解析到 Controller 接口。")
    lines.extend(["", "## 4. 接口详情", ""])
    seq = 1
    for controller in controllers:
        for endpoint in controller.endpoints:
            lines.extend([
                f"### 4.{seq} {endpoint.summary}",
                "",
                f"- 控制器分组：{endpoint.controller_tag}",
                f"- 控制器类：`{endpoint.controller_name}`",
                f"- 处理方法：`{endpoint.method_name}`",
                f"- HTTP 方法：`{endpoint.http_method}`",
                f"- 服务内路径：`{endpoint.path}`",
                f"- 网关路径：{', '.join(f'`{p}`' for p in endpoint.external_paths) if endpoint.external_paths else '未在网关配置中解析到'}",
                f"- 鉴权要求：{endpoint.auth}",
                f"- 接口类型：{endpoint.endpoint_type}",
                f"- 返回类型：`{endpoint.response_type}`",
                f"- 源码位置：`{endpoint.source_file.as_posix()}`",
                f"- 方法签名：`{endpoint.method_signature}`",
                "",
            ])
            if endpoint.description:
                lines.extend(["#### 接口说明", "", f"{endpoint.description}", ""])
            lines.extend(["#### Path 参数", "", render_param_table(endpoint.path_params)])
            lines.extend(["#### Query 参数", "", render_param_table(endpoint.query_params)])
            lines.extend(["#### Header 参数", "", render_param_table(endpoint.header_params)])
            if endpoint.misc_params:
                lines.extend(["#### 其他参数", "", render_param_table(endpoint.misc_params)])
            lines.extend(["#### 请求体模型", "", render_model_ref(endpoint.request_body_type, model_cache, controller.import_map, controller.package, module_name, java_index)])
            lines.extend(["#### 响应体模型", "", render_model_ref(result_inner_type(endpoint.response_type), model_cache, controller.import_map, controller.package, module_name, java_index)])
            seq += 1
    lines.extend(["", "## 5. 模型定义", ""])
    rendered_models: OrderedDict[str, JavaModel] = OrderedDict()
    for controller in controllers:
        for name, model in collect_models_for_controller(controller, java_index, model_cache).items():
            rendered_models.setdefault(name, model)
    if rendered_models:
        for model in rendered_models.values():
            lines.append(render_model_definition(model))
    else:
        lines.append("当前模块未解析到需要展开的 DTO / Enum 模型。")
    return "\n".join(lines).rstrip() + "\n"


def build_docs() -> None:
    DOCS_DIR.mkdir(parents=True, exist_ok=True)
    java_index = collect_java_index()
    gateway_module_routes = parse_gateway_routes()
    model_cache: Dict[Path, JavaModel] = {}
    docs_index_rows = []

    for order, module_name in enumerate(MODULE_ORDER, start=1):
        module_dir = ROOT / module_name
        service_name, port = parse_application_info(module_dir)
        controllers = []
        if module_name not in {"actionow-common", "actionow-gateway"}:
            for controller_path in sorted(module_dir.glob("src/main/java/**/*Controller.java")):
                controllers.append(parse_controller(controller_path.relative_to(ROOT), gateway_module_routes, java_index))
        content = render_module_doc(module_name, service_name, port, controllers, gateway_module_routes, java_index, model_cache)
        filename = f"{order:02d}-{module_name}-api.md"
        (DOCS_DIR / filename).write_text(content, encoding="utf-8")
        docs_index_rows.append([str(order), f"`{module_name}`", f"`{filename}`", f"`{len(controllers)}`", f"`{sum(len(c.endpoints) for c in controllers)}`"])

    readme_lines = [
        "# Actionow 模块 API 文档索引",
        "",
        f"- 生成时间：`{DATE}`",
        "- 生成原则：仅基于当前仓库源码生成，不参考历史 API 文档",
        "- 模块顺序：与根 `pom.xml` 中模块声明顺序保持一致",
        "- 使用说明：请以本索引列出的 `01-13` 编号文档为准，旧的平铺文档不再作为本次重建结果",
        "",
        markdown_table(["序号", "模块", "文档文件", "控制器数", "接口数"], docs_index_rows),
        "",
    ]
    (DOCS_DIR / "README.md").write_text("\n".join(readme_lines), encoding="utf-8")


if __name__ == "__main__":
    build_docs()
