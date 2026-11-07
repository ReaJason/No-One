---
name: noone-plugins
description: Plugin development guide for the noone multi-language plugin system. Use when adding, fixing, updating, testing, or deleting plugins in noone-plugins/. Covers Java (JDK6 syntax), Node.js (node: modules), and .NET (.netstandard 2.0) plugin development, including templates, tests, release metadata, and the build workflow.
allowed-tools: Read, Glob, Grep, Edit, Write, Bash
---

# noone-plugins Development Guide

## Project Layout

```
noone-plugins/
├── README.md
├── java-plugins/
│   ├── build.gradle.kts                    # javaPluginMapping + generateRelease task
│   └── src/
│       ├── main/java/com/reajason/noone/plugin/   # Java plugin sources
│       └── test/java/com/reajason/noone/plugin/   # Java plugin tests (JUnit 5)
├── nodejs-plugins/
│   ├── <plugin-id>.mjs                     # Node.js plugin sources
│   ├── <plugin-id>.test.mjs                # Node.js plugin tests
│   └── runner.mjs                          # Local runner
├── dotnet-plugins/
│   ├── dotnet-plugins.csproj               # .netstandard 2.0, auto base64 on build
│   ├── dotnet-<plugin-id>.cs               # .NET plugin sources
│   └── build/                              # Generated .base64 files
├── dotnet-plugins.Tests/
│   └── Dotnet<PluginName>PluginTests.cs    # xUnit tests
├── plugin-tests/                           # Cross-environment integration tests
└── release/
    ├── java/<lang>-<plugin-id>-plugin-<ver>.json
    ├── nodejs/<lang>-<plugin-id>-plugin-<ver>.json
    └── dotnet/<lang>-<plugin-id>-plugin-<ver>.json
```

## Plugin Architecture

All plugins across all languages share the same execution contract (v2):

1. The runtime calls `execute(ctx)` on the plugin instance
2. `ctx` is a Map/Dictionary/Object carrying input parameters
3. The plugin reads args from `ctx`, executes logic, and **returns** the result
4. The returned value is put into the response `data` field — nothing is written back to `ctx`

Entry-point signatures:
- Java: `public Object execute(Map<String, Object> ctx)`
- Node.js: `execute(ctx)` (may be async)
- .NET: `public object Execute(IDictionary<string, object> ctx)`

**Why reflection instead of a shared interface?** — The plugin class is loaded dynamically (from base64 bytes / eval'd code / loaded DLL) by a classloader that cannot see the core's classes. A shared typed interface would fail to link, so the core resolves `execute` reflectively by name and parameter type. `equals` was used in v1 but cannot return a result in Java/.NET, hence the dedicated `execute` method.

**Context Map contract:**
- Input params: `ctx.get("key")` — varies per plugin
- Runtime-reserved keys: `pluginCaches`, `globalCaches` (Java also gets `request` and `response`)
- Output: the method's return value — usually a Map/Dict with plugin results
- Errors: throw an exception (`RuntimeException` / `Error` / `Exception`). The core catches it and returns `code=1` with the message and stack trace — never put `error`/`errorCode` fields in the result
- Incremental progress (long-running plugins): `ctx.put("progress", partialResult)` in Java, `ctx.progress = partialResult` in Node.js; TaskManager polls it

---

## Java Plugins

### Constraints (CRITICAL)

Java plugins are compiled with JDK8 source/target but ByteBuddy rewrites bytecode to JDK6.
You MUST follow these rules:

- **No lambdas** — use anonymous-free explicit loops/methods
- **No diamond operator** — always `new HashMap<String, Object>()` not `new HashMap<>()`
- **No try-with-resources** — manually close in finally blocks
- **No inner classes or anonymous classes** — single class only, because the plugin is a single `.class` file base64-encoded
- **No multi-catch** — use separate catch blocks
- **No `String.join()`, `List.of()`, streams, or JDK8+ APIs**
- **Manual `readAllBytes()`** — use `ByteArrayOutputStream` + loop pattern

### Source Template

File: `noone-plugins/java-plugins/src/main/java/com/reajason/noone/plugin/<PluginName>.java`

```java
package com.reajason.noone.plugin;

import java.util.HashMap;
import java.util.Map;

public class PluginName {

    public Object execute(Map<String, Object> ctx) {
        HashMap<String, Object> result = new HashMap<String, Object>();
        // Read args from ctx
        String arg = (String) ctx.get("arg");
        if (arg == null || arg.length() == 0) {
            throw new IllegalArgumentException("arg is required");
        }

        // Plugin logic here...

        // Return results; throw on failure and let the core build the error response
        result.put("data", "value");
        return result;
    }
}
```

### Test Template

File: `noone-plugins/java-plugins/src/test/java/com/reajason/noone/plugin/<PluginName>Test.java`

```java
package com.reajason.noone.plugin;

import com.alibaba.fastjson2.JSON;
import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class PluginNameTest {

    @Test
    void testExecute() {
        HashMap<String, Object> ctx = new HashMap<>();
        Object result = new PluginName().execute(ctx);
        assertNotNull(result);
        System.out.println(JSON.toJSONString(result));
    }

    @Test
    void testByteBuddyRedefineWithTargetJreVersion() {
        assertDoesNotThrow(() -> new ByteBuddy()
                .redefine(PluginName.class)
                .visit(TargetJreVersionVisitorWrapper.DEFAULT)
                .make()
                .getBytes());
    }
}
```

The ByteBuddy test is **mandatory** — it catches JDK6-incompatible bytecode (lambdas, diamond, etc.) at test time.

### Build Registration

After writing the plugin, add it to `javaPluginMapping` in `noone-plugins/java-plugins/build.gradle.kts`:

```kotlin
val javaPluginMapping = mapOf(
    // ... existing entries ...
    "plugin-id" to "com.reajason.noone.plugin.PluginName",
)
```

The `plugin-id` must match the `"id"` field in the release JSON.

---

## Node.js Plugins

### Constraints

- Node.js 18+ required
- **Must use `node:` prefix** for all built-in modules (e.g., `node:os`, `node:fs`, `node:child_process`) — ensures compatibility with Deno and Bun
- Plugin is wrapped in an IIFE that returns an object with an `execute` method (sync or async)
- Multi-line source is collapsed to a single line in the release payload

### Source Template

File: `noone-plugins/nodejs-plugins/<plugin-id>.mjs`

```javascript
(function () {
    return {
        execute: async function (ctx) {
            const result = {};
            const os = await import("node:os");

            // Read args from ctx
            const arg = ctx.arg;
            if (!arg) {
                throw new Error("arg is required");
            }

            // Plugin logic here...

            // Return results; throw on failure and let the core build the error response
            result.data = "value";
            return result;
        }
    };
})();
```

### Test Convention

Test files use the pattern `<plugin-id>.test.mjs` or a shared `test.mjs`:

```javascript
(async function() {
    const fs = await import("node:fs");
    let code = fs.readFileSync("plugin-id.mjs", "utf-8");
    const plugin = new Function("return " + code)();
    const ctx = {};
    const result = await plugin.execute(ctx);
    console.log(JSON.stringify(result, null, 2));
})();
```

Run with: `node noone-plugins/nodejs-plugins/test.mjs`

---

## .NET Plugins

### Constraints

- Target **.netstandard 2.0** — no C# 8+ features (no nullable reference types, no switch expressions, no ranges, no default interface methods)
- Use `sealed class` for performance
- The `.csproj` auto-generates a `.base64` file in `build/` after `dotnet build`
- All plugin projects share one directory, so `Directory.Build.props` gives each project its own `obj/<project>/` and `bin/<project>/`; without it parallel builds overwrite `obj/project.assets.json` and fail with CS0518
- `dotnet-plugins.sln` builds all plugins at once; add every new plugin project to it (`dotnet sln dotnet-plugins.sln add <file>.csproj`)

### Source Template

File: `noone-plugins/dotnet-plugins/dotnet-<plugin-id>.cs`

```csharp
using System;
using System.Collections.Generic;

namespace NoOne.Plugins.Dotnet.PluginName
{
    public sealed class DotnetPluginNamePlugin
    {
        public object Execute(IDictionary<string, object> ctx)
        {
            Dictionary<string, object> result = new Dictionary<string, object>();
            // Read args from ctx
            object argObj;
            ctx.TryGetValue("arg", out argObj);
            string arg = argObj as string;
            if (string.IsNullOrEmpty(arg))
            {
                throw new ArgumentException("arg is required");
            }

            // Plugin logic here...

            // Return results; throw on failure and let the core build the error response
            result["data"] = "value";
            return result;
        }
    }
}
```

### .csproj Setup

The `dotnet-plugins.csproj` contains an MSBuild `EncodeFileToBase64` task that runs after build.
Key properties:

```xml
<TargetFramework>netstandard2.0</TargetFramework>
<PluginId>plugin-id</PluginId>
<PluginBase64Output>$(MSBuildProjectDirectory)/build/$(PluginId).base64</PluginBase64Output>
```

Build: `cd noone-plugins/dotnet-plugins && dotnet build dotnet-plugins.sln`

### Test Template

File: `noone-plugins/dotnet-plugins.Tests/Dotnet<PluginName>PluginTests.cs`

```csharp
using System.Collections.Generic;
using System.Text.Json;
using NoOne.Plugins.Dotnet.PluginName;
using Xunit;
using Xunit.Abstractions;

namespace dotnet_plugins.Tests;

public class DotnetPluginNamePluginTests
{
    private readonly ITestOutputHelper _output;

    public DotnetPluginNamePluginTests(ITestOutputHelper output)
    {
        _output = output;
    }

    [Fact]
    public void Execute_ShouldReturnResult()
    {
        var plugin = new DotnetPluginNamePlugin();
        var ctx = new Dictionary<string, object>();

        object result = plugin.Execute(ctx);
        string json = JsonSerializer.Serialize(result,
            new JsonSerializerOptions { WriteIndented = true });
        _output.WriteLine(json);

        Assert.NotNull(result);
    }
}
```

Run: `cd noone-plugins/dotnet-plugins.Tests && dotnet test`

---

## Release Metadata

Release JSON files live in `noone-plugins/release/<language>/`.

### Naming Convention

`<language>-<plugin-id>-plugin-<version>.json`

Examples: `java-system-info-plugin-0.0.1.json`, `nodejs-command-execute-plugin-0.0.1.json`

### Standard Plugin JSON

For basic plugins (system-info, command-execute, file-manager, etc.):

```json
{
  "id": "plugin-id",
  "name": "Plugin Display Name",
  "version": "0.0.1",
  "language": "java",
  "author": "ReaJason",
  "description": "What this plugin does",
  "type": "Standard",
  "payload": ""
}
```

The `payload` field is left empty — it gets filled by `generateRelease`.

### Extension Plugin JSON

For plugins with custom UI actions (port-scanner, etc.):

```json
{
  "id": "plugin-id",
  "name": "Plugin Display Name",
  "version": "0.0.1",
  "language": "java",
  "author": "ReaJason",
  "description": "What this plugin does",
  "type": "Extension",
  "runMode": "async",
  "payload": "",
  "actions": {
    "action-name": {
      "name": "Action Display Name",
      "description": "What this action does",
      "argSchema": [
        {
          "name": "paramName",
          "type": "input",
          "label": "Param Label",
          "required": true,
          "description": "Param description"
        },
        {
          "name": "optionalParam",
          "type": "number",
          "label": "Optional Label",
          "default": "100",
          "description": "Param description"
        }
      ],
      "resultSchema": {
        "type": "json"
      }
    }
  }
}
```

**`argSchema` field types:** `input` (text), `number`, `select`, `textarea`
**`resultSchema` types:** `json`, `table` (with `columns`), `txt`

---

## Development Workflow

### 1. Write the plugin source

Create the plugin file in the appropriate language directory following the templates above.

### 2. Write tests

- **Java**: JUnit 5 test + ByteBuddy redefine test (mandatory)
- **Node.js**: `<plugin-id>.test.mjs` in `nodejs-plugins/`
- **.NET**: xUnit test in `dotnet-plugins.Tests/`

### 3. Register the plugin (Java only)

Add the plugin to `javaPluginMapping` in `noone-plugins/java-plugins/build.gradle.kts`.

### 4. Create the release JSON

Create the metadata file in `noone-plugins/release/<language>/` with the appropriate format.
Leave `payload` as an empty string.

### 5. Generate release payloads

```bash
# For .NET plugins, build the solution first:
cd noone-plugins/dotnet-plugins && dotnet build dotnet-plugins.sln

# Generate all release payloads (Java + Node.js + .NET):
./gradlew :noone-plugins:generateRelease
```

### 6. Run tests

```bash
# Java tests
./gradlew :noone-plugins:java-plugins:test

# Node.js tests
node noone-plugins/nodejs-plugins/test.mjs

# .NET tests
cd noone-plugins/dotnet-plugins.Tests && dotnet test
```

---

## Common Mistakes

| Mistake | Why it breaks | Fix |
|---------|--------------|-----|
| Using lambdas/diamond/try-with-resources in Java | ByteBuddy JDK6 rewrite fails | Use JDK6 syntax only |
| Inner classes or anonymous classes in Java | Plugin is a single `.class` base64 | Keep everything in one class |
| Forgetting `javaPluginMapping` entry | `generateRelease` skips the plugin silently | Add mapping before running generateRelease |
| Using bare module names in Node.js (`"os"`) | Fails in Deno/Bun | Always use `"node:os"` prefix |
| Using C# 8+ features in .NET | Incompatible with .netstandard 2.0 | Stick to C# 7.3 syntax |
| Using the v1 `equals(ctx)` contract | Core only resolves `execute(ctx)`; plugin fails to load | Implement `execute` and return the result |
| Writing results to `ctx["result"]` instead of returning | Core ignores `ctx` writes; response data is null | `return result;` from `execute` |
| Returning `error`/`errorCode` fields instead of throwing | Bypasses the core's uniform failure response | Throw an exception; the core fills `code`/`error` |
| Forgetting ByteBuddy test | JDK6 incompatibility caught only at runtime | Always include the redefine test |
| Release JSON `id` doesn't match `javaPluginMapping` key | Payload generation silently skipped | Ensure IDs match exactly |
| Not building .NET before generateRelease | Missing `.base64` file, plugin skipped | Run `dotnet build dotnet-plugins.sln` first |
| New .NET project not added to `dotnet-plugins.sln` | Solution build skips it, payload never regenerates | `dotnet sln dotnet-plugins.sln add <file>.csproj` |
