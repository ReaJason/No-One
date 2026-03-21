import { ArrowRight, File as FileIcon, Loader2, Minus, Plus, Upload, X } from "lucide-react";
import { useCallback, useMemo, useRef, useState } from "react";

import { CopyButton } from "@/components/memshell/code-viewer";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { useShellRouteFetcher } from "@/hooks/use-shell-route-fetcher";
import { buildShellRouteFormData, createShellRouteRequestId } from "@/lib/shell-route";

const HTTP_METHODS = ["GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"] as const;
type HttpMethod = (typeof HTTP_METHODS)[number];

const BODY_TYPES = ["none", "form-urlencoded", "form-data", "json", "xml", "raw"] as const;
type BodyType = (typeof BODY_TYPES)[number];

const METHODS_WITH_BODY = new Set<string>(["POST", "PUT", "PATCH", "DELETE"]);

interface KeyValuePair {
  id: string;
  key: string;
  value: string;
}

interface FormDataEntry {
  id: string;
  key: string;
  value: string;
  type: "text" | "file";
  filename?: string;
  fileContentType?: string;
}

interface HttpResponse {
  statusCode: number;
  statusMessage: string;
  responseHeaders: Record<string, string>;
  body: string;
  contentLength: number;
  error?: string;
  url: string;
  method: string;
  redirectCount?: number;
  finalUrl?: string;
}

interface HttpRepeaterProps {
  shellId: number;
  actionPath: string;
}

function createPair(): KeyValuePair {
  return { id: crypto.randomUUID(), key: "", value: "" };
}

function createFormEntry(): FormDataEntry {
  return { id: crypto.randomUUID(), key: "", value: "", type: "text" };
}

function getStatusColor(code: number): string {
  if (code >= 200 && code < 300) return "bg-emerald-600 text-white";
  if (code >= 300 && code < 400) return "bg-blue-600 text-white";
  if (code >= 400 && code < 500) return "bg-amber-600 text-white";
  return "bg-red-600 text-white";
}

function fileToBase64(file: globalThis.File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = reader.result as string;
      const base64 = dataUrl.split(",")[1] || "";
      resolve(base64);
    };
    reader.onerror = () => reject(new Error("Failed to read file"));
    reader.readAsDataURL(file);
  });
}

function KeyValueEditor({
  pairs,
  onChange,
  disabled,
  keyPlaceholder = "Key",
  valuePlaceholder = "Value",
}: {
  pairs: KeyValuePair[];
  onChange: (pairs: KeyValuePair[]) => void;
  disabled?: boolean;
  keyPlaceholder?: string;
  valuePlaceholder?: string;
}) {
  const updatePair = (id: string, field: "key" | "value", val: string) => {
    onChange(pairs.map((p) => (p.id === id ? { ...p, [field]: val } : p)));
  };

  const removePair = (id: string) => {
    const next = pairs.filter((p) => p.id !== id);
    onChange(next.length === 0 ? [createPair()] : next);
  };

  return (
    <div className="space-y-1.5">
      {pairs.map((pair) => (
        <div key={pair.id} className="flex items-center gap-1.5">
          <Input
            value={pair.key}
            onChange={(e) => updatePair(pair.id, "key", e.target.value)}
            placeholder={keyPlaceholder}
            disabled={disabled}
            className="h-7 flex-1 font-mono text-xs"
          />
          <Input
            value={pair.value}
            onChange={(e) => updatePair(pair.id, "value", e.target.value)}
            placeholder={valuePlaceholder}
            disabled={disabled}
            className="h-7 flex-1 font-mono text-xs"
          />
          <Button
            variant="ghost"
            size="icon"
            className="size-7 shrink-0"
            onClick={() => removePair(pair.id)}
            disabled={disabled}
          >
            <Minus className="size-3" />
          </Button>
        </div>
      ))}
      <Button
        variant="ghost"
        size="sm"
        onClick={() => onChange([...pairs, createPair()])}
        disabled={disabled}
        className="h-6 gap-1 text-xs text-muted-foreground"
      >
        <Plus className="size-3" />
        Add
      </Button>
    </div>
  );
}

function FormDataEditor({
  entries,
  onChange,
  disabled,
}: {
  entries: FormDataEntry[];
  onChange: (entries: FormDataEntry[]) => void;
  disabled?: boolean;
}) {
  const fileInputRefs = useRef<Record<string, HTMLInputElement | null>>({});

  const updateEntry = (id: string, update: Partial<FormDataEntry>) => {
    onChange(entries.map((e) => (e.id === id ? { ...e, ...update } : e)));
  };

  const removeEntry = (id: string) => {
    const next = entries.filter((e) => e.id !== id);
    onChange(next.length === 0 ? [createFormEntry()] : next);
  };

  const toggleType = (id: string, newType: "text" | "file") => {
    onChange(
      entries.map((e) =>
        e.id === id
          ? { ...e, type: newType, value: "", filename: undefined, fileContentType: undefined }
          : e,
      ),
    );
  };

  const handleFileSelect = async (id: string, file: globalThis.File) => {
    const base64 = await fileToBase64(file);
    updateEntry(id, {
      value: base64,
      filename: file.name,
      fileContentType: file.type || "application/octet-stream",
    });
  };

  return (
    <div className="space-y-1.5">
      {entries.map((entry) => (
        <div key={entry.id} className="flex items-center gap-1.5">
          <Input
            value={entry.key}
            onChange={(e) => updateEntry(entry.id, { key: e.target.value })}
            placeholder="Field name"
            disabled={disabled}
            className="h-7 w-32 shrink-0 font-mono text-xs"
          />
          <Select
            value={entry.type}
            onValueChange={(v) => toggleType(entry.id, v as "text" | "file")}
          >
            <SelectTrigger className="h-7 w-16 shrink-0 text-xs">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="text">Text</SelectItem>
              <SelectItem value="file">File</SelectItem>
            </SelectContent>
          </Select>
          {entry.type === "text" ? (
            <Input
              value={entry.value}
              onChange={(e) => updateEntry(entry.id, { value: e.target.value })}
              placeholder="Field value"
              disabled={disabled}
              className="h-7 flex-1 font-mono text-xs"
            />
          ) : (
            <div className="flex flex-1 items-center gap-1">
              <input
                ref={(el) => {
                  fileInputRefs.current[entry.id] = el;
                }}
                type="file"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) handleFileSelect(entry.id, file);
                }}
              />
              <Button
                variant="outline"
                size="sm"
                className="h-7 gap-1 text-xs"
                disabled={disabled}
                onClick={() => fileInputRefs.current[entry.id]?.click()}
              >
                <Upload className="size-3" />
                File
              </Button>
              {entry.filename && (
                <span className="truncate text-xs text-muted-foreground">{entry.filename}</span>
              )}
            </div>
          )}
          <Button
            variant="ghost"
            size="icon"
            className="size-7 shrink-0"
            onClick={() => removeEntry(entry.id)}
            disabled={disabled}
          >
            <Minus className="size-3" />
          </Button>
        </div>
      ))}
      <Button
        variant="ghost"
        size="sm"
        onClick={() => onChange([...entries, createFormEntry()])}
        disabled={disabled}
        className="h-6 gap-1 text-xs text-muted-foreground"
      >
        <Plus className="size-3" />
        Add
      </Button>
    </div>
  );
}

export default function HttpRepeater({ shellId, actionPath }: HttpRepeaterProps) {
  const [method, setMethod] = useState<HttpMethod>("GET");
  const [url, setUrl] = useState("https://t.cn");
  const [bodyType, setBodyType] = useState<BodyType>("none");
  const [bodyText, setBodyText] = useState("");
  const [rawFile, setRawFile] = useState<{ base64: string; name: string; type: string } | null>(
    null,
  );
  const [rawBodyMode, setRawBodyMode] = useState<"text" | "file">("text");
  const [formEntries, setFormEntries] = useState<FormDataEntry[]>([createFormEntry()]);
  const [formUrlencodedPairs, setFormUrlencodedPairs] = useState<KeyValuePair[]>([createPair()]);
  const [headerPairs, setHeaderPairs] = useState<KeyValuePair[]>([createPair()]);
  const [paramPairs, setParamPairs] = useState<KeyValuePair[]>([createPair()]);
  const [followRedirects, setFollowRedirects] = useState(true);
  const [maxRedirects, setMaxRedirects] = useState("5");
  const [timeout, setTimeout_] = useState("5000");
  const [loading, setLoading] = useState(false);
  const [response, setResponse] = useState<HttpResponse | null>(null);
  const [responseTab, setResponseTab] = useState("body");
  const [requestTab, setRequestTab] = useState("params");
  const [elapsed, setElapsed] = useState<number | null>(null);

  const { submit } = useShellRouteFetcher<HttpResponse>();
  const startTimeRef = useRef<number>(0);
  const rawFileInputRef = useRef<HTMLInputElement | null>(null);

  const handleMethodChange = useCallback((val: string | null) => {
    if (!val) return;
    const m = val as HttpMethod;
    setMethod(m);
    if (!METHODS_WITH_BODY.has(m)) {
      setBodyType("none");
    }
  }, []);

  const handleRawFileSelect = useCallback(async (file: globalThis.File) => {
    const base64 = await fileToBase64(file);
    setRawFile({ base64, name: file.name, type: file.type || "application/octet-stream" });
  }, []);

  const sendRequest = useCallback(async () => {
    if (!url.trim()) return;

    setLoading(true);
    setResponse(null);
    setElapsed(null);
    startTimeRef.current = performance.now();

    try {
      const headers: Record<string, string> = {};
      for (const pair of headerPairs) {
        if (pair.key.trim()) {
          headers[pair.key.trim()] = pair.value;
        }
      }

      const params = paramPairs
        .filter((p) => p.key.trim())
        .map((p) => ({ key: p.key.trim(), value: p.value }));

      const args: Record<string, unknown> = {
        url: url.trim(),
        method,
        headers,
        params: params.length > 0 ? params : undefined,
        contentType: METHODS_WITH_BODY.has(method) ? bodyType : "none",
        followRedirects,
        maxRedirects: followRedirects ? parseInt(maxRedirects, 10) || 5 : undefined,
        timeout: parseInt(timeout, 10) || 5000,
      };

      if (METHODS_WITH_BODY.has(method) && bodyType !== "none") {
        if (bodyType === "form-urlencoded") {
          args.formData = formUrlencodedPairs
            .filter((p) => p.key.trim())
            .map((p) => ({ key: p.key.trim(), value: p.value }));
        } else if (bodyType === "form-data") {
          args.formData = formEntries
            .filter((e) => e.key.trim())
            .map((e) => ({
              key: e.key.trim(),
              value: e.value,
              type: e.type,
              filename: e.filename,
              fileContentType: e.fileContentType,
            }));
        } else if (bodyType === "raw" && rawBodyMode === "file" && rawFile) {
          args.bodyBase64 = rawFile.base64;
          if (rawFile.type) {
            headers["Content-Type"] = rawFile.type;
            args.headers = headers;
          }
        } else {
          args.body = bodyText;
        }
      }

      const requestId = createShellRouteRequestId();
      const timeoutMs = parseInt(timeout, 10) || 30000;
      const submitPromise = submit(
        buildShellRouteFormData("send-request", args, requestId),
        { method: "post", action: actionPath },
        requestId,
      );
      const timeoutPromise = new Promise<never>((_, reject) => {
        window.setTimeout(() => reject(new Error("Request timed out")), timeoutMs + 10000);
      });
      const result = await Promise.race([submitPromise, timeoutPromise]);

      setElapsed(Math.round(performance.now() - startTimeRef.current));
      setResponse(result as unknown as HttpResponse);
    } catch (err: any) {
      setElapsed(Math.round(performance.now() - startTimeRef.current));
      setResponse({
        statusCode: 0,
        statusMessage: "",
        responseHeaders: {},
        body: "",
        contentLength: 0,
        error: err.message || "Request failed",
        url: url.trim(),
        method,
      });
    } finally {
      setLoading(false);
    }
  }, [
    url,
    method,
    bodyType,
    bodyText,
    rawBodyMode,
    rawFile,
    formEntries,
    formUrlencodedPairs,
    headerPairs,
    paramPairs,
    followRedirects,
    maxRedirects,
    timeout,
    submit,
    actionPath,
  ]);

  const showBody = METHODS_WITH_BODY.has(method);
  const respHeaderCount = response?.responseHeaders
    ? Object.keys(response.responseHeaders).length
    : 0;

  const responseCopyHeaders = useMemo(() => {
    if (!response?.responseHeaders) return "";
    return Object.entries(response.responseHeaders)
      .map(([k, v]) => `${k}: ${v}`)
      .join("\n");
  }, [response?.responseHeaders]);

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      {/* URL bar */}
      <div className="flex items-center gap-2 py-2">
        <Select value={method} onValueChange={handleMethodChange}>
          <SelectTrigger className="h-8 w-[110px] shrink-0 font-mono text-sm font-semibold">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {HTTP_METHODS.map((m) => (
              <SelectItem key={m} value={m}>
                {m}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Input
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="https://example.com/api/endpoint"
          className="h-8 flex-1 font-mono text-sm"
          onKeyDown={(e) => {
            if (e.key === "Enter" && !loading) sendRequest();
          }}
        />
        <Button onClick={sendRequest} disabled={loading || !url.trim()} className="h-8 gap-1.5">
          {loading ? (
            <Loader2 className="size-3.5 animate-spin" />
          ) : (
            <ArrowRight className="size-3.5" />
          )}
        </Button>
      </div>

      {/* Left-right split */}
      <div className="flex min-h-0 flex-1">
        {/* Left: request config */}
        <div className="flex min-h-0 w-1/2 flex-col overflow-hidden border-r border-border/70">
          <Tabs
            value={requestTab}
            onValueChange={setRequestTab}
            className="flex min-h-0 flex-1 flex-col"
          >
            <TabsList variant="line">
              <TabsTrigger value="params">Params</TabsTrigger>
              {showBody && <TabsTrigger value="body">Body</TabsTrigger>}
              <TabsTrigger value="headers">Headers</TabsTrigger>
              <TabsTrigger value="settings">Settings</TabsTrigger>
            </TabsList>

            <ScrollArea className="flex-1">
              <div className="p-3">
                <TabsContent value="params" className="mt-0 flex flex-col gap-2">
                  <KeyValueEditor
                    pairs={paramPairs}
                    onChange={setParamPairs}
                    disabled={loading}
                    keyPlaceholder="Param name"
                    valuePlaceholder="Param value"
                  />
                </TabsContent>

                {showBody && (
                  <TabsContent value="body" className="mt-0 flex flex-col gap-3">
                    <div className="flex flex-wrap items-center gap-2">
                      {BODY_TYPES.map((bt) => (
                        <label key={bt} className="flex cursor-pointer items-center gap-1 text-xs">
                          <input
                            type="radio"
                            name="bodyType"
                            value={bt}
                            checked={bodyType === bt}
                            onChange={() => setBodyType(bt)}
                            disabled={loading}
                            className="accent-primary"
                          />
                          <span
                            className={
                              bodyType === bt
                                ? "font-medium text-foreground"
                                : "text-muted-foreground"
                            }
                          >
                            {bt}
                          </span>
                        </label>
                      ))}
                    </div>

                    {bodyType === "none" && (
                      <p className="text-xs text-muted-foreground">
                        This request does not have a body.
                      </p>
                    )}

                    {bodyType === "form-urlencoded" && (
                      <KeyValueEditor
                        pairs={formUrlencodedPairs}
                        onChange={setFormUrlencodedPairs}
                        disabled={loading}
                        keyPlaceholder="Field name"
                        valuePlaceholder="Field value"
                      />
                    )}

                    {bodyType === "form-data" && (
                      <FormDataEditor
                        entries={formEntries}
                        onChange={setFormEntries}
                        disabled={loading}
                      />
                    )}

                    {bodyType === "json" && (
                      <Textarea
                        value={bodyText}
                        onChange={(e) => setBodyText(e.target.value)}
                        placeholder='{"key": "value"}'
                        disabled={loading}
                        className="min-h-[200px] font-mono text-xs"
                      />
                    )}

                    {bodyType === "xml" && (
                      <Textarea
                        value={bodyText}
                        onChange={(e) => setBodyText(e.target.value)}
                        placeholder='<?xml version="1.0"?>'
                        disabled={loading}
                        className="min-h-[200px] font-mono text-xs"
                      />
                    )}

                    {bodyType === "raw" && (
                      <div className="flex flex-col gap-2">
                        <div className="flex items-center gap-3">
                          {(["text", "file"] as const).map((mode) => (
                            <label
                              key={mode}
                              className="flex cursor-pointer items-center gap-1 text-xs"
                            >
                              <input
                                type="radio"
                                name="rawBodyMode"
                                value={mode}
                                checked={rawBodyMode === mode}
                                onChange={() => setRawBodyMode(mode)}
                                disabled={loading}
                                className="accent-primary"
                              />
                              <span
                                className={
                                  rawBodyMode === mode
                                    ? "font-medium text-foreground"
                                    : "text-muted-foreground"
                                }
                              >
                                {mode === "text" ? "Text" : "File"}
                              </span>
                            </label>
                          ))}
                        </div>

                        {rawBodyMode === "text" ? (
                          <Textarea
                            value={bodyText}
                            onChange={(e) => setBodyText(e.target.value)}
                            placeholder="Raw request body..."
                            disabled={loading}
                            className="min-h-[200px] font-mono text-xs"
                          />
                        ) : (
                          <div className="flex items-center gap-2">
                            <input
                              ref={rawFileInputRef}
                              type="file"
                              className="hidden"
                              onChange={(e) => {
                                const file = e.target.files?.[0];
                                if (file) handleRawFileSelect(file);
                              }}
                            />
                            <Button
                              variant="outline"
                              size="sm"
                              className="h-7 gap-1 text-xs"
                              disabled={loading}
                              onClick={() => rawFileInputRef.current?.click()}
                            >
                              <Upload className="size-3" />
                              Choose File
                            </Button>
                            {rawFile ? (
                              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                <FileIcon className="size-3" />
                                <span className="max-w-[120px] truncate">{rawFile.name}</span>
                                <Button
                                  variant="ghost"
                                  size="icon"
                                  className="size-5"
                                  onClick={() => setRawFile(null)}
                                >
                                  <X className="size-2.5" />
                                </Button>
                              </div>
                            ) : (
                              <span className="text-xs text-muted-foreground">
                                No file selected
                              </span>
                            )}
                          </div>
                        )}
                      </div>
                    )}
                  </TabsContent>
                )}

                <TabsContent value="headers" className="mt-0 flex flex-col gap-2">
                  <KeyValueEditor
                    pairs={headerPairs}
                    onChange={setHeaderPairs}
                    disabled={loading}
                    keyPlaceholder="Header name"
                    valuePlaceholder="Header value"
                  />
                </TabsContent>

                <TabsContent
                  value="settings"
                  className="mt-0 flex flex-col divide-y divide-border/70"
                >
                  <div className="flex items-center justify-between py-3">
                    <div>
                      <div className="text-xs font-medium">Follow Redirects</div>
                      <div className="text-[11px] text-muted-foreground">
                        Automatically follow HTTP redirects
                      </div>
                    </div>
                    <Switch
                      checked={followRedirects}
                      onCheckedChange={setFollowRedirects}
                      disabled={loading}
                    />
                  </div>
                  {followRedirects && (
                    <div className="flex items-center justify-between py-3">
                      <div>
                        <div className="text-xs font-medium">Max Redirects</div>
                        <div className="text-[11px] text-muted-foreground">
                          Limit the number of redirects to follow
                        </div>
                      </div>
                      <Input
                        value={maxRedirects}
                        onChange={(e) => setMaxRedirects(e.target.value)}
                        type="number"
                        min="1"
                        max="20"
                        disabled={loading}
                        className="h-7 w-20 font-mono text-xs"
                      />
                    </div>
                  )}
                  <div className="flex items-center justify-between py-3">
                    <div>
                      <div className="text-xs font-medium">Timeout (ms)</div>
                      <div className="text-[11px] text-muted-foreground">
                        Maximum time to wait before aborting the request
                      </div>
                    </div>
                    <Input
                      value={timeout}
                      onChange={(e) => setTimeout_(e.target.value)}
                      type="number"
                      disabled={loading}
                      className="h-7 w-20 font-mono text-xs"
                    />
                  </div>
                </TabsContent>
              </div>
            </ScrollArea>
          </Tabs>
        </div>

        {/* Right: response */}
        <div className="flex min-h-0 w-1/2 flex-col overflow-hidden">
          {!response && !loading && (
            <div className="flex flex-1 items-center justify-center text-sm text-muted-foreground">
              Response
            </div>
          )}

          {loading && !response && (
            <div className="flex flex-1 items-center justify-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" />
              Sending request...
            </div>
          )}

          {response && (
            <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
              <div className="flex shrink-0 items-center justify-between px-2">
                <Tabs value={responseTab} onValueChange={setResponseTab} className="flex-1">
                  <TabsList variant="line">
                    <TabsTrigger value="body">Response</TabsTrigger>
                    <TabsTrigger value="headers">
                      Headers {respHeaderCount > 0 && `(${respHeaderCount})`}
                    </TabsTrigger>
                  </TabsList>
                </Tabs>
                <div className="flex shrink-0 items-center gap-2 pr-1">
                  {response.error && !response.statusCode ? (
                    <Badge variant="destructive" className="text-[10px]">
                      Error
                    </Badge>
                  ) : (
                    <Badge className={`text-[10px] ${getStatusColor(response.statusCode)}`}>
                      {response.statusCode} {response.statusMessage}
                    </Badge>
                  )}
                  {elapsed != null && (
                    <span className="text-[10px] text-muted-foreground">{elapsed}ms</span>
                  )}
                  {response.contentLength > 0 && (
                    <span className="text-[10px] text-muted-foreground">
                      {response.contentLength}B
                    </span>
                  )}
                  <CopyButton
                    value={responseTab === "body" ? response.body || "" : responseCopyHeaders}
                  />
                </div>
              </div>

              {response.error && (
                <div className="mx-2 mt-2 shrink-0 rounded bg-red-950/50 px-3 py-2 text-xs text-red-300">
                  {response.error}
                </div>
              )}

              <div className="relative min-h-0 flex-1">
                <div className="absolute inset-0 overflow-auto">
                  {responseTab === "body" && (
                    <pre className="p-3 font-mono text-xs leading-relaxed break-all whitespace-pre-wrap text-foreground/90">
                      {response.body || "(empty)"}
                    </pre>
                  )}

                  {responseTab === "headers" && (
                    <div className="space-y-0.5 p-3 font-mono text-xs">
                      {respHeaderCount > 0 ? (
                        Object.entries(response.responseHeaders).map(([k, v]) => (
                          <div key={k} className="leading-relaxed">
                            <span className="text-blue-400">{k}</span>
                            <span className="text-muted-foreground">: </span>
                            <span className="text-foreground/90">{v}</span>
                          </div>
                        ))
                      ) : (
                        <span className="text-muted-foreground">(no headers)</span>
                      )}
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
