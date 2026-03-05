/**
 * Node.js Plugin Runner — executes a plugin inside a Docker container.
 *
 * Usage: node runner.mjs <plugin.mjs> <input.json> <output.json>
 *
 * 1. Reads the plugin source and evaluates it via `new Function()`
 * 2. Reads the input context from a JSON file
 * 3. Calls `plugin.equals(ctx)`
 * 4. Writes `ctx.result` to the output JSON file
 */
import { readFileSync, writeFileSync } from "node:fs";

const pluginPath = process.argv[2];
const inputPath = process.argv[3];
const outputPath = process.argv[4];

if (!pluginPath || !inputPath || !outputPath) {
    process.stderr.write(
        "Usage: node runner.mjs <plugin.mjs> <input.json> <output.json>\n"
    );
    process.exit(1);
}

const code = readFileSync(pluginPath, "utf8");
const plugin = new Function("return " + code)();
const ctx = JSON.parse(readFileSync(inputPath, "utf8"));

process.stdout.write("[Runner] Plugin loaded, executing equals()...\n");

plugin
    .equals(ctx)
    .then(() => {
        const result = ctx.result || {};
        writeFileSync(outputPath, JSON.stringify(result), "utf8");
        process.stdout.write(
            "[Runner] Done. Result keys: " +
            Object.keys(result).join(", ") +
            "\n"
        );
        process.exit(0);
    })
    .catch((err) => {
        const errorResult = { error: "Runner caught error: " + err.message };
        writeFileSync(outputPath, JSON.stringify(errorResult), "utf8");
        process.stderr.write("[Runner] FAILED: " + err.message + "\n");
        process.exit(1);
    });
