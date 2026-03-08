import assert from "node:assert/strict";
import { mkdirSync, rmSync } from "node:fs";
import { resolve } from "node:path";
import process from "node:process";
import test from "node:test";

import { createTempDir, executePlugin } from "./plugin-test-utils.mjs";

test("command-execute plugin requires op", async () => {
    const result = await executePlugin("command-execute.mjs");

    assert.equal(result.error, "op is required");
});

test("command-execute plugin changes cwd for cd op", async (t) => {
    const baseDir = createTempDir("node-command-execute-");
    t.after(() => rmSync(baseDir, { recursive: true, force: true }));

    const childDir = resolve(baseDir, "child");
    mkdirSync(childDir, { recursive: true });

    const result = await executePlugin("command-execute.mjs", {
        op: "cd",
        cdTarget: "child",
        cwd: baseDir,
    });

    assert.equal(result.error, undefined);
    assert.equal(result.exitCode, 0);
    assert.equal(result.stdout, "");
    assert.equal(result.stderr, "");
    assert.equal(result.charsetUsed, "utf8");
    assert.equal(result.cwd, childDir);
});

test("command-execute plugin executes a command in the provided cwd", async (t) => {
    const workingDir = createTempDir("node-command-execute-");
    t.after(() => rmSync(workingDir, { recursive: true, force: true }));

    const result = await executePlugin("command-execute.mjs", {
        op: "exec",
        executable: process.execPath,
        argv: ["-e", "process.stdout.write(process.cwd())"],
        cwd: workingDir,
    });

    assert.equal(result.error, undefined);
    assert.equal(result.exitCode, 0);
    assert.equal(result.stdout, workingDir);
    assert.equal(result.stderr, "");
    assert.equal(result.charsetUsed, "utf8");
    assert.equal(result.cwd, workingDir);
});
