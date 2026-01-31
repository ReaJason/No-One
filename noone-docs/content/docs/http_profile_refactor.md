---
title: HttpProtocolConfig
---

### HttpRequestBodyType 设计

1. HttpRequestBodyType.FORM_URLENCODED 时 requestTemplate 需要值为
   username=admin&action=login&q={{payload}}&token=123456
2. HttpRequestBodyType.TEXT 时 requestTemplate 需要值为 hello{{payload}}world
3. HttpRequestBodyType.MULTIPART_FORM_DATA 时 requestTemplate 需要值为（对于二进制数据不仅支持 <hex> 还支持 <base64>）
   ```text
   --{{boundary}}
    Content-Disposition: form-data; name="username"
    
    admin
    --{{boundary}}
    Content-Disposition: form-data; name="file"; filename="test.png"
    Content-Type: image/png
    
    <hex>89504E470D0A1A0A0000000D4948445200000001000000010802000000907753DE0000000C49444154789C63F8CFC000000301010018DD8D000000000049454E44AE426082</hex>{{payload}}
    --{{boundary}}--
   ```
4. HttpRequestBodyType.JSON 时 requestTemplate 需要值为 {"hello": "{{payload}}"}
5. HttpRequestBodyType.XML 时 requestTemplate 需要值为 <hello>{{payload}}</hello>
6. HttpRequestBodyType.BINARY 时 requestTemplate 需要值为 <base64>aGVsbG8=</base64>{{payload}}

HttpResponseBodyType 与 HttpRequestBodyType 类似

### HttpRequestBodyType 实现

> 通过字节码生成技术使 NoOneMemShellGenerator 仅保留需要的方法和逻辑

1. HttpRequestBodyType.FORM_URLENCODED 时，假设模板为 username=admin&action=login&q={{payload}}&token=123456，对于
   NoOneMemShellGenerator 需要将对应的模板参数取出来，即 `q`，然后使用字节码生成修改 `getArgFromRequest` 通过
   request.getParameter("q") 直接获取，对于 HttpClient 来说，发送请求时需要替换 {{payload}} 为对应的数据并确保其使用了
   urlencoded
2. HttpRequestBodyType.FORM_URLENCODED 时，假设模板为 username=admin&action=login&q=fuck{{payload}}123123&token=123456，对于
   NoOneMemShellGenerator 需要将对应的模板参数取出来，即 `q`，然后使用字节码生成修改 `getArgFromRequest` 通过
   request.getParameter("q") 获取参数之后，并记录 prefix index 和 suffix index 进行截断，对于 HttpClient 来说，发送请求时需要替换
   {{payload}} 为对应的数据并确保其使用了 urlencoded
3. HttpRequestBodyType.TEXT 时，假设模板为 hello{{payload}}world，对于 NoOneMemShellGenerator 需要记录 prefix index 和
   suffix index，然后使用字节码生成修改 `getArgFromRequest` 使用 request 读取 body 流并截取出对应的数据
4. HttpRequestBodyType.MULTIPART_FORM_DATA 时，假设模板为
   ```text
      --{{boundary}}
       Content-Disposition: form-data; name="username"
    
       admin
       --{{boundary}}
       Content-Disposition: form-data; name="file"; filename="test.png"
       Content-Type: image/png
    
       <hex>89504E470D0A1A0A0000000D4948445200000001000000010802000000907753DE0000000C49444154789C63F8CFC000000301010018DD8D000000000049454E44AE426082</hex>{{payload}}
       --{{boundary}}--
   ```
   对于 NoOneMemShellGenerator 需要在假设 {{boundary}} 填充长度限制的前提下，并且对 <hex> 或 <base64> 进行二进制流转换，推断
   payload 所处的 prefix index 和 suffix index，这样通过字节码生成修改 `getArgFromRequest` 使用 request 读取 body
   流并截取出对应的数据，在 HttpClient 中则需要填充 {{boundary}} 和 {{payload}} 并将 <hex> <base64> 等进行二进制流的转换以发送请求
5. HttpRequestBodyType.JSON、HttpRequestBodyType.XML、HttpRequestBodyType.BINARY 时，等等都类似记录 prefix index 和 suffix
   index

### HttpResponseBodyType 实现

> 通过字节码生成技术使 NoOneMemShellGenerator 仅保留需要的方法和逻辑

1. HttpRequestBodyType.TEXT 时，假设模板为 hello{{payload}}world，对于 NoOneMemShellGenerator 需要将模板以 payload 拆分为
   prefix text 和 suffix text，然后通过字节码生成修改 `wrapResData` 将 prefix text 和 suffix text 拼接上去，对于
   HttpClient 需要计算 prefix index 和 suffix index 来截取对应的数据
2. HttpRequestBodyType.JSON、HttpRequestBodyType.XML、HttpRequestBodyType.BINARY 时，等等都类似记录 prefix index 和 suffix
   index

### Response Code/Response headers 实现

对于 NoOneMemShellGenerator 需要通过字节码生成修改 `wrapResponse` 设置响应头和响应状态