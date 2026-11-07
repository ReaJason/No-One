# Shared Workflow

## Inspect These Files First

- `noone-plugins/README.md` for the high-level release flow
- `noone-plugins/release/<language>/...` for the published plugin metadata
- The nearest existing plugin in the same runtime for naming, result shape, and test style

## Runtime Contract

All runtimes follow the same contract:

1. The platform loads the plugin dynamically.
2. The platform calls `execute(ctx)`.
3. The plugin reads input values from `ctx`.
4. The plugin returns the result; the runtime places it in the response `data`.
5. Expected failures are reported by throwing an exception; the core catches it and returns `code=1` with `error`. Do not return `error`/`errorCode` fields or write to `ctx`.

Entry-point signatures: Java `public Object execute(Map<String, Object> ctx)`, Node.js `execute(ctx)` (sync or async), .NET `public object Execute(IDictionary<string, object> ctx)`. The runtime also injects `pluginCaches` and `globalCaches` into `ctx`; Java additionally injects `request` and `response`.

Treat this contract as the stable boundary across Java, Node.js, and .NET.

## End-to-End Change Flow

1. Identify the runtime and the plugin id.
2. Update the runtime-specific source file and the narrowest matching tests.
3. Sync metadata:
   - Create or update `noone-plugins/release/<language>/<language>-<plugin-id>-plugin-0.0.1.json`
   - Leave `payload` empty for new metadata or treat it as stale until regeneration
   - For Java, also update `javaPluginMapping` in `noone-plugins/java-plugins/build.gradle.kts`
   - For .NET, ensure the `.csproj` `PluginId` matches the release json `id`
4. Regenerate artifacts:
   - Build the matching `.NET` project first if the plugin is in `dotnet-plugins/`
   - Run `./gradlew :noone-plugins:generateRelease`
5. Run targeted tests, then broader compatibility tests only if the change needs them.

## Alignment Checks

Before finishing, verify these identifiers all agree:

- Source filename
- Public plugin class or factory shape
- Test class or test script name
- Release JSON `id`
- Java `javaPluginMapping` key when applicable
- `.NET` `PluginId` and emitted `build/<plugin-id>.base64` when applicable

## Useful Validation Commands

```bash
./gradlew :noone-plugins:generateRelease
./gradlew :noone-plugins:plugin-tests:test
./gradlew :noone-plugins:plugin-tests:dockerAllMatrixTest
```

Use the matrix tasks only when validating compatibility across JDK, Node.js, or .NET runtime variants.
