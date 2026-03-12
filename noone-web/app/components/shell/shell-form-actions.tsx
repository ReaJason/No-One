import { Edit, Plus, Wifi } from "lucide-react";
import { Button } from "@/components/ui/button";

type ShellFormActionsProps = {
  isEdit: boolean;
  isSubmitting: boolean;
  isTesting: boolean;
  onCancel: () => void;
  onTestConnection: () => void;
};

export default function ShellFormActions({
  isEdit,
  isSubmitting,
  isTesting,
  onCancel,
  onTestConnection,
}: ShellFormActionsProps) {
  const submitLabel = isEdit ? "Update Shell" : "Create Shell";
  const submittingLabel = isEdit ? "Updating..." : "Creating...";
  const SubmitIcon = isEdit ? Edit : Plus;

  return (
    <div className="flex flex-col gap-3 border-t border-border/70 pt-2 sm:flex-row sm:items-center">
      <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>
        Cancel
      </Button>
      <Button
        type="button"
        variant="secondary"
        onClick={onTestConnection}
        disabled={isSubmitting || isTesting}
        className="gap-2"
      >
        <Wifi className={`h-4 w-4 ${isTesting ? "animate-pulse" : ""}`} />
        {isTesting ? "Testing..." : "Test Connection"}
      </Button>
      <Button type="submit" disabled={isSubmitting} className="gap-2">
        <SubmitIcon className="h-4 w-4" />
        {isSubmitting ? submittingLabel : submitLabel}
      </Button>
    </div>
  );
}
