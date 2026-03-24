---
name: noone-plugins
description: Guide for the noone multi-language plugin system. Use when adding, fixing, reviewing, testing, or regenerating plugins under noone-plugins/, including Java plugin classes and tests, Node.js .mjs plugins, .NET plugin projects, release JSON metadata, and the generateRelease workflow.
---

# NoOne Plugins

Implement or update plugins for the noone multi-runtime plugin subsystem. Read the shared workflow first, then load only the language guide that matches the files you are changing.

## Start Here

1. Read `references/workflow.md` for the shared runtime contract and end-to-end workflow.
2. Read exactly one language guide for the current change:
   - `references/java.md` for `noone-plugins/java-plugins/...` and `noone-plugins/release/java/...`
   - `references/nodejs.md` for `noone-plugins/nodejs-plugins/...` and `noone-plugins/release/nodejs/...`
   - `references/dotnet.md` for `noone-plugins/dotnet-plugins/...`, `noone-plugins/dotnet-plugins.Tests/...`, and `noone-plugins/release/dotnet/...`
3. Use adjacent plugins as the primary style baseline before introducing new structure.

## Shared Rules

- Preserve the execution contract: the runtime invokes `equals(ctx)`, the plugin writes a map-like result to `ctx.result` or `ctx["result"]`, and the method returns `true`.
- Keep plugin id, source filename, test name, project metadata, and release JSON aligned.
- Treat `payload` in release JSON as generated output. Edit source and metadata first, then regenerate artifacts.
- Prefer small, self-contained changes that match the conventions of the target runtime.
- Trust the real build and test hooks in this repository over generic language rules when compatibility is unclear.

## Validation Strategy

- Run the narrowest language-specific tests first.
- Regenerate release payloads only after code, mapping, and release metadata are internally consistent.
- Use `noone-plugins/plugin-tests` tasks when the change is about runtime compatibility rather than a single plugin implementation detail.

## References

- `references/workflow.md`
- `references/java.md`
- `references/nodejs.md`
- `references/dotnet.md`
