import assert from "node:assert/strict";
import process from "node:process";
import test from "node:test";

import { executePlugin } from "./plugin-test-utils.mjs";

test("system-info plugin collects core runtime details", async () => {
    const result = await executePlugin("system-info.mjs");

    assert.equal(result.error, undefined);
    assert.equal(typeof result.os?.name, "string");
    assert.equal(result.runtime?.type, "node");
    assert.equal(result.runtime?.version, process.version);
    assert.equal(result.process?.cwd, process.cwd());
    assert.ok(Number.isInteger(result.process?.pid));
    assert.equal(typeof result.env, "object");
    assert.ok(Array.isArray(result.network));
    assert.ok(Array.isArray(result.file_systems));
    assert.ok(result.file_systems.length >= 1);
});
