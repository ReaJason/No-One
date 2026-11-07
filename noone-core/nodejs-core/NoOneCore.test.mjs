import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import assert from "node:assert/strict";

const CORE_PATHS = [
    fileURLToPath(new URL("./NoOneCore.mjs", import.meta.url)),
    fileURLToPath(new URL("../src/main/resources/nodejs-core.mjs", import.meta.url)),
];

const NULL = 0x00;
const STRING = 0x01;
const INTEGER = 0x02;
const BOOLEAN = 0x05;
const LONG_STRING = 0x0a;
const MAP = 0x10;

function loadCore(path) {
    // The core is an IIFE expression, so evaluate it the same way the shell
    // template does and return the object.
    return new Function("return " + readFileSync(path, "utf8"))();
}

function writeMapEntry(parts, key, value) {
    const keyBytes = Buffer.from(key, "utf8");
    const keyLength = Buffer.alloc(2);
    keyLength.writeUInt16BE(keyBytes.length, 0);
    parts.push(keyLength, keyBytes);
    parts.push(writeValue(value));
}

function writeValue(value) {
    if (value == null) {
        return Buffer.from([NULL]);
    }
    if (typeof value === "string") {
        const bytes = Buffer.from(value, "utf8");
        const length = Buffer.alloc(4);
        length.writeInt32BE(bytes.length, 0);
        return Buffer.concat([Buffer.from([LONG_STRING]), length, bytes]);
    }
    if (typeof value === "number") {
        const bytes = Buffer.alloc(4);
        bytes.writeInt32BE(value, 0);
        return Buffer.concat([Buffer.from([INTEGER]), bytes]);
    }
    if (typeof value === "boolean") {
        return Buffer.from([BOOLEAN, value ? 1 : 0]);
    }
    return writeMap(value);
}

function writeMap(map) {
    const parts = [Buffer.from([MAP])];
    const size = Buffer.alloc(4);
    size.writeInt32BE(Object.keys(map).length, 0);
    parts.push(size);
    for (const [key, value] of Object.entries(map)) {
        writeMapEntry(parts, key, value);
    }
    return Buffer.concat(parts);
}

function readValue(buffer, state) {
    const type = buffer[state.offset++];
    switch (type) {
        case NULL:
            return null;
        case STRING: {
            const length = buffer.readUInt16BE(state.offset);
            state.offset += 2;
            const value = buffer.subarray(state.offset, state.offset + length).toString("utf8");
            state.offset += length;
            return value;
        }
        case LONG_STRING: {
            const length = buffer.readInt32BE(state.offset);
            state.offset += 4;
            const value = buffer.subarray(state.offset, state.offset + length).toString("utf8");
            state.offset += length;
            return value;
        }
        case INTEGER: {
            const value = buffer.readInt32BE(state.offset);
            state.offset += 4;
            return value;
        }
        case BOOLEAN:
            return buffer[state.offset++] !== 0;
        case MAP: {
            const size = buffer.readInt32BE(state.offset);
            state.offset += 4;
            const map = {};
            for (let i = 0; i < size; i++) {
                const keyLength = buffer.readUInt16BE(state.offset);
                state.offset += 2;
                const key = buffer.subarray(state.offset, state.offset + keyLength).toString("utf8");
                state.offset += keyLength;
                map[key] = readValue(buffer, state);
            }
            return map;
        }
        default:
            throw new Error(`Unexpected type 0x${type.toString(16)} at ${state.offset - 1}`);
    }
}

async function invoke(core, request) {
    // The core logs request/result payloads; keep test output readable.
    const originalLog = console.log;
    console.log = () => {};
    try {
        return readValue(await core.equals(writeMap(request)), { offset: 0 });
    } finally {
        console.log = originalLog;
    }
}

for (const path of CORE_PATHS) {
    test(`long string round-trips through ${path}`, async () => {
        const core = loadCore(path);
        const longString = "x".repeat(70000);

        const loadResponse = await invoke(core, {
            action: "load",
            plugin: "long-string-plugin",
            version: "1.0.0",
            pluginCode: "return { execute: function (ctx) { return '" + longString + "'; } };",
        });
        assert.equal(loadResponse.code, 0, loadResponse.error);
        assert.equal(loadResponse.data, true);

        const runResponse = await invoke(core, {
            action: "run",
            plugin: "long-string-plugin",
            args: {},
        });
        assert.equal(runResponse.code, 0, runResponse.error);
        assert.equal(runResponse.data.length, longString.length);
        assert.equal(runResponse.data, longString);
    });

    test(`oversized declared length is rejected by ${path}`, async () => {
        const core = loadCore(path);
        const request = Buffer.from([
            MAP, 0, 0, 0, 1, 0, 1, 0x78, 0x06, 0x7f, 0xff, 0xff, 0xff,
        ]);

        const response = readValue(await core.equals(request), { offset: 0 });
        assert.equal(response.code, 1);
        assert.ok(response.error);
    });
}
