import { FileTextIcon } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { type MemShellResult } from "@/types/memshell";

import { CopyableField } from "../copyable-field";

export function BasicInfo({ generateResult }: Readonly<{ generateResult?: MemShellResult }>) {
  const isDubbo = generateResult?.shellConfig.server === "Dubbo";
  const helperClassName =
    generateResult?.helperClassName ?? generateResult?.injectorConfig.injectorHelperClassName;
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between">
          <div className="text-md flex items-center gap-2">
            <FileTextIcon className="h-5" />
            <span>{"basicInfo"}</span>
          </div>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-1 gap-2 md:grid-cols-2"></div>
        <Separator className="my-1" />
        <div className="grid grid-cols-1">
          <CopyableField
            label={"injectorClassName"}
            value={generateResult?.injectorClassName}
            text={`${generateResult?.injectorClassName} (${generateResult?.injectorSize} bytes)`}
          />
          <CopyableField
            label={"shellClassName"}
            value={generateResult?.shellClassName}
            text={`${generateResult?.shellClassName} (${generateResult?.shellSize} bytes)`}
          />
          {isDubbo && (
            <>
              <CopyableField
                label={"serviceName"}
                value={generateResult?.injectorConfig.urlPattern}
                text={`${generateResult?.injectorConfig.urlPattern}`}
              />
              <CopyableField
                label={"interfaceName"}
                value={helperClassName}
                text={`${helperClassName}`}
              />
            </>
          )}
          {!isDubbo && helperClassName && (
            <CopyableField
              label={"helperClassName"}
              value={helperClassName}
              text={`${helperClassName} (${generateResult?.helperSize ?? 0} bytes)`}
            />
          )}
          {generateResult?.shellConfig.probe?.probeMethod === "ResponseHeader" &&
            generateResult.shellConfig.probe.respHeaderName && (
              <CopyableField
                label={"responseHeaderName"}
                value={generateResult.shellConfig.probe.respHeaderName}
                text={generateResult.shellConfig.probe.respHeaderName}
              />
            )}
        </div>
      </CardContent>
    </Card>
  );
}
