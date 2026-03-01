import "@xterm/xterm/css/xterm.css";
import { useTheme } from "next-themes";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import * as shellApi from "@/api/shell-api";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

interface CommandExecuteProps {
  shellId: number;
  systemHints?: {
    osName?: string;
    cwd?: string;
  } | null;
}

interface CommandTemplatePayload {
  executable: string;
  args: string[];
  env: Record<string, string>;
}

interface PersistedCommandConfig {
  cwd: string;
  charset: string;
  executable: string;
  args: string;
  env: string;
}

interface XtermTheme {
  background: string;
  foreground: string;
  cursor: string;
  black: string;
  red: string;
  green: string;
  yellow: string;
  blue: string;
  magenta: string;
  cyan: string;
  white: string;
  brightBlack: string;
  brightRed: string;
  brightGreen: string;
  brightYellow: string;
  brightBlue: string;
  brightMagenta: string;
  brightCyan: string;
  brightWhite: string;
}

type OsFamily = "windows" | "unix" | "unknown";

interface TemplatePreset {
  executable: string;
  args: string[];
}

interface CharsetOption {
  value: string;
  label: string;
}

const PROMPT = "$ ";
const ANSI_RESET = "\x1b[0m";
const ANSI_RED = "\x1b[31m";
const ANSI_YELLOW = "\x1b[33m";
const ANSI_CYAN = "\x1b[36m";
const ANSI_DIM = "\x1b[2m";
const ANSI_ESCAPE_PATTERN = /\u001b\[[0-9;?]*[ -/]*[@-~]/;
const DEFAULT_TEMPLATE_ARGS = "{{cmd}}";
const AUTO_CHARSET_VALUE = "__AUTO__";
const CHARSET_OPTIONS: CharsetOption[] = [
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

const XTERM_THEME_GITHUB_LIGHT_COLORBLIND: XtermTheme = {
  foreground: "#24292f",
  background: "#ffffff",
  cursor: "#0969da",
  black: "#24292f",
  red: "#b35900",
  green: "#0550ae",
  yellow: "#4d2d00",
  blue: "#0969da",
  magenta: "#8250df",
  cyan: "#1b7c83",
  white: "#6e7781",
  brightBlack: "#57606a",
  brightRed: "#8a4600",
  brightGreen: "#0969da",
  brightYellow: "#633c01",
  brightBlue: "#218bff",
  brightMagenta: "#a475f9",
  brightCyan: "#3192aa",
  brightWhite: "#8c959f",
};

const XTERM_THEME_GITHUB_DARK_COLORBLIND: XtermTheme = {
  foreground: "#c9d1d9",
  background: "#0d1117",
  cursor: "#58a6ff",
  black: "#484f58",
  red: "#ec8e2c",
  green: "#58a6ff",
  yellow: "#d29922",
  blue: "#58a6ff",
  magenta: "#bc8cff",
  cyan: "#39c5cf",
  white: "#b1bac4",
  brightBlack: "#6e7681",
  brightRed: "#fdac54",
  brightGreen: "#79c0ff",
  brightYellow: "#e3b341",
  brightBlue: "#79c0ff",
  brightMagenta: "#d2a8ff",
  brightCyan: "#56d4dd",
  brightWhite: "#ffffff",
};

const getXtermTheme = (mode: "light" | "dark"): XtermTheme => {
  return mode === "light"
    ? XTERM_THEME_GITHUB_LIGHT_COLORBLIND
    : XTERM_THEME_GITHUB_DARK_COLORBLIND;
};

const detectOsFamily = (osName?: string): OsFamily => {
  const normalized = osName?.trim().toLowerCase() ?? "";
  if (!normalized) return "unknown";
  if (
      normalized.includes("linux") ||
      normalized.includes("mac") ||
      normalized.includes("darwin") ||
      normalized.includes("os x")
  ) {
    return "unix";
  }
  if (normalized.includes("win")) return "windows";
  return "unknown";
};

const getDefaultCharsetByOs = (osFamily: OsFamily): string => {
  if (osFamily === "windows") return "GBK";
  if (osFamily === "unix") return "UTF-8";
  return "";
};

const getDefaultTemplateByOs = (osFamily: OsFamily): TemplatePreset | null => {
  if (osFamily === "windows") {
    return {
      executable: "cmd.exe",
      args: ["/c", "{{cmd}}"],
    };
  }
  if (osFamily === "unix") {
    return {
      executable: "/bin/sh",
      args: ["-c", "{{cmd}}"],
    };
  }
  return null;
};

export default function CommandExecute({ shellId, systemHints }: CommandExecuteProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const terminalRef = useRef<{
    write: (data: string) => void;
    writeln: (data: string) => void;
    clear: () => void;
    focus: () => void;
    options: { theme?: unknown };
    dispose: () => void;
  } | null>(null);
  const fitAddonRef = useRef<{ fit: () => void } | null>(null);
  const runningRef = useRef(false);
  const inputBufferRef = useRef("");
  const commandHistoryRef = useRef<string[]>([]);
  const historyIndexRef = useRef(0);
  const systemDefaultsAppliedShellIdsRef = useRef<Set<number>>(new Set());
  const loadedStorageKeyRef = useRef<string | null>(null);
  const { resolvedTheme, theme } = useTheme();

  const [configLoaded, setConfigLoaded] = useState(false);
  const [cwdInput, setCwdInput] = useState("");
  const [charset, setCharset] = useState("");
  const [templateExecutable, setTemplateExecutable] = useState("");
  const [templateArgs, setTemplateArgs] = useState(DEFAULT_TEMPLATE_ARGS);
  const [templateEnv, setTemplateEnv] = useState("");

  const storageKey = useMemo(() => `noone.command.execute.config.${shellId}`, [shellId]);
  const effectiveTheme = useMemo<"light" | "dark">(() => {
    if (resolvedTheme === "light" || resolvedTheme === "dark") {
      return resolvedTheme;
    }
    if (theme === "light" || theme === "dark") {
      return theme;
    }
    return "dark";
  }, [resolvedTheme, theme]);
  const terminalTheme = useMemo(() => getXtermTheme(effectiveTheme), [effectiveTheme]);
  const terminalThemeRef = useRef<XtermTheme>(terminalTheme);

  const writePrompt = useCallback(() => {
    terminalRef.current?.write(PROMPT);
  }, []);

  const redrawCurrentLine = useCallback((value: string) => {
    inputBufferRef.current = value;
    const term = terminalRef.current;
    if (!term) return;
    term.write("\x1b[2K\r");
    term.write(PROMPT + value);
  }, []);

  const normalizeLineBreaks = useCallback((text: string) => {
    return text.replace(/\r?\n/g, "\r\n");
  }, []);

  const hasAnsiEscape = useCallback((text: string) => {
    return ANSI_ESCAPE_PATTERN.test(text);
  }, []);

  const ensureAnsiReset = useCallback(
    (text: string) => {
      if (!text) return text;
      if (!hasAnsiEscape(text)) return text;
      return text.endsWith(ANSI_RESET) ? text : `${text}${ANSI_RESET}`;
    },
    [hasAnsiEscape],
  );

  const enhanceStdout = useCallback(
    (text: string) => {
      return ensureAnsiReset(normalizeLineBreaks(text));
    },
    [ensureAnsiReset, normalizeLineBreaks],
  );

  const enhanceStderr = useCallback(
    (text: string) => {
      const normalized = ensureAnsiReset(normalizeLineBreaks(text));
      if (hasAnsiEscape(normalized)) {
        return normalized;
      }
      return `${ANSI_RED}${normalized}${ANSI_RESET}`;
    },
    [ensureAnsiReset, hasAnsiEscape, normalizeLineBreaks],
  );

  const toPrintable = useCallback((value: unknown) => {
    if (value == null) return "";
    if (typeof value === "string") return value;
    return String(value);
  }, []);

  const parseTemplateArgs = useCallback((value: string) => {
    return value
      .split(/\r?\n/g)
      .map((line) => line.trim())
      .filter((line) => line.length > 0);
  }, []);

  const parseTemplateEnv = useCallback((value: string) => {
    const env: Record<string, string> = {};
    const lines = value.split(/\r?\n/g);
    for (const rawLine of lines) {
      const line = rawLine.trim();
      if (!line || line.startsWith("#")) {
        continue;
      }
      const separatorIndex = line.indexOf("=");
      if (separatorIndex <= 0) {
        continue;
      }
      const key = line.slice(0, separatorIndex).trim();
      if (!key) {
        continue;
      }
      env[key] = line.slice(separatorIndex + 1);
    }
    return env;
  }, []);

  const applyTemplatePreset = useCallback((executable: string, args: string[]) => {
    setTemplateExecutable(executable);
    setTemplateArgs(args.join("\n"));
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    loadedStorageKeyRef.current = null;
    setConfigLoaded(false);
    const raw = window.localStorage.getItem(storageKey);
    if (!raw) {
      loadedStorageKeyRef.current = storageKey;
      setConfigLoaded(true);
      return;
    }
    try {
      const persisted = JSON.parse(raw) as Partial<PersistedCommandConfig>;
      if (typeof persisted.cwd === "string") {
        setCwdInput(persisted.cwd);
      }
      if (typeof persisted.charset === "string") {
        setCharset(persisted.charset);
      }
      if (typeof persisted.executable === "string") {
        setTemplateExecutable(persisted.executable);
      }
      if (typeof persisted.args === "string") {
        setTemplateArgs(persisted.args || DEFAULT_TEMPLATE_ARGS);
      }
      if (typeof persisted.env === "string") {
        setTemplateEnv(persisted.env);
      }
    } catch {
      // Ignore invalid local storage payload and keep defaults.
    } finally {
      loadedStorageKeyRef.current = storageKey;
      setConfigLoaded(true);
    }
  }, [storageKey]);

  useEffect(() => {
    if (
      typeof window === "undefined" ||
      !configLoaded ||
      loadedStorageKeyRef.current !== storageKey
    ) {
      return;
    }
    const payload: PersistedCommandConfig = {
      cwd: cwdInput,
      charset,
      executable: templateExecutable,
      args: templateArgs,
      env: templateEnv,
    };
    window.localStorage.setItem(storageKey, JSON.stringify(payload));
  }, [charset, configLoaded, cwdInput, storageKey, templateArgs, templateEnv, templateExecutable]);

  useEffect(() => {
    if (!configLoaded) {
      return;
    }
    if (systemDefaultsAppliedShellIdsRef.current.has(shellId)) {
      return;
    }

    const osFamily = detectOsFamily(systemHints?.osName);
    const defaultCharset = getDefaultCharsetByOs(osFamily);
    const defaultTemplate = getDefaultTemplateByOs(osFamily);
    const defaultCwd = (systemHints?.cwd ?? "").trim();

    if (!defaultCwd && !defaultCharset && !defaultTemplate) {
      return;
    }

    if (defaultCwd) {
      setCwdInput(defaultCwd);
    }
    if (defaultCharset) {
      setCharset(defaultCharset);
    }
    if (defaultTemplate) {
      applyTemplatePreset(defaultTemplate.executable, defaultTemplate.args);
    }

    systemDefaultsAppliedShellIdsRef.current.add(shellId);
  }, [applyTemplatePreset, configLoaded, shellId, systemHints]);

  const runCommand = useCallback(
    async (cmd: string) => {
      const term = terminalRef.current;
      if (!term) return;
      if (runningRef.current) {
        term.writeln(`${ANSI_YELLOW}Command is running, please wait...${ANSI_RESET}`);
        return;
      }

      runningRef.current = true;
      commandHistoryRef.current.push(cmd);
      historyIndexRef.current = commandHistoryRef.current.length;
      inputBufferRef.current = "";

      try {
        const template: CommandTemplatePayload = {
          executable: templateExecutable.trim(),
          args: parseTemplateArgs(templateArgs),
          env: parseTemplateEnv(templateEnv),
        };
        const requestArgs: Record<string, unknown> = {
          cmd,
          commandTemplate: template,
        };
        const normalizedCwd = cwdInput.trim();
        if (normalizedCwd) {
          requestArgs.cwd = normalizedCwd;
        }
        const normalizedCharset = charset.trim();
        if (normalizedCharset) {
          requestArgs.charset = normalizedCharset;
        }

        const data = await shellApi.dispatchPlugin({
          id: shellId,
          pluginId: "command-execute",
          args: requestArgs,
        });

        const result = data?.data ?? data?.result ?? data;
        const stdout = toPrintable(result?.stdout);
        const stderr = toPrintable(result?.stderr);
        const error = toPrintable(result?.error);
        const cwd = toPrintable(result?.cwd).trim();
        const charsetUsed = toPrintable(result?.charsetUsed).trim();
        const exitCode = result?.exitCode;

        if (stdout) {
          term.write(enhanceStdout(stdout));
          if (!stdout.endsWith("\n")) {
            term.write("\r\n");
          }
        }

        if (stderr) {
          term.write(`${ANSI_RED}stderr:${ANSI_RESET}\r\n`);
          term.write(enhanceStderr(stderr));
          if (!stderr.endsWith("\n")) {
            term.write("\r\n");
          }
        }

        if (error) {
          term.writeln(`${ANSI_RED}Error: ${error}${ANSI_RESET}`);
        }

        if (cwd) {
          setCwdInput(cwd);
        }

        if (charsetUsed) {
          term.writeln(`${ANSI_DIM}charset: ${charsetUsed}${ANSI_RESET}`);
        }

        if (typeof exitCode === "number" && exitCode !== 0) {
          term.writeln(`${ANSI_YELLOW}exit code: ${exitCode}${ANSI_RESET}`);
        }
      } catch (error: unknown) {
        const message =
          error instanceof Error && error.message ? error.message : "Command execution failed";
        term.writeln(`${ANSI_RED}Error: ${message}${ANSI_RESET}`);
      } finally {
        runningRef.current = false;
        inputBufferRef.current = "";
        term.write("\r\n");
        writePrompt();
      }
    },
    [
      charset,
      cwdInput,
      enhanceStderr,
      enhanceStdout,
      parseTemplateArgs,
      parseTemplateEnv,
      shellId,
      templateArgs,
      templateEnv,
      templateExecutable,
      toPrintable,
      writePrompt,
    ],
  );

  const handleHistoryNavigation = useCallback(
    (direction: -1 | 1) => {
      const history = commandHistoryRef.current;
      if (history.length === 0) return;

      let index = historyIndexRef.current;
      const minIndex = 0;
      const maxIndex = history.length;
      index = Math.max(minIndex, Math.min(maxIndex, index + direction));
      historyIndexRef.current = index;

      if (index === history.length) {
        redrawCurrentLine("");
        return;
      }

      redrawCurrentLine(history[index] || "");
    },
    [redrawCurrentLine],
  );

  const handleTerminalData = useCallback(
    (data: string) => {
      const term = terminalRef.current;
      if (!term) return;

      if (data.length > 1 && !data.startsWith("\x1b")) {
        for (const ch of data) {
          handleTerminalData(ch);
        }
        return;
      }

      if (runningRef.current) {
        if (data === "\u0003") {
          term.writeln("^C");
          term.writeln(`${ANSI_YELLOW}Cannot interrupt: non-PTY command mode${ANSI_RESET}`);
          return;
        }
        if (data === "\r") {
          term.writeln("");
          term.writeln(`${ANSI_YELLOW}Command is running, please wait...${ANSI_RESET}`);
        }
        return;
      }

      if (data === "\r") {
        const cmd = inputBufferRef.current.trim();
        term.write("\r\n");
        if (!cmd) {
          inputBufferRef.current = "";
          writePrompt();
          return;
        }
        runCommand(cmd);
        return;
      }

      if (data === "\u007f") {
        if (!inputBufferRef.current) return;
        inputBufferRef.current = inputBufferRef.current.slice(0, -1);
        term.write("\b \b");
        return;
      }

      if (data === "\u0003") {
        inputBufferRef.current = "";
        term.writeln("^C");
        writePrompt();
        return;
      }

      if (data === "\u000c") {
        inputBufferRef.current = "";
        term.clear();
        term.writeln(`${ANSI_CYAN}NoOne Command Console${ANSI_RESET}`);
        term.writeln(`${ANSI_DIM}Non-PTY mode: each Enter executes one command.${ANSI_RESET}`);
        term.writeln(
          `${ANSI_DIM}Set command template above. Built-in cd works without template.${ANSI_RESET}`,
        );
        writePrompt();
        return;
      }

      if (data === "\x1b[A") {
        handleHistoryNavigation(-1);
        return;
      }

      if (data === "\x1b[B") {
        handleHistoryNavigation(1);
        return;
      }

      if (data >= " " && data !== "\u007f") {
        inputBufferRef.current += data;
        term.write(data);
      }
    },
    [handleHistoryNavigation, runCommand, writePrompt],
  );

  useEffect(() => {
    let resizeObserver: ResizeObserver | null = null;
    let dataSubscription: { dispose: () => void } | null = null;
    let disposed = false;

    const bootstrap = async () => {
      if (!containerRef.current) return;

      const [{ Terminal }, { FitAddon }] = await Promise.all([
        import("@xterm/xterm"),
        import("@xterm/addon-fit"),
      ]);

      if (disposed || !containerRef.current) {
        return;
      }

      const term = new Terminal({
        cursorBlink: true,
        convertEol: true,
        fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
        fontSize: 13,
        lineHeight: 1.2,
        scrollback: 3000,
        theme: terminalThemeRef.current,
      });

      const fitAddon = new FitAddon();
      term.loadAddon(fitAddon);

      term.open(containerRef.current);
      fitAddon.fit();
      term.focus();

      terminalRef.current = term;
      fitAddonRef.current = fitAddon;
      inputBufferRef.current = "";
      commandHistoryRef.current = [];
      historyIndexRef.current = 0;
      runningRef.current = false;

      term.writeln(`${ANSI_CYAN}NoOne Command Console${ANSI_RESET}`);
      term.writeln(`${ANSI_DIM}Non-PTY mode: each Enter executes one command.${ANSI_RESET}`);
      term.writeln(
        `${ANSI_DIM}Set command template above. Built-in cd works without template.${ANSI_RESET}`,
      );
      writePrompt();

      dataSubscription = term.onData((data) => {
        handleTerminalData(data);
      });

      resizeObserver = new ResizeObserver(() => {
        fitAddonRef.current?.fit();
      });
      resizeObserver.observe(containerRef.current);
    };

    bootstrap().catch((error) => {
      const container = containerRef.current;
      if (!container) return;
      const message = error instanceof Error ? error.message : String(error);
      container.innerText = `Failed to initialize terminal: ${message}`;
    });

    return () => {
      disposed = true;
      dataSubscription?.dispose();
      resizeObserver?.disconnect();
      terminalRef.current?.dispose();
      terminalRef.current = null;
      fitAddonRef.current = null;
      runningRef.current = false;
      inputBufferRef.current = "";
      commandHistoryRef.current = [];
      historyIndexRef.current = 0;
    };
  }, [handleTerminalData, writePrompt]);

  useEffect(() => {
    terminalThemeRef.current = terminalTheme;
    const term = terminalRef.current;
    if (!term) {
      return;
    }
    term.options.theme = terminalTheme;
    fitAddonRef.current?.fit();
  }, [terminalTheme]);

  return (
    <div className="flex min-h-0 flex-1 flex-col rounded-lg border">
      <div className="shrink-0 border-b  p-3">
        <div className="grid gap-2 md:grid-cols-2">
          <label className="flex flex-col gap-1 text-xs ">
            Working Directory
            <input
              className="rounded border px-2 py-1 font-mono text-xs "
              value={cwdInput}
              onChange={(event) => setCwdInput(event.target.value)}
              placeholder="server default"
            />
          </label>
          <label className="flex flex-col gap-1 text-xs ">
            Charset (optional)
            <Select
              value={charset || AUTO_CHARSET_VALUE}
              onValueChange={(value) => {
                if (!value || value === AUTO_CHARSET_VALUE) {
                  setCharset("");
                  return;
                }
                setCharset(value);
              }}
            >
              <SelectTrigger className="w-full font-mono text-xs">
                <SelectValue placeholder="Select charset" />
              </SelectTrigger>
              <SelectContent>
                {CHARSET_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>
        </div>

        <div className="mt-2 grid gap-2 md:grid-cols-2">
          <label className="flex flex-col gap-1 text-xs ">
            Template Executable
            <input
              className="rounded border px-2 py-1 font-mono text-xs "
              value={templateExecutable}
              onChange={(event) => setTemplateExecutable(event.target.value)}
              placeholder="/bin/sh or cmd.exe"
            />
          </label>
          <label className="flex flex-col gap-1 text-xs">
            Template Args (one per line, supports {"{{cmd}} / {{cwd}}"})
            <textarea
              className="min-h-16 rounded border  px-2 py-1 font-mono text-xs "
              value={templateArgs}
              onChange={(event) => setTemplateArgs(event.target.value)}
            />
          </label>
        </div>

        <label className="mt-2 flex flex-col gap-1 text-xs">
          Template Env (KEY=VALUE per line, supports {"{{cmd}} / {{cwd}}"})
          <textarea
            className="min-h-14 rounded border px-2 py-1 font-mono text-xs"
            value={templateEnv}
            onChange={(event) => setTemplateEnv(event.target.value)}
          />
        </label>

        <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
          <span className="">Presets:</span>
          <button
            type="button"
            className="rounded border px-2 py-1"
            onClick={() => applyTemplatePreset("/bin/sh", ["-c", "{{cmd}}"])}
          >
            /bin/sh -c
          </button>
          <button
            type="button"
            className="rounded border  px-2 py-1"
            onClick={() => applyTemplatePreset("cmd.exe", ["/c", "{{cmd}}"])}
          >
            cmd.exe /c
          </button>
          <button
            type="button"
            className="rounded border  px-2 py-1"
            onClick={() => applyTemplatePreset("powershell.exe", ["-Command", "{{cmd}}"])}
          >
            powershell -Command
          </button>
        </div>
      </div>
      <div ref={containerRef} className="h-full min-h-0 flex-1 p-2" />
    </div>
  );
}
