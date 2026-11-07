import { type HTMLProps, type ReactNode } from "react";
import { PrismLight as SyntaxHighlighter } from "react-syntax-highlighter";
import java from "react-syntax-highlighter/dist/esm/languages/prism/java";
import materialDark from "react-syntax-highlighter/dist/esm/styles/prism/material-dark";

import { CopyButton } from "@/components/memshell/copy-button";
import { cn } from "@/lib/utils";

SyntaxHighlighter.registerLanguage("java", java);

export default function CodeViewer({
  code,
  header,
  button,
  language,
  height,
  showLineNumbers = true,
  wrapLongLines = true,
}: Readonly<CodeViewerProps>) {
  const lineProps: lineTagPropsFunction | HTMLProps<HTMLElement> | undefined = wrapLongLines
    ? { style: { overflowWrap: "break-word", whiteSpace: "pre-wrap" } }
    : undefined;
  return (
    <div className="rounded-lg border">
      <div
        className={cn("flex items-center justify-end border-b p-2", header && "justify-between")}
      >
        {header}
        <div className="flex items-center gap-2">
          {button}
          <CopyButton value={code} variant="ghost" size="sm" />
        </div>
      </div>
      <div className="wrap-all relative overflow-hidden text-xs">
        <SyntaxHighlighter
          language={language}
          style={materialDark}
          showLineNumbers={showLineNumbers}
          wrapLongLines={wrapLongLines}
          lineProps={lineProps}
          customStyle={{
            margin: 0,
            padding: showLineNumbers ? 0 : "1em 1em",
            borderRadius: "0 0 var(--radius) var(--radius)",
            height: height,
            whiteSpace: wrapLongLines ? "pre-wrap" : "pre",
            overflowWrap: wrapLongLines ? "normal" : "break-word",
          }}
        >
          {code}
        </SyntaxHighlighter>
      </div>
    </div>
  );
}

export interface CodeViewerProps {
  code: string;
  language: string;
  header?: ReactNode;
  button?: ReactNode;
  height?: string | number;
  showLineNumbers?: boolean;
  wrapLongLines?: boolean;
  lineProps?: (lineNumber: number) => React.HTMLProps<HTMLElement>;
}
