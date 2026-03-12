import { ArrowLeft, KeyRound, LaptopMinimal, ShieldAlert } from "lucide-react";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";
import { Form, redirect, useActionData, useLoaderData, useNavigate } from "react-router";
import { createAuthFetch } from "@/api.server";
import {
  getUserById,
  getUserLoginLogs,
  getUserSessions,
  revokeAllUserSessions,
  revokeUserSession,
} from "@/api/user-api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDate } from "@/lib/format";
import { createPasswordChallenge } from "@/lib/security-challenge";
import type { LoginLog, User, UserSession } from "@/types/admin";

interface LoaderData {
  user: User;
  loginLogs: LoginLog[];
  sessions: UserSession[];
}

export async function loader({
  context,
  params,
  request,
}: LoaderFunctionArgs): Promise<LoaderData> {
  const userId = Number.parseInt(params.userId as string, 10);
  if (Number.isNaN(userId)) {
    throw new Response("Invalid user ID", { status: 400 });
  }

  const authFetch = createAuthFetch(request, context);
  const [user, loginLogs, sessions] = await Promise.all([
    getUserById(userId, authFetch),
    getUserLoginLogs(userId, authFetch),
    getUserSessions(userId, authFetch),
  ]);

  if (!user) {
    throw new Response("User not found", { status: 404 });
  }

  return { user, loginLogs, sessions };
}

export async function action({ request, context, params }: ActionFunctionArgs) {
  const userId = Number.parseInt(params.userId as string, 10);
  if (Number.isNaN(userId)) {
    throw new Response("Invalid user ID", { status: 400 });
  }

  const formData = await request.formData();
  const intent = formData.get("intent") as string;
  const verificationPassword = (formData.get("verificationPassword") as string | null) ?? "";

  try {
    const authFetch = createAuthFetch(request, context);
    if (intent === "revoke-all") {
      const challengeToken = await createPasswordChallenge({
        request,
        password: verificationPassword,
        action: "user.revoke-all-sessions",
        targetType: "user",
        targetId: String(userId),
      });
      await revokeAllUserSessions(userId, authFetch, { challengeToken });
      return redirect(`/admin/users/security/${userId}`);
    }

    if (intent === "revoke-one") {
      const sessionId = (formData.get("sessionId") as string | null) ?? "";
      if (!sessionId) {
        return { errors: { general: "Session ID is required" } };
      }
      const challengeToken = await createPasswordChallenge({
        request,
        password: verificationPassword,
        action: "user.revoke-session",
        targetType: "session",
        targetId: sessionId,
      });
      await revokeUserSession(userId, sessionId, authFetch, { challengeToken });
      return redirect(`/admin/users/security/${userId}`);
    }

    return { errors: { general: "Unsupported action" } };
  } catch (error: any) {
    return {
      errors: {
        general: error?.message || "Sensitive security action failed",
      },
    };
  }
}

function SessionStateBadge({ session }: { session: UserSession }) {
  if (session.revoked) {
    return <Badge variant="destructive">Revoked</Badge>;
  }
  return <Badge variant="secondary">Active</Badge>;
}

function LoginStatusBadge({ status }: { status: string }) {
  const variant =
    status === "SUCCESS"
      ? "secondary"
      : status === "REQUIRE_PASSWORD_CHANGE" || status === "REQUIRE_SETUP"
        ? "outline"
        : "destructive";
  return <Badge variant={variant}>{status}</Badge>;
}

export default function UserSecurityPage() {
  const { user, loginLogs, sessions } = useLoaderData() as LoaderData;
  const actionData = useActionData() as { errors?: Record<string, string> } | undefined;
  const navigate = useNavigate();

  return (
    <div className="container mx-auto max-w-6xl p-6">
      <div className="mb-8">
        <Button
          variant="ghost"
          onClick={() => navigate("/admin/users")}
          className="mb-4 flex items-center gap-2"
        >
          <ArrowLeft className="h-4 w-4" />
          Return to user list
        </Button>

        <h1 className="text-3xl font-bold text-balance">User Security</h1>
        <p className="mt-2 text-muted-foreground">
          Review login history and active sessions for{" "}
          <span className="font-semibold">{user.username}</span>
        </p>
      </div>

      {actionData?.errors?.general ? (
        <div className="mb-6 rounded-md bg-destructive/15 p-3 text-sm text-destructive">
          {actionData.errors.general}
        </div>
      ) : null}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <LaptopMinimal className="h-5 w-5" />
                Active Sessions
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Session</TableHead>
                    <TableHead>Client</TableHead>
                    <TableHead>Last Seen</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="w-[240px]">Action</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sessions.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={5} className="text-center text-muted-foreground">
                        No session records
                      </TableCell>
                    </TableRow>
                  ) : (
                    sessions.map((session) => (
                      <TableRow key={session.sessionId}>
                        <TableCell className="font-mono text-xs">{session.sessionId}</TableCell>
                        <TableCell>
                          <div className="space-y-1">
                            <div>{session.deviceInfo || session.userAgent || "Unknown device"}</div>
                            <div className="text-xs text-muted-foreground">
                              {session.ipAddress || "--"}
                            </div>
                          </div>
                        </TableCell>
                        <TableCell>
                          {session.lastSeenAt ? formatDate(session.lastSeenAt) : "--"}
                        </TableCell>
                        <TableCell>
                          <SessionStateBadge session={session} />
                        </TableCell>
                        <TableCell>
                          <Form method="post" className="flex items-center gap-2">
                            <input type="hidden" name="intent" value="revoke-one" />
                            <input type="hidden" name="sessionId" value={session.sessionId} />
                            <Input
                              name="verificationPassword"
                              type="password"
                              placeholder="Current admin password"
                              disabled={session.revoked}
                            />
                            <Button type="submit" variant="outline" disabled={session.revoked}>
                              Revoke
                            </Button>
                          </Form>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <ShieldAlert className="h-5 w-5" />
                Recent Login Logs
              </CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Time</TableHead>
                    <TableHead>IP</TableHead>
                    <TableHead>Device</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Reason</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {loginLogs.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={5} className="text-center text-muted-foreground">
                        No login records
                      </TableCell>
                    </TableRow>
                  ) : (
                    loginLogs.map((log) => (
                      <TableRow key={log.id}>
                        <TableCell>{formatDate(log.loginTime)}</TableCell>
                        <TableCell>{log.ipAddress || "--"}</TableCell>
                        <TableCell>
                          <div className="space-y-1">
                            <div>{log.deviceInfo || log.userAgent || "Unknown device"}</div>
                            <div className="text-xs text-muted-foreground">
                              {[log.browser, log.os].filter(Boolean).join(" / ") || "--"}
                            </div>
                          </div>
                        </TableCell>
                        <TableCell>
                          <LoginStatusBadge status={log.status} />
                        </TableCell>
                        <TableCell>{log.failReason || "--"}</TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </div>

        <Card className="h-fit">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <KeyRound className="h-5 w-5" />
              Security Summary
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-1">
              <div className="text-sm text-muted-foreground">User</div>
              <div className="font-medium">{user.username}</div>
            </div>
            <div className="space-y-1">
              <div className="text-sm text-muted-foreground">Status</div>
              <Badge variant="outline">{user.status}</Badge>
            </div>
            <div className="space-y-1">
              <div className="text-sm text-muted-foreground">Must change password</div>
              <div>{user.mustChangePassword ? "Yes" : "No"}</div>
            </div>
            <div className="space-y-1">
              <div className="text-sm text-muted-foreground">2FA bound</div>
              <div>{user.mfaBoundAt ? formatDate(user.mfaBoundAt) : "Not yet"}</div>
            </div>
            <div className="space-y-1">
              <div className="text-sm text-muted-foreground">Last login</div>
              <div>{user.lastLogin ? formatDate(user.lastLogin) : "Never"}</div>
            </div>

            <div className="rounded-lg border p-4">
              <Form method="post" className="space-y-3">
                <input type="hidden" name="intent" value="revoke-all" />
                <div className="space-y-2">
                  <Label htmlFor="revokeAllPassword">Current admin password</Label>
                  <Input
                    id="revokeAllPassword"
                    name="verificationPassword"
                    type="password"
                    placeholder="Required for force logout"
                  />
                </div>
                <Button type="submit" variant="destructive" className="w-full">
                  Revoke All Sessions
                </Button>
              </Form>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
