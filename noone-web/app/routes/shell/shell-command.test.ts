import { clientAction } from "@/routes/shell/shell-command";

const dispatchPluginMock = vi.hoisted(() => vi.fn());

vi.mock("@/api/shell-api", () => ({
  dispatchPlugin: dispatchPluginMock,
}));

function commandRequest(requestId: string) {
  const body = new URLSearchParams({
    intent: "run-command",
    payload: JSON.stringify({ cmd: "whoami", commandTemplate: { executable: "/bin/sh" } }),
    requestId,
  }).toString();

  return new Request("http://localhost/shells/1/command", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  });
}

async function runAction(requestId: string) {
  const response = await clientAction({
    request: commandRequest(requestId),
    params: { shellId: "1" },
    context: {},
  } as any);
  return await response.json();
}

describe("shell-command clientAction", () => {
  beforeEach(() => {
    dispatchPluginMock.mockReset();
  });

  it("keeps the requestId when the dispatch throws", async () => {
    dispatchPluginMock.mockRejectedValue(new Error("plugin [command-execute] not found"));

    const data = await runAction("request-1");

    expect(data).toEqual({
      ok: false,
      error: "plugin [command-execute] not found",
      requestId: "request-1",
    });
  });

  it("keeps the requestId when the dispatch returns a failure payload", async () => {
    dispatchPluginMock.mockResolvedValue({ code: 1, error: "remote failure" });

    const data = await runAction("request-2");

    expect(data).toEqual({
      ok: false,
      error: "remote failure",
      requestId: "request-2",
    });
  });
});
