import type { CreateProfileRequest, Profile } from "@/types/profile";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";

import { Edit, Plus } from "lucide-react";
import { redirect, useActionData, useLoaderData, useNavigate } from "react-router";

import { createAuthFetch } from "@/api.server";
import { createProfile, getProfileById, updateProfile } from "@/api/profile-api";
import { FormPageShell } from "@/components/form-page-shell";
import { ProfileForm } from "@/components/profile/profile-form";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import {
  getProfileFormSeed,
  parseProfileFormData,
  type ProfileActionData,
} from "@/routes/profile/profile-form.shared";

type LoaderData = {
  profile?: Profile;
};

export async function loader({
  context,
  params,
  request,
}: LoaderFunctionArgs): Promise<LoaderData> {
  const profileId = params.profileId;
  if (!profileId) {
    return {};
  }

  const authFetch = createAuthFetch(request, context);
  const profile = await getProfileById(profileId, authFetch);
  if (!profile) {
    throw new Response("Profile not found", { status: 404 });
  }

  return { profile };
}

export async function action({ request, context, params }: ActionFunctionArgs) {
  const mode = params.profileId ? "edit" : "create";
  const parsed = parseProfileFormData(await request.formData(), { mode });
  if (parsed.errors) {
    return {
      errors: parsed.errors,
      success: false,
      values: parsed.values,
    } satisfies ProfileActionData;
  }

  try {
    const authFetch = createAuthFetch(request, context);
    if (params.profileId) {
      await updateProfile(params.profileId, parsed.payload, authFetch);
    } else {
      await createProfile(parsed.payload as CreateProfileRequest, authFetch);
    }

    return redirect("/profiles");
  } catch (error: any) {
    return {
      errors: { general: error?.message || "Failed to save profile" },
      success: false,
      values: parsed.values,
    } satisfies ProfileActionData;
  }
}

export const handle = createBreadcrumb(({ params }) => {
  if (params.profileId) {
    return {
      id: "profiles-edit",
      label: "Edit Profile",
      to: `/profiles/edit/${params.profileId}`,
    };
  }

  return {
    id: "profiles-create",
    label: "Create Profile",
    to: "/profiles/create",
  };
});

export default function ProfileEditor() {
  const { profile } = useLoaderData() as LoaderData;
  const actionData = useActionData() as ProfileActionData | undefined;
  const navigate = useNavigate();
  const isEdit = Boolean(profile);
  const initialValues = actionData?.values ?? getProfileFormSeed(profile);

  return (
    <FormPageShell
      backHref="/profiles"
      backLabel="Return to profile list"
      badges={[
        {
          label: isEdit ? "Edit mode" : "New profile",
          variant: isEdit ? "secondary" : "default",
        },
        ...(profile ? [{ label: profile.protocolType, variant: "outline" as const }] : []),
      ]}
      title={isEdit ? "Edit Profile" : "Create Profile"}
      description={
        isEdit
          ? `Update transport details, identifiers, and payload templates for ${profile?.name}.`
          : "Define a reusable request profile with protocol settings, identifiers, and transformation rules."
      }
    >
      <ProfileForm
        key={`${isEdit ? "edit" : "create"}:${JSON.stringify(initialValues)}`}
        mode={isEdit ? "edit" : "create"}
        icon={isEdit ? Edit : Plus}
        submitLabel={isEdit ? "Update Profile" : "Create Profile"}
        initialValues={initialValues}
        errors={actionData?.errors}
        onCancel={() => navigate("/profiles")}
      />
    </FormPageShell>
  );
}
