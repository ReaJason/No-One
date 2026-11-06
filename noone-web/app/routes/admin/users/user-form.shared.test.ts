import { parseUserFormData } from "@/routes/admin/users/user-form.shared";

function createUserFormData(status = "UNACTIVATED") {
  const formData = new FormData();
  formData.set("username", "browser-regression");
  formData.set("password", "Temporary!2026");
  formData.set("email", "browser-regression@example.test");
  formData.set("status", status);
  formData.set("roleIds", "7");
  return formData;
}

describe("parseUserFormData", () => {
  it("omits the read-only status from user creation payloads", () => {
    const result = parseUserFormData(createUserFormData(), { mode: "create" });

    expect(result.errors).toBeUndefined();
    expect(result.createPayload).toEqual({
      username: "browser-regression",
      password: "Temporary!2026",
      email: "browser-regression@example.test",
      roleIds: [7],
    });
    expect(result.createPayload).not.toHaveProperty("status");
  });

  it("keeps status editable when updating a user", () => {
    const result = parseUserFormData(createUserFormData("ENABLED"), { mode: "edit" });

    expect(result.errors).toBeUndefined();
    expect(result.updatePayload).toEqual({
      email: "browser-regression@example.test",
      status: "ENABLED",
    });
  });
});
