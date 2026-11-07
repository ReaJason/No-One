# Node.js Plugins

## Read These Files

- `noone-plugins/nodejs-plugins/runner.mjs`
- `noone-plugins/nodejs-plugins/test.mjs`
- The matching release file under `noone-plugins/release/nodejs/`

## Source Shape

Node.js plugins are source strings that the build reads directly and collapses into a single-line release payload.

Keep these rules:

- Export the plugin as an IIFE that returns an object.
- Implement `execute` (sync or async).
- Return the result from `execute`; the runtime uses the return value as the response data. Do not write results to `ctx.result`.
- Use the `node:` prefix for built-in modules inside plugin source files.
- Keep the file executable through `new Function("return " + code)()` as used by the local runner.

## Minimal Shape

```javascript
(function () {
    return {
        execute: async function (ctx) {
            const result = {};
            const fs = await import("node:fs");
            if (!fs) {
                throw new Error("Failed to load node:fs");
            }
            result.ok = true;
            return result;
        }
    };
})();
```

## Change Checklist

1. Update or add `noone-plugins/nodejs-plugins/<plugin-id>.mjs`.
2. Use a nearby plugin as the baseline for argument names and result shape.
3. Add or update a focused runner script or reuse `test.mjs` when that is enough.
4. Add or update `noone-plugins/release/nodejs/nodejs-<plugin-id>-plugin-0.0.1.json`.
5. Regenerate payloads with `./gradlew :noone-plugins:generateRelease`.

## Validation Commands

```bash
node noone-plugins/nodejs-plugins/test.mjs
node noone-plugins/nodejs-plugins/runner.mjs noone-plugins/nodejs-plugins/system-info.mjs /tmp/input.json /tmp/output.json
./gradlew :noone-plugins:generateRelease
```
