import assert from "node:assert/strict";
import { mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import test from "node:test";

import { createTempDir, executePlugin } from "./plugin-test-utils.mjs";

test("file-manager plugin requires op", async () => {
    const result = await executePlugin("file-manager.mjs");

    assert.equal(result.errorCode, "INVALID_ARG");
    assert.equal(result.error, "op is required");
});

test("file-manager plugin lists directories before files", async (t) => {
    const baseDir = createTempDir("node-file-manager-");
    t.after(() => rmSync(baseDir, { recursive: true, force: true }));

    const childDir = resolve(baseDir, "child");
    const textFile = resolve(baseDir, "alpha.txt");
    mkdirSync(childDir, { recursive: true });
    writeFileSync(textFile, "hello", "utf8");

    const result = await executePlugin("file-manager.mjs", {
        op: "list",
        cwd: baseDir,
        path: baseDir,
    });

    assert.equal(result.error, undefined);
    assert.equal(result.errorCode, undefined);
    assert.equal(result.cwd, baseDir);
    assert.equal(result.path, baseDir);
    assert.deepEqual(
        result.entries.map((entry) => entry.name),
        ["child", "alpha.txt"],
    );
    assert.equal(result.entries[0].entryType, "directory");
    assert.equal(result.entries[0].fileType, "Folder");
    assert.equal(result.entries[0].path, childDir);
    assert.equal(result.entries[1].entryType, "file");
    assert.equal(result.entries[1].fileType, "Document");
    assert.equal(result.entries[1].path, textFile);
    assert.equal(result.entries[1].sizeBytes, 5);
});

test("file-manager plugin writes and reads file bytes", async (t) => {
    const baseDir = createTempDir("node-file-manager-");
    t.after(() => rmSync(baseDir, { recursive: true, force: true }));

    const relativePath = join("nested", "message.txt");
    const targetPath = resolve(baseDir, relativePath);
    const payload = Buffer.from("hello node plugin", "utf8");

    const writeResult = await executePlugin("file-manager.mjs", {
        op: "write-all",
        cwd: baseDir,
        path: relativePath,
        bytes: payload,
        createParent: true,
    });

    assert.equal(writeResult.error, undefined);
    assert.equal(writeResult.errorCode, undefined);
    assert.equal(writeResult.path, targetPath);
    assert.equal(writeResult.fileSize, payload.length);
    assert.equal(readFileSync(targetPath, "utf8"), payload.toString("utf8"));

    const readResult = await executePlugin("file-manager.mjs", {
        op: "read-all",
        cwd: baseDir,
        path: relativePath,
    });

    assert.equal(readResult.error, undefined);
    assert.equal(readResult.errorCode, undefined);
    assert.equal(readResult.path, targetPath);
    assert.equal(readResult.fileSize, payload.length);
    assert.ok(Buffer.isBuffer(readResult.bytes));
    assert.deepEqual([...readResult.bytes], [...payload]);
});
