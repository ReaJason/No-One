import type { Plugin } from "@/types/plugin";

import { Edit, Loader, MoreHorizontal, Trash2 } from "lucide-react";
import React, { useEffect, useRef, useState } from "react";
import { useFetcher, useNavigate } from "react-router";
import { toast } from "sonner";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

type DeleteFetcherData = {
  success?: boolean;
  errors?: Record<string, string>;
};

export const PluginActionsCell = React.memo(function PluginActionsCell({
  plugin,
}: {
  plugin: Plugin;
}) {
  const navigate = useNavigate();
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const deleteFetcher = useFetcher<DeleteFetcherData>();
  const isDeleting = deleteFetcher.state !== "idle";
  const wasDeletingRef = useRef(isDeleting);
  const deleteError = deleteFetcher.data?.errors?.general;

  useEffect(() => {
    if (!wasDeletingRef.current || isDeleting) {
      wasDeletingRef.current = isDeleting;
      return;
    }

    if (deleteFetcher.data?.success) {
      setIsDeleteOpen(false);
      toast.success("Plugin deleted");
    } else if (deleteFetcher.data?.errors?.general) {
      toast.error(deleteFetcher.data.errors.general);
    }

    wasDeletingRef.current = isDeleting;
  }, [deleteFetcher.data, isDeleting]);

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger
          render={
            <Button variant="ghost" className="h-8 w-8 p-0">
              <span className="sr-only">Open menu</span>
              <MoreHorizontal className="h-4 w-4" />
            </Button>
          }
        ></DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuGroup>
            <DropdownMenuLabel>Actions</DropdownMenuLabel>
            <DropdownMenuItem onClick={() => navigate(`/plugins/edit/${plugin.id}`)}>
              <Edit className="mr-2 h-4 w-4" />
              Edit
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={() => setIsDeleteOpen(true)}
              className="text-destructive"
              disabled={isDeleting}
            >
              {isDeleting ? (
                <Loader className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Trash2 className="mr-2 h-4 w-4" />
              )}
              Delete
            </DropdownMenuItem>
          </DropdownMenuGroup>
        </DropdownMenuContent>
      </DropdownMenu>

      <AlertDialog open={isDeleteOpen} onOpenChange={setIsDeleteOpen}>
        <AlertDialogContent>
          <deleteFetcher.Form method="post" action="/plugins">
            <input type="hidden" name="intent" value="delete" />
            <input type="hidden" name="pluginDbId" value={String(plugin.id)} />
            <AlertDialogHeader>
              <AlertDialogTitle>Are you sure you want to delete this plugin?</AlertDialogTitle>
              <AlertDialogDescription>
                This action cannot be undone. The plugin and its metadata will be permanently
                removed.
              </AlertDialogDescription>
            </AlertDialogHeader>
            {deleteError ? <p className="text-sm text-destructive">{deleteError}</p> : null}
            <AlertDialogFooter>
              <AlertDialogCancel disabled={isDeleting}>Cancel</AlertDialogCancel>
              <AlertDialogAction>
                <Button type="submit" className="text-sm" disabled={isDeleting}>
                  {isDeleting ? <Loader className="mr-2 h-4 w-4 animate-spin" /> : null}
                  Confirm
                </Button>
              </AlertDialogAction>
            </AlertDialogFooter>
          </deleteFetcher.Form>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
});
