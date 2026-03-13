import type { ProjectMutationRequest } from "@/api/project-api";
import type { Project, ProjectStatus } from "@/types/project";

export type ProjectActionData = {
  errors?: Record<string, string>;
  success?: boolean;
  values?: ProjectFormSeed;
};

export type ProjectFormSeed = {
  name: string;
  code: string;
  status: ProjectStatus;
  bizName: string;
  description: string;
  startedAt: string;
  endedAt: string;
  remark: string;
  memberIds: number[];
};

export const PROJECT_STATUS_ITEMS: Array<{ label: string; value: ProjectStatus }> = [
  { label: "Draft", value: "DRAFT" },
  { label: "Active", value: "ACTIVE" },
  { label: "Archived", value: "ARCHIVED" },
];

export function getProjectFormSeed(project?: Project): ProjectFormSeed {
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

export function parseProjectFormData(formData: FormData): {
  errors?: Record<string, string>;
  payload: ProjectMutationRequest;
  values: ProjectFormSeed;
} {
  const values: ProjectFormSeed = {
    name: readTrimmedString(formData, "name"),
    code: readTrimmedString(formData, "code"),
    status: readProjectStatus(formData, "status"),
    bizName: readTrimmedString(formData, "bizName"),
    description: readTrimmedString(formData, "description"),
    startedAt: readDateTimeString(formData, "startedAt"),
    endedAt: readDateTimeString(formData, "endedAt"),
    remark: readTrimmedString(formData, "remark"),
    memberIds: formData
      .getAll("memberIds")
      .map((value) => Number(value))
      .filter((value) => Number.isFinite(value)),
  };

  const errors: Record<string, string> = {};
  if (!values.name) {
    errors.name = "Project name is required";
  }
  if (!values.code) {
    errors.code = "Project code is required";
  }
  if (values.startedAt && values.endedAt && values.endedAt < values.startedAt) {
    errors.endedAt = "End time must be after the start time";
  }

  return {
    errors: Object.keys(errors).length > 0 ? errors : undefined,
    payload: {
      name: values.name,
      code: values.code,
      status: values.status,
      bizName: values.bizName,
      description: values.description,
      memberIds: values.memberIds,
      startedAt: values.startedAt || null,
      endedAt: values.endedAt || null,
      remark: values.remark,
    },
    values,
  };
}

function readTrimmedString(formData: FormData, key: string) {
  return String(formData.get(key) ?? "").trim();
}

function readDateTimeString(formData: FormData, key: string) {
  return String(formData.get(key) ?? "").trim();
}

function readProjectStatus(formData: FormData, key: string): ProjectStatus {
  const rawValue = String(formData.get(key) ?? "DRAFT").toUpperCase();
  if (rawValue === "ACTIVE" || rawValue === "ARCHIVED" || rawValue === "DRAFT") {
    return rawValue;
  }
  return "DRAFT";
}

function toDateTimeLocalValue(value?: string | null) {
  if (!value) {
    return "";
  }
  return value.slice(0, 16);
}
