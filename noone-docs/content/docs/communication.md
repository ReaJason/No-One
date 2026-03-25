---
title: 通信协议设计
---

# 全协议高级 Agent 通信架构设计（Staging / Stageless）

## 1. 架构基本定义与限制条件

*   **协议无关性**：底层通信不依赖任何 HTTP 状态码、Header 等特定协议特征。所有的握手、心跳、业务数据、异常反馈，均在**双向 Payload 字节流**的加解密层面完成。
*   **单向流消耗**：针对某些容器或 RPC 框架的限制，请求的输入流（InputStream / Request Payload）只能被读取一次，不可重复消费。
*   **双 Profile 隔离**：Loader 拥有轻量级的解密密钥与特征（Loader Profile），Core 拥有完整的加密套件与通信协议（Core Profile）。
*   **Exception-Based Signaling (异常信令)**：极简 Loader 不具备业务判断能力。加解密/反序列化过程中产生的异常，将触发预置的加密异常流，作为状态切换的依据。

---

## 2. Staging 模式（分段动态加载）通信流转

Loader 不内置 Core，体积达到极限小，仅包含：读取流 -> Loader Profile解密 -> 动态类加载 -> 内存全局挂载。

### 场景 A：初次连接与注入 (Initial Injection)
1.  平台使用 **Loader Profile 流量** 包裹加密的 Core 字节码，向目标 Endpoint 发起通信。
2.  服务端 Loader 发现内存无 Core 实例，消费流并使用 Loader Profile 密钥解密，反射实例化并存入全局上下文（如 ServletContext / 静态变量）。
3.  **成功响应**：Loader 加载成功后，向输出流返回 Loader Profile 加密的“成功标识”。
4.  **失败判定**：如果平台未收到数据流，或使用 Loader Profile 无法解密返回流，判定为**网络不通/被拦截/密钥错误**，Agent 连接失败。
5.  测试成功后，平台缓存状态更新为 `[已注入 Core]`。

### 场景 B：日常业务通信 (Routine Operation)
1.  平台检测到状态为 `[已注入 Core]`，针对文件/命令操作，使用 **Core Profile 流量** 发起通信。
2.  服务端 Loader 发现内存有 Core，不读取流，直接将输入输出流（IO Stream / Context）的控制权移交给 Core 实例。
3.  Core 消费流并用 Core Profile 解密，执行完毕后加密返回。通信成功。

### 场景 C：目标服务重启与状态自愈 (Auto-Recovery on Restart)
1.  目标服务重启，内存中的 Core 丢失。平台缓存仍为 `[已注入 Core]`。
2.  平台发出 **Core Profile 流量**（如执行 `whoami`）。
3.  服务端 Loader 发现无 Core，进入注入逻辑。它消费输入流，并尝试用 Loader Profile 密钥去解密这段实际上是 Core 流量的数据。
4.  **隐蔽报错**：解密/类加载必然抛出异常。Loader 捕获异常，向输出流写入一段**预置硬编码的 Loader Profile 密文**（例如解密后为 `"ERR_NO_CORE"`），然后关闭连接。
5.  平台收到数据流，用 Core 密钥解密失败，尝试用 Loader 密钥解密，得出 `"ERR_NO_CORE"`。
6.  平台立刻判定：**目标存活，但 Core 已丢失**。平台静默将状态置为 `[未注入]`，自动在后台发起【场景 A】的重新注入流程，并在注入成功后重放（Replay）刚刚的 `whoami` 指令。用户无感知。

### 场景 D：核心热更新 / 强制重置 (Hot Update)
1.  平台需要下发新版 Core 给已注入的目标。
2.  平台使用 **Core Profile 流量** 发送一条特殊的内置控制流：`Action=SelfDestruct`。
3.  内存中的当前 Core 收到后，主动将自己从全局上下文中卸载（比如将 loader 中的 core 置空），并返回清理成功的密文。
4.  平台随后紧跟一条 **Loader Profile 流量**（包含新版 Core），触发全新注入，完成平滑热更新。

---

## 3. Stageless 模式（全量内嵌加载）通信流转

Loader 源码中直接硬编码了经过高度混淆压缩的 Core 字节数组。Loader 具备“自我展开”能力。

### 场景 E：初次连接与业务通信 (Self-Expanding Connection)
1.  平台不需要维护“注入状态”，默认直接使用 **Core Profile 流量** 发送业务指令（如测试连通性）。
2.  服务端 Loader 被触发，判断内存无 Core。
3.  **内置加载**：Loader **不去读取外部输入流**，而是直接解密自身硬编码的字节数组，在内存中实例化 Core 并挂载。
4.  **同源转交**：因为外部输入流尚未被消费，Loader 实例化 Core 后，立刻在当前请求上下文中将流控制权移交给 Core。
5.  Core 读取流，解密指令并执行。响应成功，即代表 Agent 连接成功且业务就绪。
6.  **失败判定**：如果平台收不到预期响应或解密失败，判定连接失败（无需像 Staging 那样排查是不是 Core 没注入，失败即意味着彻底连不上）。

### 场景 F：目前服务重启 (Restart Resilience)
1.  服务重启，内存 Core 丢失。
2.  平台不知情，继续使用 **Core Profile 流量** 通信。
3.  服务端 Loader 发现无 Core，再次触发自身硬编码的解密展开逻辑，随后转交流控制权完成业务。
4.  由于展开过程在毫秒级且发生在同一个网络请求周期内，平台端**无需任何特殊处理机制**，通信保持绝对稳健。

## 4. 架构总结与优势

1. **极度隐蔽的异常处理**：全流程消灭了基于协议的错误暴露。即使蓝队截获流量或者主动探测，如果密钥不对，得到的永远是合法的加解密报错流或预置的安全密文，无法通过 404/500/RST 定位异常。
2. **泛用性极强**：由于去除了对 Request/Response 协议对象的强依赖，这套架构核心代码（Loader 路由与 Core 交互）可以原封不动地移植到 Filter、Interceptor、WebSocket Endpoint，甚至 Dubbo/gRPC 的 Service 实现中。
3. **无缝的用户体验**：对使用者而言，无论是 Staging 还是 Stageless，无论是网络抖动还是目标服务重启，平台的“状态机”和“自动重发机制”掩盖了所有底层的脆弱性。用户唯一需要做的就是敲入命令，剩下的交给平台。



## 文件位置


loader 和 core 的文件位置：

java core: noone-core/java-core/src/main/java/com/reajason/noone/core/NoOneCore.java
nodejs core: noone-core/nodejs-core/NoOneCore.mjs
dotnet core: noone-core/dotnet-core/NoOneCore.cs

java webshell loader: noone-core/java-core/src/main/resources/templates/java/vul-java-server.jsp
dotnet webshell loader: noone-core/java-core/src/main/resources/templates/dotnet/vul-dotnet-server.aspx
nodejs webshell loader: noone-core/java-core/src/main/resources/nodejs-core.mjs
java memshell loader:
1. noone-core/java-core/src/main/java/com/reajason/noone/core/shelltool/NoOneServlet.java
2. noone-core/java-core/src/main/java/com/reajason/noone/core/shelltool/NoOneWebFilter.java
3. noone-core/java-core/src/main/java/com/reajason/noone/core/shelltool/NoOneNettyHandler.java

loader 和 core 生成的文件位置：

java webshell generator: noone-server/src/main/java/com/reajason/noone/core/generator/NoOneJavaWebShellGenerator.java
java memshell generator: noone-server/src/main/java/com/reajason/noone/core/generator/JavaMemShellGenerator.java
nodejs webshell generator: noone-server/src/main/java/com/reajason/noone/core/generator/NoOneNodeJsWebShellGenerator.java
dotnet webshell generator: noone-server/src/main/java/com/reajason/noone/core/generator/NoOneDotNetWebShellGenerator.java

loader 和 core 通信文件位置：

noone-server/src/main/java/com/reajason/noone/core/JavaConnection.java
noone-server/src/main/java/com/reajason/noone/core/NodeJsConnection.java
noone-server/src/main/java/com/reajason/noone/core/DotNetConnection.java


## 最终设计

Java 内存马种类繁多，有 Servlet API/Netty API/WebSocket API 等等

### Staging 模式

不内置 core，通过通信传递 core 代码进行动态加载

loader profile != core profile

第一种生成模式：
生成时只需选取 loader profile + api 类型配置即可，生成一个 loader 带流量特征加载 core adaptor（适配流量层做 core profile 转发给 core） + core
连接时需配置 loader profile + core profile + api 类型
因为需要通过 loader profile 流量传递 core adaptor + core 给 loader，并且传递的 core adaptor + core 通过选择的 core profile + api 进行动态生成

优点是：loader 只有 loader profile 体积尽可能小
缺点是：连接的时候需要额外配置 api 类型来生成 core adaptor 用户体验多一个选项，并且这个只在 Java 中需要选择，nodejs 和 .net 中不需要

第二种生成模式：
生成时选取 loader profile + core profile + api，生成一个 loader 带流量特征，并且内置上 core adaptor
连接时只需要选择 loader profile + core profile 即可
因为通过 loader profile 只需要包括 core，传递给 loader，loader 加载 core 并初始化 core adaptor，后续流量交由 core adaptor 即可

优点是：连接时不需要选取 api，因为生成的时候本来就是 api 敏感所以必须要选 api，坏处是 loader 内置了 loader profile 和 core profile，体积会变大并且当防御方拿到 loader 即可知道两者的流量

loader profile = core profile

生成选取 loader profile + core profile + api 类型
因为 loader profile = core profile，loader 此时直接换成 core profile 走 core adaptor 的逻辑即可

连接选取 loader profile + core profile 类型即可，因为前面把 loader 中内置了 core profile adaptor 逻辑直接传 core 即可

第三种生成模式

生成选取 loader profile + core profile + api 类型，loader 仅包含 loader profile，
连接时需配置 loader profile + core profile + api 类型

优点是：仅包含 loader profile，字节码较小，不过和第二种方式几乎一样，缺点则是连接时还需要明确指定 api 类型增加用户交互，并且这个只在 Java 中需要选择，nodejs 和 .net 中不需要

### Stageless 模式

内置 core

loader profile 是无用状态，因此直接用 core profile 即可，因此此场景没有双 profile 一说

生成的时候需要选择 profile + api 类型配置即可
连接的时候需要选择 profile 配置即可
