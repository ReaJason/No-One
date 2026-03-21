import type { ShellOperationLog } from "@/types/shell-operation-log";

import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { formatDate } from "@/lib/format";

interface ShellOperationDetailDialogProps {
  log: ShellOperationLog;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-2 py-1.5 text-sm">
      <span className="w-32 shrink-0 font-medium text-muted-foreground">{label}</span>
      <span className="min-w-0 break-all">{children}</span>
    </div>
  );
}

export function ShellOperationDetailDialog({
  log,
  open,
  onOpenChange,
}: ShellOperationDetailDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {log.operation} - {log.pluginId ?? "N/A"}
          </DialogTitle>
          <DialogDescription>Shell #{log.shellId} operation details</DialogDescription>
        </DialogHeader>

        <div className="divide-y">
          <DetailRow label="Shell ID">{log.shellId}</DetailRow>
          <DetailRow label="Username">{log.username}</DetailRow>
          <DetailRow label="Operation">
            <Badge variant="secondary">{log.operation}</Badge>
          </DetailRow>
          <DetailRow label="Plugin">{log.pluginId ?? "--"}</DetailRow>
          <DetailRow label="Action">{log.action ?? "--"}</DetailRow>
          <DetailRow label="Status">
            <Badge
              variant="secondary"
              className={
                log.success
                  ? "bg-green-100 text-green-800 hover:bg-green-100"
                  : "bg-red-100 text-red-800 hover:bg-red-100"
              }
            >
              {log.success ? "Success" : "Failed"}
            </Badge>
          </DetailRow>
          {log.errorMessage && <DetailRow label="Error">{log.errorMessage}</DetailRow>}
          <DetailRow label="Duration">{log.durationMs}ms</DetailRow>
          <DetailRow label="Created At">{formatDate(log.createdAt)}</DetailRow>
        </div>

        {log.args && Object.keys(log.args).length > 0 && (
          <div>
            <p className="mb-2 text-sm font-medium text-muted-foreground">Request Args</p>
            <pre className="max-h-60 max-w-full overflow-auto rounded-md bg-muted p-3 text-xs break-all whitespace-pre-wrap">
              {JSON.stringify(log.args, null, 2)}
            </pre>
          </div>
        )}

        {log.result && Object.keys(log.result).length > 0 && (
          <div>
            <p className="mb-2 text-sm font-medium text-muted-foreground">Response Result</p>
            <pre className="max-h-60 max-w-full overflow-auto rounded-md bg-muted p-3 text-xs break-all whitespace-pre-wrap">
              {JSON.stringify(log.result, null, 2)}
            </pre>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
