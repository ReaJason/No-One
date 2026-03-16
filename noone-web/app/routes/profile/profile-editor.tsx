import type { Route } from "./+types/profile-editor";
import type { CreateProfileRequest, Profile } from "@/types/profile";

import { Edit, Plus } from "lucide-react";
import { useMemo } from "react";
import {
  type ActionFunctionArgs,
  type LoaderFunctionArgs,
  isRouteErrorResponse,
  redirect,
  useLoaderData,
  useNavigate,
  useParams,
} from "react-router";

import { createAuthFetch } from "@/api/api.server";
import { createProfile, getProfileById, updateProfile } from "@/api/profile-api";
import { FormPageShell } from "@/components/form-page-shell";
import { NotFoundErrorBoundary } from "@/components/not-found-error-boundary";
import { ProfileForm } from "@/components/profile/profile-form";
import { createBreadcrumb } from "@/lib/breadcrumb-utils";
import {
  getDefaultValues,
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
  return { profile };
}

export async function action({ request, context, params }: ActionFunctionArgs) {
  const mode = params.profileId ? "edit" : "create";
  const parsed = parseProfileFormData(await request.formData(), { mode });
  if ("errors" in parsed) {
    return {
      errors: parsed.errors,
      success: false,
      values: parsed.values,
    } satisfies ProfileActionData;
  }

  try {
    const authFetch = createAuthFetch(request, context);
    console.log("payload: ", parsed.payload);
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

export function ErrorBoundary({ error }: Route.ErrorBoundaryProps) {
  const params = useParams();
  if (isRouteErrorResponse(error) && error.status === 404) {
    return (
      <NotFoundErrorBoundary
        title={"Profile not found"}
        backLabel={"Back to Profile"}
        backHref={"/profiles"}
        resourceType={"Profile"}
        resourceId={params.profileId}
      />
    );
  }
  throw error;
}

export default function ProfileEditor() {
  const { profile } = useLoaderData() as LoaderData;
  const navigate = useNavigate();
  const isEdit = Boolean(profile);
  const mode = isEdit ? "edit" : "create";
  const initialValues = useMemo(() => getDefaultValues(profile), [profile]);

  const pageMeta = isEdit
    ? {
        badge: { label: "Edit mode", variant: "secondary" as const },
        description: `Update transport details, identifiers, and payload templates for ${profile?.name}.`,
        icon: Edit,
        submitLabel: "Update Profile",
        title: "Edit Profile",
      }
    : {
        badge: { label: "New profile", variant: "default" as const },
        description:
          "Define a reusable request profile with protocol settings, identifiers, and transformation rules.",
        icon: Plus,
        submitLabel: "Create Profile",
        title: "Create Profile",
      };

  return (
    <FormPageShell
      backHref="/profiles"
      backLabel="Return to profile list"
      badges={[
        pageMeta.badge,
        ...(profile ? [{ label: profile.protocolType, variant: "outline" as const }] : []),
      ]}
      title={pageMeta.title}
      description={pageMeta.description}
    >
      <ProfileForm
        mode={mode}
        icon={pageMeta.icon}
        submitLabel={pageMeta.submitLabel}
        initialValues={initialValues}
        onCancel={() => navigate(-1)}
      />
    </FormPageShell>
  );
}
