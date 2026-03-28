# Dubbo Client Proxy Support Design

## Problem

`DubboClient` in `noone-transport` has no proxy support. HTTP and WebSocket clients already support HTTP and SOCKS5 proxies via `ProxyConfig` + `OkHttpSupport`, but Dubbo manages its own networking (Netty for TCP protocols, URLConnection for HTTP protocols), so proxy injection requires a different strategy.

The current single `DubboClient` also only supports Apache Dubbo 3.x. Targets running Alibaba Dubbo 2.x services cannot be reached.

## Goals

- HTTP and SOCKS5 proxy support (with optional authentication) for all Dubbo protocols
- Support both Alibaba Dubbo 2.x and Apache Dubbo 3.x
- Per-connection proxy configuration (no global JVM state)
- Consistent with existing `ProxyConfig` DTO used by HTTP/WebSocket clients

## Non-Goals

- SOCKS4-only proxy support: we do not target SOCKS4-only proxies; operators should use SOCKS5. If a `ProxyConfig` with type `SOCKS4` is passed, Dubbo clients will reject it explicitly (unlike HTTP/WebSocket clients which pass it through to `java.net.Proxy`).
- Dubbo registry integration (direct URL connections only, matching current design)
- Proxy auto-detection from environment variables

## Approach: Dubbo SPI Extensions

Dubbo's architecture is SPI-driven. We register custom `Transporter` and `Protocol` SPI extensions that inject proxy support into Dubbo's internal transport layer. This preserves the `ReferenceConfig`/`GenericService.$invoke` abstraction for all protocols.

## Protocol Coverage

| Client | Protocols | Transport Layer |
|--------|-----------|-----------------|
| `ApacheDubboClient` | `dubbo://`, `hessian://`, `tri://`, `http://` | Netty (dubbo, tri), URLConnection (hessian, http) |
| `AlibabaDubboClient` | `dubbo://`, `hessian://` | Netty (dubbo), URLConnection (hessian) |

All protocol × proxy type (HTTP, SOCKS5) × auth (yes, no) combinations are supported.

## Design

### 1. Config Layer

`DubboClientConfig` gains a `ProxyConfig` field, reusing the existing DTO shared with `HttpClientConfig` and `WebSocketClientConfig`:

```java
@Data
@Builder
public class DubboClientConfig {
    private String interfaceName;
    @Builder.Default
    private String methodName = "handle";
    @Builder.Default
    private String[] parameterTypes = new String[]{"[B"};
    @Builder.Default
    private int readTimeoutMs = 60000;
    private ProxyConfig proxy;  // new
}
```

When `connect()` is called, proxy config is embedded as Dubbo URL parameters (`proxy.type`, `proxy.host`, `proxy.port`, `proxy.username`, `proxy.password`). Dubbo's URL is the universal config carrier -- SPI extensions read these parameters to configure transport.

### 2. Client Structure

The existing `DubboClient.java` is deleted, replaced by two new classes:

- **`ApacheDubboClient`** -- imports from `org.apache.dubbo.*` (Dubbo 3.x). Supports `dubbo://`, `hessian://`, `tri://`, `http://`.
- **`AlibabaDubboClient`** -- imports from `com.alibaba.dubbo.*` (Dubbo 2.x). Supports `dubbo://`, `hessian://`.

Both implement `Client` and follow the same pattern:
- `connect()` builds `ReferenceConfig<GenericService>`, injects proxy URL params, sets `transporter=proxy-netty` when proxy is configured
- `send()` calls `GenericService.$invoke(methodName, parameterTypes, payload)`
- `disconnect()` calls `ref.destroy()`
- Same reconnect, error handling, and result conversion logic as current `DubboClient`

Both live in the `noone-transport` module alongside existing clients. Both share `DubboClientConfig`.

#### Proxy URL Parameter Wiring

When `config.getProxy() != null`, the client merges proxy parameters into the existing `ReferenceConfig` parameters alongside any other params (e.g., the current `"reconnect"="false"`):

```java
Map<String, String> params = new HashMap<>();
params.put("reconnect", "false");
if (config.getProxy() != null) {
    params.put("transporter", "proxy-netty");
    params.put("proxy.type", config.getProxy().getType());
    params.put("proxy.host", config.getProxy().getHost());
    params.put("proxy.port", String.valueOf(config.getProxy().getPort()));
    if (config.getProxy().hasAuth()) {
        params.put("proxy.username", config.getProxy().getUsername());
        params.put("proxy.password", config.getProxy().getPassword());
    }
}
ref.setParameters(params);
```

For `ApacheDubboClient`, this uses `org.apache.dubbo.config.ReferenceConfig.setParameters(Map)`. For `AlibabaDubboClient`, the equivalent `com.alibaba.dubbo.config.ReferenceConfig.setParameters(Map)`. Both accept arbitrary key-value pairs that get embedded into the Dubbo URL.

### 3. Proxy Injection -- Netty Protocols (`dubbo://`, `tri://`)

Custom `Transporter` SPI wraps the default Netty transport and adds proxy handlers to the channel pipeline.

**Class hierarchy (Apache Dubbo 3.x):**

```
org.apache.dubbo.remoting.Transporter (SPI interface)
  └── NettyTransporter (default, key="netty")
  └── ProxyNettyTransporter (custom, key="proxy-netty")
        └── connect() → returns ProxyNettyClient

ProxyNettyClient extends o.a.d.remoting.transport.netty4.NettyClient
  └── overrides doOpen()
      └── inserts Socks5ProxyHandler or HttpProxyHandler as FIRST handler in Netty pipeline
```

**Class hierarchy (Alibaba Dubbo 2.x):** same pattern under `com.alibaba.dubbo.*` packages. Note: Alibaba Dubbo 2.x may use `com.alibaba.dubbo.remoting.transport.netty4.NettyClient` (Netty 4) or `com.alibaba.dubbo.remoting.transport.netty.NettyClient` (Netty 3) depending on the version. During implementation, inspect the default `Transporter` SPI in `com.alibaba:dubbo:2.6.12` to identify the correct Netty client superclass.

Netty proxy handlers are transparent -- they handle SOCKS5 handshake or HTTP CONNECT tunnel before the Dubbo codec/handler pipeline runs. The rest of the Dubbo stack operates unchanged above the proxy tunnel.

For `tri://` (Triple protocol in Dubbo 3.x): uses HTTP/2 over Netty. If Triple shares the `Transporter` SPI, the same `ProxyNettyTransporter` works. If Triple has its own bootstrap path, a separate extension point may be needed (to be verified during implementation).

**Proxy handler creation** (shared utility):

```java
public final class DubboProxyUtils {
    public static ChannelHandler createNettyProxyHandler(URL url) {
        String type = url.getParameter("proxy.type");
        String host = url.getParameter("proxy.host");
        int port = url.getParameter("proxy.port", 0);
        String username = url.getParameter("proxy.username");
        String password = url.getParameter("proxy.password");

        InetSocketAddress proxyAddr = new InetSocketAddress(host, port);
        if ("SOCKS5".equalsIgnoreCase(type)) {
            return username != null
                ? new Socks5ProxyHandler(proxyAddr, username, password)
                : new Socks5ProxyHandler(proxyAddr);
        }
        return username != null
            ? new HttpProxyHandler(proxyAddr, username, password)
            : new HttpProxyHandler(proxyAddr);
    }
}
```

### 4. Proxy Injection -- HTTP Protocols (`hessian://`, `http://`)

HTTP-based Dubbo protocols use `java.net.URLConnection` under the hood.

**Hessian protocol (`hessian://`):**

Dubbo's `HessianProtocol` creates Caucho's `HessianProxyFactory`, which opens connections via `HessianURLConnectionFactory`. A custom connection factory intercepts this to open connections with a `java.net.Proxy`:

```java
public class ProxyHessianConnectionFactory extends HessianURLConnectionFactory {
    private final Proxy proxy;
    private final String username;
    private final String password;

    @Override
    public HessianConnection open(URL url) throws IOException {
        URLConnection conn = url.openConnection(proxy);
        if (username != null) {
            String encoded = java.util.Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Proxy-Authorization", "Basic " + encoded);
        }
        return new HessianURLConnection(url, conn);
    }
}
```

**Must-verify:** The claim that Alibaba Dubbo 2.x uses Caucho `com.caucho.hessian.client.*` for `hessian://` RPC (not `hessian-lite`) must be confirmed against `com.alibaba:dubbo-rpc-hessian:2.6.12` sources during implementation. If Alibaba's hessian RPC extension uses `hessian-lite` classes instead, a separate `AlibabaProxyHessianConnectionFactory` extending `com.alibaba.com.caucho.hessian.client.HessianURLConnectionFactory` is needed.

A custom `Protocol` SPI (`ProxyHessianProtocol`) extends the default `HessianProtocol`, registered with key `hessian` to replace the default. When proxy URL params are present, it injects `ProxyHessianConnectionFactory` on the `HessianProxyFactory`. When no proxy params exist, it delegates to default behavior unchanged. Since this replacement is global to the classloader, the fall-through behavior is critical -- all non-proxied hessian connections must remain unaffected. Dubbo's SPI extension loading order is deterministic (custom `META-INF/dubbo/` overrides `META-INF/dubbo/internal/`), so our registration reliably takes precedence.

**HTTP protocol (`http://`):**

Apache Dubbo 3.x's `http://` protocol uses `org.apache.dubbo.rpc.protocol.http.HttpProtocol`, which relies on Spring's `HttpInvokerProxyFactoryBean` or similar HTTP-based RPC. Proxy injection follows the same `URLConnection` interception pattern. A custom `ProxyHttpProtocol` extends the default `HttpProtocol` and injects proxy-aware connection handling when proxy URL params are present. This requires an additional SPI registration for the `http` protocol key.

**SOCKS5 for HTTP protocols:** `java.net.Proxy.Type.SOCKS` works natively with `URLConnection`. For SOCKS auth on Java 8 (global `Authenticator`), use a thread-local `Authenticator` pattern.

### 5. SPI Registration

Each Dubbo SPI extension is registered in a **separate file** under `src/main/resources/META-INF/dubbo/`. The filename is the fully-qualified SPI interface name. The file body contains `name=implementation` lines.

**File: `META-INF/dubbo/org.apache.dubbo.remoting.Transporter`**
```
proxy-netty=com.reajason.noone.core.client.dubbo.apache.ProxyNettyTransporter
```

**File: `META-INF/dubbo/org.apache.dubbo.rpc.Protocol`**
```
hessian=com.reajason.noone.core.client.dubbo.apache.ProxyHessianProtocol
http=com.reajason.noone.core.client.dubbo.apache.ProxyHttpProtocol
```

**File: `META-INF/dubbo/com.alibaba.dubbo.remoting.Transporter`**
```
proxy-netty=com.reajason.noone.core.client.dubbo.alibaba.ProxyNettyTransporter
```

**File: `META-INF/dubbo/com.alibaba.dubbo.rpc.Protocol`**
```
hessian=com.reajason.noone.core.client.dubbo.alibaba.ProxyHessianProtocol
```

**Activation rules:**

| SPI | When activated | Behavior |
|-----|---------------|----------|
| `ProxyNettyTransporter` | Client sets `transporter=proxy-netty` URL param (only when proxy configured) | Creates `ProxyNettyClient` with proxy handlers in Netty pipeline |
| `ProxyHessianProtocol` | Always active (replaces default `hessian` key) | Checks for proxy URL params; injects proxy connection factory if present, falls through to default otherwise |
| `ProxyHttpProtocol` | Always active (replaces default `http` key, Apache only) | Same pattern as ProxyHessianProtocol |

### 6. Package Structure

```
com.reajason.noone.core.client/
├── Client.java                             (existing, unchanged)
├── DubboClientConfig.java                  (modified: +ProxyConfig field)
├── ProxyConfig.java                        (existing, unchanged)
├── ApacheDubboClient.java                  (new)
├── AlibabaDubboClient.java                 (new)
├── DubboClient.java                        (deleted)
│
├── dubbo/
│   ├── DubboProxyUtils.java                (shared: Netty proxy handler factory)
│   ├── ProxyHessianConnectionFactory.java  (shared: Caucho com.caucho.hessian.client.*)
│   │
│   ├── apache/
│   │   ├── ProxyNettyTransporter.java
│   │   ├── ProxyNettyClient.java
│   │   ├── ProxyHessianProtocol.java
│   │   └── ProxyHttpProtocol.java
│   │
│   └── alibaba/
│       ├── ProxyNettyTransporter.java
│       ├── ProxyNettyClient.java
│       └── ProxyHessianProtocol.java

src/main/resources/META-INF/dubbo/
├── org.apache.dubbo.remoting.Transporter
├── org.apache.dubbo.rpc.Protocol
├── com.alibaba.dubbo.remoting.Transporter
└── com.alibaba.dubbo.rpc.Protocol
```

### 7. Dependency Changes

**New dependencies** in `noone-transport/build.gradle.kts`:

| Artifact | Purpose |
|----------|---------|
| `com.alibaba:dubbo:2.6.12` | Alibaba Dubbo 2.x (exclude Netty, Spring transitives) |
| `com.alibaba:dubbo-rpc-hessian:2.6.12` | Hessian protocol for 2.x (exclude Netty) |
| `io.netty:netty-handler-proxy` | `Socks5ProxyHandler`, `HttpProxyHandler` |

**Existing** (unchanged): `org.apache.dubbo:dubbo:3.3.6`, `dubbo-rpc-hessian:3.3.0`, `dubbo-remoting-http:3.3.0-beta.2`.

**Version management:** The `netty-handler-proxy` version must match the Netty version already pulled transitively by `org.apache.dubbo:dubbo:3.3.6`. Add the version to `gradle/libs.versions.toml` aligned with Dubbo 3.x's Netty transitive (check via `./gradlew :noone-transport:dependencies`). Do NOT reference a Dubbo BOM (the project does not use one).

**Classpath coexistence:** Alibaba Dubbo (`com.alibaba.dubbo.*`) and Apache Dubbo (`org.apache.dubbo.*`) have non-overlapping Java package namespaces, so no class collisions. However, they may pull conflicting versions of shared transitive dependencies (Javassist, Hessian, logging, Curator, etc.). Mitigations:
- Exclude Netty, Spring, and other framework transitives from the Alibaba dependency
- Use Gradle `resolutionStrategy` or `constraints` to force consistent versions where conflicts arise
- Add a smoke test verifying that both `ApacheDubboClient` and `AlibabaDubboClient` can be constructed and invoked in the same JVM

### 8. Upstream Impact

Changes are needed across three layers:

**`noone-server` / `ClientFactory`:**
- `ClientFactory.create()` currently calls `new DubboClient(shell.getUrl(), config)` for `ProtocolType.DUBBO`. This must branch to `ApacheDubboClient` or `AlibabaDubboClient` based on a new dubbo stack selector.
- `ClientFactory.buildDubboClientConfig()` currently does **not** read `proxyUrl` from `shell.getClientConfig()`. It must be updated to parse `proxyUrl` → `ProxyConfig` (using the existing `parseProxyUrl()` helper) and set it on `DubboClientConfig.proxy`, matching the pattern already used by HTTP/WebSocket builders. SOCKS4 type should be rejected at this boundary with a clear error.

**`noone-core` / profile config:**
- `DubboProtocolConfig` needs a new field (e.g., `dubboStack` with values `APACHE` / `ALIBABA`) to indicate which Dubbo client to instantiate. This propagates through `Profile` → `ClientFactory`.
- Alternatively, `ProtocolType` could be extended with `DUBBO_APACHE` / `DUBBO_ALIBABA` variants, but a field on `DubboProtocolConfig` is less disruptive since `ProtocolType.DUBBO` is already used in Jackson polymorphic deserialization (`@JsonSubTypes`).

**`noone-web` / UI:**
- Dubbo profile forms need a dubbo stack selector (Apache 3.x / Alibaba 2.x).
- Dubbo shell config forms need a proxy URL input field, consistent with the existing HTTP/WebSocket shell config UI.

### 9. Testing

**Unit tests** (`noone-transport/src/test/`):

| Test | Verifies |
|------|----------|
| `DubboProxyUtilsTest` | Correct Netty handler type for SOCKS5/HTTP, auth/no-auth, missing params |
| `ProxyHessianConnectionFactoryTest` | `openConnection(proxy)` called with correct proxy, auth header set |
| `ApacheDubboClientTest` | Proxy params embedded in Dubbo URL, `transporter=proxy-netty` added, no params when no proxy |
| `AlibabaDubboClientTest` | Same as above for Alibaba |

**Integration tests** (Docker Compose + Testcontainers):

Infrastructure:
- SOCKS5 proxy container (e.g., `serjs/go-socks5-proxy`)
- HTTP proxy container (e.g., `sameersbn/squid`)
- Dubbo provider exposing a test service on each protocol

Test matrix: client × protocol × proxy type × auth = full coverage. Follows existing `noone-test/server-compatibility` patterns.

**Classpath smoke test:** Verify both `ApacheDubboClient` and `AlibabaDubboClient` can be constructed and invoked in the same JVM without classpath conflicts.

## Open Questions

1. **Triple protocol bootstrap path** -- does `tri://` in Dubbo 3.x share the `Transporter` SPI, or does it have its own Netty bootstrap? Needs verification during implementation. If Triple has a separate path, a dedicated SPI extension is needed.
2. **SOCKS auth on Java 8** -- `java.net.Authenticator` is global. Thread-local pattern may have edge cases under concurrent connections with different SOCKS credentials. Verify in integration tests.
3. **Alibaba Dubbo 2.6.12 + Netty version** -- confirm `com.alibaba:dubbo:2.6.12` works with the Netty version from Apache Dubbo 3.x after excluding Alibaba's transitive Netty. Also verify broader transitive dependency compatibility (Javassist, Curator, etc.) by running `./gradlew :noone-transport:dependencies` and resolving conflicts.
4. **Alibaba Hessian RPC library** -- verify whether `com.alibaba:dubbo-rpc-hessian:2.6.12` uses Caucho `com.caucho.hessian.client.*` or the `hessian-lite` fork for the `hessian://` RPC protocol. This determines whether `ProxyHessianConnectionFactory` can be shared or needs a separate Alibaba variant.
