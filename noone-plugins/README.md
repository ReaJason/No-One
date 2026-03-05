## No One Plugins

- java-plugins: 编写 Java 相关的插件
- nodejs-plugins: 编写 Node.js 相关的插件
- dotnet-plugins: 编写 .NET 相关的插件
- release: 平台可接收的插件描述 json 文件

### Development Workflow

Currently, after editing any plugin (Java / Node.js / .NET), you can generate all release payload JSONs with a single command:

```bash
# For .NET, you must build the project first:
# cd noone-plugins/dotnet-plugins && dotnet build

./gradlew :noone-plugins:java-plugins:generateRelease
```

This task will automatically:
1. Compile Java plugins, run ByteBuddy to ensure JDK6 compatibility, and base64-encode the results.
2. Read `.mjs` files for Node.js plugins and collapse them into a single line.
3. Read the pre-compiled `.base64` output mapped from the `.NET` builds.
4. Replace the `payload` field in the respective `release/` JSON artifact definitions.