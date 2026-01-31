---
title: Profile Transformers
---

## 加密流量 Transformers 设计

规定加密流量需要处理的周期为 压缩 - 加密 - 编码 三个周期。

目前仅针对 Java 编程语言层的算法支持而言进行实现。
- 压缩可选为 Gzip/Deflate/LZ4
- 加密可选为 XOR/AES/TripleDES
- 编码可选为 Base64/Hex/BigInteger

对于加密而言，需要使用 password 字段来生成唯一确定的 key（同一个 password 无论进行多少次生成都是一个加密 key），并且无法通过 key 反推出 password 字段，以免密码遭到泄露。

加密流量分为请求流量和加密流量
1. 在 Server 层的处理逻辑为，请求流量 解码 - 解密 - 解压缩， 响应流量 压缩 - 加密 - 编码
2. 在 Client 层的处理逻辑为 请求流量 压缩 - 加密 - 编码，响应流量 解码 - 解密 - 解压缩

## 加密流量 Transformers 实现

在 com.reajason.noone.server.profile.Profile 中有 requestTransformations 和 responseTransformations 两个字段用于存储。

**重要：transformations 必须是包含恰好 3 个元素的列表，按顺序为：[compress, encrypt, encode]**

transformations 中间可以为 None 即为空，可选的情况有
1. ["Gzip", "None", "None"]，仅压缩
2. ["Gzip", "XOR", "None"]，压缩并加密
3. ["None", "AES", "Base64"]，加密并 Base64 编码
4. ["None", "None", "None"]，无转换

任何不是 3 个元素的列表都会抛出 IllegalArgumentException。

Server 端的实现在 NoOneMemShellGenerator 中，需要参考 applyHttpProtocolConfig 使用 Advice 进行方法体的填充。
requestTransformations 通过字节码修改填充 `transformReqPayload` 方法，responseTransformations 通过字节码修改填充 `transformResData` 方法。
要求为不能引入第三方库实现，可以在类中引入辅助方法，并不是所有情况都需要辅助方法，因此不能直接在 NoOneServlet 源码中加，需要使用 ByteBuddy 或 ASM 字节码修改工具添加辅助方法。

Client 端的实现在 HttpClient 中进行。