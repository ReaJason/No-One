import type { ShellConnection } from "@/types/shell-connection";

import {
  buildCreatePayload,
  buildTestConfigPayload,
  getDefaultValues,
} from "@/routes/shell/shell-form.shared";

describe("shell-form.shared", () => {
  it("builds clientConfig for create payloads", () => {
    const payload = buildCreatePayload({
      name: "demo",
      url: "http://127.0.0.1/test",
      language: "java",
      profileId: "1",
      shellType: "",
      interfaceName: "",
      projectId: "",
      staging: false,
      loaderProfileId: "",
      proxyUrl: "http://127.0.0.1:8080",
      customHeaders: '{"X-Test":"yes"}',
      connectTimeoutMs: "1500",
      readTimeoutMs: "2500",
      maxRetries: "2",
      retryDelayMs: "3000",
      skipSslVerify: true,
    });

    expect(payload.clientConfig).toEqual({
      proxyUrl: "http://127.0.0.1:8080",
      customHeaders: { "X-Test": "yes" },
      connectTimeoutMs: 1500,
      readTimeoutMs: 2500,
      skipSslVerify: true,
      maxRetries: 2,
      retryDelayMs: 3000,
    });
    expect(payload).not.toHaveProperty("proxyUrl");
    expect(payload).not.toHaveProperty("customHeaders");
  });

  it("builds clientConfig for test payloads", () => {
    const payload = buildTestConfigPayload({
      name: "demo",
      url: "http://127.0.0.1/test",
      language: "java",
      profileId: "1",
      shellType: "",
      interfaceName: "",
      projectId: "",
      staging: false,
      loaderProfileId: "",
      proxyUrl: "http://127.0.0.1:8080",
      customHeaders: "",
      connectTimeoutMs: "",
      readTimeoutMs: "2500",
      maxRetries: "",
      retryDelayMs: "",
      skipSslVerify: false,
    });

    expect(payload.clientConfig).toEqual({
      proxyUrl: "http://127.0.0.1:8080",
      readTimeoutMs: 2500,
    });
    expect(payload).not.toHaveProperty("proxyUrl");
  });

  it("reads connection overrides from clientConfig defaults", () => {
    const shell: ShellConnection = {
      id: 1,
      name: "demo",
      url: "http://127.0.0.1/test",
      language: "java",
      status: "DISCONNECTED",
      profileId: 1,
      createdAt: "2026-03-28T00:00:00Z",
      updatedAt: "2026-03-28T00:00:00Z",
      clientConfig: {
        proxyUrl: "http://127.0.0.1:8080",
        customHeaders: { "X-Test": "yes" },
        connectTimeoutMs: 1500,
        readTimeoutMs: 2500,
        skipSslVerify: true,
        maxRetries: 2,
        retryDelayMs: 3000,
      },
    };

    expect(getDefaultValues(shell)).toMatchObject({
      proxyUrl: "http://127.0.0.1:8080",
      customHeaders: '{"X-Test":"yes"}',
      connectTimeoutMs: "1500",
      readTimeoutMs: "2500",
      skipSslVerify: true,
      maxRetries: "2",
      retryDelayMs: "3000",
    });
  });
});
