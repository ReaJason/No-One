import type * as React from "react";
import {X} from "lucide-react";
import type {Column} from "@tanstack/react-table";

import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue,} from "@/components/ui/select";

interface DataTableToolbarProps {
  table: any;
  searchKey?: string;
  searchPlaceholder?: string;
  filters?: {
    key: string;
    label: string;
    options: { label: string; value: string }[];
  }[];
  onRefresh?: () => void;
}

export function DataTableToolbar({
  table,
  searchKey,
  searchPlaceholder = "Search...",
  filters = [],
  onRefresh,
}: DataTableToolbarProps) {
  const isFiltered = table.getState().columnFilters.length > 0;

  return (
    <div className="flex items-center justify-between">
      <div className="flex flex-1 items-center space-x-2">
        {searchKey && (
          <Input
            placeholder={searchPlaceholder}
            value={(table.getColumn(searchKey)?.getFilterValue() as string) ?? ""}
            onChange={(event) =>
              table.getColumn(searchKey)?.setFilterValue(event.target.value)
            }
            className="h-8 w-[150px] lg:w-[250px]"
          />
        )}
        {filters.map((filter) => (
          <Select
            key={filter.key}
            value={(table.getColumn(filter.key)?.getFilterValue() as string) ?? ""}
            onValueChange={(value) =>
              table.getColumn(filter.key)?.setFilterValue(value === "all" ? "" : value)
            }
          >
            <SelectTrigger className="h-8 w-[150px]">
              <SelectValue placeholder={filter.label} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All {filter.label.toLowerCase()}</SelectItem>
              {filter.options.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ))}
        {isFiltered && (
          <Button
            variant="ghost"
            onClick={() => table.resetColumnFilters()}
            className="h-8 px-2 lg:px-3"
          >
            Reset
            <X className="ml-2 h-4 w-4" />
          </Button>
        )}
      </div>
      <div className="flex items-center space-x-2">
        {onRefresh && (
          <Button variant="outline" size="sm" onClick={onRefresh}>
            Refresh
          </Button>
        )}
      </div>
    </div>
  );
}

export function DataTableFacetedFilter({
  column,
  title,
  options,
}: {
  column?: Column<any, unknown>;
  title?: string;
  options: { label: string; value: string; icon?: React.ComponentType }[];
}) {
  const facets = column?.getFacetedUniqueValues();
  const selectedValues = new Set(column?.getFilterValue() as string[]);

  return (
    <Select
      value={Array.from(selectedValues).join(",")}
      onValueChange={(value) => {
        const values = value ? value.split(",") : [];
        column?.setFilterValue(values.length ? values : undefined);
      }}
    >
      <SelectTrigger className="h-8 w-[150px]">
        <SelectValue placeholder={title} />
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => {
          return (
            <SelectItem key={option.value} value={option.value}>
              <div className="flex items-center">
                {option.icon && <option.icon className="mr-2 h-4 w-4" />}
                <span>{option.label}</span>
                {facets?.get(option.value) && (
                  <span className="ml-auto flex h-4 w-4 items-center justify-center font-mono text-xs">
                    {facets.get(option.value)}
                  </span>
                )}
              </div>
            </SelectItem>
          );
        })}
      </SelectContent>
    </Select>
  );
}
