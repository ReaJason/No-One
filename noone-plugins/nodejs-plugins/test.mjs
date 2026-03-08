import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import process from "node:process";

const testFiles = [
    fileURLToPath(new URL("./system-info.test.mjs", import.meta.url)),
    fileURLToPath(new URL("./command-execute.test.mjs", import.meta.url)),
    fileURLToPath(new URL("./file-manager.test.mjs", import.meta.url)),
];

const result = spawnSync(process.execPath, ["--test", ...testFiles], {
    stdio: "inherit",
});

if (result.error) {
    throw result.error;
}

process.exit(result.status ?? 1);
