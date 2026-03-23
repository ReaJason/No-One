import { index, layout, prefix, route, type RouteConfig } from "@react-router/dev/routes";

export default [
  route("/shells/:shellId/connect", "routes/shell/shell-connect.tsx"),
  route("/shells/:shellId", "routes/shell/shell-manager.tsx", [
    index("routes/shell/shell-manager-index.tsx"),
    route("info", "routes/shell/shell-info.tsx"),
    route("files", "routes/shell/shell-files.tsx"),
    route("files/data", "routes/shell/shell-files-data.ts"),
    route("command", "routes/shell/shell-command.tsx"),
    route("repeater", "routes/shell/shell-repeater.tsx"),
    route("processes", "routes/shell/shell-processes.tsx"),
    route("extensions", "routes/shell/shell-extensions.tsx"),
    route("extensions/plugin-status", "routes/shell/shell-extension-plugin-status.ts"),
    route("extensions/status", "routes/shell/shell-extension-task-status.ts"),
    route("status", "routes/shell/shell-status.tsx"),
    route("plugin-statuses", "routes/shell/shell-plugin-statuses.ts"),
    route("operations", "routes/shell/shell-operations.tsx"),
  ]),
  route("/auth/login", "routes/auth/login.tsx"),
  route("/auth/verify-2fa", "routes/auth/verify-2fa.tsx"),
  route("/auth/logout", "routes/auth/logout.tsx"),
  route("/auth/unauthorized", "routes/auth/unauthorized.tsx"),
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
      route("/create", "routes/profile/profile-editor.tsx", { id: "profiles-create" }),
      route("/edit/:profileId", "routes/profile/profile-editor.tsx", { id: "profiles-edit" }),
    ]),
    ...prefix("plugins", [
      index("routes/plugins/list.tsx"),
      route("/create", "routes/plugins/create.tsx"),
      route("/edit/:pluginId", "routes/plugins/edit.tsx", { id: "plugins-edit" }),
    ]),
    route("/settings", "routes/settings.tsx"),
    route("/audit", "routes/audit.tsx"),
    route("/shell-operations", "routes/shell-operations.tsx"),
    ...prefix("admin", [
      ...prefix("users", [
        index("routes/admin/users.tsx"),
        route("/create", "routes/admin/users/user-editor.tsx", { id: "users-create" }),
        route("/edit/:userId", "routes/admin/users/user-editor.tsx", { id: "users-edit" }),
        route("/:userId/login-logs", "routes/admin/users/user-login-logs.tsx", {
          id: "users-login-logs",
        }),
        route("/:userId/sessions", "routes/admin/users/user-sessions.tsx", {
          id: "users-sessions",
        }),
      ]),
      route("/roles", "routes/admin/roles.tsx"),
      route("/roles/create", "routes/admin/roles/role-editor.tsx", { id: "roles-create" }),
      route("/roles/edit/:roleId", "routes/admin/roles/role-editor.tsx", { id: "roles-edit" }),
      ...prefix("permissions", [
        index("routes/admin/permissions.tsx"),
        route("/create", "routes/admin/permissions/permission-editor.tsx", {
          id: "permissions-create",
        }),
        route("/edit/:permissionId", "routes/admin/permissions/permission-editor.tsx", {
          id: "permissions-edit",
        }),
      ]),
    ]),
    ...prefix("projects", [
      index("routes/project/project-list.tsx"),
      route("/create", "routes/project/project-editor.tsx", { id: "projects-create" }),
      route("/edit/:projectId", "routes/project/project-editor.tsx", { id: "projects-edit" }),
    ]),
  ]),
] satisfies RouteConfig;
