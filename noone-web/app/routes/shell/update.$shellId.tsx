import type {ActionFunctionArgs} from "react-router";
import {redirect} from "react-router";
import {toast} from "sonner";
import {updateShellConnection} from "@/lib/shell-connection-api";

export async function action({ request, params }: ActionFunctionArgs) {
  const shellId = params.shellId as string | undefined;
  if (!shellId) {
    throw new Response("Invalid shell ID", { status: 400 });
  }

  const formData = await request.formData();
  const url = (formData.get("url") as string)?.trim();
  const group = (formData.get("group") as string)?.trim();
  const projectIdRaw = (formData.get("projectId") as string)?.trim();
  const profileIdRaw = (formData.get("profileId") as string)?.trim();
  const profileId = profileIdRaw ? Number(profileIdRaw) : undefined;
  let projectId: number | null | undefined;
  if (projectIdRaw === "") {
    projectId = null;
  } else if (projectIdRaw) {
    projectId = Number(projectIdRaw);
  }

  // Advanced settings
  const proxyUrl = (formData.get("proxyUrl") as string)?.trim() || undefined;
  const connectTimeoutMsRaw = (
    formData.get("connectTimeoutMs") as string
  )?.trim();
  const readTimeoutMsRaw = (formData.get("readTimeoutMs") as string)?.trim();
  const maxRetriesRaw = (formData.get("maxRetries") as string)?.trim();
  const retryDelayMsRaw = (formData.get("retryDelayMs") as string)?.trim();
  const connectTimeoutMs = connectTimeoutMsRaw
    ? Number(connectTimeoutMsRaw)
    : undefined;
  const readTimeoutMs = readTimeoutMsRaw ? Number(readTimeoutMsRaw) : undefined;
  const maxRetries = maxRetriesRaw ? Number(maxRetriesRaw) : undefined;
  const retryDelayMs = retryDelayMsRaw ? Number(retryDelayMsRaw) : undefined;
  const skipSslVerify = formData.get("skipSslVerify") === "on";

  // Parse custom headers JSON
  const customHeadersRaw = (formData.get("customHeaders") as string)?.trim();
  let customHeaders: Record<string, string> | undefined;
  if (customHeadersRaw) {
    try {
      customHeaders = JSON.parse(customHeadersRaw);
    } catch {
      return {
        errors: { customHeaders: "Invalid JSON format for custom headers" },
        success: false,
      };
    }
  }

  const errors: Record<string, string> = {};
  if (!url) errors.url = "URL is required";
  if (!profileId || !Number.isFinite(profileId)) {
    errors.profileId = "Profile is required";
  }
  if (projectIdRaw && !Number.isFinite(projectId)) {
    errors.projectId = "Project ID must be a number";
  }

  if (Object.keys(errors).length > 0) {
    return { errors, success: false };
  }

  try {
    await updateShellConnection(shellId, {
      url,
      group: group || undefined,
      projectId,
      profileId: profileId!,
      proxyUrl,
      customHeaders,
      connectTimeoutMs,
      readTimeoutMs,
      skipSslVerify: skipSslVerify || undefined,
      maxRetries,
      retryDelayMs,
    });
    toast.success("Shell connection updated successfully");
    return redirect("/shells");
  } catch (error: any) {
    toast.error(error?.message || "Failed to update shell connection");
    return {
      errors: {
        general: error?.message || "Failed to update shell connection",
      },
      success: false,
    };
  }
}
