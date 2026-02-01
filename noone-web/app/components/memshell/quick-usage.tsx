import { ScrollTextIcon } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export function QuickUsage() {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <ScrollTextIcon className="h-5" />
          <span>Quick Usage</span>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <ol className="flex flex-col gap-4 list-decimal list-inside text-sm">
          <li>Select Target Server</li>
          <li>Select Shell Mount Type, Filter, Listener, etc.</li>
          <li>Select NoOne Profile</li>
          <li>"Select Packing Method"</li>
          <li>Click Generate Shell</li>
        </ol>
      </CardContent>
    </Card>
  );
}
