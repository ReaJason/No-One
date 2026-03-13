import type { User } from "@/types/admin";
import type { ProjectStatus } from "@/types/project";
import type { LucideIcon } from "lucide-react";

import { Search } from "lucide-react";
import { useDeferredValue, useEffect, useMemo, useState } from "react";
import { Form } from "react-router";

import { Button } from "@/components/ui/button";
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
import { PROJECT_STATUS_ITEMS, type ProjectFormSeed } from "@/routes/project/project-form.shared";

type ProjectFormProps = {
  mode: "create" | "edit";
  icon: LucideIcon;
  submitLabel: string;
  users: User[];
  initialValues: ProjectFormSeed;
  errors?: Record<string, string>;
  onCancel: () => void;
  action?: string;
};

export function ProjectForm({
  mode,
  icon: Icon,
  submitLabel,
  users,
  initialValues,
  errors,
  onCancel,
  action,
}: ProjectFormProps) {
  const [status, setStatus] = useState<ProjectStatus>(initialValues.status);
  const [query, setQuery] = useState("");
  const deferredQuery = useDeferredValue(query);
  const [selectedMemberIds, setSelectedMemberIds] = useState<Set<number>>(
    () => new Set(initialValues.memberIds),
  );

  useEffect(() => {
    setStatus(initialValues.status);
    setSelectedMemberIds(new Set(initialValues.memberIds));
    setQuery("");
  }, [initialValues]);

  const filteredUsers = useMemo(() => {
    const normalizedQuery = deferredQuery.trim().toLowerCase();
    if (!normalizedQuery) {
      return users;
    }
    return users.filter((user) => {
      const username = user.username.toLowerCase();
      const email = user.email.toLowerCase();
      return username.includes(normalizedQuery) || email.includes(normalizedQuery);
    });
  }, [deferredQuery, users]);

  const toggleMember = (userId: number, checked: boolean) => {
    setSelectedMemberIds((previous) => {
      const next = new Set(previous);
      if (checked) {
        next.add(userId);
      } else {
        next.delete(userId);
      }
      return next;
    });
  };

  return (
    <Form method="post" action={action} className="space-y-6">
      {[...selectedMemberIds].map((memberId) => (
        <input key={memberId} type="hidden" name="memberIds" value={memberId} />
      ))}

      {errors?.general ? (
        <div className="rounded-md bg-destructive/15 p-3 text-sm text-destructive">
          {errors.general}
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
                <Field data-invalid={Boolean(errors?.code)}>
                  <FieldLabel htmlFor="code">Project Code *</FieldLabel>
                  <Input
                    id="code"
                    name="code"
                    type="text"
                    defaultValue={initialValues.code}
                    placeholder="SANDBOX"
                    aria-invalid={Boolean(errors?.code) || undefined}
                    required
                  />
                  <FieldDescription>Unique code used to identify the project.</FieldDescription>
                  <FieldError>{errors?.code}</FieldError>
                </Field>

                <Field data-invalid={Boolean(errors?.name)}>
                  <FieldLabel htmlFor="name">Project Name *</FieldLabel>
                  <Input
                    id="name"
                    name="name"
                    type="text"
                    defaultValue={initialValues.name}
                    placeholder="Internal Sandbox"
                    aria-invalid={Boolean(errors?.name) || undefined}
                    required
                  />
                  <FieldDescription>Name shown throughout project listings.</FieldDescription>
                  <FieldError>{errors?.name}</FieldError>
                </Field>

                <Field>
                  <FieldLabel htmlFor="bizName">Business Name</FieldLabel>
                  <Input
                    id="bizName"
                    name="bizName"
                    type="text"
                    defaultValue={initialValues.bizName}
                    placeholder="Acme Corp"
                  />
                  <FieldDescription>
                    Optional external or client-facing business label.
                  </FieldDescription>
                </Field>

                <Field data-invalid={Boolean(errors?.status)}>
                  <FieldLabel htmlFor="status">Status *</FieldLabel>
                  <input type="hidden" name="status" value={status} />
                  <Select
                    value={status}
                    onValueChange={(value) => setStatus((value ?? "DRAFT") as ProjectStatus)}
                  >
                    <SelectTrigger
                      id="status"
                      className="w-full"
                      aria-invalid={Boolean(errors?.status) || undefined}
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
                  <FieldDescription>
                    Controls visibility and archived timestamp handling.
                  </FieldDescription>
                  <FieldError>{errors?.status}</FieldError>
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
                <Field data-invalid={Boolean(errors?.startedAt)}>
                  <FieldLabel htmlFor="startedAt">Start Time</FieldLabel>
                  <Input
                    id="startedAt"
                    name="startedAt"
                    type="datetime-local"
                    defaultValue={initialValues.startedAt}
                    aria-invalid={Boolean(errors?.startedAt) || undefined}
                  />
                  <FieldDescription>Optional kickoff time for the engagement.</FieldDescription>
                  <FieldError>{errors?.startedAt}</FieldError>
                </Field>

                <Field data-invalid={Boolean(errors?.endedAt)}>
                  <FieldLabel htmlFor="endedAt">End Time</FieldLabel>
                  <Input
                    id="endedAt"
                    name="endedAt"
                    type="datetime-local"
                    defaultValue={initialValues.endedAt}
                    aria-invalid={Boolean(errors?.endedAt) || undefined}
                  />
                  <FieldDescription>Optional completion or sunset timestamp.</FieldDescription>
                  <FieldError>{errors?.endedAt}</FieldError>
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
                <Textarea
                  id="description"
                  name="description"
                  defaultValue={initialValues.description}
                  placeholder="Summarize project scope, client context, or operating notes."
                  rows={5}
                />
                <FieldDescription>Long-form context for dashboards and handoffs.</FieldDescription>
              </Field>

              <Field>
                <FieldLabel htmlFor="remark">Remark</FieldLabel>
                <Textarea
                  id="remark"
                  name="remark"
                  defaultValue={initialValues.remark}
                  placeholder="Internal notes, cautions, or follow-up reminders."
                  rows={4}
                />
                <FieldDescription>Private operational notes for the team.</FieldDescription>
              </Field>
            </FieldGroup>
          </FieldSet>
        </CardContent>
      </Card>

      <div className="flex gap-4 pt-2">
        <Button type="submit" className="flex items-center gap-2">
          <Icon className="h-4 w-4" />
          {submitLabel}
        </Button>
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </Form>
  );
}
