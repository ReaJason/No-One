import React, { useMemo, useState } from "react";
import { FileCode, Search } from "lucide-react";
import { Card, CardAction, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableRow } from "@/components/ui/table";

export default function KeyValueList({
  data,
  title,
}: {
  data: Record<string, string>;
  title: string;
}) {
  const [filter, setFilter] = useState("");

  const filteredEntries = useMemo(() => {
    if (!data) return [];
    return Object.entries(data).filter(
      ([k, v]) =>
        k.toLowerCase().includes(filter.toLowerCase()) ||
        String(v).toLowerCase().includes(filter.toLowerCase()),
    );
  }, [data, filter]);

  return (
    <Card className="flex h-full flex-col">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm font-semibold">
          <FileCode className="h-4 w-4 " />
          {title}
        </CardTitle>
        <CardAction className={"relative"}>
          <Search className="absolute top-1.5 left-2 h-3 w-3 text-zinc-400" />
          <input
            type="text"
            placeholder="Filter..."
            className="w-28 rounded-md border border-zinc-200 bg-transparent py-1 pr-2 pl-6 text-xs transition-[width] focus:w-44 sm:w-36 sm:focus:w-56 dark:border-zinc-700"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </CardAction>
      </CardHeader>
      <div className="max-h-[320px] flex-1 overflow-auto">
        <Table className="text-left text-xs">
          <TableBody className="divide-y divide-zinc-100 dark:divide-zinc-800">
            {filteredEntries.map(([k, v]) => (
              <TableRow
                key={k}
                className="border-b-0 hover:bg-zinc-50/50 dark:hover:bg-zinc-900/40"
              >
                <TableCell className="w-56 border-r border-dashed px-4 py-1.5 align-top font-medium break-all whitespace-normal text-zinc-700 dark:text-zinc-300">
                  {k}
                </TableCell>
                <TableCell className="px-4 py-1.5 font-mono break-all whitespace-normal text-zinc-600 dark:text-zinc-400">
                  {String(v)}
                </TableCell>
              </TableRow>
            ))}
            {filteredEntries.length === 0 && (
              <TableRow className="border-b-0">
                <TableCell colSpan={2} className="py-4 text-center text-zinc-400">
                  No matching keys found
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </Card>
  );
}
