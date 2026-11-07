# .NET Plugins

## Read These Files

- The target source file under `noone-plugins/dotnet-plugins/`
- The matching project file such as `dotnet-command-execute.csproj`
- `noone-plugins/dotnet-plugins.Tests/dotnet-plugins.Tests.csproj`
- The matching release file under `noone-plugins/release/dotnet/`

## Project Layout Rules

Each plugin is compiled from its own `.csproj` and emits `build/<plugin-id>.base64` after `dotnet build`.

Use these conventions:

- Target `netstandard2.0` in plugin projects.
- Keep the plugin class `sealed` when practical.
- Implement `public object Execute(IDictionary<string, object> ctx)` and return the result.
- Return results directly; do not assign `ctx["result"]`.
- Report failures by throwing an exception; the core converts it into `code=1`/`error`. Do not add `error` fields to the result dictionary.
- Keep `PluginId` in the `.csproj`, the release json `id`, and the expected base64 filename identical.
- When adding a new plugin, add a dedicated `dotnet-<plugin-id>.csproj` modeled after the existing ones.
- Add a `ProjectReference` in `dotnet-plugins.Tests/dotnet-plugins.Tests.csproj` when the test project must compile against the new plugin assembly.
- `dotnet-plugins.sln` builds all plugin projects at once; add a new plugin project to it.
- `Directory.Build.props` gives each project its own `obj/<project>/` and `bin/<project>/`; keep it, or parallel builds will overwrite `obj/project.assets.json` and fail with CS0518.

## Minimal Shape

```csharp
using System.Collections.Generic;

namespace NoOne.Plugins.Dotnet.PluginName
{
    public sealed class DotnetPluginNamePlugin
    {
        public object Execute(IDictionary<string, object> ctx)
        {
            Dictionary<string, object> result = new Dictionary<string, object>();
            return result;
        }
    }
}
```

## Change Checklist

1. Update or add `dotnet-<plugin-id>.cs`.
2. Update or add the matching `dotnet-<plugin-id>.csproj`.
3. Update or add focused xUnit coverage in `dotnet-plugins.Tests/`.
4. Add the release JSON under `noone-plugins/release/dotnet/`.
5. Build the matching project so `build/<plugin-id>.base64` exists.
6. Run `./gradlew :noone-plugins:generateRelease` to sync the release payload.

## Validation Commands

```bash
dotnet build noone-plugins/dotnet-plugins/dotnet-plugins.sln
dotnet test noone-plugins/dotnet-plugins.Tests/dotnet-plugins.Tests.csproj --filter DotnetCommandExecutePluginTests
./gradlew :noone-plugins:generateRelease
```
