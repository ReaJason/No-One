import { useCallback, useEffect, useMemo, useRef, useState } from "react";

type OsFamily = "windows" | "unix" | "unknown";

interface PersistedCommandConfig {
  cwd: string;
  charset: string;
  executable: string;
  args: string;
  env: string;
}

export interface CharsetOption {
  value: string;
  label: string;
}

export const DEFAULT_TEMPLATE_ARGS = "{{cmd}}";
export const AUTO_CHARSET_VALUE = "__AUTO__";

export const CHARSET_OPTIONS: CharsetOption[] = [
  { value: AUTO_CHARSET_VALUE, label: "Auto (plugin default)" },
  { value: "UTF-8", label: "UTF-8" },
  { value: "GBK", label: "GBK" },
  { value: "GB18030", label: "GB18030" },
  { value: "Big5", label: "Big5" },
  { value: "Shift_JIS", label: "Shift_JIS" },
  { value: "EUC-KR", label: "EUC-KR" },
  { value: "ISO-8859-1", label: "ISO-8859-1" },
  { value: "Windows-1252", label: "Windows-1252" },
];

export interface TemplatePreset {
  label: string;
  executable: string;
  args: string[];
}

export const TEMPLATE_PRESETS: TemplatePreset[] = [
  { label: "/bin/sh -c", executable: "/bin/sh", args: ["-c", "{{cmd}}"] },
  { label: "cmd.exe /c", executable: "cmd.exe", args: ["/c", "{{cmd}}"] },
  { label: "powershell -Command", executable: "powershell.exe", args: ["-Command", "{{cmd}}"] },
];

function detectOsFamily(osName?: string): OsFamily {
  if (!osName) return "unknown";
  const lower = osName.trim().toLowerCase();
  if (lower === "windows") return "windows";
  if (lower === "linux" || lower === "macos") return "unix";
  if (lower.includes("win")) return "windows";
  if (
    lower.includes("linux") ||
    lower.includes("mac") ||
    lower.includes("darwin") ||
    lower.includes("os x")
  ) {
    return "unix";
  }
  return "unknown";
}

function getDefaultCharsetByOs(osFamily: OsFamily): string {
  if (osFamily === "windows") return "GBK";
  if (osFamily === "unix") return "UTF-8";
  return "";
}

function getDefaultTemplateByOs(osFamily: OsFamily): { executable: string; args: string[] } | null {
  if (osFamily === "windows") return { executable: "cmd.exe", args: ["/c", "{{cmd}}"] };
  if (osFamily === "unix") return { executable: "/bin/sh", args: ["-c", "{{cmd}}"] };
  return null;
}

export function parseTemplateArgs(value: string) {
  return value
    .split(/\r?\n/g)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

export function parseTemplateEnv(value: string) {
  const env: Record<string, string> = {};
  for (const rawLine of value.split(/\r?\n/g)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const sep = line.indexOf("=");
    if (sep <= 0) continue;
    const key = line.slice(0, sep).trim();
    if (key) env[key] = line.slice(sep + 1);
  }
  return env;
}

interface UsePersistedCommandConfigOptions {
  shellId: number;
  osName?: string;
  cwdHint?: string;
}

export function usePersistedCommandConfig({
  shellId,
  osName,
  cwdHint,
}: UsePersistedCommandConfigOptions) {
  const loadedStorageKeyRef = useRef<string | null>(null);
  const [configLoaded, setConfigLoaded] = useState(false);
  const [cwd, setCwd] = useState("");
  const [charset, setCharset] = useState("");
  const [templateExecutable, setTemplateExecutable] = useState("");
  const [templateArgs, setTemplateArgs] = useState(DEFAULT_TEMPLATE_ARGS);
  const [templateEnv, setTemplateEnv] = useState("");

  const storageKey = useMemo(() => `noone.command.execute.config.${shellId}`, [shellId]);

  useEffect(() => {
    if (typeof window === "undefined" || loadedStorageKeyRef.current === storageKey) return;
    loadedStorageKeyRef.current = null;
    setConfigLoaded(false);

    const osFamily = detectOsFamily(osName);
    const defaultTemplate = getDefaultTemplateByOs(osFamily);
    const next: PersistedCommandConfig = {
      cwd: (cwdHint ?? "").trim(),
      charset: getDefaultCharsetByOs(osFamily),
      executable: defaultTemplate?.executable ?? "",
      args: defaultTemplate ? defaultTemplate.args.join("\n") : DEFAULT_TEMPLATE_ARGS,
      env: "",
    };

    const raw = window.localStorage.getItem(storageKey);
    if (raw) {
      try {
        const persisted = JSON.parse(raw) as Partial<PersistedCommandConfig>;
        if (typeof persisted.cwd === "string") next.cwd = persisted.cwd;
        if (typeof persisted.charset === "string") next.charset = persisted.charset;
        if (typeof persisted.executable === "string") next.executable = persisted.executable;
        if (typeof persisted.args === "string") next.args = persisted.args || DEFAULT_TEMPLATE_ARGS;
        if (typeof persisted.env === "string") next.env = persisted.env;
      } catch {
        // Ignore invalid localStorage data
      }
    }

    setCwd(next.cwd);
    setCharset(next.charset);
    setTemplateExecutable(next.executable);
    setTemplateArgs(next.args);
    setTemplateEnv(next.env);

    loadedStorageKeyRef.current = storageKey;
    setConfigLoaded(true);
  }, [cwdHint, osName, storageKey]);

  useEffect(() => {
    if (
      typeof window === "undefined" ||
      !configLoaded ||
      loadedStorageKeyRef.current !== storageKey
    )
      return;
    const payload: PersistedCommandConfig = {
      cwd,
      charset,
      executable: templateExecutable,
      args: templateArgs,
      env: templateEnv,
    };
    window.localStorage.setItem(storageKey, JSON.stringify(payload));
  }, [charset, configLoaded, cwd, storageKey, templateArgs, templateEnv, templateExecutable]);

  const applyPreset = useCallback((executable: string, args: string[]) => {
    setTemplateExecutable(executable);
    setTemplateArgs(args.join("\n"));
  }, []);

  return {
    cwd,
    setCwd,
    charset,
    setCharset,
    templateExecutable,
    setTemplateExecutable,
    templateArgs,
    setTemplateArgs,
    templateEnv,
    setTemplateEnv,
    applyPreset,
  };
}
