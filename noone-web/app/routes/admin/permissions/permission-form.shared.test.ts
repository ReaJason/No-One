import { parsePermissionFormData } from "@/routes/admin/permissions/permission-form.shared";

function permissionFormData(code: string) {
  const formData = new FormData();
  formData.set("name", "Regression Permission");
  formData.set("code", code);
  return formData;
}

describe("parsePermissionFormData", () => {
  it.each(["spa:regression", "Project:Read42"])("accepts server-valid code %s", (code) => {
    const result = parsePermissionFormData(permissionFormData(code));

    expect(result.errors).toBeUndefined();
    expect(result.payload).toEqual({ name: "Regression Permission", code });
  });

  it.each(["spa:regression:test", "spa_regression", "1spa:regression", "spa:regression-test"])(
    "rejects server-invalid code %s before submitting",
    (code) => {
      const result = parsePermissionFormData(permissionFormData(code));

      expect(result.errors?.code).toBe(
        "Permission code must use the module:action format with letters and numbers",
      );
      expect(result.payload).toEqual({ name: "Regression Permission", code });
    },
  );
});
