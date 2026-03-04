type LocationKind = "cwd" | "home" | "disk";
type OsFamily = "windows" | "unix";

interface FileManagerLocation {
  id: string;
  label: string;
  path: string;
  kind: LocationKind;
}

export interface FileManagerInitialState {
  osFamily: OsFamily;
  cwd: string;
  locations: FileManagerLocation[];
  activeLocationId: string;
}

function isWindowsAbsolutePath(value: string) {
  return /^[A-Za-z]:[\\/]/.test(value) || /^[A-Za-z]:$/.test(value);
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

function readEnvCaseInsensitive(env: Record<string, unknown>, key: string) {
  const lower = key.toLowerCase();
  const entry = Object.entries(env).find(([name]) => name.toLowerCase() === lower);
  return typeof entry?.[1] === "string" ? entry[1] : "";
}

function detectOsFamily(osName: unknown, cwd: string, disks: string[]): OsFamily {
  if (typeof osName === "string" && osName.toLowerCase() === "darwin") {
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

export function deriveFileManagerInitialState(payload: unknown): FileManagerInitialState {
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
  const fallbackRoot = osFamily === "windows" ? "C:\\" : "/";
  const cwd = normalizeAbsolutePath(rawCwd || fallbackRoot, osFamily);
  const home = normalizeAbsolutePath(homeFromEnv || fallbackRoot, osFamily);
  const normalizedDisks = Array.from(
    new Set(
      (disks.length > 0 ? disks : [fallbackRoot]).map((disk) =>
        normalizeAbsolutePath(disk, osFamily),
      ),
    ),
  );

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

  return {
    osFamily,
    cwd,
    locations,
    activeLocationId: "cwd",
  };
}
