import {
  buildPayload,
  getDefaultValues,
  parseProjectFormData,
  type ProjectFormValues,
} from "@/routes/project/project-form.shared";

function createFormData(values: Partial<ProjectFormValues> = {}) {
  const merged = {
    ...getDefaultValues(),
    ...values,
  };
  const formData = new FormData();
  formData.set("name", merged.name);
  formData.set("code", merged.code);
  formData.set("status", merged.status);
  formData.set("bizName", merged.bizName);
  formData.set("description", merged.description);
  formData.set("startedAt", merged.startedAt);
  formData.set("endedAt", merged.endedAt);
  formData.set("remark", merged.remark);
  for (const memberId of merged.memberIds) {
    formData.append("memberIds", String(memberId));
  }
  return formData;
}

describe("project-form.shared", () => {
  it("returns field errors when required fields are missing", () => {
    const parsed = parseProjectFormData(
      createFormData({
        name: "",
        code: "",
      }),
      { mode: "create" },
    );

    expect("errors" in parsed).toBe(true);
    if ("errors" in parsed) {
      expect(parsed.errors).toMatchObject({
        code: "Project code is required",
        name: "Project name is required",
      });
    }
  });

  it("returns an endedAt error when the end date is before the start date", () => {
    const parsed = parseProjectFormData(
      createFormData({
        name: "Alpha",
        code: "ALPHA",
        startedAt: "2026-03-20T09:00",
        endedAt: "2026-03-19T09:00",
      }),
      { mode: "edit" },
    );

    expect("errors" in parsed).toBe(true);
    if ("errors" in parsed) {
      expect(parsed.errors.endedAt).toBe("End date must be on or after the start date");
    }
  });

  it("converts empty date values to null in the payload", () => {
    const payload = buildPayload({
      ...getDefaultValues(),
      name: "Alpha",
      code: "ALPHA",
      startedAt: "",
      endedAt: "",
      memberIds: [1, 2],
    });

    expect(payload).toMatchObject({
      name: "Alpha",
      code: "ALPHA",
      memberIds: [1, 2],
      startedAt: null,
      endedAt: null,
    });
  });
});
