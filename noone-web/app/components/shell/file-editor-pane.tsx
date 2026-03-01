import { Save, X } from "lucide-react";
import { useTheme } from "next-themes";
import React, { useEffect, useMemo, useRef, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface FileEditorPaneProps {
  filePath: string | null;
  language: string;
  value: string;
  isDirty: boolean;
  readOnlyReason?: string | null;
  onChange: (value: string) => void;
  onSave: () => void;
  onClose: () => void;
}

type MonacoEditorComponent = (props: {
  defaultValue?: string;
  defaultLanguage?: string;
  path?: string;
  theme?: string;
  options?: Record<string, unknown>;
  onChange?: (value: string | undefined) => void;
  onMount?: (editor: any, monaco: any) => void;
  className?: string;
}) => React.ReactElement;

function toErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }
  return String(error);
}

function getFileName(path: string) {
  const normalized = path.replace(/\\/g, "/");
  const parts = normalized.split("/").filter(Boolean);
  return parts[parts.length - 1] ?? path;
}

export default function FileEditorPane({
  filePath,
  language,
  value,
  isDirty,
  readOnlyReason = null,
  onChange,
  onSave,
  onClose,
}: FileEditorPaneProps) {
  const [MonacoEditor, setMonacoEditor] = useState<MonacoEditorComponent | null>(null);
  const [monacoLoadError, setMonacoLoadError] = useState<string | null>(null);
  const editorRef = useRef<any>(null);
  const monacoRef = useRef<any>(null);
  const isInternalChange = useRef(false);
  const { resolvedTheme, theme } = useTheme();

  const monacoTheme = useMemo(() => {
    if (resolvedTheme === "light" || theme === "light") {
      return "vs";
    }
    return "vs-dark";
  }, [resolvedTheme, theme]);

  useEffect(() => {
    let disposed = false;
    const bootstrap = async () => {
      try {
        const [{ default: Monaco, loader }, monaco] = await Promise.all([
          import("@monaco-editor/react"),
          import("monaco-editor"),
        ]);
        if (disposed) return;
        loader.config({ monaco });
        setMonacoEditor(() => Monaco as MonacoEditorComponent);
        setMonacoLoadError(null);
      } catch (error) {
        if (!disposed) {
          setMonacoLoadError(toErrorMessage(error));
          setMonacoEditor(null);
        }
      }
    };
    bootstrap().catch((error) => {
      if (!disposed) {
        setMonacoLoadError(toErrorMessage(error));
      }
    });
    return () => {
      disposed = true;
    };
  }, []);

  const isMonacoReady = MonacoEditor != null && monacoLoadError == null;

  useEffect(() => {
    if (!filePath || isMonacoReady) return;
    const onKeyDown = (event: KeyboardEvent) => {
      const withMeta = event.metaKey || event.ctrlKey;
      if (!withMeta) return;
      if (event.key.toLowerCase() !== "s") return;
      event.preventDefault();
      onSave();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [filePath, onSave, isMonacoReady]);

  useEffect(() => {
    if (isInternalChange.current) {
      isInternalChange.current = false;
      return;
    }
    if (!filePath || !editorRef.current || !monacoRef.current) return;
    const model = editorRef.current.getModel?.();
    if (!model) return;
    const currentValue = model.getValue?.() ?? "";
    if (currentValue !== value) {
      model.setValue?.(value);
    }
  }, [filePath, value]);

  useEffect(() => {
    if (!editorRef.current || !monacoRef.current) return;
    const model = editorRef.current.getModel?.();
    if (!model) return;
    const currentLanguage = model.getLanguageId?.();
    if (currentLanguage !== language) {
      monacoRef.current.editor.setModelLanguage(model, language);
    }
  }, [language, filePath]);

  if (!filePath) {
    return (
      <div className="flex h-full min-h-0 flex-col">
        <div className="border-b border-border/70 px-4 py-3">
          <div className="text-sm font-medium">Editor</div>
        </div>
        <div className="flex min-h-0 flex-1 items-center justify-center p-6 text-sm text-muted-foreground">
          Open a text or code file to preview and edit.
        </div>
      </div>
    );
  }

  const fileName = getFileName(filePath);
  const isReadOnly = readOnlyReason != null && readOnlyReason.length > 0;

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex items-center gap-2 border-b border-border/70 px-4 py-3">
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-medium">{fileName}</div>
          <div className="truncate font-mono text-[11px] text-muted-foreground">{filePath}</div>
        </div>
        <Badge variant={isDirty ? "default" : "secondary"}>{isDirty ? "Dirty" : "Saved"}</Badge>
        <Button type="button" size="sm" variant="outline" onClick={onSave} disabled={!isDirty || isReadOnly}>
          <Save className="size-3.5" />
          Save
        </Button>
        <Button type="button" size="icon-sm" variant="ghost" onClick={onClose} title="Close">
          <X className="size-3.5" />
        </Button>
      </div>

      <div className="min-h-0 flex-1">
        {isReadOnly ? (
          <div className="flex h-full min-h-0 flex-col">
            <div className="border-b border-border/70 bg-amber-50 px-3 py-2 text-xs text-amber-800">
              {readOnlyReason}
            </div>
            <div className="flex min-h-0 flex-1 items-center justify-center p-6 text-sm text-muted-foreground">
              Preview is disabled for this file.
            </div>
          </div>
        ) : MonacoEditor && !monacoLoadError ? (
          <MonacoEditor
            className="h-full"
            path={filePath}
            defaultLanguage={language}
            defaultValue={value}
            theme={monacoTheme}
            onChange={(nextValue) => {
              isInternalChange.current = true;
              onChange(nextValue ?? "");
            }}
            onMount={(editor, monaco) => {
              editorRef.current = editor;
              monacoRef.current = monaco;
              editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
                onSave();
              });
            }}
            options={{
              automaticLayout: true,
              minimap: { enabled: false },
              fontSize: 13,
              scrollBeyondLastLine: false,
              tabSize: 2,
              wordWrap: "on",
            }}
          />
        ) : (
          <div className="flex h-full min-h-0 flex-col">
            <div
              className={cn(
                "border-b border-border/70 px-3 py-2 text-xs",
                monacoLoadError ? "text-amber-700" : "text-muted-foreground",
              )}
            >
              {monacoLoadError
                ? `Monaco load failed, fallback editor enabled: ${monacoLoadError}`
                : "Loading Monaco..."}
            </div>
            <textarea
              value={value}
              onChange={(event) => onChange(event.target.value)}
              className="h-full min-h-0 w-full resize-none border-0 bg-transparent p-3 font-mono text-sm outline-none"
              spellCheck={false}
            />
          </div>
        )}
      </div>
    </div>
  );
}
