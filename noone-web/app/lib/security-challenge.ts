interface PasswordChallengeInput {
  request: Request;
  password: string;
  action: string;
  targetType?: string;
  targetId?: string;
}

export async function createPasswordChallenge(input: PasswordChallengeInput): Promise<string> {
  const { authUtils } = await import("@/lib/" + "auth.server");
  if (!input.password.trim()) {
    throw new Error("Current password is required");
  }

  const challenge = await authUtils.createSensitiveActionChallenge(
    {
      verificationMethod: "PASSWORD",
      action: input.action,
      targetType: input.targetType,
      targetId: input.targetId,
      password: input.password,
    },
    input.request,
  );

  return challenge.challengeToken;
}
