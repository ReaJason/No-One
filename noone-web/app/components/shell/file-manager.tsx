import {
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  ChevronRight,
  File,
  FileArchive,
  FileAudio,
  FileCode2,
  FileImage,
  FilePlus,
  FileText,
  FileVideo,
  Filter,
  Folder,
  FolderPlus,
  HardDrive,
  Home,
  RefreshCw,
  TerminalSquare,
  Upload,
} from "lucide-react";
import { useVirtualizer } from "@tanstack/react-virtual";
import React, { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import * as shellApi from "@/api/shell-api";
import FileEditorPane from "@/components/shell/file-editor-pane";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import { Button } from "@/components/ui/button";
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuSeparator,
  ContextMenuTrigger,
} from "@/components/ui/context-menu";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Progress } from "@/components/ui/progress";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { Spinner } from "@/components/ui/spinner";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";

type LocationKind = "cwd" | "home" | "disk";
type EntryType = "file" | "directory";
type OsFamily = "windows" | "unix";
type PendingActionType =
  | "move"
  | "copy"
  | "rename"
  | "touch"
  | "new-file"
  | "new-folder"
  | "delete"
  | "compress"
  | "extract";
type SortKey = "name" | "type" | "size" | "createdAt" | "modifiedAt" | "permissions";
type SortDir = "asc" | "desc";
type OperationKind = "upload" | "download" | "compress" | "extract" | "other";
type OperationStatus = "running" | "success" | "error" | "cancelled";

interface FileManagerProps {
  shellId: number;
  shellUrl: string;
}

interface FileManagerLocation {
  id: string;
  label: string;
  path: string;
  kind: LocationKind;
}

interface FileNode {
  name: string;
  path: string;
  parentPath: string | null;
  entryType: EntryType;
  fileType: string;
  sizeBytes: number;
  createdAt: string;
  modifiedAt: string;
  permissions: string;
}

interface FileSelectionState {
  selectedPath: string | null;
}

interface PendingActionState {
  type: PendingActionType;
  value: string;
  datetimeValue: string;
  targetPath: string | null;
}

interface FileBufferState {
  savedContent: string;
  draftContent: string;
  language: string;
  isDirty: boolean;
  readOnlyReason: string | null;
}

interface BreadcrumbSegment {
  label: string;
  path: string;
  isCurrent: boolean;
}

interface PluginRequestOptions {
  signal?: AbortSignal;
  timeout?: number;
}

interface ReadWholeFileOptions extends PluginRequestOptions {
  onBytesRead?: (count: number) => void;
}

interface OperationState {
  id: number;
  kind: OperationKind;
  status: OperationStatus;
  totalFiles: number;
  completedFiles: number;
  totalBytes: number;
  processedBytes: number;
  currentFile: string | null;
  message: string;
}

const EMPTY_SELECTION: FileSelectionState = {
  selectedPath: null,
};

const MAX_PREVIEW_EDIT_BYTES = 5 * 1024 * 1024;
const CHUNK_SIZE = 256 * 1024;
const ROW_ESTIMATE_HEIGHT = 40;
const ROW_OVERSCAN = 8;
const DEFAULT_FILE_PERMISSIONS = "-rw-r--r--";
const DEFAULT_DIR_PERMISSIONS = "drwxr-xr-x";
const OPERATION_CLEAR_DELAY_MS = 2500;

const EDITABLE_FILE_EXTENSIONS = new Set([
  "txt",
  "log",
  "md",
  "json",
  "yaml",
  "yml",
  "xml",
  "ini",
  "toml",
  "properties",
  "env",
  "conf",
  "config",
  "sh",
  "bash",
  "zsh",
  "ps1",
  "bat",
  "cmd",
  "sql",
  "html",
  "css",
  "scss",
  "less",
  "js",
  "jsx",
  "ts",
  "tsx",
  "java",
  "kt",
  "kts",
  "go",
  "py",
  "rb",
  "php",
  "rs",
  "c",
  "h",
  "cpp",
  "cc",
  "cxx",
  "hpp",
  "cs",
]);

const BINARY_FILE_EXTENSIONS = new Set([
  // Images
  "png", "jpg", "jpeg", "gif", "bmp", "ico", "webp", "svg", "tiff", "tif", "psd",
  // Audio
  "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a",
  // Video
  "mp4", "avi", "mov", "mkv", "wmv", "flv", "webm", "m4v",
  // Archives
  "zip", "tar", "gz", "bz2", "xz", "rar", "7z", "jar", "war", "ear",
  // Executables/binaries
  "exe", "dll", "so", "dylib", "bin", "class", "o", "obj", "pyc", "pyo",
  // Documents
  "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods",
  // Databases
  "db", "sqlite", "sqlite3", "mdb",
  // Fonts
  "ttf", "otf", "woff", "woff2", "eot",
  // Disk images
  "iso", "img", "dmg",
  // Other
  "dat", "swp", "swo",
]);

function createAbortError(): Error {
  if (typeof DOMException !== "undefined") {
    return new DOMException("The operation was aborted.", "AbortError");
  }
  const error = new Error("The operation was aborted.");
  (error as Error & { name: string }).name = "AbortError";
  return error;
}

function throwIfAborted(signal?: AbortSignal) {
  if (signal?.aborted) {
    throw createAbortError();
  }
}

function buildTransferSummary(
  kind: OperationKind,
  status: OperationStatus,
  completedFiles: number,
  totalFiles: number,
  processedBytes: number,
  totalBytes: number,
) {
  const verb =
    kind === "download"
      ? "Downloaded"
      : kind === "upload"
        ? "Uploaded"
        : kind === "compress"
          ? "Compressed"
          : kind === "extract"
            ? "Extracted"
            : "Processed";
  const progress = `${completedFiles}/${totalFiles} file${totalFiles === 1 ? "" : "s"} · ${formatBytes(processedBytes)}/${formatBytes(totalBytes)}`;
  if (status === "cancelled") {
    return `Cancelled. ${verb} ${progress}.`;
  }
  if (status === "success") {
    return `${verb} ${progress}.`;
  }
  if (status === "error") {
    return `${verb} interrupted at ${progress}.`;
  }
  return `${verb} ${progress}...`;
}

function getPathSeparator(osFamily: OsFamily) {
  return osFamily === "windows" ? "\\" : "/";
}

function isWindowsAbsolutePath(value: string) {
  return /^[A-Za-z]:[\\/]/.test(value) || /^[A-Za-z]:$/.test(value);
}

function isAbsolutePath(value: string, osFamily: OsFamily) {
  if (osFamily === "windows") {
    return isWindowsAbsolutePath(value) || value.startsWith("\\");
  }
  return value.startsWith("/");
}

function normalizeAbsolutePath(rawValue: string, osFamily: OsFamily) {
  if (osFamily === "windows") {
    let value = rawValue.replace(/\//g, "\\").trim();
    if (/^[A-Za-z]:$/.test(value)) {
      value = `${value}\\`;
    }

    if (!isWindowsAbsolutePath(value)) {
      if (value.startsWith("\\")) {
        value = `C:${value}`;
      } else {
        value = `C:\\${value.replace(/^\\+/, "")}`;
      }
    }

    const driveMatch = value.match(/^([A-Za-z]:)(.*)$/);
    const drive = (driveMatch?.[1] ?? "C:").toUpperCase();
    const rest = (driveMatch?.[2] ?? "").replace(/^\\+/, "");
    const segments = rest.split("\\").filter(Boolean);
    const normalized: string[] = [];

    for (const segment of segments) {
      if (segment === ".") continue;
      if (segment === "..") {
        normalized.pop();
        continue;
      }
      normalized.push(segment);
    }

    if (normalized.length === 0) {
      return `${drive}\\`;
    }
    return `${drive}\\${normalized.join("\\")}`;
  }

  const value = rawValue.replace(/\\/g, "/").trim();
  const segments = value.split("/").filter(Boolean);
  const normalized: string[] = [];

  for (const segment of segments) {
    if (segment === ".") continue;
    if (segment === "..") {
      normalized.pop();
      continue;
    }
    normalized.push(segment);
  }

  if (normalized.length === 0) {
    return "/";
  }
  return `/${normalized.join("/")}`;
}

function resolvePath(input: string, basePath: string, osFamily: OsFamily, homePath: string) {
  const trimmed = input.trim();
  if (!trimmed) return basePath;

  let candidate = trimmed;
  if (trimmed === "~") {
    candidate = homePath;
  } else if (trimmed.startsWith("~/") || trimmed.startsWith("~\\")) {
    candidate = `${homePath}${getPathSeparator(osFamily)}${trimmed.slice(2)}`;
  }

  if (!isAbsolutePath(candidate, osFamily)) {
    const sep = getPathSeparator(osFamily);
    const merged = basePath.endsWith(sep) ? `${basePath}${candidate}` : `${basePath}${sep}${candidate}`;
    return normalizeAbsolutePath(merged, osFamily);
  }

  return normalizeAbsolutePath(candidate, osFamily);
}

function getRootPath(path: string, osFamily: OsFamily) {
  if (osFamily === "windows") {
    const match = path.match(/^([A-Za-z]:)\\/);
    return match ? `${match[1]}\\` : "C:\\";
  }
  return "/";
}

function getParentPath(path: string, osFamily: OsFamily): string | null {
  const normalized = normalizeAbsolutePath(path, osFamily);
  const root = getRootPath(normalized, osFamily);
  if (normalized === root) {
    return null;
  }

  const sep = getPathSeparator(osFamily);
  const parts = normalized.split(sep).filter(Boolean);
  if (parts.length === 0) {
    return null;
  }

  if (osFamily === "windows") {
    const drive = parts[0] ?? "C:";
    if (parts.length <= 1) {
      return `${drive}\\`;
    }
    const parentParts = parts.slice(1, -1);
    return parentParts.length === 0 ? `${drive}\\` : `${drive}\\${parentParts.join("\\")}`;
  }

  const parentParts = parts.slice(0, -1);
  return parentParts.length === 0 ? "/" : `/${parentParts.join("/")}`;
}

function getBaseName(path: string, osFamily: OsFamily) {
  const normalized = normalizeAbsolutePath(path, osFamily);
  const root = getRootPath(normalized, osFamily);
  if (normalized === root) {
    return normalized;
  }
  const sep = getPathSeparator(osFamily);
  const parts = normalized.split(sep).filter(Boolean);
  return parts[parts.length - 1] ?? normalized;
}

function parseBreadcrumbs(path: string, osFamily: OsFamily): BreadcrumbSegment[] {
  const normalized = normalizeAbsolutePath(path, osFamily);
  if (osFamily === "windows") {
    const driveMatch = normalized.match(/^([A-Za-z]:\\)(.*)/);
    if (!driveMatch) return [{ label: normalized, path: normalized, isCurrent: true }];
    const root = driveMatch[1]!;
    const segments = (driveMatch[2] ?? "").split("\\").filter(Boolean);
    const crumbs: BreadcrumbSegment[] = [{ label: root, path: root, isCurrent: segments.length === 0 }];
    let acc = root;
    for (let i = 0; i < segments.length; i++) {
      acc = acc.endsWith("\\") ? `${acc}${segments[i]}` : `${acc}\\${segments[i]}`;
      crumbs.push({ label: segments[i]!, path: acc, isCurrent: i === segments.length - 1 });
    }
    return crumbs;
  }
  const segments = normalized.split("/").filter(Boolean);
  if (segments.length === 0) return [{ label: "/", path: "/", isCurrent: true }];
  const crumbs: BreadcrumbSegment[] = [{ label: "/", path: "/", isCurrent: false }];
  let acc = "";
  for (let i = 0; i < segments.length; i++) {
    acc = `${acc}/${segments[i]}`;
    crumbs.push({ label: segments[i]!, path: acc, isCurrent: i === segments.length - 1 });
  }
  return crumbs;
}

function joinPath(parentPath: string, name: string, osFamily: OsFamily) {
  const sep = getPathSeparator(osFamily);
  if (osFamily === "windows") {
    if (parentPath.endsWith("\\")) {
      return normalizeAbsolutePath(`${parentPath}${name}`, osFamily);
    }
    return normalizeAbsolutePath(`${parentPath}${sep}${name}`, osFamily);
  }
  if (parentPath === "/") {
    return normalizeAbsolutePath(`/${name}`, osFamily);
  }
  return normalizeAbsolutePath(`${parentPath}${sep}${name}`, osFamily);
}

function formatBytes(bytes?: number) {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  const digits = value >= 10 || unitIndex === 0 ? 0 : 1;
  return `${value.toFixed(digits)} ${units[unitIndex]}`;
}

function formatDateTime(value: string) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  return new Intl.DateTimeFormat(undefined, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function toDatetimeLocalValue(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const pad = (v: number) => String(v).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function readEnvCaseInsensitive(env: Record<string, unknown>, key: string) {
  const lower = key.toLowerCase();
  const entry = Object.entries(env).find(([name]) => name.toLowerCase() === lower);
  return typeof entry?.[1] === "string" ? entry[1] : "";
}

function detectOsFamily(osName: unknown, cwd: string, disks: string[]): OsFamily {
  if(typeof osName === "string" && osName.toLowerCase() === "darwin"){
    return "unix";
  }
  if (typeof osName === "string" && osName.toLowerCase().includes("win")) {
    return "windows";
  }
  if (isWindowsAbsolutePath(cwd)) {
    return "windows";
  }
  if (disks.some((disk) => isWindowsAbsolutePath(disk))) {
    return "windows";
  }
  return "unix";
}

function extractDataContainer(payload: unknown): Record<string, unknown> {
  if (!payload || typeof payload !== "object") {
    return {};
  }
  const p = payload as Record<string, unknown>;
  if (p.data && typeof p.data === "object") {
    return p.data as Record<string, unknown>;
  }
  if (p.result && typeof p.result === "object") {
    return p.result as Record<string, unknown>;
  }
  return p;
}

function deriveSystemPaths(payload: unknown) {
  const container = extractDataContainer(payload);
  const processInfo = (container.process as Record<string, unknown> | undefined) ?? {};
  const envInfo = (container.env as Record<string, unknown> | undefined) ?? {};
  const osObj = (container.os as Record<string, unknown> | undefined) ?? {};
  const osName = osObj.name;
  const rawFs = Array.isArray(container.file_systems) ? container.file_systems : [];
  const disks = rawFs
    .map((fs) => {
      if (!fs || typeof fs !== "object") return "";
      const path = (fs as Record<string, unknown>).path;
      return typeof path === "string" ? path.trim() : "";
    })
    .filter((path): path is string => path.length > 0);

  const rawCwd = typeof processInfo.cwd === "string" ? processInfo.cwd : "";
  const homeFromEnv =
    readEnvCaseInsensitive(envInfo, "HOME") ||
    readEnvCaseInsensitive(envInfo, "USERPROFILE") ||
    `${readEnvCaseInsensitive(envInfo, "HOMEDRIVE")}${readEnvCaseInsensitive(envInfo, "HOMEPATH")}`;

  const osFamily = detectOsFamily(osName, rawCwd, disks);
  console.log(osFamily);
  const fallbackRoot = osFamily === "windows" ? "C:\\" : "/";
  const cwd = normalizeAbsolutePath(rawCwd || fallbackRoot, osFamily);
  const home = normalizeAbsolutePath(homeFromEnv || fallbackRoot, osFamily);
  const normalizedDisks = Array.from(new Set((disks.length > 0 ? disks : [fallbackRoot]).map((disk) => normalizeAbsolutePath(disk, osFamily))));

  const locations: FileManagerLocation[] = [
    { id: "cwd", label: "CWD", path: cwd, kind: "cwd" },
    { id: "home", label: "Home", path: home, kind: "home" },
    ...normalizedDisks.map((path, index) => ({
      id: `disk-${index}`,
      label: `Disk ${path}`,
      path,
      kind: "disk" as const,
    })),
  ];

  return { osFamily, cwd, home, locations };
}

function inferFileType(name: string, entryType: EntryType) {
  if (entryType === "directory") return "Folder";
  const ext = name.split(".").pop()?.toLowerCase() ?? "";
  if (["md", "txt", "pdf", "doc", "docx"].includes(ext)) return "Document";
  if (["ts", "tsx", "js", "jsx", "java", "go", "py", "rs", "json", "yaml", "yml"].includes(ext)) return "Code";
  if (["png", "jpg", "jpeg", "gif", "svg", "webp"].includes(ext)) return "Image";
  if (["zip", "tar", "gz", "rar", "7z"].includes(ext)) return "Archive";
  if (["mp3", "wav", "flac"].includes(ext)) return "Audio";
  if (["mp4", "avi", "mov", "mkv"].includes(ext)) return "Video";
  return "File";
}

function getFileTypeIcon(fileType: string) {
  if (fileType === "Folder") return Folder;
  if (fileType === "Code") return FileCode2;
  if (fileType === "Image") return FileImage;
  if (fileType === "Archive") return FileArchive;
  if (fileType === "Audio") return FileAudio;
  if (fileType === "Video") return FileVideo;
  if (fileType === "Document") return FileText;
  return File;
}

function getPathExtension(path: string) {
  const normalized = path.replace(/\\/g, "/");
  const base = normalized.split("/").pop() ?? "";
  const dotIndex = base.lastIndexOf(".");
  if (dotIndex <= 0 || dotIndex === base.length - 1) {
    return "";
  }
  return base.slice(dotIndex + 1).toLowerCase();
}

function isZipArchivePath(path: string) {
  return getPathExtension(path) === "zip";
}

function stripZipExtension(name: string) {
  if (name.toLowerCase().endsWith(".zip") && name.length > 4) {
    return name.slice(0, -4);
  }
  return name;
}

function isEditableTextCodeFile(path: string, entryType: EntryType) {
  if (entryType !== "file") return false;
  const normalized = path.replace(/\\/g, "/");
  const base = normalized.split("/").pop()?.toLowerCase() ?? "";
  if (base === "") return false;
  const ext = getPathExtension(path);
  // If extension is a known binary type, reject
  if (ext.length > 0 && BINARY_FILE_EXTENSIONS.has(ext)) return false;
  // Allow everything else: no extension, known text ext, unknown ext
  return true;
}

function resolveMonacoLanguage(path: string) {
  const normalized = path.replace(/\\/g, "/");
  const base = normalized.split("/").pop()?.toLowerCase() ?? "";
  if (base === "dockerfile") return "dockerfile";
  if (base === "makefile") return "makefile";
  if (base === ".gitignore" || base === ".env") return "plaintext";

  const ext = getPathExtension(path);
  if (!ext) return "plaintext";
  if (ext === "md") return "markdown";
  if (["txt", "log", "conf", "config"].includes(ext)) return "plaintext";
  if (ext === "json") return "json";
  if (["yaml", "yml"].includes(ext)) return "yaml";
  if (ext === "xml") return "xml";
  if (["ini", "properties", "toml", "env"].includes(ext)) return "plaintext";
  if (["sh", "bash", "zsh", "ps1", "bat", "cmd"].includes(ext)) return "shell";
  if (ext === "sql") return "sql";
  if (ext === "html") return "html";
  if (["css", "scss", "less"].includes(ext)) return "css";
  if (["js", "jsx"].includes(ext)) return "javascript";
  if (["ts", "tsx"].includes(ext)) return "typescript";
  if (ext === "java") return "java";
  if (["kt", "kts"].includes(ext)) return "kotlin";
  if (ext === "go") return "go";
  if (ext === "py") return "python";
  if (ext === "rb") return "ruby";
  if (ext === "php") return "php";
  if (ext === "rs") return "rust";
  if (["c", "h"].includes(ext)) return "c";
  if (["cpp", "cc", "cxx", "hpp"].includes(ext)) return "cpp";
  if (ext === "cs") return "csharp";
  return "plaintext";
}

function parseFileNode(raw: unknown, osFamily: OsFamily): FileNode | null {
  if (!raw || typeof raw !== "object") {
    return null;
  }
  const node = raw as Record<string, unknown>;
  const path = typeof node.path === "string" ? normalizeAbsolutePath(node.path, osFamily) : "";
  if (!path) {
    return null;
  }
  const name = typeof node.name === "string" && node.name.length > 0 ? node.name : getBaseName(path, osFamily);
  const entryType: EntryType = node.entryType === "directory" ? "directory" : "file";
  const fileType = typeof node.fileType === "string" && node.fileType.length > 0 ? node.fileType : inferFileType(name, entryType);

  return {
    name,
    path,
    parentPath:
      typeof node.parentPath === "string" && node.parentPath.length > 0
        ? normalizeAbsolutePath(node.parentPath, osFamily)
        : getParentPath(path, osFamily),
    entryType,
    fileType,
    sizeBytes: typeof node.sizeBytes === "number" ? node.sizeBytes : 0,
    createdAt: typeof node.createdAt === "string" ? node.createdAt : new Date().toISOString(),
    modifiedAt: typeof node.modifiedAt === "string" ? node.modifiedAt : new Date().toISOString(),
    permissions:
      typeof node.permissions === "string"
        ? node.permissions
        : entryType === "directory"
          ? DEFAULT_DIR_PERMISSIONS
          : DEFAULT_FILE_PERMISSIONS,
  };
}

function numbersToBytes(raw: unknown): Uint8Array {
  if (!Array.isArray(raw)) {
    return new Uint8Array();
  }
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) {
    const value = raw[i];
    if (typeof value !== "number" || !Number.isFinite(value)) {
      throw new Error(`Invalid bytes payload at index ${i}`);
    }
    const normalized = Math.trunc(value);
    if (normalized < 0 || normalized > 255) {
      throw new Error(`Invalid byte value at index ${i}: ${value}`);
    }
    bytes[i] = normalized;
  }
  return bytes;
}

function bytesToNumbers(bytes: Uint8Array): number[] {
  return Array.from(bytes, (b) => b);
}

function concatBytes(chunks: Uint8Array[]): Uint8Array {
  const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  const merged = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.length;
  }
  return merged;
}

function decodeText(bytes: Uint8Array): string {
  const decoder = new TextDecoder("utf-8", { fatal: false });
  return decoder.decode(bytes);
}

function encodeText(content: string): Uint8Array {
  const encoder = new TextEncoder();
  return encoder.encode(content);
}

function downloadBinaryFile(filename: string, bytes: Uint8Array) {
  const normalized = new Uint8Array(bytes.length);
  normalized.set(bytes);
  const blob = new Blob([normalized]);
  const url = window.URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.URL.revokeObjectURL(url);
}

function ensureResultPayload(payload: unknown): Record<string, unknown> {
  const container = extractDataContainer(payload);
  const code = container.code;
  if (typeof code === "number" && code !== 0) {
    throw new Error(typeof container.error === "string" ? container.error : "Plugin dispatch failed");
  }
  const result = (container.data as Record<string, unknown> | undefined) ?? container;
  if (typeof result.error === "string" && result.error.length > 0) {
    throw new Error(result.error);
  }
  return result;
}

export default function FileManager({ shellId, shellUrl: _shellUrl }: FileManagerProps) {
  const [osFamily, setOsFamily] = useState<OsFamily>("unix");
  const [locations, setLocations] = useState<FileManagerLocation[]>([]);
  const [entries, setEntries] = useState<FileNode[]>([]);
  const [fileBuffersByPath, setFileBuffersByPath] = useState<Map<string, FileBufferState>>(new Map());
  const [currentPath, setCurrentPath] = useState("/");
  const [pathInput, setPathInput] = useState("/");
  const [activeLocationId, setActiveLocationId] = useState<string | null>(null);
  const [openedFilePath, setOpenedFilePath] = useState<string | null>(null);
  const [unsavedDialogOpen, setUnsavedDialogOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [selection, setSelection] = useState<FileSelectionState>(EMPTY_SELECTION);
  const [contextMenuTargetPath, setContextMenuTargetPath] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<PendingActionState | null>(null);
  const [sortKey, setSortKey] = useState<SortKey>("name");
  const [sortDir, setSortDir] = useState<SortDir>("asc");
  const [nameFilter, setNameFilter] = useState("");
  const [listVersion, setListVersion] = useState(0);
  const [operationState, setOperationState] = useState<OperationState | null>(null);

  const pendingNavigationRef = useRef<(() => void) | null>(null);
  const uploadInputRef = useRef<HTMLInputElement | null>(null);
  const listScrollRef = useRef<HTMLDivElement | null>(null);
  const latestListRequestIdRef = useRef(0);
  const abortControllerRef = useRef<AbortController | null>(null);
  const activeOperationIdRef = useRef<number | null>(null);
  const operationSeqRef = useRef(0);
  const operationClearTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const homePath = useMemo(() => {
    return locations.find((location) => location.kind === "home")?.path ?? (osFamily === "windows" ? "C:\\" : "/");
  }, [locations, osFamily]);

  const currentEntries = useMemo(() => {
    const q = nameFilter.trim().toLowerCase();
    let list = entries;
    if (q) {
      list = list.filter((entry) => entry.name.toLowerCase().includes(q));
    }
    const sorted = [...list].sort((a, b) => {
      if (a.entryType === "directory" && b.entryType !== "directory") return -1;
      if (a.entryType !== "directory" && b.entryType === "directory") return 1;
      let cmp = 0;
      if (sortKey === "name") cmp = a.name.localeCompare(b.name);
      else if (sortKey === "type") cmp = a.fileType.localeCompare(b.fileType);
      else if (sortKey === "size") cmp = a.sizeBytes - b.sizeBytes;
      else if (sortKey === "createdAt") cmp = a.createdAt.localeCompare(b.createdAt);
      else if (sortKey === "modifiedAt") cmp = a.modifiedAt.localeCompare(b.modifiedAt);
      else if (sortKey === "permissions") cmp = a.permissions.localeCompare(b.permissions);
      return sortDir === "asc" ? cmp : -cmp;
    });
    return sorted;
  }, [entries, nameFilter, sortKey, sortDir]);

  const totalEntriesCount = entries.length;
  const entryByPath = useMemo(() => {
    const map = new Map<string, FileNode>();
    for (const entry of entries) {
      map.set(entry.path, entry);
    }
    return map;
  }, [entries]);
  const selectedSingleNode = selection.selectedPath ? (entryByPath.get(selection.selectedPath) ?? null) : null;
  const contextMenuTargetNode = contextMenuTargetPath
    ? (entryByPath.get(contextMenuTargetPath) ?? null)
    : null;
  const selectedCount = selectedSingleNode ? 1 : 0;

  const rowVirtualizer = useVirtualizer({
    count: currentEntries.length,
    getScrollElement: () => listScrollRef.current,
    estimateSize: () => ROW_ESTIMATE_HEIGHT,
    getItemKey: (index) => currentEntries[index]?.path ?? index,
    overscan: ROW_OVERSCAN,
  });
  const virtualRows = rowVirtualizer.getVirtualItems();
  const paddingTop = virtualRows.length > 0 ? virtualRows[0]!.start : 0;
  const paddingBottom =
    virtualRows.length > 0
      ? rowVirtualizer.getTotalSize() - virtualRows[virtualRows.length - 1]!.end
      : 0;

  const openedFileBuffer = useMemo(() => {
    if (!openedFilePath) return null;
    return fileBuffersByPath.get(openedFilePath) ?? null;
  }, [openedFilePath, fileBuffersByPath]);

  const operationProgressPercent = useMemo(() => {
    if (!operationState) {
      return 0;
    }
    if (operationState.totalBytes > 0) {
      const ratio = operationState.processedBytes / operationState.totalBytes;
      return Math.max(0, Math.min(100, Math.round(ratio * 100)));
    }
    if (operationState.totalFiles > 0) {
      const ratio = operationState.completedFiles / operationState.totalFiles;
      return Math.max(0, Math.min(100, Math.round(ratio * 100)));
    }
    return 0;
  }, [operationState]);

  const isLongTaskRunning =
    operationState?.status === "running" &&
    (
      operationState.kind === "upload" ||
      operationState.kind === "download" ||
      operationState.kind === "compress" ||
      operationState.kind === "extract"
    );

  const clearOperationTimer = useCallback(() => {
    if (operationClearTimerRef.current == null) {
      return;
    }
    clearTimeout(operationClearTimerRef.current);
    operationClearTimerRef.current = null;
  }, []);

  const scheduleOperationClear = useCallback(
    (operationId: number) => {
      clearOperationTimer();
      operationClearTimerRef.current = setTimeout(() => {
        setOperationState((prev) => (prev?.id === operationId ? null : prev));
        if (activeOperationIdRef.current === operationId) {
          activeOperationIdRef.current = null;
        }
      }, OPERATION_CLEAR_DELAY_MS);
    },
    [clearOperationTimer],
  );

  const startOperation = useCallback(
    (kind: OperationKind, totalFiles: number, totalBytes: number, message: string) => {
      clearOperationTimer();
      operationSeqRef.current += 1;
      const operationId = operationSeqRef.current;
      activeOperationIdRef.current = operationId;
      setOperationState({
        id: operationId,
        kind,
        status: "running",
        totalFiles,
        completedFiles: 0,
        totalBytes,
        processedBytes: 0,
        currentFile: null,
        message,
      });
      return operationId;
    },
    [clearOperationTimer],
  );

  const updateOperationProgress = useCallback(
    (operationId: number, patch: Partial<OperationState>) => {
      setOperationState((prev) => {
        if (!prev || prev.id !== operationId) {
          return prev;
        }
        return { ...prev, ...patch };
      });
    },
    [],
  );

  const finishOperation = useCallback(
    (operationId: number, status: Extract<OperationStatus, "success" | "error">, message: string, patch: Partial<OperationState> = {}) => {
      clearOperationTimer();
      setOperationState((prev) => {
        if (!prev || prev.id !== operationId) {
          return prev;
        }
        return {
          ...prev,
          ...patch,
          status,
          currentFile: null,
          message,
        };
      });
      if (status === "success") {
        scheduleOperationClear(operationId);
      }
      if (activeOperationIdRef.current === operationId) {
        activeOperationIdRef.current = null;
      }
    },
    [clearOperationTimer, scheduleOperationClear],
  );

  const markOperationCancelled = useCallback(
    (operationId: number) => {
      let shouldSchedule = false;
      clearOperationTimer();
      setOperationState((prev) => {
        if (!prev || prev.id !== operationId) {
          return prev;
        }
        if (prev.status === "cancelled") {
          return prev;
        }
        shouldSchedule = true;
        return {
          ...prev,
          status: "cancelled",
          currentFile: null,
          message: buildTransferSummary(
            prev.kind,
            "cancelled",
            prev.completedFiles,
            prev.totalFiles,
            prev.processedBytes,
            prev.totalBytes,
          ),
        };
      });
      if (shouldSchedule) {
        scheduleOperationClear(operationId);
      }
      if (activeOperationIdRef.current === operationId) {
        activeOperationIdRef.current = null;
      }
    },
    [clearOperationTimer, scheduleOperationClear],
  );

  const cancelOperation = useCallback(() => {
    const controller = abortControllerRef.current;
    const operationId = activeOperationIdRef.current;
    if (!controller || operationId == null) {
      return;
    }
    controller.abort();
    markOperationCancelled(operationId);
  }, [markOperationCancelled]);

  const runCancelableTask = useCallback(
    async <T,>(
      kind: Extract<OperationKind, "upload" | "download" | "compress" | "extract">,
      totalFiles: number,
      totalBytes: number,
      run: (signal: AbortSignal, operationId: number) => Promise<T>,
      onSuccess: (result: T) => string,
      onAfterSuccess?: (result: T) => Promise<void> | void,
    ) => {
      if (abortControllerRef.current) {
        toast.warning("Another file operation is running.");
        return false;
      }

      const controller = new AbortController();
      abortControllerRef.current = controller;
      const operationId = startOperation(
        kind,
        totalFiles,
        totalBytes,
        buildTransferSummary(kind, "running", 0, totalFiles, 0, totalBytes),
      );

      try {
        const result = await run(controller.signal, operationId);
        const successMessage = onSuccess(result);
        finishOperation(operationId, "success", successMessage);
        if (onAfterSuccess) {
          try {
            await onAfterSuccess(result);
          } catch (error) {
            const message = error instanceof Error ? error.message : "Post-operation refresh failed";
            toast.error(message);
          }
        }
        return true;
      } catch (error) {
        if (shellApi.isAbortError(error) || controller.signal.aborted) {
          markOperationCancelled(operationId);
          return false;
        }
        const message = error instanceof Error ? error.message : "Operation failed";
        finishOperation(operationId, "error", message);
        toast.error(message);
        return false;
      } finally {
        if (abortControllerRef.current === controller) {
          abortControllerRef.current = null;
        }
      }
    },
    [finishOperation, markOperationCancelled, startOperation],
  );

  const dispatchFileManager = useCallback(
    async (args: Record<string, unknown>, options: PluginRequestOptions = {}) => {
      const payload = await shellApi.dispatchPlugin({
        id: shellId,
        pluginId: "file-manager",
        args,
      }, options);
      return ensureResultPayload(payload);
    },
    [shellId],
  );

  const listDirectory = useCallback(
    async (path: string) => {
      const requestId = ++latestListRequestIdRef.current;
      const result = await dispatchFileManager({ op: "list", path });
      if (requestId !== latestListRequestIdRef.current) {
        return normalizeAbsolutePath(path, osFamily);
      }
      const parsedPathRaw = typeof result.path === "string" ? result.path : path;
      const parsedPath = normalizeAbsolutePath(parsedPathRaw, osFamily);
      const rawEntries = Array.isArray(result.entries) ? result.entries : [];
      const nextEntries = rawEntries
        .map((item) => parseFileNode(item, osFamily))
        .filter((item): item is FileNode => item != null);
      // Reset scroll position before setting state so the virtualizer sees
      // scrollTop=0 during the next render and computes correct virtual items.
      if (listScrollRef.current) {
        listScrollRef.current.scrollTop = 0;
      }
      setEntries(nextEntries);
      setCurrentPath(parsedPath);
      setPathInput(parsedPath);
      setSelection(EMPTY_SELECTION);
      setContextMenuTargetPath(null);
      setNameFilter("");
      setListVersion((prev) => prev + 1);
      return parsedPath;
    },
    [dispatchFileManager, osFamily],
  );

  const statPath = useCallback(
    async (path: string) => {
      const result = await dispatchFileManager({ op: "stat", path });
      const entry = parseFileNode(result.entry, osFamily);
      if (!entry) {
        throw new Error(`Invalid stat result for ${path}`);
      }
      return entry;
    },
    [dispatchFileManager, osFamily],
  );

  const readAllBytes = useCallback(
    async (path: string, maxBytes?: number, options: PluginRequestOptions = {}) => {
      const args: Record<string, unknown> = { op: "read-all", path };
      if (typeof maxBytes === "number") {
        args.maxBytes = maxBytes;
      }
      const result = await dispatchFileManager(args, options);
      return {
        bytes: numbersToBytes(result.bytes),
        fileSize: typeof result.fileSize === "number" ? result.fileSize : 0,
      };
    },
    [dispatchFileManager],
  );

  const readChunk = useCallback(
    async (path: string, offset: number, length: number, options: PluginRequestOptions = {}) => {
      const result = await dispatchFileManager({ op: "read-chunk", path, offset, length }, options);
      return {
        bytes: numbersToBytes(result.bytes),
        nextOffset: typeof result.nextOffset === "number" ? result.nextOffset : offset,
        eof: Boolean(result.eof),
        fileSize: typeof result.fileSize === "number" ? result.fileSize : 0,
      };
    },
    [dispatchFileManager],
  );

  const writeAllBytes = useCallback(
    async (path: string, bytes: Uint8Array, options: PluginRequestOptions = {}) => {
      await dispatchFileManager({
        op: "write-all",
        path,
        bytes: bytesToNumbers(bytes),
        createParent: true,
      }, options);
    },
    [dispatchFileManager],
  );

  const writeChunk = useCallback(
    async (path: string, offset: number, bytes: Uint8Array, truncate: boolean, options: PluginRequestOptions = {}) => {
      await dispatchFileManager({
        op: "write-chunk",
        path,
        offset,
        bytes: bytesToNumbers(bytes),
        truncate,
        createParent: true,
      }, options);
    },
    [dispatchFileManager],
  );

  const readWholeFile = useCallback(
    async (path: string, fileSize: number, options: ReadWholeFileOptions = {}) => {
      const { signal, timeout, onBytesRead } = options;
      throwIfAborted(signal);
      if (fileSize <= MAX_PREVIEW_EDIT_BYTES) {
        const result = await readAllBytes(path, MAX_PREVIEW_EDIT_BYTES, { signal, timeout });
        throwIfAborted(signal);
        if (result.bytes.length > 0) {
          onBytesRead?.(result.bytes.length);
        }
        return result.bytes;
      }
      const chunks: Uint8Array[] = [];
      let offset = 0;
      while (true) {
        throwIfAborted(signal);
        const chunkResult = await readChunk(path, offset, CHUNK_SIZE, { signal, timeout });
        if (chunkResult.bytes.length > 0) {
          chunks.push(chunkResult.bytes);
          onBytesRead?.(chunkResult.bytes.length);
        }
        offset = chunkResult.nextOffset;
        if (chunkResult.eof) {
          break;
        }
      }
      return concatBytes(chunks);
    },
    [readAllBytes, readChunk],
  );

  const setSelectedPath = useCallback((path: string | null) => {
    setSelection({ selectedPath: path });
  }, []);

  const navigateToDirectory = useCallback(
    async (path: string) => {
      setLoading(true);
      try {
        await listDirectory(path);
      } catch (error) {
        const message = error instanceof Error ? error.message : "Failed to load directory";
        toast.error(message);
      } finally {
        setLoading(false);
      }
    },
    [listDirectory],
  );

  const initializeFromSystemInfo = useCallback(async () => {
    const payload = await shellApi.dispatchPlugin({ id: shellId, pluginId: "system-info" });
    console.log(payload)
    const parsed = deriveSystemPaths(payload);
    setOsFamily(parsed.osFamily);
    setLocations(parsed.locations);
    setActiveLocationId("cwd");
    await listDirectory(parsed.cwd);
  }, [listDirectory, shellId]);

  useEffect(() => {
    let disposed = false;

    const bootstrap = async () => {
      setLoading(true);
      try {
        await initializeFromSystemInfo();
      } catch (error) {
        if (!disposed) {
          const message = error instanceof Error ? error.message : "Failed to initialize file manager";
          toast.error(message);
        }
      } finally {
        if (!disposed) {
          setLoading(false);
        }
      }
    };

    void bootstrap();

    return () => {
      disposed = true;
    };
  }, [initializeFromSystemInfo]);

  useLayoutEffect(() => {
    if (listVersion <= 0) {
      return;
    }
    listScrollRef.current?.scrollTo({ top: 0, behavior: "auto" });
    rowVirtualizer.scrollToOffset(0);
    rowVirtualizer.measure();
    const raf = requestAnimationFrame(() => {
      rowVirtualizer.measure();
    });
    return () => cancelAnimationFrame(raf);
  }, [listVersion, rowVirtualizer]);

  useEffect(() => {
    return () => {
      abortControllerRef.current?.abort();
      clearOperationTimer();
    };
  }, [clearOperationTimer]);

  const runPendingNavigation = useCallback(() => {
    const pending = pendingNavigationRef.current;
    pendingNavigationRef.current = null;
    setUnsavedDialogOpen(false);
    if (pending) {
      pending();
    }
  }, []);

  const requestNavigationWithUnsavedGuard = useCallback(
    (action: () => void) => {
      if (!openedFilePath) {
        action();
        return;
      }
      const buffer = fileBuffersByPath.get(openedFilePath);
      if (!buffer?.isDirty) {
        action();
        return;
      }
      pendingNavigationRef.current = action;
      setUnsavedDialogOpen(true);
    },
    [openedFilePath, fileBuffersByPath],
  );

  const saveFile = useCallback(
    async (path: string) => {
      const buffer = fileBuffersByPath.get(path);
      if (!buffer) return false;
      if (buffer.readOnlyReason) {
        toast.warning(buffer.readOnlyReason);
        return false;
      }
      if (!buffer.isDirty) return true;

      const bytes = encodeText(buffer.draftContent);
      if (bytes.length > MAX_PREVIEW_EDIT_BYTES) {
        toast.error(`Edited content exceeds ${formatBytes(MAX_PREVIEW_EDIT_BYTES)}. Save is blocked.`);
        return false;
      }

      await writeAllBytes(path, bytes);
      const now = new Date().toISOString();
      setFileBuffersByPath((prev) => {
        const existing = prev.get(path);
        if (!existing) return prev;
        const next = new Map(prev);
        next.set(path, {
          ...existing,
          savedContent: existing.draftContent,
          isDirty: false,
        });
        return next;
      });
      setEntries((prev) =>
        prev.map((entry) =>
          entry.path === path
            ? {
                ...entry,
                modifiedAt: now,
                sizeBytes: bytes.length,
              }
            : entry,
        ),
      );
      toast.success(`Saved ${getBaseName(path, osFamily)}.`);
      return true;
    },
    [fileBuffersByPath, osFamily, writeAllBytes],
  );

  const discardOpenedFileDraft = useCallback(() => {
    if (!openedFilePath) return;
    setFileBuffersByPath((prev) => {
      const existing = prev.get(openedFilePath);
      if (!existing || !existing.isDirty) return prev;
      const next = new Map(prev);
      next.set(openedFilePath, {
        ...existing,
        draftContent: existing.savedContent,
        isDirty: false,
      });
      return next;
    });
  }, [openedFilePath]);

  const handleSaveAndContinue = useCallback(async () => {
    if (openedFilePath) {
      const saved = await saveFile(openedFilePath);
      if (!saved) return;
    }
    runPendingNavigation();
  }, [openedFilePath, runPendingNavigation, saveFile]);

  const handleDiscardAndContinue = useCallback(() => {
    discardOpenedFileDraft();
    runPendingNavigation();
  }, [discardOpenedFileDraft, runPendingNavigation]);

  const handleCancelNavigation = useCallback(() => {
    pendingNavigationRef.current = null;
    setUnsavedDialogOpen(false);
  }, []);

  const openFile = useCallback(
    async (node: FileNode) => {
      if (!isEditableTextCodeFile(node.path, node.entryType)) {
        toast.warning("Preview/Edit not supported for this file type.");
        return;
      }

      if (node.sizeBytes > MAX_PREVIEW_EDIT_BYTES) {
        const next = new Map(fileBuffersByPath);
        next.set(node.path, {
          savedContent: "",
          draftContent: "",
          language: resolveMonacoLanguage(node.path),
          isDirty: false,
          readOnlyReason: `This file is ${formatBytes(node.sizeBytes)}. Direct preview/edit is disabled above ${formatBytes(MAX_PREVIEW_EDIT_BYTES)}. Use chunk upload/download.`,
        });
        setFileBuffersByPath(next);
        setOpenedFilePath(node.path);
        return;
      }

      setLoading(true);
      try {
        const readResult = await readAllBytes(node.path, MAX_PREVIEW_EDIT_BYTES);
        const content = decodeText(readResult.bytes);
        setFileBuffersByPath((prev) => {
          const next = new Map(prev);
          next.set(node.path, {
            savedContent: content,
            draftContent: content,
            language: resolveMonacoLanguage(node.path),
            isDirty: false,
            readOnlyReason: null,
          });
          return next;
        });
        setOpenedFilePath(node.path);
      } catch (error) {
        const message = error instanceof Error ? error.message : "Failed to open file";
        toast.error(message);
      } finally {
        setLoading(false);
      }
    },
    [fileBuffersByPath, readAllBytes],
  );

  const openNode = useCallback(
    (entry: FileNode) => {
      if (entry.entryType === "directory") {
        requestNavigationWithUnsavedGuard(() => {
          void navigateToDirectory(entry.path);
        });
        return;
      }
      requestNavigationWithUnsavedGuard(() => {
        void openFile(entry);
      });
    },
    [navigateToDirectory, openFile, requestNavigationWithUnsavedGuard],
  );

  const goToPath = useCallback(
    (rawPath: string) => {
      const resolved = resolvePath(rawPath, currentPath, osFamily, homePath);
      requestNavigationWithUnsavedGuard(() => {
        void navigateToDirectory(resolved);
      });
    },
    [currentPath, homePath, navigateToDirectory, osFamily, requestNavigationWithUnsavedGuard],
  );

  const closeOpenedEditor = useCallback(() => {
    requestNavigationWithUnsavedGuard(() => {
      setOpenedFilePath(null);
    });
  }, [requestNavigationWithUnsavedGuard]);

  const handleEditorChange = useCallback(
    (nextValue: string) => {
      if (!openedFilePath) return;
      setFileBuffersByPath((prev) => {
        const existing = prev.get(openedFilePath);
        if (!existing || existing.readOnlyReason) return prev;
        const next = new Map(prev);
        next.set(openedFilePath, {
          ...existing,
          draftContent: nextValue,
          isDirty: nextValue !== existing.savedContent,
        });
        return next;
      });
    },
    [openedFilePath],
  );

  const handleEditorSave = useCallback(() => {
    if (!openedFilePath) return;
    void saveFile(openedFilePath);
  }, [openedFilePath, saveFile]);

  const updateSelectionForClick = useCallback(
    (path: string) => {
      setSelectedPath(path);
      setContextMenuTargetPath(null);
    },
    [setSelectedPath],
  );

  const handleRowContextMenu = useCallback(
    (entry: FileNode) => {
      setSelectedPath(entry.path);
      setContextMenuTargetPath(entry.path);
    },
    [setSelectedPath],
  );

  const copyText = useCallback(async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text);
      toast.success(`${label} copied.`);
    } catch {
      toast.error(`Failed to copy ${label}.`);
    }
  }, []);

  const handleActionCopyName = useCallback(
    async (node: FileNode | null) => {
      if (!node) return;
      await copyText(node.name, "name");
    },
    [copyText],
  );

  const handleActionCopyPath = useCallback(
    async (node: FileNode | null) => {
      if (!node) return;
      await copyText(node.path, "path");
    },
    [copyText],
  );

  const handleDownloadNode = useCallback(
    async (node: FileNode | null) => {
      if (!node) {
        return;
      }
      if (node.entryType !== "file") {
        toast.warning("Download is only supported for files.");
        return;
      }

      const totalBytes = node.sizeBytes;
      await runCancelableTask(
        "download",
        1,
        totalBytes,
        async (signal, operationId) => {
          let processedBytes = 0;
          updateOperationProgress(operationId, {
            currentFile: node.name,
            completedFiles: 0,
            processedBytes,
            message: buildTransferSummary("download", "running", 0, 1, processedBytes, totalBytes),
          });
          const bytes = await readWholeFile(node.path, node.sizeBytes, {
            signal,
            onBytesRead: (count) => {
              processedBytes += count;
              updateOperationProgress(operationId, {
                processedBytes,
                message: buildTransferSummary("download", "running", 0, 1, processedBytes, totalBytes),
              });
            },
          });
          throwIfAborted(signal);
          downloadBinaryFile(node.name, bytes);
          updateOperationProgress(operationId, {
            completedFiles: 1,
            processedBytes,
            message: buildTransferSummary("download", "running", 1, 1, processedBytes, totalBytes),
          });
          return { processedBytes };
        },
        (result) => buildTransferSummary("download", "success", 1, 1, result.processedBytes, totalBytes),
      );
    },
    [readWholeFile, runCancelableTask, updateOperationProgress],
  );

  const handleUploadClick = useCallback(() => {
    if (isLongTaskRunning) {
      return;
    }
    uploadInputRef.current?.click();
  }, [isLongTaskRunning]);

  const handleUploadFiles = useCallback(
    async (fileList: FileList | null) => {
      const files = fileList ? Array.from(fileList) : [];
      if (files.length === 0) return;

      const totalBytes = files.reduce((sum, file) => sum + file.size, 0);
      await runCancelableTask(
        "upload",
        files.length,
        totalBytes,
        async (signal, operationId) => {
          let completedFiles = 0;
          let processedBytes = 0;
          for (const file of files) {
            throwIfAborted(signal);
            updateOperationProgress(operationId, {
              currentFile: file.name,
              completedFiles,
              processedBytes,
              message: buildTransferSummary(
                "upload",
                "running",
                completedFiles,
                files.length,
                processedBytes,
                totalBytes,
              ),
            });
            const targetPath = joinPath(currentPath, file.name, osFamily);
            if (file.size <= MAX_PREVIEW_EDIT_BYTES) {
              const bytes = new Uint8Array(await file.arrayBuffer());
              throwIfAborted(signal);
              await writeAllBytes(targetPath, bytes, { signal });
              processedBytes += bytes.length;
            } else {
              let offset = 0;
              let truncate = true;
              while (offset < file.size) {
                throwIfAborted(signal);
                const slice = file.slice(offset, offset + CHUNK_SIZE);
                const chunkBytes = new Uint8Array(await slice.arrayBuffer());
                throwIfAborted(signal);
                await writeChunk(targetPath, offset, chunkBytes, truncate, { signal });
                truncate = false;
                offset += chunkBytes.length;
                processedBytes += chunkBytes.length;
                updateOperationProgress(operationId, {
                  processedBytes,
                  message: buildTransferSummary(
                    "upload",
                    "running",
                    completedFiles,
                    files.length,
                    processedBytes,
                    totalBytes,
                  ),
                });
              }
            }
            completedFiles += 1;
            updateOperationProgress(operationId, {
              completedFiles,
              processedBytes,
              message: buildTransferSummary(
                "upload",
                "running",
                completedFiles,
                files.length,
                processedBytes,
                totalBytes,
              ),
            });
          }
          return { completedFiles, processedBytes };
        },
        (result) =>
          buildTransferSummary(
            "upload",
            "success",
            result.completedFiles,
            files.length,
            result.processedBytes,
            totalBytes,
          ),
        async () => {
          await listDirectory(currentPath);
        },
      );
    },
    [currentPath, listDirectory, osFamily, runCancelableTask, updateOperationProgress, writeAllBytes, writeChunk],
  );

  const openPendingAction = useCallback(
    (type: PendingActionType, targetNode?: FileNode | null) => {
      const actionTarget = targetNode ?? contextMenuTargetNode ?? selectedSingleNode;
      const now = new Date().toISOString();
      if (type === "rename") {
        setPendingAction({
          type,
          value: actionTarget?.name ?? "",
          datetimeValue: toDatetimeLocalValue(actionTarget?.modifiedAt ?? now),
          targetPath: actionTarget?.path ?? null,
        });
        return;
      }
      if (type === "touch") {
        setPendingAction({
          type,
          value: "",
          datetimeValue: toDatetimeLocalValue(actionTarget?.modifiedAt ?? now),
          targetPath: actionTarget?.path ?? null,
        });
        return;
      }
      if (type === "move" || type === "copy") {
        setPendingAction({
          type,
          value: currentPath,
          datetimeValue: toDatetimeLocalValue(now),
          targetPath: actionTarget?.path ?? null,
        });
        return;
      }
      if (type === "compress") {
        const targetParentPath = actionTarget?.parentPath ?? currentPath;
        const defaultName = actionTarget ? `${actionTarget.name}.zip` : "archive.zip";
        setPendingAction({
          type,
          value: joinPath(targetParentPath, defaultName, osFamily),
          datetimeValue: toDatetimeLocalValue(now),
          targetPath: actionTarget?.path ?? null,
        });
        return;
      }
      if (type === "extract") {
        const targetParentPath = actionTarget?.parentPath ?? currentPath;
        const baseName = actionTarget ? stripZipExtension(actionTarget.name) : "archive";
        const defaultDirName = baseName.length > 0 ? baseName : "archive";
        setPendingAction({
          type,
          value: joinPath(targetParentPath, defaultDirName, osFamily),
          datetimeValue: toDatetimeLocalValue(now),
          targetPath: actionTarget?.path ?? null,
        });
        return;
      }
      if (type === "new-file") {
        setPendingAction({
          type,
          value: "new-file.txt",
          datetimeValue: toDatetimeLocalValue(now),
          targetPath: null,
        });
        return;
      }
      if (type === "delete") {
        setPendingAction({
          type,
          value: "",
          datetimeValue: toDatetimeLocalValue(now),
          targetPath: actionTarget?.path ?? null,
        });
        return;
      }
      setPendingAction({
        type,
        value: "new-folder",
        datetimeValue: toDatetimeLocalValue(now),
        targetPath: null,
      });
    },
    [contextMenuTargetNode, currentPath, osFamily, selectedSingleNode],
  );

  const handleSubmitPendingAction = useCallback(async () => {
    if (!pendingAction) return;
    const actionTarget = pendingAction.targetPath
      ? (entryByPath.get(pendingAction.targetPath) ?? null)
      : null;

    try {
      if (pendingAction.type === "rename") {
        if (!actionTarget) {
          toast.error("Select a target item first.");
          return;
        }
        const nextName = pendingAction.value.trim();
        if (!nextName || nextName.includes("/") || nextName.includes("\\")) {
          toast.error("Invalid file name.");
          return;
        }
        await dispatchFileManager({
          op: "rename",
          path: actionTarget.path,
          newName: nextName,
        });
        setPendingAction(null);
        setContextMenuTargetPath(null);
        await listDirectory(currentPath);
        toast.success("Rename completed.");
        return;
      }

      if (pendingAction.type === "touch") {
        if (!actionTarget) {
          toast.error("Select a target item first.");
          return;
        }
        const timestamp = new Date(pendingAction.datetimeValue);
        if (Number.isNaN(timestamp.getTime())) {
          toast.error("Invalid datetime.");
          return;
        }
        await dispatchFileManager({
          op: "touch",
          paths: [actionTarget.path],
          modifiedAtEpochMs: timestamp.getTime(),
        });
        setPendingAction(null);
        setContextMenuTargetPath(null);
        await listDirectory(currentPath);
        toast.success("Modified time updated.");
        return;
      }

      if (pendingAction.type === "new-file" || pendingAction.type === "new-folder") {
        const name = pendingAction.value.trim();
        if (!name || name.includes("/") || name.includes("\\")) {
          toast.error("Invalid name.");
          return;
        }
        const path = joinPath(currentPath, name, osFamily);
        if (pendingAction.type === "new-file") {
          await dispatchFileManager({ op: "create-file", path, createParent: true });
        } else {
          await dispatchFileManager({ op: "mkdir", path, recursive: true });
        }
        setPendingAction(null);
        setContextMenuTargetPath(null);
        await listDirectory(currentPath);
        toast.success(`${pendingAction.type === "new-file" ? "File" : "Folder"} created.`);
        return;
      }

      if (pendingAction.type === "move" || pendingAction.type === "copy") {
        if (!actionTarget) {
          toast.error("Select a target item first.");
          return;
        }
        const destinationPath = resolvePath(pendingAction.value, currentPath, osFamily, homePath);
        await dispatchFileManager({
          op: pendingAction.type,
          sourcePaths: [actionTarget.path],
          destinationPath,
          overwrite: false,
        });
        setPendingAction(null);
        setContextMenuTargetPath(null);
        await listDirectory(currentPath);
        toast.success(`${pendingAction.type === "move" ? "Move" : "Copy"} completed.`);
        return;
      }

      if (pendingAction.type === "compress") {
        if (!actionTarget) {
          toast.error("Select a target item first.");
          return;
        }
        const destinationPath = resolvePath(pendingAction.value, currentPath, osFamily, homePath);
        const totalBytes = actionTarget.entryType === "file" ? actionTarget.sizeBytes : 0;
        setPendingAction(null);
        setContextMenuTargetPath(null);
        const ok = await runCancelableTask(
          "compress",
          1,
          totalBytes,
          async (signal, operationId) => {
            updateOperationProgress(operationId, {
              currentFile: actionTarget.name,
              completedFiles: 0,
              processedBytes: 0,
              message: buildTransferSummary("compress", "running", 0, 1, 0, totalBytes),
            });
            const response = await dispatchFileManager({
              op: "zip",
              sourcePaths: [actionTarget.path],
              destinationPath,
              overwrite: false,
              createParent: true,
            }, { signal });
            const processedBytes = typeof response.archiveSize === "number" ? response.archiveSize : totalBytes;
            updateOperationProgress(operationId, {
              completedFiles: 1,
              processedBytes,
              message: buildTransferSummary("compress", "running", 1, 1, processedBytes, totalBytes),
            });
            return response;
          },
          (response) => {
            const processedBytes = typeof response.archiveSize === "number" ? response.archiveSize : totalBytes;
            return buildTransferSummary("compress", "success", 1, 1, processedBytes, totalBytes);
          },
          async () => {
            await listDirectory(currentPath);
          },
        );
        if (!ok) {
          return;
        }
        toast.success("ZIP archive created.");
        return;
      }

      if (pendingAction.type === "extract") {
        if (!actionTarget) {
          toast.error("Select a target item first.");
          return;
        }
        if (actionTarget.entryType !== "file" || !isZipArchivePath(actionTarget.path)) {
          toast.error("Extract is only supported for .zip files.");
          return;
        }
        const destinationPath = resolvePath(pendingAction.value, currentPath, osFamily, homePath);
        const totalBytes = actionTarget.sizeBytes;
        setPendingAction(null);
        setContextMenuTargetPath(null);
        const ok = await runCancelableTask(
          "extract",
          1,
          totalBytes,
          async (signal, operationId) => {
            updateOperationProgress(operationId, {
              currentFile: actionTarget.name,
              completedFiles: 0,
              processedBytes: 0,
              message: buildTransferSummary("extract", "running", 0, 1, 0, totalBytes),
            });
            const response = await dispatchFileManager({
              op: "unzip",
              path: actionTarget.path,
              destinationPath,
              overwrite: false,
              createParent: true,
            }, { signal });
            const processedBytes = typeof response.writtenBytes === "number" ? response.writtenBytes : totalBytes;
            updateOperationProgress(operationId, {
              completedFiles: 1,
              processedBytes,
              message: buildTransferSummary("extract", "running", 1, 1, processedBytes, totalBytes),
            });
            return response;
          },
          (response) => {
            const processedBytes = typeof response.writtenBytes === "number" ? response.writtenBytes : totalBytes;
            return buildTransferSummary("extract", "success", 1, 1, processedBytes, totalBytes);
          },
          async () => {
            await listDirectory(currentPath);
          },
        );
        if (!ok) {
          return;
        }
        toast.success("ZIP extracted.");
        return;
      }

      if (pendingAction.type === "delete") {
        if (!actionTarget) {
          toast.error("Select a target item first.");
          return;
        }
        const deletingPath = actionTarget.path;
        await dispatchFileManager({
          op: "delete",
          paths: [deletingPath],
          recursive: true,
        });
        if (openedFilePath && deletingPath === openedFilePath) {
          setOpenedFilePath(null);
        }
        setPendingAction(null);
        setContextMenuTargetPath(null);
        setSelection(EMPTY_SELECTION);
        await listDirectory(currentPath);
        toast.success("Deleted 1 item.");
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "Operation failed";
      toast.error(message);
    }
  }, [
    currentPath,
    dispatchFileManager,
    homePath,
    listDirectory,
    osFamily,
    pendingAction,
    runCancelableTask,
    updateOperationProgress,
    entryByPath,
    openedFilePath,
  ]);

  const handleRefreshRequest = useCallback(() => {
    if (isLongTaskRunning) {
      return;
    }
    requestNavigationWithUnsavedGuard(() => {
      void navigateToDirectory(currentPath);
    });
  }, [currentPath, isLongTaskRunning, navigateToDirectory, requestNavigationWithUnsavedGuard]);

  const handleGoUp = useCallback(() => {
    const parent = getParentPath(currentPath, osFamily);
    if (!parent) return;
    requestNavigationWithUnsavedGuard(() => {
      void navigateToDirectory(parent);
    });
  }, [currentPath, navigateToDirectory, osFamily, requestNavigationWithUnsavedGuard]);

  const handleSortColumn = useCallback(
    (key: SortKey) => {
      if (sortKey === key) setSortDir((d) => (d === "asc" ? "desc" : "asc"));
      else {
        setSortKey(key);
        setSortDir("asc");
      }
    },
    [sortKey],
  );

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const active = document.activeElement;
      const inInput =
        active instanceof HTMLInputElement ||
        active instanceof HTMLTextAreaElement ||
        (active instanceof HTMLElement && active.isContentEditable) ||
        (active instanceof HTMLElement && active.closest(".monaco-editor") != null);
      if (inInput) return;
      if (event.key === "Backspace") {
        event.preventDefault();
        handleGoUp();
      } else if (event.key === "F2" && selectedSingleNode) {
        event.preventDefault();
        openPendingAction("rename", selectedSingleNode);
      } else if (event.key === "Delete" && selectedSingleNode) {
        event.preventDefault();
        openPendingAction("delete", selectedSingleNode);
      } else if (event.key === "Enter" && selectedSingleNode) {
        event.preventDefault();
        openNode(selectedSingleNode);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [handleGoUp, openNode, openPendingAction, selectedSingleNode]);

  return (
    <div className="flex min-h-0 flex-1 overflow-hidden rounded-2xl border border-border/70 bg-background shadow-sm">
      <aside className="hidden w-56 shrink-0 border-r border-border/70 bg-muted/20 md:flex md:flex-col">
        <div className="px-3 py-3 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          Locations
        </div>
        <Separator />
        <ScrollArea className="min-h-0 flex-1 p-2">
          <div className="space-y-1">
            {locations.map((location) => {
              const Icon =
                location.kind === "cwd" ? TerminalSquare : location.kind === "home" ? Home : HardDrive;
              return (
                <button
                  key={location.id}
                  type="button"
                  onClick={() => {
                    requestNavigationWithUnsavedGuard(() => {
                      setActiveLocationId(location.id);
                      void navigateToDirectory(location.path);
                    });
                  }}
                  className={cn(
                    "flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left text-sm transition-colors",
                    activeLocationId === location.id
                      ? "bg-primary/10 text-foreground ring-1 ring-inset ring-primary/20"
                      : "text-muted-foreground hover:bg-muted/70 hover:text-foreground",
                  )}
                  title={location.path}
                >
                  <Icon className="size-4 shrink-0" />
                  <div className="min-w-0 flex-1">
                    <div className="truncate font-medium">{location.label}</div>
                    <div className="truncate text-[11px] opacity-80">{location.path}</div>
                  </div>
                </button>
              );
            })}
          </div>
        </ScrollArea>
      </aside>

      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <div className="shrink-0 border-b border-border/70 bg-muted/10 px-4 py-3 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <Button
              type="button"
              size="icon-sm"
              variant="outline"
              onClick={handleGoUp}
              disabled={getParentPath(currentPath, osFamily) === null}
              title="Go to parent (Backspace)"
            >
              <ArrowUp className="size-3.5" />
            </Button>
            <Input
              value={pathInput}
              onChange={(e) => setPathInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  goToPath(pathInput);
                }
              }}
              placeholder="Enter directory path"
              className="min-w-48 flex-1 font-mono text-sm"
            />
            <Button type="button" size="sm" onClick={() => goToPath(pathInput)}>
              Go
            </Button>
            <Button type="button" size="sm" variant="outline" onClick={handleUploadClick} disabled={isLongTaskRunning}>
              <Upload className="size-3.5" />
              Upload
            </Button>
            <Button type="button" size="sm" variant="outline" onClick={() => openPendingAction("new-file")}>
              <FilePlus className="size-3.5" />
              New File
            </Button>
            <Button type="button" size="sm" variant="outline" onClick={() => openPendingAction("new-folder")}>
              <FolderPlus className="size-3.5" />
              New Folder
            </Button>
            <Button
              type="button"
              size="icon-sm"
              variant="outline"
              onClick={handleRefreshRequest}
              disabled={loading || isLongTaskRunning}
              title="Refresh"
            >
              <RefreshCw className={cn("size-3.5", loading && "animate-spin")} />
            </Button>
          </div>

          <div className="flex items-center gap-3 min-w-0">
            <Breadcrumb className="min-w-0 flex-1 overflow-hidden">
              <BreadcrumbList className="flex-nowrap overflow-x-auto">
                {parseBreadcrumbs(currentPath, osFamily).map((crumb, i, arr) => (
                  <React.Fragment key={crumb.path}>
                    <BreadcrumbItem>
                      {crumb.isCurrent ? (
                        <BreadcrumbPage className="max-w-40 truncate font-medium">{crumb.label}</BreadcrumbPage>
                      ) : (
                        <BreadcrumbLink render={<button type="button" />} className="max-w-32 truncate cursor-pointer" onClick={() => goToPath(crumb.path)}>
                          {crumb.label}
                        </BreadcrumbLink>
                      )}
                    </BreadcrumbItem>
                    {i < arr.length - 1 && (
                      <BreadcrumbSeparator>
                        <ChevronRight className="size-3" />
                      </BreadcrumbSeparator>
                    )}
                  </React.Fragment>
                ))}
              </BreadcrumbList>
            </Breadcrumb>

            <div className="relative shrink-0">
              <Filter className="absolute left-2 top-1/2 -translate-y-1/2 size-3 text-muted-foreground pointer-events-none" />
              <Input
                value={nameFilter}
                onChange={(e) => setNameFilter(e.target.value)}
                placeholder="Filter…"
                className="h-7 pl-6 pr-2 text-xs w-36"
              />
            </div>
          </div>

          {operationState ? (
            <div
              className={cn(
                "rounded-lg border px-3 py-2",
                operationState.status === "error"
                  ? "border-destructive/40 bg-destructive/5"
                  : operationState.status === "cancelled"
                    ? "border-amber-400/40 bg-amber-50/40"
                    : operationState.status === "success"
                      ? "border-emerald-400/40 bg-emerald-50/40"
                      : "border-border/60 bg-background/80",
              )}
            >
              <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                <div className="min-w-0 flex items-center gap-2">
                  {operationState.status === "running" ? <Spinner className="size-3.5" /> : null}
                  <span
                    className={cn(
                      "font-medium",
                      operationState.status === "error" && "text-destructive",
                      operationState.status === "cancelled" && "text-amber-700",
                      operationState.status === "success" && "text-emerald-700",
                    )}
                  >
                    {operationState.kind === "upload"
                      ? "Upload"
                      : operationState.kind === "download"
                        ? "Download"
                        : operationState.kind === "compress"
                          ? "Compress"
                          : operationState.kind === "extract"
                            ? "Extract"
                            : "Operation"}{" "}
                    {operationState.status}
                  </span>
                  <span className="truncate text-muted-foreground">{operationState.currentFile ?? operationState.message}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="tabular-nums text-muted-foreground">
                    {operationState.completedFiles}/{operationState.totalFiles} files
                  </span>
                  <span className="tabular-nums text-muted-foreground">
                    {formatBytes(operationState.processedBytes)}/{formatBytes(operationState.totalBytes)}
                  </span>
                  <span className="tabular-nums text-muted-foreground">{operationProgressPercent}%</span>
                  {operationState.status === "running" &&
                  (
                    operationState.kind === "upload" ||
                    operationState.kind === "download" ||
                    operationState.kind === "compress" ||
                    operationState.kind === "extract"
                  ) ? (
                    <Button type="button" size="sm" variant="outline" onClick={cancelOperation}>
                      Cancel
                    </Button>
                  ) : null}
                </div>
              </div>
              <Progress value={operationProgressPercent} className="mt-2 w-full" />
            </div>
          ) : null}
        </div>

        <div className="min-h-0 flex flex-1 flex-col">
          <div className="relative min-h-0 flex-1">
            {loading && (
              <div className="absolute inset-0 z-10 flex justify-center pt-32 bg-background/60 backdrop-blur-sm">
                <Spinner className="size-6 text-muted-foreground" />
              </div>
            )}
            <div
              key={`list-${currentPath}-${listVersion}`}
              ref={listScrollRef}
              className="h-full overflow-auto"
            >
              <Table>
                <TableHeader>
                  <TableRow>
                    {(
                      [
                        { key: "name", label: "Name", className: "" },
                        { key: "type", label: "Type", className: "" },
                        { key: "permissions", label: "Permissions", className: "" },
                        { key: "size", label: "Size", className: "text-right" },
                        { key: "createdAt", label: "Created", className: "" },
                        { key: "modifiedAt", label: "Modified", className: "" },
                      ] as const
                    ).map(({ key, label, className }) => (
                      <TableHead key={key} className={cn("cursor-pointer select-none", className)} onClick={() => handleSortColumn(key)}>
                        <span className="inline-flex items-center gap-1">
                          {label}
                          {sortKey === key ? sortDir === "asc" ? <ArrowUp className="size-3 opacity-70" /> : <ArrowDown className="size-3 opacity-70" /> : <ArrowUpDown className="size-3 opacity-30" />}
                        </span>
                      </TableHead>
                    ))}
                  </TableRow>
                </TableHeader>
                <TableBody key={`tbody-${currentPath}-${listVersion}`}>
                  {currentEntries.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={6} className="h-28 text-center text-sm text-muted-foreground">
                        {nameFilter.trim() ? `No entries match "${nameFilter.trim()}".` : "No entries in this directory."}
                      </TableCell>
                    </TableRow>
                  ) : (
                    <>
                      {paddingTop > 0 ? (
                        <TableRow aria-hidden>
                          <TableCell colSpan={6} style={{ height: paddingTop, padding: 0 }} />
                        </TableRow>
                      ) : null}
                      {virtualRows.map((virtualRow) => {
                        const entry = currentEntries[virtualRow.index];
                        if (!entry) return null;
                        const isSelected = selection.selectedPath === entry.path;
                        const Icon = getFileTypeIcon(entry.fileType);
                        return (
                          <ContextMenu
                            key={virtualRow.key}
                            onOpenChange={(open) => {
                              if (!open && contextMenuTargetPath === entry.path) {
                                setContextMenuTargetPath(null);
                              }
                            }}
                          >
                            <ContextMenuTrigger
                              onContextMenu={() => handleRowContextMenu(entry)}
                              onClick={() => updateSelectionForClick(entry.path)}
                              onDoubleClick={() => openNode(entry)}
                              render={<TableRow data-state={isSelected ? "selected" : undefined} className="cursor-default" />}
                            >
                              <TableCell className="max-w-80">
                                <div className="flex items-center gap-2">
                                  <Icon className="size-4 shrink-0 text-muted-foreground" />
                                  <span className="truncate font-medium" title={entry.path}>
                                    {entry.name}
                                  </span>
                                </div>
                              </TableCell>
                              <TableCell>{entry.fileType}</TableCell>
                              <TableCell className="font-mono text-xs">{entry.permissions}</TableCell>
                              <TableCell className="text-right tabular-nums">
                                {entry.entryType === "directory" ? "—" : formatBytes(entry.sizeBytes)}
                              </TableCell>
                              <TableCell className="tabular-nums">{formatDateTime(entry.createdAt)}</TableCell>
                              <TableCell className="tabular-nums">{formatDateTime(entry.modifiedAt)}</TableCell>
                            </ContextMenuTrigger>
                            <ContextMenuContent>
                              <ContextMenuItem onClick={() => openNode(entry)}>Open</ContextMenuItem>
                              {entry.entryType === "file" ? (
                                <ContextMenuItem onClick={() => void handleDownloadNode(entry)}>Download</ContextMenuItem>
                              ) : null}
                              <ContextMenuItem onClick={() => openPendingAction("compress", entry)}>Compress to ZIP...</ContextMenuItem>
                              {entry.entryType === "file" && isZipArchivePath(entry.path) ? (
                                <ContextMenuItem onClick={() => openPendingAction("extract", entry)}>Extract ZIP...</ContextMenuItem>
                              ) : null}
                              <ContextMenuSeparator />
                              <ContextMenuItem onClick={() => void handleActionCopyName(entry)}>Copy Name</ContextMenuItem>
                              <ContextMenuItem onClick={() => void handleActionCopyPath(entry)}>Copy Path</ContextMenuItem>
                              <ContextMenuSeparator />
                              <ContextMenuItem onClick={() => openPendingAction("rename", entry)}>Rename</ContextMenuItem>
                              <ContextMenuItem onClick={() => openPendingAction("move", entry)}>Move To</ContextMenuItem>
                              <ContextMenuItem onClick={() => openPendingAction("copy", entry)}>Copy To</ContextMenuItem>
                              <ContextMenuItem onClick={() => openPendingAction("touch", entry)}>Modify Time</ContextMenuItem>
                              <ContextMenuSeparator />
                              <ContextMenuItem variant="destructive" onClick={() => openPendingAction("delete", entry)}>
                                Delete
                              </ContextMenuItem>
                            </ContextMenuContent>
                          </ContextMenu>
                        );
                      })}
                      {paddingBottom > 0 ? (
                        <TableRow aria-hidden>
                          <TableCell colSpan={6} style={{ height: paddingBottom, padding: 0 }} />
                        </TableRow>
                      ) : null}
                    </>
                  )}
                </TableBody>
              </Table>
            </div>
          </div>

          <div className="flex shrink-0 items-center justify-between border-t border-border/70 bg-muted/10 px-4 py-2 text-xs text-muted-foreground">
            <span>
              {nameFilter.trim() ? `${currentEntries.length} of ${totalEntriesCount} items` : `${currentEntries.length} item${currentEntries.length === 1 ? "" : "s"}`}
            </span>
            <span>{selectedCount} selected</span>
          </div>
        </div>
      </div>

      <input
        ref={uploadInputRef}
        type="file"
        className="hidden"
        multiple
        onChange={(event) => {
          void handleUploadFiles(event.target.files);
          event.currentTarget.value = "";
        }}
      />

      <Dialog
        open={openedFilePath != null}
        onOpenChange={(open) => {
          if (!open) {
            closeOpenedEditor();
          }
        }}
      >
        <DialogContent showCloseButton={false} className="h-[76vh] w-[96vw] max-w-[96vw] sm:max-w-[96vw] gap-0 overflow-hidden p-0">
          <FileEditorPane
            filePath={openedFilePath}
            language={openedFileBuffer?.language ?? "plaintext"}
            value={openedFileBuffer?.draftContent ?? ""}
            isDirty={openedFileBuffer?.isDirty ?? false}
            readOnlyReason={openedFileBuffer?.readOnlyReason ?? null}
            onChange={handleEditorChange}
            onSave={handleEditorSave}
            onClose={closeOpenedEditor}
          />
        </DialogContent>
      </Dialog>

      <Dialog
        open={unsavedDialogOpen}
        onOpenChange={(open) => {
          if (!open) {
            handleCancelNavigation();
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Unsaved changes</DialogTitle>
            <DialogDescription>You have unsaved changes in the current file. Save before leaving?</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={handleCancelNavigation}>
              Cancel
            </Button>
            <Button variant="secondary" onClick={handleDiscardAndContinue}>
              Discard
            </Button>
            <Button onClick={() => void handleSaveAndContinue()}>Save</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={pendingAction != null}
        onOpenChange={(open) => {
          if (!open) {
            setPendingAction(null);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {pendingAction?.type === "move" && "Move To"}
              {pendingAction?.type === "copy" && "Copy To"}
              {pendingAction?.type === "rename" && "Rename"}
              {pendingAction?.type === "touch" && "Modify Time"}
              {pendingAction?.type === "new-file" && "New File"}
              {pendingAction?.type === "new-folder" && "New Folder"}
              {pendingAction?.type === "delete" && "Delete"}
              {pendingAction?.type === "compress" && "Compress to ZIP"}
              {pendingAction?.type === "extract" && "Extract ZIP"}
            </DialogTitle>
            <DialogDescription>
              {pendingAction?.type === "move" && "Move the selected entry to target directory."}
              {pendingAction?.type === "copy" && "Copy the selected entry to target directory."}
              {pendingAction?.type === "rename" && "Rename the selected entry."}
              {pendingAction?.type === "touch" && "Update modified time for the selected entry."}
              {pendingAction?.type === "new-file" && "Create a new file in current directory."}
              {pendingAction?.type === "new-folder" && "Create a new folder in current directory."}
              {pendingAction?.type === "delete" && "Delete the selected entry recursively. This action cannot be undone."}
              {pendingAction?.type === "compress" && "Create a ZIP archive from the selected entry."}
              {pendingAction?.type === "extract" && "Extract the selected ZIP archive to target directory."}
            </DialogDescription>
          </DialogHeader>

          {pendingAction?.type === "move" ||
          pendingAction?.type === "copy" ||
          pendingAction?.type === "rename" ||
          pendingAction?.type === "new-file" ||
          pendingAction?.type === "new-folder" ||
          pendingAction?.type === "compress" ||
          pendingAction?.type === "extract" ? (
            <Input
              value={pendingAction.value}
              onChange={(event) =>
                setPendingAction((prev) => (prev ? { ...prev, value: event.target.value } : prev))
              }
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  void handleSubmitPendingAction();
                }
              }}
              autoFocus
            />
          ) : null}

          {pendingAction?.type === "touch" ? (
            <Input
              type="datetime-local"
              value={pendingAction.datetimeValue}
              onChange={(event) =>
                setPendingAction((prev) => (prev ? { ...prev, datetimeValue: event.target.value } : prev))
              }
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  void handleSubmitPendingAction();
                }
              }}
              autoFocus
            />
          ) : null}

          <DialogFooter>
            <Button variant="outline" onClick={() => setPendingAction(null)}>
              Cancel
            </Button>
            <Button onClick={() => void handleSubmitPendingAction()}>Apply</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
