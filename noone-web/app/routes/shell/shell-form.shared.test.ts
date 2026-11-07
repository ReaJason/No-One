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
      browserImpersonation: true,
      impersonationProfile: "MAC_FIREFOX",
      clientCertificate: "MIIBcAIBAzCCASY=",
      clientCertificatePassword: "test-password",
    });

    expect(payload.clientConfig).toEqual({
      proxyUrl: "http://127.0.0.1:8080",
      customHeaders: { "X-Test": "yes" },
      connectTimeoutMs: 1500,
      readTimeoutMs: 2500,
      skipSslVerify: true,
      maxRetries: 2,
      retryDelayMs: 3000,
      browserImpersonation: true,
      impersonationProfile: "MAC_FIREFOX",
      clientCertificate: "MIIBcAIBAzCCASY=",
      clientCertificatePassword: "test-password",
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
      browserImpersonation: false,
      impersonationProfile: "",
      clientCertificate: "",
      clientCertificatePassword: "",
    });

    expect(payload.clientConfig).toEqual({
      proxyUrl: "http://127.0.0.1:8080",
      readTimeoutMs: 2500,
      browserImpersonation: false,
      impersonationProfile: "",
      clientCertificate: "",
      clientCertificatePassword: "",
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
        browserImpersonation: true,
        impersonationProfile: "ANDROID",
        clientCertificate: "MIIBcAIBAzCCASY=",
        clientCertificatePassword: "test-password",
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
      browserImpersonation: true,
      impersonationProfile: "ANDROID",
      clientCertificate: "MIIBcAIBAzCCASY=",
      clientCertificatePassword: "test-password",
    });
  });

  it("clears the stored impersonation profile when the switch is turned off", () => {
    const payload = buildCreatePayload({
      name: "demo",
      url: "https://127.0.0.1/test",
      language: "java",
      profileId: "1",
      shellType: "",
      interfaceName: "",
      projectId: "",
      staging: false,
      loaderProfileId: "",
      proxyUrl: "",
      customHeaders: "",
      connectTimeoutMs: "",
      readTimeoutMs: "",
      maxRetries: "",
      retryDelayMs: "",
      skipSslVerify: false,
      browserImpersonation: false,
      impersonationProfile: "MAC_SAFARI",
      clientCertificate: "",
      clientCertificatePassword: "",
    });

    expect(payload.clientConfig).toMatchObject({
      browserImpersonation: false,
      impersonationProfile: "",
    });
  });

  it("clears a stored client certificate when it is removed from the form", () => {
    const payload = buildCreatePayload({
      name: "demo",
      url: "https://127.0.0.1/test",
      language: "java",
      profileId: "1",
      shellType: "",
      interfaceName: "",
      projectId: "",
      staging: false,
      loaderProfileId: "",
      proxyUrl: "",
      customHeaders: "",
      connectTimeoutMs: "",
      readTimeoutMs: "",
      maxRetries: "",
      retryDelayMs: "",
      skipSslVerify: false,
      browserImpersonation: false,
      impersonationProfile: "",
      clientCertificate: "",
      clientCertificatePassword: "",
    });

    expect(payload.clientConfig).toMatchObject({
      clientCertificate: "",
      clientCertificatePassword: "",
    });
  });
});
