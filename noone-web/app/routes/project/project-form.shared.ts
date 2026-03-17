import type { ProjectMutationRequest } from "@/api/project-api";
import type { Project, ProjectStatus } from "@/types/project";

import { z } from "zod";

const PROJECT_STATUSES = ["DRAFT", "ACTIVE", "ARCHIVED"] as const;
const TRIMMED_FIELDS = [
  "name",
  "code",
  "bizName",
  "description",
  "startedAt",
  "endedAt",
  "remark",
] as const;

export const projectFormSchema = z
  .object({
    name: z.string().trim().min(1, "Project name is required"),
    code: z.string().trim().min(1, "Project code is required"),
    status: z.enum(PROJECT_STATUSES),
    bizName: z.string(),
    description: z.string(),
    startedAt: z.string(),
    endedAt: z.string(),
    remark: z.string(),
    memberIds: z.array(z.number().finite()),
  })
  .refine((values) => !values.startedAt || !values.endedAt || values.endedAt >= values.startedAt, {
    message: "End date must be on or after the start date",
    path: ["endedAt"],
  });

export type ProjectFormValues = z.infer<typeof projectFormSchema>;

export type ProjectActionData = {
  errors?: Record<string, string>;
  success?: boolean;
  values?: ProjectFormValues;
};

export const PROJECT_STATUS_ITEMS: Array<{ label: string; value: ProjectStatus }> = [
  { label: "Draft", value: "DRAFT" },
  { label: "Active", value: "ACTIVE" },
  { label: "Archived", value: "ARCHIVED" },
];

export function getDefaultValues(project?: Project): ProjectFormValues {
  return {
    name: project?.name ?? "",
    code: project?.code ?? "",
    status: project?.status ?? "DRAFT",
    bizName: project?.bizName ?? "",
    description: project?.description ?? "",
    startedAt: toDateTimeLocalValue(project?.startedAt),
    endedAt: toDateTimeLocalValue(project?.endedAt),
    remark: project?.remark ?? "",
    memberIds: project?.members?.map((member) => member.id) ?? [],
  };
}

export function buildPayload(values: ProjectFormValues): ProjectMutationRequest {
  return {
    name: values.name,
    code: values.code,
    status: values.status,
    bizName: values.bizName,
    description: values.description,
    memberIds: values.memberIds,
    startedAt: values.startedAt || null,
    endedAt: values.endedAt || null,
    remark: values.remark,
  };
}

export function parseProjectFormData(
  formData: FormData,
  _options: { mode: "create" | "edit" },
):
  | {
      errors: Record<string, string>;
      values: ProjectFormValues;
    }
  | {
      payload: ProjectMutationRequest;
      values: ProjectFormValues;
    } {
  const values = normalizeProjectFormValues(formData);
  const parsed = projectFormSchema.safeParse(values);

  if (!parsed.success) {
    return {
      errors: toFieldErrors(parsed.error),
      values,
    };
  }

  return {
    payload: buildPayload(parsed.data),
    values: parsed.data,
  };
}

function normalizeProjectFormValues(formData: FormData): ProjectFormValues {
  const defaults = getDefaultValues();
  const values = {
    ...defaults,
    ...Object.fromEntries(
      Array.from(formData.entries(), ([key, value]) => [
        key,
        typeof value === "string" ? value : "",
      ]),
    ),
  } as Omit<ProjectFormValues, "memberIds"> & { memberIds?: unknown };

  for (const field of TRIMMED_FIELDS) {
    trimField(values, field);
  }

  return {
    ...values,
    status: normalizeProjectStatus(values.status),
    memberIds: formData
      .getAll("memberIds")
      .map((value) => Number(value))
      .filter((value) => Number.isFinite(value)),
  };
}

function normalizeProjectStatus(value: string): ProjectStatus {
  return value === "ACTIVE" || value === "ARCHIVED" || value === "DRAFT" ? value : "DRAFT";
}

function trimField(
  values: Omit<ProjectFormValues, "memberIds"> & { memberIds?: unknown },
  field: (typeof TRIMMED_FIELDS)[number],
) {
  const value = values[field];
  if (typeof value === "string") {
    values[field] = value.trim();
  }
}

function toFieldErrors(error: z.ZodError<ProjectFormValues>) {
  const fieldErrors = error.flatten().fieldErrors;
  return Object.fromEntries(
    Object.entries(fieldErrors).flatMap(([key, messages]) =>
      messages?.[0] ? [[key, messages[0]]] : [],
    ),
  );
}

function toDateTimeLocalValue(value?: string | null) {
  if (!value) {
    return "";
  }
  return value.slice(0, 16);
}
