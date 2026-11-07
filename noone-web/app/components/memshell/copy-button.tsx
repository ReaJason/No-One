import type { VariantProps } from "class-variance-authority";

import { Check, Copy } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import CopyToClipboard from "react-copy-to-clipboard";
import { toast } from "sonner";

import { Button, type buttonVariants } from "@/components/ui/button";

interface CopyButtonProps extends React.ComponentProps<"button"> {
  value: string;
  src?: string;
}

export function CopyButton({
  value,
}: Readonly<CopyButtonProps & VariantProps<typeof buttonVariants>>) {
  const [hasCopied, setHasCopied] = useState(false);

  useEffect(() => {
    if (hasCopied) {
      const timer = setTimeout(() => {
        setHasCopied(false);
      }, 1000);
      return () => clearTimeout(timer);
    }
  }, [hasCopied]);

  const handleCopy = useCallback(() => {
    if (!hasCopied) {
      setHasCopied(true);
      toast.success("copySuccess", { duration: 1000 });
    }
  }, [hasCopied]);

  return (
    <CopyToClipboard.CopyToClipboard text={value} onCopy={handleCopy}>
      <Button
        variant="ghost"
        size="icon"
        type="button"
        className="h-7 w-7 [&_svg]:h-4 [&_svg]:w-4"
        disabled={hasCopied}
      >
        {hasCopied ? <Check /> : <Copy />}
      </Button>
    </CopyToClipboard.CopyToClipboard>
  );
}
