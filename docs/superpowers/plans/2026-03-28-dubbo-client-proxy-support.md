# Dubbo Client Proxy Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add HTTP/SOCKS5 proxy support to Dubbo clients and split into ApacheDubboClient (3.x) and AlibabaDubboClient (2.x).

**Architecture:** Dubbo SPI extensions inject proxy handlers into the transport layer. For Netty-based protocols (dubbo://, tri://), custom `Transporter` adds `Socks5ProxyHandler`/`HttpProxyHandler` to the Netty pipeline. For HTTP-based protocols (hessian://, http://), custom `Protocol` SPI injects a proxy-aware `HessianConnectionFactory`. Both clients share `DubboClientConfig` with a new `ProxyConfig` field.

**Tech Stack:** Apache Dubbo 3.3.6, Alibaba Dubbo 2.6.12, Netty (netty-handler-proxy), Caucho Hessian, JUnit 5

**Spec:** `docs/superpowers/specs/2026-03-28-dubbo-client-proxy-support-design.md`

---

## File Map

### New Files
| File | Responsibility |
|------|---------------|
| `noone-transport/src/main/java/.../dubbo/DubboProxyUtils.java` | Shared utility: create Netty proxy handlers from Dubbo URL params |
| `noone-transport/src/main/java/.../dubbo/ProxyHessianConnectionFactory.java` | Shared: proxy-aware Caucho HessianURLConnectionFactory |
| `noone-transport/src/main/java/.../dubbo/apache/ProxyNettyTransporter.java` | Apache Dubbo Transporter SPI |
| `noone-transport/src/main/java/.../dubbo/apache/ProxyNettyClient.java` | Apache Dubbo NettyClient with proxy pipeline |
| `noone-transport/src/main/java/.../dubbo/apache/ProxyHessianProtocol.java` | Apache Dubbo Protocol SPI for hessian |
| `noone-transport/src/main/java/.../dubbo/apache/ProxyHttpProtocol.java` | Apache Dubbo Protocol SPI for http |
| `noone-transport/src/main/java/.../dubbo/alibaba/ProxyNettyTransporter.java` | Alibaba Dubbo Transporter SPI |
| `noone-transport/src/main/java/.../dubbo/alibaba/ProxyNettyClient.java` | Alibaba Dubbo NettyClient with proxy pipeline |
| `noone-transport/src/main/java/.../dubbo/alibaba/ProxyHessianProtocol.java` | Alibaba Dubbo Protocol SPI for hessian |
| `noone-transport/src/main/java/.../ApacheDubboClient.java` | Client impl using org.apache.dubbo (3.x) |
| `noone-transport/src/main/java/.../AlibabaDubboClient.java` | Client impl using com.alibaba.dubbo (2.x) |
| `noone-transport/src/main/resources/META-INF/dubbo/org.apache.dubbo.remoting.Transporter` | Apache Transporter SPI registration |
| `noone-transport/src/main/resources/META-INF/dubbo/org.apache.dubbo.rpc.Protocol` | Apache Protocol SPI registration |
| `noone-transport/src/main/resources/META-INF/dubbo/com.alibaba.dubbo.remoting.Transporter` | Alibaba Transporter SPI registration |
| `noone-transport/src/main/resources/META-INF/dubbo/com.alibaba.dubbo.rpc.Protocol` | Alibaba Protocol SPI registration |
| `noone-transport/src/test/java/.../DubboClientConfigTest.java` | Unit tests for config with ProxyConfig |
| `noone-transport/src/test/java/.../dubbo/DubboProxyUtilsTest.java` | Unit tests for proxy handler creation |
| `noone-transport/src/test/java/.../dubbo/ProxyHessianConnectionFactoryTest.java` | Unit tests for proxy connection factory |
| `noone-transport/src/test/java/.../ApacheDubboClientTest.java` | Unit tests for URL param wiring |
| `noone-transport/src/test/java/.../AlibabaDubboClientTest.java` | Unit tests for URL param wiring |
| `noone-transport/src/test/java/.../DubboClientClasspathTest.java` | Classpath coexistence smoke test |

*All `.../` paths expand to `com/reajason/noone/core/client/`.*

### Modified Files
| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Add alibaba-dubbo, netty-handler-proxy versions and library entries |
| `noone-transport/build.gradle.kts` | Add alibaba-dubbo, netty-handler-proxy dependencies |
| `noone-transport/src/main/java/.../DubboClientConfig.java` | Add `ProxyConfig proxy` field |
| `noone-core/src/main/java/.../profile/config/DubboProtocolConfig.java` | Add `dubboStack` field |
| `noone-server/src/main/java/.../shell/ClientFactory.java` | Branch on dubboStack, wire proxy into DubboClientConfig |

### Deleted Files
| File | Reason |
|------|--------|
| `noone-transport/src/main/java/.../DubboClient.java` | Replaced by ApacheDubboClient + AlibabaDubboClient |

---

## Task 1: Add Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `noone-transport/build.gradle.kts`

- [ ] **Step 1: Add version catalog entries**

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
alibaba-dubbo = "2.6.12"
netty-handler-proxy = "4.1.118.Final"
```

> **Note:** The netty-handler-proxy version must match the Netty version pulled by `org.apache.dubbo:dubbo:3.3.6`. Run `./gradlew :noone-transport:dependencies | grep netty-common` to find the exact version. Adjust `4.1.118.Final` to match.

Add to `[libraries]`:

```toml
alibaba-dubbo = { module = "com.alibaba:dubbo", version.ref = "alibaba-dubbo" }
alibaba-dubbo-rpc-hessian = { module = "com.alibaba:dubbo-rpc-hessian", version.ref = "alibaba-dubbo" }
netty-handler-proxy = { module = "io.netty:netty-handler-proxy", version.ref = "netty-handler-proxy" }
```

- [ ] **Step 2: Add dependencies to build.gradle.kts**

In `noone-transport/build.gradle.kts`, add to the `dependencies` block:

```kotlin
implementation(libs.alibaba.dubbo) {
    exclude(group = "io.netty")
    exclude(group = "org.springframework")
    exclude(group = "org.javassist")
}
implementation(libs.alibaba.dubbo.rpc.hessian) {
    exclude(group = "io.netty")
}
implementation(libs.netty.handler.proxy)
```

- [ ] **Step 3: Verify dependency resolution**

Run: `./gradlew :noone-transport:dependencies --configuration compileClasspath | head -80`
Expected: Both `com.alibaba:dubbo:2.6.12` and `org.apache.dubbo:dubbo:3.3.6` appear. No resolution errors. `io.netty:netty-handler-proxy` version matches other Netty modules.

- [ ] **Step 4: Resolve classpath conflicts if any**

Run: `./gradlew :noone-transport:dependencies --configuration compileClasspath`

Check for duplicate/conflicting versions of shared transitives (Javassist, Curator, logging, etc.). If conflicts appear, add resolution rules to `noone-transport/build.gradle.kts`:

```kotlin
configurations.all {
    resolutionStrategy {
        // Force consistent versions where Alibaba and Apache Dubbo conflict
        // e.g.: force("org.javassist:javassist:3.29.2-GA")
    }
}
```

Re-run dependency check to confirm resolution.

- [ ] **Step 5: Verify compile**

Run: `./gradlew :noone-transport:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml noone-transport/build.gradle.kts
git commit -m "chore: add Alibaba Dubbo 2.x and netty-handler-proxy dependencies"
```

---

## Task 2: Update DubboClientConfig

**Files:**
- Modify: `noone-transport/src/main/java/com/reajason/noone/core/client/DubboClientConfig.java`
- Test: `noone-transport/src/test/java/com/reajason/noone/core/client/DubboClientConfigTest.java`

- [ ] **Step 1: Write the failing test**

Create `noone-transport/src/test/java/com/reajason/noone/core/client/DubboClientConfigTest.java`:

```java
package com.reajason.noone.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DubboClientConfigTest {

    @Test
    void defaultConfigHasNoProxy() {
        DubboClientConfig config = DubboClientConfig.builder().build();
        assertNull(config.getProxy());
    }

    @Test
    void configWithProxy() {
        ProxyConfig proxy = ProxyConfig.builder()
                .type("SOCKS5")
                .host("127.0.0.1")
                .port(1080)
                .build();
        DubboClientConfig config = DubboClientConfig.builder()
                .proxy(proxy)
                .build();
        assertNotNull(config.getProxy());
        assertEquals("SOCKS5", config.getProxy().getType());
        assertEquals("127.0.0.1", config.getProxy().getHost());
        assertEquals(1080, config.getProxy().getPort());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :noone-transport:test --tests "com.reajason.noone.core.client.DubboClientConfigTest" --info`
Expected: FAIL — `DubboClientConfig` has no `proxy` field, so `getProxy()` doesn't exist.

- [ ] **Step 3: Add ProxyConfig field to DubboClientConfig**

In `noone-transport/src/main/java/com/reajason/noone/core/client/DubboClientConfig.java`, add after the `readTimeoutMs` field:

```java
private ProxyConfig proxy;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :noone-transport:test --tests "com.reajason.noone.core.client.DubboClientConfigTest" --info`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add noone-transport/src/main/java/com/reajason/noone/core/client/DubboClientConfig.java \
       noone-transport/src/test/java/com/reajason/noone/core/client/DubboClientConfigTest.java
git commit -m "feat: add ProxyConfig field to DubboClientConfig"
```

---

## Task 3: Shared Utility — DubboProxyUtils

**Files:**
- Create: `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/DubboProxyUtils.java`
- Test: `noone-transport/src/test/java/com/reajason/noone/core/client/dubbo/DubboProxyUtilsTest.java`

- [ ] **Step 1: Write the failing tests**

Create `noone-transport/src/test/java/com/reajason/noone/core/client/dubbo/DubboProxyUtilsTest.java`:

```java
package com.reajason.noone.core.client.dubbo;

import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class DubboProxyUtilsTest {

    @Test
    void createSocks5HandlerWithoutAuth() {
        var handler = DubboProxyUtils.createNettyProxyHandler(
                "SOCKS5", "127.0.0.1", 1080, null, null);
        assertInstanceOf(Socks5ProxyHandler.class, handler);
        assertEquals(new InetSocketAddress("127.0.0.1", 1080),
                handler.proxyAddress());
    }

    @Test
    void createSocks5HandlerWithAuth() {
        var handler = DubboProxyUtils.createNettyProxyHandler(
                "SOCKS5", "127.0.0.1", 1080, "user", "pass");
        assertInstanceOf(Socks5ProxyHandler.class, handler);
        Socks5ProxyHandler socks = (Socks5ProxyHandler) handler;
        assertEquals("user", socks.username());
    }

    @Test
    void createHttpHandlerWithoutAuth() {
        var handler = DubboProxyUtils.createNettyProxyHandler(
                "HTTP", "proxy.example.com", 8080, null, null);
        assertInstanceOf(HttpProxyHandler.class, handler);
        assertEquals(new InetSocketAddress("proxy.example.com", 8080),
                handler.proxyAddress());
    }

    @Test
    void createHttpHandlerWithAuth() {
        var handler = DubboProxyUtils.createNettyProxyHandler(
                "HTTP", "proxy.example.com", 8080, "user", "pass");
        assertInstanceOf(HttpProxyHandler.class, handler);
    }

    @Test
    void typeIsCaseInsensitive() {
        var handler = DubboProxyUtils.createNettyProxyHandler(
                "socks5", "127.0.0.1", 1080, null, null);
        assertInstanceOf(Socks5ProxyHandler.class, handler);
    }

    @Test
    void defaultsToHttpProxy() {
        var handler = DubboProxyUtils.createNettyProxyHandler(
                "UNKNOWN", "127.0.0.1", 8080, null, null);
        assertInstanceOf(HttpProxyHandler.class, handler);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :noone-transport:test --tests "com.reajason.noone.core.client.dubbo.DubboProxyUtilsTest" --info`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Implement DubboProxyUtils**

Create `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/DubboProxyUtils.java`:

```java
package com.reajason.noone.core.client.dubbo;

import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;

import java.net.InetSocketAddress;

public final class DubboProxyUtils {

    private DubboProxyUtils() {
    }

    public static ProxyHandler createNettyProxyHandler(
            String type, String host, int port, String username, String password) {
        InetSocketAddress proxyAddr = new InetSocketAddress(host, port);
        if ("SOCKS5".equalsIgnoreCase(type)) {
            if (username != null && !username.isEmpty()) {
                return new Socks5ProxyHandler(proxyAddr, username, password);
            }
            return new Socks5ProxyHandler(proxyAddr);
        }
        if (username != null && !username.isEmpty()) {
            return new HttpProxyHandler(proxyAddr, username, password);
        }
        return new HttpProxyHandler(proxyAddr);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :noone-transport:test --tests "com.reajason.noone.core.client.dubbo.DubboProxyUtilsTest" --info`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/DubboProxyUtils.java \
       noone-transport/src/test/java/com/reajason/noone/core/client/dubbo/DubboProxyUtilsTest.java
git commit -m "feat: add DubboProxyUtils for creating Netty proxy handlers"
```

---

## Task 4: Shared Utility — ProxyHessianConnectionFactory

**Files:**
- Create: `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/ProxyHessianConnectionFactory.java`

> **Investigation required:** Before writing this class, verify which Hessian client library `com.alibaba:dubbo-rpc-hessian:2.6.12` uses. Check:
> ```bash
> ./gradlew :noone-transport:dependencies --configuration compileClasspath | grep -i hessian
> ```
> If Alibaba uses `com.caucho:hessian` (same as Apache), the shared factory works. If it uses `hessian-lite` (`com.alibaba.com.caucho.hessian.*`), you'll need a separate `AlibabaProxyHessianConnectionFactory` in the `alibaba/` package.

- [ ] **Step 1: Verify Hessian library dependency**

Run: `./gradlew :noone-transport:dependencies --configuration compileClasspath | grep -i hessian`
Check which Hessian artifact Alibaba Dubbo pulls in. Record the result — it determines whether the shared factory works for both.

- [ ] **Step 2: Implement ProxyHessianConnectionFactory**

Create `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/ProxyHessianConnectionFactory.java`:

```java
package com.reajason.noone.core.client.dubbo;

import com.caucho.hessian.client.HessianConnection;
import com.caucho.hessian.client.HessianURLConnectionFactory;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ProxyHessianConnectionFactory extends HessianURLConnectionFactory {

    private final Proxy proxy;
    private final String proxyAuthHeader;

    public ProxyHessianConnectionFactory(Proxy proxy, String username, String password) {
        this.proxy = proxy;
        if (username != null && !username.isEmpty()) {
            String encoded = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            this.proxyAuthHeader = "Basic " + encoded;
        } else {
            this.proxyAuthHeader = null;
        }
    }

    @Override
    public HessianConnection open(URL url) throws IOException {
        URLConnection conn = url.openConnection(proxy);
        if (proxyAuthHeader != null) {
            conn.setRequestProperty("Proxy-Authorization", proxyAuthHeader);
        }
        return new com.caucho.hessian.client.HessianURLConnection(url, conn);
    }
}
```

> **If step 1 revealed Alibaba uses hessian-lite:** Also create `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/alibaba/AlibabaProxyHessianConnectionFactory.java` with the same logic but extending `com.alibaba.com.caucho.hessian.client.HessianURLConnectionFactory`.

- [ ] **Step 3: Investigate SOCKS5 auth for URLConnection**

When `proxy.type` is SOCKS5 with credentials, `java.net.URLConnection` needs `java.net.Authenticator` for auth. On Java 8, `Authenticator` is global. Implement a thread-local `Authenticator` that returns SOCKS credentials during `open()` and clears them afterward. If this proves unreliable under concurrency, document it as a known limitation and consider requiring HTTP proxy for authenticated hessian/http protocols.

- [ ] **Step 4: Write ProxyHessianConnectionFactory test**

Create `noone-transport/src/test/java/com/reajason/noone/core/client/dubbo/ProxyHessianConnectionFactoryTest.java`:

```java
package com.reajason.noone.core.client.dubbo;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class ProxyHessianConnectionFactoryTest {

    @Test
    void constructsWithHttpProxy() {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.example.com", 8080));
        ProxyHessianConnectionFactory factory = new ProxyHessianConnectionFactory(proxy, null, null);
        assertNotNull(factory);
    }

    @Test
    void constructsWithSocksProxy() {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 1080));
        ProxyHessianConnectionFactory factory = new ProxyHessianConnectionFactory(proxy, null, null);
        assertNotNull(factory);
    }

    @Test
    void constructsWithAuthenticatedProxy() {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.example.com", 8080));
        ProxyHessianConnectionFactory factory = new ProxyHessianConnectionFactory(proxy, "user", "pass");
        assertNotNull(factory);
    }
}
```

- [ ] **Step 5: Verify compile and run tests**

Run: `./gradlew :noone-transport:compileJava && ./gradlew :noone-transport:test --tests "com.reajason.noone.core.client.dubbo.ProxyHessianConnectionFactoryTest"`
Expected: BUILD SUCCESSFUL, tests PASS

- [ ] **Step 6: Commit**

```bash
git add noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/ProxyHessianConnectionFactory.java \
       noone-transport/src/test/java/com/reajason/noone/core/client/dubbo/ProxyHessianConnectionFactoryTest.java
git commit -m "feat: add proxy-aware HessianConnectionFactory with tests"
```

---

## Task 5: Apache Dubbo SPI — ProxyNettyTransporter + ProxyNettyClient

**Files:**
- Create: `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/apache/ProxyNettyTransporter.java`
- Create: `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/apache/ProxyNettyClient.java`
- Create: `noone-transport/src/main/resources/META-INF/dubbo/org.apache.dubbo.remoting.Transporter`

> **Investigation required:** Before implementing, examine the Apache Dubbo 3.3.6 source for `NettyTransporter` and `NettyClient`:
> - Find the `doOpen()` method in `org.apache.dubbo.remoting.transport.netty4.NettyClient`
> - Identify where the Netty `Bootstrap` channel initializer sets up the pipeline
> - Determine the exact constructor signature of `NettyClient`
> Use IDE "Go to Definition" or browse the Dubbo 3.3.6 source JAR.

- [ ] **Step 1: Study NettyTransporter and NettyClient API**

Open the Dubbo 3.3.6 source (in IDE or via JAR) and examine:
1. `org.apache.dubbo.remoting.Transporter` interface — note the `connect()` method signature
2. `org.apache.dubbo.remoting.transport.netty4.NettyTransporter` — note how it creates `NettyClient`
3. `org.apache.dubbo.remoting.transport.netty4.NettyClient` — note `doOpen()`, the `Bootstrap` setup, and the `ChannelInitializer` pipeline

Record the exact method signatures and constructor parameters.

- [ ] **Step 2: Implement ProxyNettyClient**

Create `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/apache/ProxyNettyClient.java`:

```java
package com.reajason.noone.core.client.dubbo.apache;

import com.reajason.noone.core.client.dubbo.DubboProxyUtils;
import io.netty.handler.proxy.ProxyHandler;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.transport.netty4.NettyClient;

/**
 * NettyClient subclass that adds a proxy handler to the Netty pipeline.
 * The proxy handler is inserted as the first handler so it tunnels
 * the connection through HTTP CONNECT or SOCKS5 before Dubbo codecs run.
 */
public class ProxyNettyClient extends NettyClient {

    public ProxyNettyClient(URL url, ChannelHandler handler) throws RemotingException {
        super(url, handler);
    }

    @Override
    protected void doOpen() throws Throwable {
        super.doOpen();
        // After super.doOpen() sets up the Bootstrap, we need to
        // modify the channel initializer to prepend the proxy handler.
        // Implementation depends on how NettyClient exposes the Bootstrap.
        // See investigation step — adapt based on actual Dubbo 3.3.6 API.
    }

    private ProxyHandler createProxyHandler() {
        URL url = getUrl();
        String type = url.getParameter("proxy.type");
        if (type == null || type.isEmpty()) {
            return null;
        }
        return DubboProxyUtils.createNettyProxyHandler(
                type,
                url.getParameter("proxy.host"),
                url.getParameter("proxy.port", 0),
                url.getParameter("proxy.username"),
                url.getParameter("proxy.password")
        );
    }
}
```

> **Important:** The `doOpen()` override is a skeleton. During implementation, you must inspect how `NettyClient.doOpen()` configures the `Bootstrap` and determine the correct way to prepend the proxy handler. Common patterns:
> - If `NettyClient` has a `bootstrap` field, override the `ChannelInitializer` to add the proxy handler first
> - If `NettyClient` uses a `initChannel(SocketChannel)` callback, override that
> - You may need to use reflection if no clean override point exists

- [ ] **Step 3: Implement ProxyNettyTransporter**

Create `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/apache/ProxyNettyTransporter.java`:

```java
package com.reajason.noone.core.client.dubbo.apache;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Client;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.Server;
import org.apache.dubbo.remoting.Transporter;

public class ProxyNettyTransporter implements Transporter {

    @Override
    public Client connect(URL url, ChannelHandler handler) throws RemotingException {
        return new ProxyNettyClient(url, handler);
    }

    @Override
    public Server bind(URL url, ChannelHandler handler) throws RemotingException {
        throw new UnsupportedOperationException("ProxyNettyTransporter does not support server binding");
    }
}
```

- [ ] **Step 4: Register SPI**

Create `noone-transport/src/main/resources/META-INF/dubbo/org.apache.dubbo.remoting.Transporter`:

```
proxy-netty=com.reajason.noone.core.client.dubbo.apache.ProxyNettyTransporter
```

- [ ] **Step 5: Verify compile**

Run: `./gradlew :noone-transport:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/apache/ \
       noone-transport/src/main/resources/META-INF/dubbo/org.apache.dubbo.remoting.Transporter
git commit -m "feat: add Apache Dubbo ProxyNettyTransporter SPI extension"
```

---

## Task 6: Apache Dubbo SPI — ProxyHessianProtocol + ProxyHttpProtocol

**Files:**
- Create: `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/apache/ProxyHessianProtocol.java`
- Create: `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/apache/ProxyHttpProtocol.java`
- Create: `noone-transport/src/main/resources/META-INF/dubbo/org.apache.dubbo.rpc.Protocol`

> **Investigation required:** Examine `org.apache.dubbo.rpc.protocol.hessian.HessianProtocol` source to find where `HessianProxyFactory` is created and how to inject a custom `HessianConnectionFactory`. Look for `factory.setConnectionFactory()` or the method that creates the proxy object.

- [ ] **Step 1: Study HessianProtocol and HttpProtocol API**

Examine the Dubbo 3.x sources:
1. `org.apache.dubbo.rpc.protocol.hessian.HessianProtocol` — how does it create the `HessianProxyFactory`? Where can we inject `setConnectionFactory()`?
2. `org.apache.dubbo.rpc.protocol.http.HttpProtocol` — how does it create HTTP connections?

Record findings before implementing.

- [ ] **Step 2: Implement ProxyHessianProtocol**

Create `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/apache/ProxyHessianProtocol.java`:

```java
package com.reajason.noone.core.client.dubbo.apache;

import com.reajason.noone.core.client.dubbo.ProxyHessianConnectionFactory;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.protocol.hessian.HessianProtocol;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * Extends the default HessianProtocol to inject proxy-aware connection factory
 * when proxy URL parameters are present. Falls through to default behavior otherwise.
 */
public class ProxyHessianProtocol extends HessianProtocol {

    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        // If no proxy params, delegate to default
        String proxyType = url.getParameter("proxy.type");
        if (proxyType == null || proxyType.isEmpty()) {
            return super.refer(type, url);
        }

        // Build java.net.Proxy from URL params
        String proxyHost = url.getParameter("proxy.host");
        int proxyPort = url.getParameter("proxy.port", 0);
        Proxy.Type javaProxyType = "SOCKS5".equalsIgnoreCase(proxyType)
                ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        Proxy proxy = new Proxy(javaProxyType, new InetSocketAddress(proxyHost, proxyPort));

        String username = url.getParameter("proxy.username");
        String password = url.getParameter("proxy.password");

        // Inject proxy connection factory
        // Implementation depends on how HessianProtocol creates HessianProxyFactory.
        // Adapt based on investigation in step 1.
        return super.refer(type, url);
    }
}
```

> **Adapt during implementation:** The `refer()` override is a skeleton. You need to find the hook point to call `hessianProxyFactory.setConnectionFactory(new ProxyHessianConnectionFactory(...))`. This may require copying some logic from `HessianProtocol.refer()` rather than calling `super.refer()`.

- [ ] **Step 3: Implement ProxyHttpProtocol**

Create `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/apache/ProxyHttpProtocol.java` following the same pattern as ProxyHessianProtocol but extending `org.apache.dubbo.rpc.protocol.http.HttpProtocol`.

- [ ] **Step 4: Register SPI**

Create `noone-transport/src/main/resources/META-INF/dubbo/org.apache.dubbo.rpc.Protocol`:

```
hessian=com.reajason.noone.core.client.dubbo.apache.ProxyHessianProtocol
http=com.reajason.noone.core.client.dubbo.apache.ProxyHttpProtocol
```

- [ ] **Step 5: Verify compile**

Run: `./gradlew :noone-transport:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/apache/ProxyHessianProtocol.java \
       noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/apache/ProxyHttpProtocol.java \
       noone-transport/src/main/resources/META-INF/dubbo/org.apache.dubbo.rpc.Protocol
git commit -m "feat: add Apache Dubbo ProxyHessianProtocol and ProxyHttpProtocol SPI"
```

---

## Task 7: ApacheDubboClient

**Files:**
- Create: `noone-transport/src/main/java/com/reajason/noone/core/client/ApacheDubboClient.java`
- Test: `noone-transport/src/test/java/com/reajason/noone/core/client/ApacheDubboClientTest.java`

- [ ] **Step 1: Write the failing tests**

Create `noone-transport/src/test/java/com/reajason/noone/core/client/ApacheDubboClientTest.java`:

```java
package com.reajason.noone.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApacheDubboClientTest {

    @Test
    void implementsClientInterface() {
        ApacheDubboClient client = new ApacheDubboClient(
                "dubbo://localhost:20880/com.example.TestService",
                DubboClientConfig.builder().build());
        assertInstanceOf(Client.class, client);
    }

    @Test
    void getUrlReturnsConstructorValue() {
        String url = "dubbo://localhost:20880/com.example.TestService";
        ApacheDubboClient client = new ApacheDubboClient(url,
                DubboClientConfig.builder().build());
        assertEquals(url, client.getUrl());
    }

    @Test
    void defaultConfigHasNoProxy() {
        ApacheDubboClient client = new ApacheDubboClient(
                "dubbo://localhost:20880/com.example.TestService",
                DubboClientConfig.builder().build());
        assertNull(client.getConfig().getProxy());
    }

    @Test
    void configWithProxyIsRetained() {
        ProxyConfig proxy = ProxyConfig.builder()
                .type("SOCKS5").host("127.0.0.1").port(1080).build();
        DubboClientConfig config = DubboClientConfig.builder()
                .proxy(proxy).build();
        ApacheDubboClient client = new ApacheDubboClient(
                "dubbo://localhost:20880/com.example.TestService", config);
        assertNotNull(client.getConfig().getProxy());
        assertEquals("SOCKS5", client.getConfig().getProxy().getType());
    }

    @Test
    void notConnectedInitially() {
        ApacheDubboClient client = new ApacheDubboClient(
                "dubbo://localhost:20880/com.example.TestService",
                DubboClientConfig.builder().build());
        assertFalse(client.isConnected());
    }

    @Test
    void nullConfigDefaultsToEmpty() {
        ApacheDubboClient client = new ApacheDubboClient(
                "dubbo://localhost:20880/com.example.TestService", null);
        assertNotNull(client.getConfig());
    }

    @Test
    void buildParametersIncludesProxyWhenConfigured() {
        ProxyConfig proxy = ProxyConfig.builder()
                .type("SOCKS5").host("127.0.0.1").port(1080)
                .username("user").password("pass").build();
        DubboClientConfig config = DubboClientConfig.builder().proxy(proxy).build();
        ApacheDubboClient client = new ApacheDubboClient(
                "dubbo://localhost:20880/com.example.TestService", config);

        // Use reflection or a package-private test hook to verify buildParameters()
        // contains transporter=proxy-netty, proxy.type=SOCKS5, proxy.host, proxy.port,
        // proxy.username, proxy.password
        java.util.Map<String, String> params = client.buildParameters();
        assertEquals("proxy-netty", params.get("transporter"));
        assertEquals("SOCKS5", params.get("proxy.type"));
        assertEquals("127.0.0.1", params.get("proxy.host"));
        assertEquals("1080", params.get("proxy.port"));
        assertEquals("user", params.get("proxy.username"));
        assertEquals("pass", params.get("proxy.password"));
    }

    @Test
    void buildParametersOmitsProxyWhenNotConfigured() {
        DubboClientConfig config = DubboClientConfig.builder().build();
        ApacheDubboClient client = new ApacheDubboClient(
                "dubbo://localhost:20880/com.example.TestService", config);
        java.util.Map<String, String> params = client.buildParameters();
        assertNull(params.get("transporter"));
        assertNull(params.get("proxy.type"));
        assertEquals("false", params.get("reconnect"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :noone-transport:test --tests "com.reajason.noone.core.client.ApacheDubboClientTest" --info`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Implement ApacheDubboClient**

Create `noone-transport/src/main/java/com/reajason/noone/core/client/ApacheDubboClient.java`:

```java
package com.reajason.noone.core.client;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.service.GenericService;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor
public class ApacheDubboClient implements Client {

    @Setter
    @Getter
    private String url;

    @Setter
    @Getter
    private DubboClientConfig config;

    private volatile ReferenceConfig<GenericService> referenceConfig;
    private volatile GenericService genericService;
    private volatile boolean connected;
    private final Object sendLock = new Object();

    public ApacheDubboClient(String url, DubboClientConfig config) {
        this.url = url;
        this.config = config != null ? config : DubboClientConfig.builder().build();
    }

    @Override
    public boolean connect() {
        if (connected && genericService != null) {
            return true;
        }

        ReferenceConfig<GenericService> ref = new ReferenceConfig<>();
        try {
            ApplicationConfig applicationConfig = new ApplicationConfig();
            applicationConfig.setName("noone-dubbo-client");
            applicationConfig.setQosEnable(false);
            String interfaceName = resolveInterfaceName();
            ref.setApplication(applicationConfig);
            ref.setInterface(interfaceName);
            ref.setGeneric("true");
            ref.setUrl(url);
            ref.setTimeout(config.getReadTimeoutMs());
            ref.setCheck(false);
            ref.setParameters(buildParameters());

            genericService = ref.get();
            referenceConfig = ref;
            connected = true;
            return true;
        } catch (Exception e) {
            connected = false;
            try {
                ref.destroy();
            } catch (Exception ignored) {
            }
            throw new RequestSendException("Dubbo connection failed: " + e.getMessage(), 1, e);
        }
    }

    // Package-private for testing
    Map<String, String> buildParameters() {
        Map<String, String> params = new HashMap<>();
        params.put("reconnect", "false");
        ProxyConfig proxy = config.getProxy();
        if (proxy != null) {
            params.put("transporter", "proxy-netty");
            params.put("proxy.type", proxy.getType());
            params.put("proxy.host", proxy.getHost());
            params.put("proxy.port", String.valueOf(proxy.getPort()));
            if (proxy.hasAuth()) {
                params.put("proxy.username", proxy.getUsername());
                params.put("proxy.password", proxy.getPassword());
            }
        }
        return params;
    }

    @Override
    public void disconnect() {
        connected = false;
        genericService = null;
        if (referenceConfig != null) {
            try {
                referenceConfig.destroy();
            } catch (Exception ignored) {
            }
            referenceConfig = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && genericService != null;
    }

    @Override
    public byte[] send(byte[] payload) {
        synchronized (sendLock) {
            return doSend(payload, true);
        }
    }

    private byte[] doSend(byte[] payload, boolean allowReconnect) {
        if (!connected || genericService == null) {
            connect();
        }

        Object result;
        try {
            result = genericService.$invoke(
                    config.getMethodName(),
                    config.getParameterTypes(),
                    new Object[]{payload}
            );
        } catch (Exception e) {
            if (isInterruptedFailure(e)) {
                Thread.currentThread().interrupt();
                throw new RequestInterruptedException("Dubbo invocation was interrupted", e);
            }
            if (allowReconnect) {
                reconnect();
                return doSend(payload, false);
            }
            throw new RequestSendException(
                    "Dubbo invocation failed after reconnect: " + e.getMessage(), 2, e);
        }

        return convertResult(result);
    }

    private String resolveInterfaceName() {
        if (config.getInterfaceName() != null && !config.getInterfaceName().isEmpty()) {
            return config.getInterfaceName();
        }
        try {
            String path = URI.create(url).getPath();
            if (path != null && path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path != null && !path.isEmpty()) {
                return path;
            }
        } catch (Exception ignored) {
        }
        throw new IllegalStateException(
                "Cannot resolve Dubbo interface name from config or URL: " + url);
    }

    private byte[] convertResult(Object result) {
        if (result == null) {
            throw new ResponseDecodeException("Dubbo invocation returned null");
        }
        if (result instanceof byte[]) {
            return ((byte[]) result);
        }
        if (result instanceof String) {
            String str = ((String) result);
            try {
                return Base64.getDecoder().decode(str);
            } catch (IllegalArgumentException e) {
                return str.getBytes(StandardCharsets.UTF_8);
            }
        }
        throw new ResponseDecodeException(
                "Unexpected Dubbo response type: " + result.getClass().getName());
    }

    private void reconnect() {
        disconnect();
        connect();
    }

    private boolean isInterruptedFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :noone-transport:test --tests "com.reajason.noone.core.client.ApacheDubboClientTest" --info`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add noone-transport/src/main/java/com/reajason/noone/core/client/ApacheDubboClient.java \
       noone-transport/src/test/java/com/reajason/noone/core/client/ApacheDubboClientTest.java
git commit -m "feat: add ApacheDubboClient with proxy support"
```

---

## Task 8: Alibaba Dubbo SPI Extensions

**Files:**
- Create: `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/alibaba/ProxyNettyTransporter.java`
- Create: `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/alibaba/ProxyNettyClient.java`
- Create: `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/alibaba/ProxyHessianProtocol.java`
- Create: `noone-transport/src/main/resources/META-INF/dubbo/com.alibaba.dubbo.remoting.Transporter`
- Create: `noone-transport/src/main/resources/META-INF/dubbo/com.alibaba.dubbo.rpc.Protocol`

> **Investigation required:** Same as Task 5/6 but for Alibaba Dubbo 2.6.12:
> - `com.alibaba.dubbo.remoting.Transporter` interface
> - `com.alibaba.dubbo.remoting.transport.netty4.NettyClient` (or `netty.NettyClient` for Netty 3)
> - `com.alibaba.dubbo.rpc.protocol.hessian.HessianProtocol`

- [ ] **Step 1: Study Alibaba Dubbo 2.6.12 SPI APIs**

Examine the Alibaba Dubbo 2.6.12 source (in IDE or via JAR):
1. `com.alibaba.dubbo.remoting.Transporter` — `connect()` method signature
2. Default Netty transporter and NettyClient class — note exact FQCN (netty vs netty4)
3. `com.alibaba.dubbo.rpc.protocol.hessian.HessianProtocol` — `refer()` method

Record the exact class names and method signatures.

- [ ] **Step 2: Implement Alibaba ProxyNettyClient**

Create `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/alibaba/ProxyNettyClient.java`:

Same pattern as Task 5 but with `com.alibaba.dubbo.*` imports. Extends the correct Alibaba `NettyClient` superclass identified in step 1.

- [ ] **Step 3: Implement Alibaba ProxyNettyTransporter**

Create `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/alibaba/ProxyNettyTransporter.java`:

Same pattern as Task 5 but with `com.alibaba.dubbo.remoting.Transporter`.

- [ ] **Step 4: Implement Alibaba ProxyHessianProtocol**

Create `noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/alibaba/ProxyHessianProtocol.java`:

Same pattern as Task 6 but extending `com.alibaba.dubbo.rpc.protocol.hessian.HessianProtocol`.

- [ ] **Step 5: Register SPI files**

Create `noone-transport/src/main/resources/META-INF/dubbo/com.alibaba.dubbo.remoting.Transporter`:
```
proxy-netty=com.reajason.noone.core.client.dubbo.alibaba.ProxyNettyTransporter
```

Create `noone-transport/src/main/resources/META-INF/dubbo/com.alibaba.dubbo.rpc.Protocol`:
```
hessian=com.reajason.noone.core.client.dubbo.alibaba.ProxyHessianProtocol
```

- [ ] **Step 6: Verify compile**

Run: `./gradlew :noone-transport:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add noone-transport/src/main/java/com/reajason/noone/core/client/dubbo/alibaba/ \
       noone-transport/src/main/resources/META-INF/dubbo/com.alibaba.dubbo.remoting.Transporter \
       noone-transport/src/main/resources/META-INF/dubbo/com.alibaba.dubbo.rpc.Protocol
git commit -m "feat: add Alibaba Dubbo SPI extensions for proxy support"
```

---

## Task 9: AlibabaDubboClient

**Files:**
- Create: `noone-transport/src/main/java/com/reajason/noone/core/client/AlibabaDubboClient.java`
- Test: `noone-transport/src/test/java/com/reajason/noone/core/client/AlibabaDubboClientTest.java`

- [ ] **Step 1: Write the failing tests**

Create `noone-transport/src/test/java/com/reajason/noone/core/client/AlibabaDubboClientTest.java`:

Same test structure as `ApacheDubboClientTest` (Task 7) but for `AlibabaDubboClient`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :noone-transport:test --tests "com.reajason.noone.core.client.AlibabaDubboClientTest" --info`
Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Implement AlibabaDubboClient**

Create `noone-transport/src/main/java/com/reajason/noone/core/client/AlibabaDubboClient.java`:

Same logic as `ApacheDubboClient` but with `com.alibaba.dubbo.*` imports:
- `com.alibaba.dubbo.config.ApplicationConfig`
- `com.alibaba.dubbo.config.ReferenceConfig`
- `com.alibaba.dubbo.rpc.service.GenericService`

The `buildParameters()`, `resolveInterfaceName()`, `convertResult()`, `doSend()` methods are identical.

> **Note:** Alibaba Dubbo 2.x's `ReferenceConfig.setParameters()` may accept `Map<String, String>` — verify during implementation. If it doesn't exist, pass parameters via URL string manipulation.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :noone-transport:test --tests "com.reajason.noone.core.client.AlibabaDubboClientTest" --info`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add noone-transport/src/main/java/com/reajason/noone/core/client/AlibabaDubboClient.java \
       noone-transport/src/test/java/com/reajason/noone/core/client/AlibabaDubboClientTest.java
git commit -m "feat: add AlibabaDubboClient with proxy support"
```

---

## Task 10: Delete DubboClient + Update Upstream

**Files:**
- Delete: `noone-transport/src/main/java/com/reajason/noone/core/client/DubboClient.java`
- Modify: `noone-core/src/main/java/com/reajason/noone/core/profile/config/DubboProtocolConfig.java`
- Modify: `noone-server/src/main/java/com/reajason/noone/server/shell/ClientFactory.java`

- [ ] **Step 1: Add dubboStack field to DubboProtocolConfig**

In `noone-core/src/main/java/com/reajason/noone/core/profile/config/DubboProtocolConfig.java`, add a field:

```java
/**
 * Which Dubbo client stack to use: "APACHE" (3.x) or "ALIBABA" (2.x).
 * Defaults to APACHE if null or empty.
 */
private String dubboStack;
```

- [ ] **Step 2: Update ClientFactory to branch on dubboStack**

In `noone-server/src/main/java/com/reajason/noone/server/shell/ClientFactory.java`, replace the `ProtocolType.DUBBO` branch:

```java
} else if (profile.getProtocolType() == ProtocolType.DUBBO) {
    DubboClientConfig config = buildDubboClientConfig(shell, profile);
    ProtocolConfig protocolConfig = profile.getProtocolConfig();
    boolean useAlibaba = protocolConfig instanceof DubboProtocolConfig dubboProtoConfig
            && "ALIBABA".equalsIgnoreCase(dubboProtoConfig.getDubboStack());
    if (useAlibaba) {
        return new AlibabaDubboClient(shell.getUrl(), config);
    }
    return new ApacheDubboClient(shell.getUrl(), config);
}
```

Update `buildDubboClientConfig()` to wire proxy:

```java
// After existing readTimeoutMs wiring, add:
String proxyUrl = configString(cfg, "proxyUrl");
if (proxyUrl != null && !proxyUrl.isEmpty()) {
    ProxyConfig proxyConfig = parseProxyUrl(proxyUrl);
    if (proxyConfig != null) {
        if ("SOCKS4".equalsIgnoreCase(proxyConfig.getType())) {
            throw new IllegalArgumentException("SOCKS4 proxy is not supported for Dubbo clients. Use SOCKS5 instead.");
        }
        builder.proxy(proxyConfig);
    }
}
```

Update imports to use `ApacheDubboClient` and `AlibabaDubboClient` instead of `DubboClient`.

- [ ] **Step 3: Delete DubboClient.java**

```bash
rm noone-transport/src/main/java/com/reajason/noone/core/client/DubboClient.java
```

- [ ] **Step 4: Verify compile across all modules**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. If any other module references `DubboClient`, fix those references.

- [ ] **Step 5: Run all existing tests**

Run: `./gradlew :noone-transport:test :noone-server:test`
Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add noone-transport/src/main/java/com/reajason/noone/core/client/DubboClient.java \
       noone-core/src/main/java/com/reajason/noone/core/profile/config/DubboProtocolConfig.java \
       noone-server/src/main/java/com/reajason/noone/server/shell/ClientFactory.java
git commit -m "refactor: replace DubboClient with ApacheDubboClient and AlibabaDubboClient

- Delete DubboClient, replaced by ApacheDubboClient (3.x) and AlibabaDubboClient (2.x)
- Add dubboStack field to DubboProtocolConfig
- Update ClientFactory to branch on dubboStack and wire proxy config
- Reject SOCKS4 proxy for Dubbo clients"
```

---

## Task 11: Classpath Smoke Test

**Files:**
- Test: `noone-transport/src/test/java/com/reajason/noone/core/client/DubboClientClasspathTest.java`

- [ ] **Step 1: Write smoke test**

Create `noone-transport/src/test/java/com/reajason/noone/core/client/DubboClientClasspathTest.java`:

```java
package com.reajason.noone.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DubboClientClasspathTest {

    @Test
    void apacheAndAlibabaClientsCanCoexist() {
        ApacheDubboClient apache = new ApacheDubboClient(
                "dubbo://localhost:20880/com.example.TestService",
                DubboClientConfig.builder().build());
        AlibabaDubboClient alibaba = new AlibabaDubboClient(
                "dubbo://localhost:20880/com.example.TestService",
                DubboClientConfig.builder().build());

        assertNotNull(apache);
        assertNotNull(alibaba);
        assertInstanceOf(Client.class, apache);
        assertInstanceOf(Client.class, alibaba);
    }

    @Test
    void apacheClientClassLoadsCorrectly() {
        assertDoesNotThrow(() -> Class.forName("org.apache.dubbo.config.ReferenceConfig"));
    }

    @Test
    void alibabaClientClassLoadsCorrectly() {
        assertDoesNotThrow(() -> Class.forName("com.alibaba.dubbo.config.ReferenceConfig"));
    }
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew :noone-transport:test --tests "com.reajason.noone.core.client.DubboClientClasspathTest" --info`
Expected: PASS — both Dubbo stacks load without classpath conflicts.

- [ ] **Step 3: Commit**

```bash
git add noone-transport/src/test/java/com/reajason/noone/core/client/DubboClientClasspathTest.java
git commit -m "test: add classpath coexistence smoke test for both Dubbo clients"
```

---

## Task 12: Full Test Suite Verification

- [ ] **Step 1: Run all transport tests**

Run: `./gradlew :noone-transport:test`
Expected: All tests pass.

- [ ] **Step 2: Run server tests**

Run: `./gradlew :noone-server:test`
Expected: All tests pass (particularly any tests touching ClientFactory or shell creation).

- [ ] **Step 3: Run full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL across all modules.

---

## Task 13: Update noone-web for dubboStack selector

**Files:**
- Modify: `noone-web/app/types/profile.ts` (or equivalent type definitions)
- Modify: Dubbo profile form schema (Zod validation)
- Modify: Dubbo profile form component (add stack selector UI)

> This task requires familiarity with the noone-web React codebase. Check `noone-web/CLAUDE.md` if it exists for module-specific guidance.

- [ ] **Step 1: Add `dubboStack` to TypeScript types**

Find the TypeScript type that mirrors `DubboProtocolConfig` (likely in `app/types/profile.ts` or similar). Add:

```typescript
dubboStack?: "APACHE" | "ALIBABA";
```

- [ ] **Step 2: Update Zod schema**

Find the Dubbo protocol config Zod schema (likely in a shared form schema file). Add `dubboStack` as an optional enum field defaulting to `"APACHE"`.

- [ ] **Step 3: Add UI selector to Dubbo profile form**

In the Dubbo profile form component, add a select/radio control for "Dubbo Stack" with options:
- Apache Dubbo 3.x (default)
- Alibaba Dubbo 2.x

- [ ] **Step 4: Verify the existing proxy URL field for Dubbo shells**

The shell configuration form already has a `proxyUrl` input for HTTP/WebSocket. Verify it also appears (or is enabled) for Dubbo protocol shells. If not, ensure the proxy URL field is visible when the protocol is Dubbo.

- [ ] **Step 5: Format, lint, typecheck**

Run:
```bash
cd noone-web && bun run fmt && bun run lint && bun run typecheck
```
Expected: No errors.

- [ ] **Step 6: Commit**

Stage only the files modified in this task (exact paths depend on the noone-web file structure discovered in steps 1-4):

```bash
git add noone-web/app/types/profile.ts  # adjust paths based on actual files modified
git commit -m "feat(web): add Dubbo stack selector and proxy support to profile forms"
```

---

## Scope Note: Integration Tests

Docker Compose / Testcontainers integration tests (spec §9 matrix: client × protocol × proxy type × auth) are **out of scope for this implementation plan**. They require standing up Dubbo provider containers for each protocol, SOCKS5/HTTP proxy containers, and a test harness — significant infrastructure work that should be a separate follow-up plan once the transport layer is stable.

The current plan covers: unit tests, URL param wiring tests, classpath smoke tests, and full `./gradlew build` verification.

---

## Summary of Investigation Points

Several tasks require studying Dubbo internal APIs before implementing. These are flagged with "Investigation required" blocks. The key unknowns:

1. **Task 4:** Which Hessian client library does `com.alibaba:dubbo-rpc-hessian:2.6.12` use?
2. **Task 5:** How does Apache Dubbo 3.3.6's `NettyClient.doOpen()` set up the Netty `Bootstrap` and pipeline? Where to insert the proxy handler?
3. **Task 6:** How does `HessianProtocol.refer()` create `HessianProxyFactory`? Where is the hook for `setConnectionFactory()`?
4. **Task 8:** What is the correct `NettyClient` superclass in Alibaba Dubbo 2.6.12 (netty vs netty4)?
5. **Task 9:** Does Alibaba Dubbo 2.x `ReferenceConfig` support `setParameters(Map)`?

These must be resolved sequentially — do not skip them.
