import { FileTextIcon } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { type MemShellResult } from "@/types/memshell";
import { CopyableField } from "../copyable-field";

export function BasicInfo({
  generateResult,
}: Readonly<{ generateResult?: MemShellResult }>) {
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
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2"></div>
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
        </div>
      </CardContent>
    </Card>
  );
}
