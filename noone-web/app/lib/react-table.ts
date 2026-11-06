import type { RowData, TableState as TanstackTableState } from "@tanstack/react-table";
import type {
  LegacyColumn,
  LegacyColumnDef,
  LegacyFeatures,
  LegacyReactTable,
  LegacyRow,
  LegacyTableOptions,
} from "@tanstack/react-table/legacy";

export {
  type ColumnFiltersState,
  type ColumnSort,
  flexRender,
  type PaginationState,
  type RowData,
  type RowSelectionState,
  type SortingState,
  type Updater,
  type ColumnVisibilityState as VisibilityState,
} from "@tanstack/react-table";
export {
  getCoreRowModel,
  getFacetedMinMaxValues,
  getFacetedRowModel,
  getFacetedUniqueValues,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useLegacyTable as useReactTable,
} from "@tanstack/react-table/legacy";

export type Column<TData, TValue = unknown> = LegacyColumn<TData & RowData, TValue>;
export type ColumnDef<TData, TValue = unknown> = LegacyColumnDef<TData & RowData, TValue>;
export type Row<TData> = LegacyRow<TData & RowData>;
export type Table<TData> = LegacyReactTable<TData & RowData>;
export type TableOptions<TData> = LegacyTableOptions<TData & RowData>;
export type TableState = TanstackTableState<LegacyFeatures>;
