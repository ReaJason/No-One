import { useTheme } from "next-themes";
import { useCallback, useEffect, useMemo, useRef } from "react";

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

interface TerminalInstance {
  write: (data: string) => void;
  writeln: (data: string) => void;
  clear: () => void;
  focus: () => void;
  options: { theme?: unknown };
  dispose: () => void;
}

export interface CommandResult {
  stdout?: unknown;
  stderr?: unknown;
  error?: unknown;
  cwd?: unknown;
  charsetUsed?: unknown;
  exitCode?: unknown;
}

export interface UseCommandTerminalOptions {
  onCwdChanged?: (cwd: string) => void;
}

const PROMPT = "$ ";
const ANSI_RESET = "\x1b[0m";
const ANSI_RED = "\x1b[31m";
const ANSI_YELLOW = "\x1b[33m";
const ANSI_CYAN = "\x1b[36m";
const ANSI_DIM = "\x1b[2m";
// eslint-disable-next-line no-control-regex
const ANSI_ESCAPE_PATTERN = /\u001b\[[0-9;?]*[ -/]*[@-~]/;

const XTERM_THEME_LIGHT: XtermTheme = {
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

const XTERM_THEME_DARK: XtermTheme = {
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

function hasAnsiEscape(text: string) {
  return ANSI_ESCAPE_PATTERN.test(text);
}

function normalizeLineBreaks(text: string) {
  return text.replace(/\r?\n/g, "\r\n");
}

function ensureAnsiReset(text: string) {
  if (!text) return text;
  if (!hasAnsiEscape(text)) return text;
  return text.endsWith(ANSI_RESET) ? text : `${text}${ANSI_RESET}`;
}

function enhanceStdout(text: string) {
  return ensureAnsiReset(normalizeLineBreaks(text));
}

function enhanceStderr(text: string) {
  const normalized = ensureAnsiReset(normalizeLineBreaks(text));
  if (hasAnsiEscape(normalized)) return normalized;
  return `${ANSI_RED}${normalized}${ANSI_RESET}`;
}

function toPrintable(value: unknown) {
  if (value == null) return "";
  if (typeof value === "string") return value;
  return String(value);
}

export function useCommandTerminal(options?: UseCommandTerminalOptions) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const terminalRef = useRef<TerminalInstance | null>(null);
  const fitAddonRef = useRef<{ fit: () => void } | null>(null);
  const runningRef = useRef(false);
  const inputBufferRef = useRef("");
  const commandHistoryRef = useRef<string[]>([]);
  const historyIndexRef = useRef(0);
  const handleTerminalDataRef = useRef<(data: string) => void>(() => {});
  const runCommandRef = useRef<(cmd: string) => void>(() => {});
  const onCwdChangedRef = useRef(options?.onCwdChanged);

  const { resolvedTheme, theme } = useTheme();

  const effectiveTheme = useMemo<"light" | "dark">(() => {
    if (resolvedTheme === "light" || resolvedTheme === "dark") return resolvedTheme;
    if (theme === "light" || theme === "dark") return theme;
    return "dark";
  }, [resolvedTheme, theme]);

  const terminalTheme = useMemo(
    () => (effectiveTheme === "light" ? XTERM_THEME_LIGHT : XTERM_THEME_DARK),
    [effectiveTheme],
  );
  const terminalThemeRef = useRef<XtermTheme>(terminalTheme);

  useEffect(() => {
    onCwdChangedRef.current = options?.onCwdChanged;
  }, [options?.onCwdChanged]);

  const writePrompt = useCallback(() => {
    terminalRef.current?.write(PROMPT);
  }, []);

  const writeBanner = useCallback(
    (term: TerminalInstance) => {
      term.writeln(`${ANSI_CYAN}NoOne Command Console${ANSI_RESET}`);
      term.writeln(`${ANSI_DIM}Non-PTY mode: each Enter executes one command.${ANSI_RESET}`);
      term.writeln(
        `${ANSI_DIM}Set command template above. Built-in cd works without template.${ANSI_RESET}`,
      );
      writePrompt();
    },
    [writePrompt],
  );

  const writeResult = useCallback((term: TerminalInstance, result: CommandResult) => {
    const stdout = toPrintable(result.stdout);
    const stderr = toPrintable(result.stderr);
    const error = toPrintable(result.error);
    const cwd = toPrintable(result.cwd).trim();
    const charsetUsed = toPrintable(result.charsetUsed).trim();
    const exitCode = result.exitCode;

    if (stdout) {
      term.write(enhanceStdout(stdout));
      if (!stdout.endsWith("\n")) term.write("\r\n");
    }

    if (stderr) {
      term.write(`${ANSI_RED}stderr:${ANSI_RESET}\r\n`);
      term.write(enhanceStderr(stderr));
      if (!stderr.endsWith("\n")) term.write("\r\n");
    }

    if (error) {
      term.writeln(`${ANSI_RED}Error: ${error}${ANSI_RESET}`);
    }

    if (cwd) {
      onCwdChangedRef.current?.(cwd);
    }

    if (charsetUsed) {
      term.writeln(`${ANSI_DIM}charset: ${charsetUsed}${ANSI_RESET}`);
    }

    if (typeof exitCode === "number" && exitCode !== 0) {
      term.writeln(`${ANSI_YELLOW}exit code: ${exitCode}${ANSI_RESET}`);
    }
  }, []);

  const redrawCurrentLine = useCallback((value: string) => {
    inputBufferRef.current = value;
    const term = terminalRef.current;
    if (!term) return;
    term.write("\x1b[2K\r");
    term.write(PROMPT + value);
  }, []);

  const handleHistoryNavigation = useCallback(
    (direction: -1 | 1) => {
      const history = commandHistoryRef.current;
      if (history.length === 0) return;

      let index = historyIndexRef.current;
      index = Math.max(0, Math.min(history.length, index + direction));
      historyIndexRef.current = index;

      redrawCurrentLine(index === history.length ? "" : history[index] || "");
    },
    [redrawCurrentLine],
  );

  const handleTerminalData = useCallback(
    (data: string) => {
      const term = terminalRef.current;
      if (!term) return;

      if (data.length > 1 && !data.startsWith("\x1b")) {
        for (const ch of data) handleTerminalData(ch);
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
        runCommandRef.current(cmd);
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
        writeBanner(term);
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
    [handleHistoryNavigation, writeBanner, writePrompt],
  );

  useEffect(() => {
    handleTerminalDataRef.current = handleTerminalData;
  }, [handleTerminalData]);

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

      if (disposed || !containerRef.current) return;

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

      writeBanner(term);

      dataSubscription = term.onData((data) => {
        handleTerminalDataRef.current(data);
      });

      resizeObserver = new ResizeObserver(() => {
        fitAddonRef.current?.fit();
      });
      resizeObserver.observe(containerRef.current);
    };

    bootstrap().catch((err) => {
      const container = containerRef.current;
      if (!container) return;
      container.innerText = `Failed to initialize terminal: ${err instanceof Error ? err.message : String(err)}`;
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
  }, [writeBanner]);

  useEffect(() => {
    terminalThemeRef.current = terminalTheme;
    const term = terminalRef.current;
    if (!term) return;
    term.options.theme = terminalTheme;
    fitAddonRef.current?.fit();
  }, [terminalTheme]);

  const executeCommand = useCallback(
    async (execute: () => Promise<CommandResult>) => {
      const term = terminalRef.current;
      if (!term) return;
      if (runningRef.current) {
        term.writeln(`${ANSI_YELLOW}Command is running, please wait...${ANSI_RESET}`);
        return;
      }

      const cmd = inputBufferRef.current.trim();
      runningRef.current = true;
      commandHistoryRef.current.push(cmd);
      historyIndexRef.current = commandHistoryRef.current.length;
      inputBufferRef.current = "";

      try {
        const result = await execute();
        writeResult(term, result);
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
    [writePrompt, writeResult],
  );

  const setRunCommand = useCallback((fn: (cmd: string) => void) => {
    runCommandRef.current = fn;
  }, []);

  return {
    containerRef,
    executeCommand,
    setRunCommand,
  };
}
