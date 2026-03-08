import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, realpathSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

export function loadPlugin(pluginFilename) {
    const code = readFileSync(new URL(pluginFilename, import.meta.url), "utf8");
    const plugin = new Function("return " + code)();
    assert.equal(typeof plugin?.equals, "function");
    return plugin;
}

export async function executePlugin(pluginFilename, ctx = {}) {
    const plugin = loadPlugin(pluginFilename);
    await plugin.equals(ctx);
    assert.ok(Object.prototype.hasOwnProperty.call(ctx, "result"));
    return ctx.result;
}

export function createTempDir(prefix) {
    return realpathSync(mkdtempSync(join(tmpdir(), prefix)));
}
