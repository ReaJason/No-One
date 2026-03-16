import type { AuditLog } from "@/types/audit";

import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { formatDate } from "@/lib/format";

interface AuditDetailDialogProps {
  auditLog: AuditLog;
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

export function AuditDetailDialog({ auditLog, open, onOpenChange }: AuditDetailDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {auditLog.module} {auditLog.action}
          </DialogTitle>
          <DialogDescription>{auditLog.description}</DialogDescription>
        </DialogHeader>

        <div className="divide-y">
          <DetailRow label="User">{auditLog.username}</DetailRow>
          <DetailRow label="Target Type">{auditLog.targetType || "--"}</DetailRow>
          <DetailRow label="Target ID">{auditLog.targetId || "--"}</DetailRow>
          <DetailRow label="Status">
            <Badge
              variant="secondary"
              className={
                auditLog.success
                  ? "bg-green-100 text-green-800 hover:bg-green-100"
                  : "bg-red-100 text-red-800 hover:bg-red-100"
              }
            >
              {auditLog.success ? "Success" : "Failed"}
            </Badge>
          </DetailRow>
          {auditLog.errorMessage && <DetailRow label="Error">{auditLog.errorMessage}</DetailRow>}
          <DetailRow label="IP Address">{auditLog.ipAddress || "--"}</DetailRow>
          <DetailRow label="User Agent">{auditLog.userAgent || "--"}</DetailRow>
          <DetailRow label="Request">
            {auditLog.requestMethod} {auditLog.requestUri}
          </DetailRow>
          <DetailRow label="Duration">
            {auditLog.durationMs != null ? `${auditLog.durationMs}ms` : "--"}
          </DetailRow>
          <DetailRow label="Created At">{formatDate(auditLog.createdAt)}</DetailRow>
        </div>

        {auditLog.details && Object.keys(auditLog.details).length > 0 && (
          <div>
            <p className="mb-2 text-sm font-medium text-muted-foreground">Details</p>
            <pre className="max-h-60 overflow-auto rounded-md bg-muted p-3 text-xs">
              {JSON.stringify(auditLog.details, null, 2)}
            </pre>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
