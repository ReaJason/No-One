import type {ReactNode} from "react";
import type {Params} from "react-router";
import {useMatches} from "react-router";

export interface BreadcrumbDefinition {
  id?: string;
  label: ReactNode;
  to?: string;
}

export interface BreadcrumbDescriptor extends BreadcrumbDefinition {
  id: string;
}

export interface BreadcrumbContext<Data = unknown> {
  data: Data;
  params: Params<string>;
  pathname: string;
}

export type CrumbFunction<Data = unknown> = (
  context: BreadcrumbContext<Data>,
) => BreadcrumbDefinition | BreadcrumbDefinition[] | null | undefined;

export interface BreadcrumbHandle<Data = unknown> {
  crumb: CrumbFunction<Data>;
}

export function createBreadcrumb<Data = unknown>(
  crumb: CrumbFunction<Data>,
): BreadcrumbHandle<Data> {
  return { crumb };
}

const pathToBreadcrumb: Record<string, ReactNode> = {
  "/": "Home",
  "/shells": "All Shell Connections",
  "/generator": "Generator",
  "/profiles": "Profiles",
  "/profiles/create": "Create Profile",
  "/profiles/edit": "Edit Profile",
  "/plugins": "Plugins",
  "/settings": "Settings",
  "/audit": "Audit",
  "/admin": "Admin",
  "/admin/users": "Users",
  "/admin/users/create": "Create User",
  "/admin/users/edit-roles": "Edit User Roles",
  "/admin/users/update": "Update User",
  "/admin/roles": "Roles",
  "/admin/roles/create": "Create Role",
  "/admin/roles/edit": "Edit Role",
  "/admin/roles/update": "Update Role",
  "/admin/permissions": "Permissions",
  "/admin/permissions/create": "Create Permission",
  "/admin/permissions/edit": "Edit Permission",
  "/admin/permissions/update": "Update Permission",
  "/projects": "Projects",
  "/projects/create": "Create Project",
  "/projects/edit": "Edit Project",
  "/projects/update": "Update Project",
};

function asArray(
  value: BreadcrumbDefinition | BreadcrumbDefinition[] | null | undefined,
) {
  if (!value) return [] as BreadcrumbDefinition[];
  return Array.isArray(value) ? value : [value];
}

export function useBreadcrumbs(): BreadcrumbDescriptor[] {
  const matches = useMatches();

  const breadcrumbs: BreadcrumbDescriptor[] = [];

  matches.forEach((match) => {
    if (typeof match.pathname !== "string" || match.pathname === "/") {
      return;
    }

    const crumbDefinitions = asArray(
      typeof match.handle?.crumb === "function"
        ? match.handle.crumb({
            data: match.data,
            params: match.params,
            pathname: match.pathname,
          })
        : null,
    );

    if (crumbDefinitions.length === 0) {
      const fallbackLabel = pathToBreadcrumb[match.pathname];
      if (fallbackLabel) {
        breadcrumbs.push({
          id: match.id ?? match.pathname,
          label: fallbackLabel,
          to: match.pathname,
        });
      }
      return;
    }

    crumbDefinitions.forEach((definition, index) => {
      const { label, to, id } = definition;
      if (!label) {
        return;
      }

      breadcrumbs.push({
        id: id ?? `${match.id ?? match.pathname}-${index}`,
        label,
        to: to ?? match.pathname,
      });
    });
  });

  return breadcrumbs;
}
