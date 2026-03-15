import type { PackerConfig } from "@/types/memshell";

import { PackageIcon } from "lucide-react";
import { memo, useEffect, useMemo, useRef, useState } from "react";

import { PackerCombobox } from "@/components/packer/packer-combobox";
import { PackerCustomConfigFields } from "@/components/packer/packer-custom-config-fields";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldLabel } from "@/components/ui/field";
import { Spinner } from "@/components/ui/spinner";
import {
  findPackerEntry,
  getPackerDefaultConfig,
  getPackerSchemaFields,
  normalizePackerCategories,
} from "@/lib/packer-schema";

interface PackageConfigCardProps {
  packerConfig: PackerConfig | undefined;
  errors?: Record<string, string>;
  shellType?: string;
  server?: string;
}

const LoadingSpinner = () => (
  <div className="flex h-50 items-center justify-center gap-4 p-4">
    <Spinner />
    <span className="text-sm text-muted-foreground">Loading...</span>
  </div>
);

const PackageConfigCard = memo(function PackageConfigCard({
  packerConfig,
  shellType,
  server,
}: Readonly<PackageConfigCardProps>) {
  const [packingMethod, setPackingMethod] = useState("");
  const [packerCustomConfig, setPackerCustomConfig] = useState<Record<string, any>>({});

  const categories = useMemo(() => normalizePackerCategories(packerConfig), [packerConfig]);

  const filteredCategories = useMemo(() => {
    return categories
      .map((group) => ({
        ...group,
        packers: group.packers.filter((packer) => {
          if (packer.categoryAnchor) {
            return false;
          }
          const name = packer.name;
          if (!shellType || shellType === " ") {
            return true;
          }
          if (shellType.startsWith("Agent")) {
            return name.startsWith("Agent");
          }
          if ((server ?? "").startsWith("XXL")) {
            return !name.startsWith("Agent");
          }
          return !name.startsWith("Agent") && !name.toLowerCase().startsWith("xxl");
        }),
      }))
      .filter((group) => group.packers.length > 0);
  }, [categories, shellType, server]);

  const allOptionNames = useMemo(
    () => filteredCategories.flatMap((group) => group.packers.map((packer) => packer.name)),
    [filteredCategories],
  );

  const selectedPackerEntry = useMemo(
    () =>
      findPackerEntry(filteredCategories, packingMethod) ??
      findPackerEntry(categories, packingMethod),
    [categories, filteredCategories, packingMethod],
  );

  const selectedSchemaFields = useMemo(
    () => getPackerSchemaFields(selectedPackerEntry),
    [selectedPackerEntry],
  );

  // set default packing method
  useEffect(() => {
    if (allOptionNames.length > 0) {
      if (!packingMethod || !allOptionNames.includes(packingMethod)) {
        setPackingMethod(allOptionNames[0]);
      }
    }
  }, [allOptionNames, packingMethod]);

  // set default packer config only when packer name changes, protecting against Loader reference refreshes
  const lastPackerNameRef = useRef<string | undefined>(undefined);
  useEffect(() => {
    const name = selectedPackerEntry?.name;
    if (name && name !== lastPackerNameRef.current) {
      setPackerCustomConfig(getPackerDefaultConfig(selectedPackerEntry) as any);
      lastPackerNameRef.current = name;
    }
  }, [selectedPackerEntry]);

  const handleConfigChange = (key: string, value: any) => {
    setPackerCustomConfig((prev) => ({ ...prev, [key]: value }));
  };

  return (
    <Card className="w-full">
      <CardHeader className="pb-1">
        <CardTitle className="text-md flex items-center gap-2">
          <PackageIcon className="h-5" />
          <span>Packer Config</span>
        </CardTitle>
      </CardHeader>
      <CardContent>
        {allOptionNames.length > 0 ? (
          <>
            <Field className="gap-1">
              <FieldLabel>Packer Method</FieldLabel>
              <PackerCombobox
                categories={filteredCategories}
                value={packingMethod}
                onValueChange={setPackingMethod}
                placeholder="Select packer"
              />
            </Field>
            <PackerCustomConfigFields
              fields={selectedSchemaFields}
              values={packerCustomConfig}
              onValueChange={handleConfigChange}
            />
          </>
        ) : (
          <LoadingSpinner />
        )}
      </CardContent>
    </Card>
  );
});

export default PackageConfigCard;
