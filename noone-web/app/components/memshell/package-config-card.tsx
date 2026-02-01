import { PackageIcon } from "lucide-react";
import { memo, useEffect, useMemo, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { FieldLabel, FieldSet } from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Spinner } from "@/components/ui/spinner";
import type { PackerConfig } from "@/types/memshell";

interface PackageConfigCardProps {
  packerConfig: PackerConfig | undefined;
  errors?: Record<string, string>;
  shellType?: string;
  server?: string;
}

// Optimize: Extract static JSX outside component (rendering-hoist-jsx)
const LoadingSpinner = () => (
  <div className="flex items-center justify-center p-4 gap-4 h-50">
    <Spinner />
    <span className="text-sm text-muted-foreground">Loading...</span>
  </div>
);

// Optimize: Memoize component to prevent unnecessary re-renders (rerender-memo)
const PackageConfigCard = memo(function PackageConfigCard({
  packerConfig,
  shellType,
  server,
}: Readonly<PackageConfigCardProps>) {
  // Optimize: Compute options with efficient filtering (js-early-exit)
  const options = useMemo(() => {
    if (!packerConfig) {
      return [];
    }

    const filteredOptions = packerConfig.filter((name) => {
      // Early exit pattern for better performance
      if (!shellType || shellType === " ") {
        return true;
      }
      if (shellType.startsWith("Agent")) {
        return name.startsWith("Agent");
      }
      if (server?.startsWith("XXL")) {
        return !name.startsWith("Agent");
      }
      return !name.startsWith("Agent") && !name.toLowerCase().startsWith("xxl");
    });

    return filteredOptions.map((name) => ({
      name: name,
      value: name,
    }));
  }, [packerConfig, shellType, server]);

  const [packingMethod, setPackingMethod] = useState(options[0]?.value || "");

  // Optimize: Fix useEffect to only update when needed (rerender-dependencies)
  useEffect(() => {
    if (!options.length) {
      if (packingMethod) {
        setPackingMethod("");
      }
      return;
    }

    // Use Set for O(1) lookup performance (js-set-map-lookups)
    const optionValues = new Set(options.map((option) => option.value));
    if (!optionValues.has(packingMethod)) {
      setPackingMethod(options[0].value);
    }
  }, [options, packingMethod]);

  return (
    <Card className="w-full">
      <CardHeader className="pb-1">
        <CardTitle className="text-md flex items-center gap-2">
          <PackageIcon className="h-5" />
          <span>Packer Config</span>
        </CardTitle>
      </CardHeader>
      <CardContent>
        {options.length > 0 ? (
          <FieldSet>
            <FieldLabel>Packer Method</FieldLabel>
            <input type="hidden" name="packingMethod" value={packingMethod} />
            <RadioGroup
              name="packingMethod-display"
              value={packingMethod}
              defaultValue={options[0].value}
              onValueChange={setPackingMethod}
              className="grid grid-cols-2 md:grid-cols-3"
            >
              {options.map(({ name, value }) => (
                <div key={value} className="flex items-center space-x-3">
                  <RadioGroupItem value={value} id={value} />
                  <FieldLabel className="text-xs" htmlFor={value}>
                    {name}
                  </FieldLabel>
                </div>
              ))}
            </RadioGroup>
          </FieldSet>
        ) : (
          <LoadingSpinner />
        )}
      </CardContent>
    </Card>
  );
});

export default PackageConfigCard;
