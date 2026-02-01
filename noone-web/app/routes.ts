import {
  index,
  layout,
  prefix,
  type RouteConfig,
  route,
} from "@react-router/dev/routes";

export default [
  route("/shells/:shellId", "routes/shell/shell-manager.tsx"),
  route("/auth/login", "routes/auth/login.tsx"),
  route("/auth/logout", "routes/auth/logout.tsx"),
  layout("routes/layout.tsx", [
    index("routes/home.tsx"),
    route("/shells", "routes/shells.tsx"),
    route("/shells/create", "routes/shell/create-shell.tsx"),
    route("/shells/edit/:shellId", "routes/shell/edit.$shellId.tsx"),
    route("/shells/update/:shellId", "routes/shell/update.$shellId.tsx"),
    route("/generator", "routes/generator.tsx"),
    ...prefix("profiles", [
      index("routes/profile/profile-list.tsx"),
      route("/create", "routes/profile/create-profile.tsx"),
      route("/edit/:profileId", "routes/profile/edit.$profileId.tsx"),
    ]),
    route("/plugins", "routes/plugins.tsx"),
    route("/settings", "routes/settings.tsx"),
    route("/audit", "routes/audit.tsx"),
    ...prefix("admin", [
      ...prefix("users", [
        index("routes/admin/users.tsx"),
        route("/create", "routes/admin/users/create.tsx"),
        route(
          "/edit-roles/:userId",
          "routes/admin/users/edit-roles.$userId.tsx",
        ),
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
        route(
          "/edit/:permissionId",
          "routes/admin/permissions/edit.$permissionId.tsx",
        ),
        route(
          "/update/:permissionId",
          "routes/admin/permissions/update.$permissionId.tsx",
        ),
        route(
          "/delete/:permissionId",
          "routes/admin/permissions/delete.$permissionId.tsx",
        ),
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
