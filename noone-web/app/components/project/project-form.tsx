import type { User } from "@/types/admin";
import type { LucideIcon } from "lucide-react";

import { zodResolver } from "@hookform/resolvers/zod";
import { format, parseISO } from "date-fns";
import { CalendarIcon, Loader, Search, XCircle } from "lucide-react";
import { useDeferredValue, useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { useFetcher } from "react-router";

import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Field,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
  FieldLegend,
  FieldSet,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { InputGroup, InputGroupAddon, InputGroupButton } from "@/components/ui/input-group";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import {
  PROJECT_STATUS_ITEMS,
  type ProjectActionData,
  projectFormSchema,
  type ProjectFormValues,
} from "@/routes/project/project-form.shared";

type ProjectFormProps = {
  mode: "create" | "edit";
  icon: LucideIcon;
  submitLabel: string;
  users: User[];
  initialValues: ProjectFormValues;
  onCancel: () => void;
  action?: string;
};

const DATE_TIME_VALUE_FORMAT = "yyyy-MM-dd'T'HH:mm";

function parseDateTimeValue(value: string) {
  if (!value) {
    return undefined;
  }

  const parsedDate = parseISO(value);
  return Number.isNaN(parsedDate.getTime()) ? undefined : parsedDate;
}

function formatDateTimeValue(value: Date | undefined) {
  if (!value) {
    return "";
  }

  return format(value, DATE_TIME_VALUE_FORMAT);
}

function withPreservedTime(nextDate: Date, currentDate?: Date) {
  const mergedDate = new Date(nextDate);
  mergedDate.setHours(currentDate?.getHours() ?? 0, currentDate?.getMinutes() ?? 0, 0, 0);
  return mergedDate;
}

type ProjectDateFieldProps = {
  id: string;
  label: string;
  placeholder: string;
  calendarTimeZone?: string;
  ariaInvalid?: boolean;
  value: string;
  onChange: (nextValue: string) => void;
};

function ProjectDateField({
  id,
  label,
  placeholder,
  calendarTimeZone,
  ariaInvalid,
  value,
  onChange,
}: ProjectDateFieldProps) {
  const selectedDate = parseDateTimeValue(value);

  return (
    <InputGroup>
      <Popover>
        <PopoverTrigger
          render={
            <button
              id={id}
              type="button"
              data-slot="input-group-control"
              aria-invalid={ariaInvalid}
              data-empty={!selectedDate}
              className="flex min-w-0 flex-1 cursor-pointer items-center gap-2 rounded-none border-0 bg-transparent px-3 py-1.5 text-left text-sm font-normal shadow-none ring-0 outline-none data-[empty=true]:text-muted-foreground"
            >
              <CalendarIcon className="size-4 shrink-0" />
              <span className="truncate">
                {selectedDate ? format(selectedDate, "PPP") : placeholder}
              </span>
            </button>
          }
        ></PopoverTrigger>
        <PopoverContent className="w-auto p-0" align="start">
          <Calendar
            autoFocus
            mode="single"
            captionLayout="dropdown"
            selected={selectedDate}
            timeZone={calendarTimeZone}
            onSelect={(date) => {
              onChange(date ? formatDateTimeValue(withPreservedTime(date, selectedDate)) : "");
            }}
          />
        </PopoverContent>
      </Popover>
      {selectedDate ? (
        <InputGroupAddon align="inline-end">
          <InputGroupButton
            type="button"
            variant="ghost"
            size="icon-xs"
            onClick={() => onChange("")}
            aria-label={`Clear ${label.toLowerCase()}`}
          >
            <XCircle />
          </InputGroupButton>
        </InputGroupAddon>
      ) : null}
    </InputGroup>
  );
}

export function ProjectForm({
  mode,
  icon: Icon,
  submitLabel,
  users,
  initialValues,
  onCancel,
  action,
}: ProjectFormProps) {
  const [query, setQuery] = useState("");
  const [calendarTimeZone, setCalendarTimeZone] = useState<string>();
  const deferredQuery = useDeferredValue(query);
  const {
    control,
    formState: { errors: formErrors },
    handleSubmit,
    reset,
    setValue,
    watch,
  } = useForm<ProjectFormValues>({
    resolver: zodResolver(projectFormSchema),
    defaultValues: initialValues,
  });

  const fetcher = useFetcher<ProjectActionData>();
  const isSubmitting = fetcher.state === "submitting";
  const serverErrors = fetcher.data?.errors;
  const memberIds = watch("memberIds") ?? [];
  const selectedMemberIds = new Set(memberIds);

  useEffect(() => {
    setCalendarTimeZone(Intl.DateTimeFormat().resolvedOptions().timeZone);
  }, []);

  useEffect(() => {
    reset(initialValues);
    setQuery("");
  }, [initialValues, reset]);

  const normalizedQuery = deferredQuery.trim().toLowerCase();
  const filteredUsers = normalizedQuery
    ? users.filter((user) => {
        const username = user.username.toLowerCase();
        const email = user.email.toLowerCase();
        return username.includes(normalizedQuery) || email.includes(normalizedQuery);
      })
    : users;

  const fieldError = (name: keyof ProjectFormValues) =>
    formErrors[name]?.message ?? serverErrors?.[name];

  const toggleMember = (userId: number, checked: boolean) => {
    const nextMemberIds = checked
      ? Array.from(new Set([...memberIds, userId]))
      : memberIds.filter((value) => value !== userId);
    setValue("memberIds", nextMemberIds, {
      shouldDirty: true,
      shouldValidate: true,
    });
  };

  const onSubmit = async (data: ProjectFormValues) => {
    const formData = new FormData();
    formData.set("name", data.name);
    formData.set("code", data.code);
    formData.set("status", data.status);
    formData.set("bizName", data.bizName);
    formData.set("description", data.description);
    formData.set("startedAt", data.startedAt);
    formData.set("endedAt", data.endedAt);
    formData.set("remark", data.remark);
    for (const memberId of data.memberIds) {
      formData.append("memberIds", String(memberId));
    }

    await fetcher.submit(formData, {
      method: "post",
      action,
    });
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
      {serverErrors?.general ? (
        <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
          {serverErrors.general}
        </div>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Icon className="h-5 w-5" />
            Basic Details
          </CardTitle>
          <CardDescription>
            Define the project identity, business label, and delivery status.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <FieldSet>
            <FieldLegend className="sr-only">Basic Details</FieldLegend>
            <FieldGroup className="gap-6">
              <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                <Field data-invalid={Boolean(fieldError("code"))}>
                  <FieldLabel htmlFor="code">Project Code *</FieldLabel>
                  <Controller
                    name="code"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="code"
                        type="text"
                        placeholder="SANDBOX"
                        aria-invalid={Boolean(fieldError("code")) || undefined}
                        required
                        value={field.value ?? ""}
                      />
                    )}
                  />
                  <FieldDescription>Unique code used to identify the project.</FieldDescription>
                  <FieldError>{fieldError("code")}</FieldError>
                </Field>

                <Field data-invalid={Boolean(fieldError("name"))}>
                  <FieldLabel htmlFor="name">Project Name *</FieldLabel>
                  <Controller
                    name="name"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="name"
                        type="text"
                        placeholder="Internal Sandbox"
                        aria-invalid={Boolean(fieldError("name")) || undefined}
                        required
                        value={field.value ?? ""}
                      />
                    )}
                  />
                  <FieldDescription>Name shown throughout project listings.</FieldDescription>
                  <FieldError>{fieldError("name")}</FieldError>
                </Field>

                <Field>
                  <FieldLabel htmlFor="bizName">Business Name</FieldLabel>
                  <Controller
                    name="bizName"
                    control={control}
                    render={({ field }) => (
                      <Input
                        {...field}
                        id="bizName"
                        type="text"
                        placeholder="Acme Corp"
                        value={field.value ?? ""}
                      />
                    )}
                  />
                  <FieldDescription>
                    Optional external or client-facing business label.
                  </FieldDescription>
                </Field>

                <Field data-invalid={Boolean(fieldError("status"))}>
                  <FieldLabel htmlFor="status">Status *</FieldLabel>
                  <Controller
                    name="status"
                    control={control}
                    render={({ field }) => (
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger
                          id="status"
                          className="w-full"
                          aria-invalid={Boolean(fieldError("status")) || undefined}
                        >
                          <SelectValue placeholder="Select status" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectGroup>
                            {PROJECT_STATUS_ITEMS.map((item) => (
                              <SelectItem key={item.value} value={item.value}>
                                {item.label}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                    )}
                  />
                  <FieldDescription>
                    Controls visibility and archived timestamp handling.
                  </FieldDescription>
                  <FieldError>{fieldError("status")}</FieldError>
                </Field>
              </div>
            </FieldGroup>
          </FieldSet>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Lifecycle</CardTitle>
          <CardDescription>Track when the project starts and ends.</CardDescription>
        </CardHeader>
        <CardContent>
          <FieldSet>
            <FieldLegend className="sr-only">Lifecycle</FieldLegend>
            <FieldGroup className="gap-6">
              <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                <Field data-invalid={Boolean(fieldError("startedAt"))}>
                  <FieldLabel htmlFor="startedAt">Start Date</FieldLabel>
                  <Controller
                    name="startedAt"
                    control={control}
                    render={({ field }) => (
                      <ProjectDateField
                        id="startedAt"
                        label="Start Date"
                        value={field.value ?? ""}
                        onChange={field.onChange}
                        placeholder="Pick a start date"
                        calendarTimeZone={calendarTimeZone}
                        ariaInvalid={Boolean(fieldError("startedAt")) || undefined}
                      />
                    )}
                  />
                  <FieldDescription>Optional kickoff date for the engagement.</FieldDescription>
                  <FieldError>{fieldError("startedAt")}</FieldError>
                </Field>

                <Field data-invalid={Boolean(fieldError("endedAt"))}>
                  <FieldLabel htmlFor="endedAt">End Date</FieldLabel>
                  <Controller
                    name="endedAt"
                    control={control}
                    render={({ field }) => (
                      <ProjectDateField
                        id="endedAt"
                        label="End Date"
                        value={field.value ?? ""}
                        onChange={field.onChange}
                        placeholder="Pick an end date"
                        calendarTimeZone={calendarTimeZone}
                        ariaInvalid={Boolean(fieldError("endedAt")) || undefined}
                      />
                    )}
                  />
                  <FieldDescription>Optional completion or sunset date.</FieldDescription>
                  <FieldError>{fieldError("endedAt")}</FieldError>
                </Field>
              </div>
            </FieldGroup>
          </FieldSet>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Team</CardTitle>
          <CardDescription>Search and assign members who can access this project.</CardDescription>
        </CardHeader>
        <CardContent>
          <FieldSet>
            <FieldLegend className="sr-only">Team</FieldLegend>
            <FieldGroup className="gap-6">
              <div className="flex items-center justify-between gap-3">
                <div className="space-y-1">
                  <FieldLabel>Members</FieldLabel>
                  <FieldDescription>
                    {mode === "create"
                      ? "Select the users that should immediately gain access."
                      : "Adjust project membership and access scope."}
                  </FieldDescription>
                </div>
                <div className="relative">
                  <Input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Search users..."
                    className="h-8 w-56 pl-8"
                  />
                  <Search className="absolute top-1/2 left-2 size-4 -translate-y-1/2 text-muted-foreground" />
                </div>
              </div>

              <div className="rounded-md border">
                <ScrollArea className="h-[360px] p-3">
                  <div className="space-y-4">
                    {filteredUsers.map((user) => {
                      const isSelected = selectedMemberIds.has(user.id);

                      return (
                        <div key={user.id} className="rounded-lg border p-3">
                          <label className="flex items-center gap-3">
                            <Checkbox
                              checked={isSelected}
                              onCheckedChange={(checked) => toggleMember(user.id, Boolean(checked))}
                            />
                            <div>
                              <div className="font-medium">{user.username}</div>
                              <div className="text-sm text-muted-foreground">{user.email}</div>
                            </div>
                          </label>
                        </div>
                      );
                    })}

                    {filteredUsers.length === 0 ? (
                      <div className="text-sm text-muted-foreground">No matching users</div>
                    ) : null}
                  </div>
                </ScrollArea>
              </div>
            </FieldGroup>
          </FieldSet>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Notes</CardTitle>
          <CardDescription>
            Capture project context, objectives, and operator remarks.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <FieldSet>
            <FieldLegend className="sr-only">Notes</FieldLegend>
            <FieldGroup className="gap-6">
              <Field>
                <FieldLabel htmlFor="description">Description</FieldLabel>
                <Controller
                  name="description"
                  control={control}
                  render={({ field }) => (
                    <Textarea
                      {...field}
                      id="description"
                      placeholder="Summarize project scope, client context, or operating notes."
                      rows={5}
                      value={field.value ?? ""}
                    />
                  )}
                />
                <FieldDescription>Long-form context for dashboards and handoffs.</FieldDescription>
              </Field>

              <Field>
                <FieldLabel htmlFor="remark">Remark</FieldLabel>
                <Controller
                  name="remark"
                  control={control}
                  render={({ field }) => (
                    <Textarea
                      {...field}
                      id="remark"
                      placeholder="Internal notes, cautions, or follow-up reminders."
                      rows={4}
                      value={field.value ?? ""}
                    />
                  )}
                />
                <FieldDescription>Private operational notes for the team.</FieldDescription>
              </Field>
            </FieldGroup>
          </FieldSet>
        </CardContent>
      </Card>

      <div className="flex gap-4 pt-2">
        <Button type="submit" className="flex items-center gap-2" disabled={isSubmitting}>
          {isSubmitting ? (
            <Loader className="h-4 w-4 animate-spin" />
          ) : (
            <Icon className="h-4 w-4" />
          )}
          {submitLabel}
        </Button>
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
