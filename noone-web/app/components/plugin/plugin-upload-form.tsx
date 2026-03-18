import type { DragEvent } from "react";

import { FileUp, Plus, TextCursorInput } from "lucide-react";
import { useCallback, useRef, useState } from "react";
import { Form } from "react-router";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { parsePluginJson, type PluginFormSeed } from "@/routes/plugins/plugin-form.shared";

type PluginUploadFormProps = {
  action?: string;
  errors?: Record<string, string>;
  initialValues: PluginFormSeed;
  onCancel: () => void;
};

function PluginPreview({ data }: { data: Record<string, unknown> }) {
  const actions = data.actions as Record<string, unknown> | undefined;
  const actionCount = actions ? Object.keys(actions).length : 0;

  return (
    <Card>
      <CardHeader>
        <CardTitle>Plugin Preview</CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          <dt className="font-medium text-muted-foreground">ID</dt>
          <dd>{String(data.id)}</dd>

          <dt className="font-medium text-muted-foreground">Name</dt>
          <dd>{String(data.name)}</dd>

          <dt className="font-medium text-muted-foreground">Version</dt>
          <dd>{String(data.version)}</dd>

          <dt className="font-medium text-muted-foreground">Language</dt>
          <dd>
            <Badge variant="secondary">{String(data.language)}</Badge>
          </dd>

          <dt className="font-medium text-muted-foreground">Type</dt>
          <dd>
            <Badge variant="outline">{String(data.type)}</Badge>
          </dd>

          {data.author ? (
            <>
              <dt className="font-medium text-muted-foreground">Author</dt>
              <dd>{String(data.author)}</dd>
            </>
          ) : null}

          {data.description ? (
            <>
              <dt className="font-medium text-muted-foreground">Description</dt>
              <dd>{String(data.description)}</dd>
            </>
          ) : null}

          <dt className="font-medium text-muted-foreground">Actions</dt>
          <dd>{actionCount}</dd>
        </dl>
      </CardContent>
    </Card>
  );
}

export function PluginUploadForm({
  action,
  errors: serverErrors,
  initialValues,
  onCancel,
}: PluginUploadFormProps) {
  const [jsonText, setJsonText] = useState(initialValues.pluginJson);
  const [preview, setPreview] = useState<Record<string, unknown> | null>(() => {
    if (initialValues.pluginJson.trim()) {
      const result = parsePluginJson(initialValues.pluginJson);
      if ("payload" in result) return result.payload;
    }
    return null;
  });
  const [clientError, setClientError] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const processJson = useCallback((text: string) => {
    setJsonText(text);
    if (!text.trim()) {
      setPreview(null);
      setClientError(null);
      return;
    }
    const result = parsePluginJson(text);
    if ("payload" in result) {
      setPreview(result.payload);
      setClientError(null);
    } else {
      setPreview(null);
      setClientError(result.errors.pluginJson);
    }
  }, []);

  const handleFile = useCallback(
    (file: File) => {
      const reader = new FileReader();
      reader.onload = (e) => {
        const text = e.target?.result;
        if (typeof text === "string") {
          processJson(text);
        }
      };
      reader.readAsText(file);
    },
    [processJson],
  );

  const handleDrop = useCallback(
    (e: DragEvent<HTMLDivElement>) => {
      e.preventDefault();
      setIsDragging(false);
      const file = e.dataTransfer.files[0];
      if (file) handleFile(file);
    },
    [handleFile],
  );

  const handleDragOver = useCallback((e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(true);
  }, []);

  const handleDragLeave = useCallback((e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
  }, []);

  const displayError = clientError ?? serverErrors?.pluginJson;

  return (
    <Form method="post" action={action} className="space-y-6">
      {serverErrors?.general ? (
        <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
          {serverErrors.general}
        </div>
      ) : null}

      <Tabs defaultValue="upload">
        <TabsList>
          <TabsTrigger value="upload" className="flex items-center gap-2">
            <FileUp className="h-4 w-4" />
            Upload File
          </TabsTrigger>
          <TabsTrigger value="paste" className="flex items-center gap-2">
            <TextCursorInput className="h-4 w-4" />
            Paste JSON
          </TabsTrigger>
        </TabsList>

        <TabsContent value="upload" className="mt-4">
          <div
            role="button"
            tabIndex={0}
            onClick={() => fileInputRef.current?.click()}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") fileInputRef.current?.click();
            }}
            onDrop={handleDrop}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            className={`flex min-h-[160px] cursor-pointer flex-col items-center justify-center gap-3 rounded-lg border-2 border-dashed p-8 text-center transition-colors ${
              isDragging
                ? "border-primary bg-primary/5"
                : "border-muted-foreground/25 hover:border-primary/50"
            }`}
          >
            <FileUp className="h-10 w-10 text-muted-foreground" />
            <div>
              <p className="font-medium">Drop a .json file here or click to browse</p>
              <p className="text-sm text-muted-foreground">Only .json files are accepted</p>
            </div>
          </div>
          <input
            ref={fileInputRef}
            type="file"
            accept=".json"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) handleFile(file);
            }}
          />
        </TabsContent>

        <TabsContent value="paste" className="mt-4">
          <Textarea
            placeholder={`{
  "id": "PortScan",
  "name": "PortScan",
  "version": "1.0.0",
  "language": "java",
  "type": "reconnaissance",
  ...
}`}
            rows={14}
            value={jsonText}
            onChange={(e) => processJson(e.target.value)}
            className={`font-mono text-sm ${displayError ? "border-destructive" : ""}`}
          />
        </TabsContent>
      </Tabs>

      {displayError ? <p className="text-sm text-destructive">{displayError}</p> : null}

      {preview ? <PluginPreview data={preview} /> : null}

      <input type="hidden" name="pluginJson" value={jsonText} />

      <div className="flex gap-4 pt-2">
        <Button type="submit" className="flex items-center gap-2" disabled={!preview}>
          <Plus className="h-4 w-4" />
          Add Plugin
        </Button>
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </Form>
  );
}
