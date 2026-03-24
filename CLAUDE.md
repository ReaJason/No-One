# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Polyglot web shell manager with a multi-language plugin system (Java, Node.js, .NET). Gradle monorepo for Java modules; `noone-web` (Bun) and `noone-docs` (NPM) are separate, not part of Gradle.

## Build & Test Commands

### Server (Spring Boot, Spring Data JPA, Java 21)
- Build: `./gradlew :noone-server:build`
- Test: `./gradlew :noone-server:test`
- Run: `./gradlew :noone-server:bootRun` (requires `--add-opens java.base/java.lang=ALL-UNNAMED`)

### Web (React 19 + React Router v7, TypeScript)
- Install: `bun install --frozen-lockfile`
- Dev: `bun run dev`
- Build: `bun run build`
- Format: `bun run fmt` (oxfmt) — run after editing web files
- Format check: `bun run fmt:check`
- Lint: `bun run lint` (oxlint)
- Typecheck: `bun run typecheck`
- Test: `bun run test:run` (Vitest, single run)

### Java Plugins (JDK8 source, rewritten to JDK6 bytecode)
- Test: `./gradlew :noone-plugins:java-plugins:test`

### Node.js Plugins
- Test: `node noone-plugins/nodejs-plugins/test.mjs`

### .NET Plugins (.NET Standard 2.0, C# 7.3)
- Build: `cd noone-plugins/dotnet-plugins && dotnet build --configuration Release`
- Test: `cd noone-plugins/dotnet-plugins.Tests && dotnet test --configuration Release`

### Plugin Release Generation
.NET plugins MUST be built first, then: `./gradlew :noone-plugins:java-plugins:generateRelease`

## Code Style

### Conventional Commits
Use conventional commit prefixes: `feat:`, `fix:`, `refactor:`, `perf:`, `docs:`, `test:`, `chore:`

### Java Plugins — JDK6 Bytecode Constraints
Plugins are single `.class` files base64-encoded. They MUST NOT use:
- Lambdas, diamond operator (`<>`), try-with-resources
- Inner/anonymous classes, multi-catch, enhanced switch
- `String.join()`, `List.of()`, streams, any Java 7+ API
- Use manual `finally` blocks for resource cleanup

### Node.js Plugins
- MUST use `node:` prefix for all built-in modules (e.g., `node:os`, `node:fs`)

### .NET Plugins — C# 7.3 Only
- NO C# 8+ features (nullable reference types, switch expressions, ranges, default interface methods)
- Use `sealed class` for performance

### TypeScript (noone-web)
- Strict mode, import alias `@/*` → `./app/*`
- Format with oxfmt (`bun run fmt`), lint with oxlint (`bun run lint`)
- Prefix unused variables with `_` to suppress lint warnings

## Architecture Notes

- All plugins follow the same contract: runtime calls `equals(ctx)` where `ctx` is a Map carrying input params; plugin writes results to `ctx["result"]` and returns `true`
- Java plugins need a `javaPluginMapping` entry in `noone-plugins/build.gradle.kts` to generate payloads
- Java plugin tests require JVM flag: `--add-opens java.base/java.net=ALL-UNNAMED`
- Subdirectory CLAUDE.md files can be added for module-specific instructions (e.g., `noone-web/CLAUDE.md`)
