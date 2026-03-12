import { index, layout, prefix, route, type RouteConfig } from "@react-router/dev/routes";

export default [
  route("/shells/:shellId/connect", "routes/shell/shell-connect.tsx"),
  route("/shells/:shellId", "routes/shell/shell-manager.tsx", [
    index("routes/shell/shell-manager-index.tsx"),
    route("info", "routes/shell/shell-info.tsx"),
    route("files", "routes/shell/shell-files.tsx"),
    route("files/data", "routes/shell/shell-files-data.ts"),
    route("command", "routes/shell/shell-command.tsx"),
    route("extensions", "routes/shell/shell-extensions.tsx"),
    route("extensions/status", "routes/shell/shell-extension-task-status.ts"),
    route("operations", "routes/shell/shell-operations.tsx"),
  ]),
  route("/auth/login", "routes/auth/login.tsx"),
  route("/auth/logout", "routes/auth/logout.tsx"),
  route("/auth/setup", "routes/auth/setup/route.tsx"),
  route("/auth/password-change", "routes/auth/password-change.tsx"),
  route("/test", "routes/test.tsx"),
  layout("routes/layout.tsx", { id: "app-layout" }, [
    index("routes/home.tsx"),
    route("/shells", "routes/shell/shell-list.tsx"),
    route("/shells/create", "routes/shell/create-shell.tsx"),
    route("/shells/edit/:shellId", "routes/shell/create-shell.tsx", { id: "shells-edit" }),
    route("/generator", "routes/generator.tsx", [
      index("routes/generator/memshell.tsx"),
      route("webshell", "routes/generator/webshell.tsx"),
    ]),
    ...prefix("profiles", [
      index("routes/profile/profile-list.tsx"),
      route("/create", "routes/profile/create-profile.tsx"),
      route("/edit/:profileId", "routes/profile/edit.$profileId.tsx"),
    ]),
    ...prefix("plugins", [
      index("routes/plugins.tsx"),
      route("/create", "routes/plugins/create.tsx"),
    ]),
    route("/settings", "routes/settings.tsx"),
    route("/audit", "routes/audit.tsx"),
    ...prefix("admin", [
      ...prefix("users", [
        index("routes/admin/users.tsx"),
        route("/create", "routes/admin/users/create.tsx"),
        route("/edit-roles/:userId", "routes/admin/users/edit-roles.$userId.tsx"),
        route("/update/:userId", "routes/admin/users/update.$userId.tsx"),
        route("/delete/:userId", "routes/admin/users/delete.$userId.tsx"),
      ]),
      route("/roles", "routes/admin/roles.tsx"),
      route("/roles/create", "routes/admin/roles/create.tsx"),
      route("/roles/edit/:roleId", "routes/admin/roles/edit.$roleId.tsx"),
      route("/roles/update/:roleId", "routes/admin/roles/update.$roleId.tsx"),
      ...prefix("permissions", [
        index("routes/admin/permissions.tsx"),
        route("/create", "routes/admin/permissions/create.tsx"),
        route("/edit/:permissionId", "routes/admin/permissions/edit.$permissionId.tsx"),
        route("/update/:permissionId", "routes/admin/permissions/update.$permissionId.tsx"),
        route("/delete/:permissionId", "routes/admin/permissions/delete.$permissionId.tsx"),
      ]),
    ]),
    ...prefix("projects", [
      index("routes/project/project-list.tsx"),
      route("/create", "routes/project/create-project.tsx"),
      route("/edit/:projectId", "routes/project/edit.$projectId.tsx"),
      route("/update/:projectId", "routes/project/update.$projectId.tsx"),
    ]),
  ]),
] satisfies RouteConfig;
