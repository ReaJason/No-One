import { ExternalLink, PlusIcon } from "lucide-react";
import { memo, useCallback, useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";

export type AddShellParams = {
  profileId?: string;
  loaderProfileId?: string;
  shellType?: string;
  staging?: boolean;
  language?: string;
  interfaceName?: string;
} | null;

interface AddShellButtonProps {
  params: AddShellParams;
  placeholder?: string;
  variant?: "default" | "outline" | "ghost";
  size?: "default" | "sm" | "lg" | "icon";
  className?: string;
}

const AddShellButton = memo(function AddShellButton({
  params,
  placeholder = "http://example.com/shell.jsp",
  variant = "outline",
  size = "default",
  className,
}: Readonly<AddShellButtonProps>) {
  const [targetUrl, setTargetUrl] = useState("");
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [urlError, setUrlError] = useState("");

  const handleUrlChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setTargetUrl(e.target.value);
      if (urlError) setUrlError("");
    },
    [urlError],
  );

  const handleAddShell = useCallback(() => {
    if (!targetUrl.trim()) {
      setUrlError("URL is required");
      return;
    }

    try {
      new URL(targetUrl);
    } catch {
      setUrlError("Please enter a valid URL");
      return;
    }

    const searchParams = new URLSearchParams({ shellUrl: targetUrl });

    if (params?.staging) {
      searchParams.set("staging", "true");
      if (params?.loaderProfileId) {
        searchParams.set("loaderProfileId", params.loaderProfileId);
      }
    } else if (params?.profileId) {
      searchParams.set("profileId", params.profileId);
    }

    if (params?.shellType) {
      searchParams.set("shellType", params.shellType);
    }
    if (params?.language) {
      searchParams.set("language", params.language);
    }

    if (params?.interfaceName) {
      searchParams.set("interfaceName", params.interfaceName);
    }

    window.open(`/shells/create?${searchParams.toString()}`);
  }, [targetUrl, params]);

  const handleDialogOpenChange = useCallback((open: boolean) => {
    setIsDialogOpen(open);
    if (!open) {
      setTargetUrl("");
      setUrlError("");
    }
  }, []);

  return (
    <Dialog open={isDialogOpen} onOpenChange={handleDialogOpenChange}>
      <DialogTrigger
        render={
          <Button variant={variant} size={size} className={className}>
            <PlusIcon className="h-4 w-4" />
            Add Shell
          </Button>
        }
      ></DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add Shell Connection</DialogTitle>
        </DialogHeader>
        <Field data-invalid={!!urlError}>
          <FieldLabel htmlFor="add-shell-url">Shell URL *</FieldLabel>
          <Input
            id="add-shell-url"
            type="url"
            placeholder={placeholder}
            value={targetUrl}
            onChange={handleUrlChange}
            aria-invalid={!!urlError}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                handleAddShell();
              }
            }}
          />
          {urlError && <FieldError>{urlError}</FieldError>}
        </Field>
        <DialogFooter>
          <DialogClose render={<Button variant="outline">Cancel</Button>}></DialogClose>
          <Button onClick={handleAddShell}>
            <ExternalLink className="mr-2 h-4 w-4" />
            Continue
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
});

export default AddShellButton;
