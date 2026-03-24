# Java Plugins

## Read These Files

- `noone-plugins/java-plugins/build.gradle.kts`
- `noone-plugins/java-plugins/src/main/java/com/reajason/noone/plugin/PluginTemplate.java`
- A nearby implementation such as `SystemInfoCollector.java` or `CommandExecutor.java`
- The matching test under `noone-plugins/java-plugins/src/test/java/com/reajason/noone/plugin/`

## Compatibility Model

Java plugins compile as Java 8 source, then `generateRelease` rewrites the bytecode with ByteBuddy and `TargetJreVersionVisitorWrapper` before base64 encoding it into release payloads.

Use this as the practical rule set:

- Keep production plugin code simple and self-contained.
- Override `equals(Object obj)`, guard non-`Map` input, write `ctx.put("result", result)`, and return `true`.
- Prefer classic control flow and collection usage.
- Avoid production-code features that make downgrade or older-runtime compatibility fragile, especially lambdas, method references, streams, and try-with-resources.
- Do not assume a Java 8 library API is safe just because the source compiles; unavailable runtime APIs still fail after bytecode rewriting.
- Treat the ByteBuddy redefine test as the compatibility gate for every non-trivial Java plugin change.

## New or Updated Plugin Checklist

1. Update or add the source file under `src/main/java/com/reajason/noone/plugin/`.
2. Update or add a focused JUnit test under `src/test/java/com/reajason/noone/plugin/`.
3. Include a ByteBuddy redefine test when the plugin class is touched.
4. Add or update the `javaPluginMapping` entry in `build.gradle.kts` for new plugins.
5. Add or update the corresponding release JSON under `noone-plugins/release/java/`.
6. Regenerate payloads with `generateRelease` after tests and metadata are aligned.

## Minimal Shape

```java
package com.reajason.noone.plugin;

import java.util.HashMap;
import java.util.Map;

public class PluginName {

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Map)) {
            return true;
        }
        Map<String, Object> ctx = (Map<String, Object>) obj;
        HashMap<String, Object> result = new HashMap<String, Object>();
        ctx.put("result", result);
        return true;
    }
}
```

## Validation Commands

```bash
./gradlew :noone-plugins:java-plugins:test --tests "com.reajason.noone.plugin.PluginNameTest"
./gradlew :noone-plugins:java-plugins:generateRelease
```
