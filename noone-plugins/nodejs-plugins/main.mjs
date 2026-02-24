import { readFileSync, writeFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

function parseArgs(argv) {
  const args = {
    in: null,
    out: null,
  };

  for (let i = 0; i < argv.length; i++) {
    const token = argv[i];
    if (token === "--in") {
      args.in = argv[++i] ?? null;
      continue;
    }
    if (token === "--out") {
      args.out = argv[++i] ?? null;
      continue;
    }
    if (token === "--help" || token === "-h") {
      args.help = true;
      continue;
    }
  }

  return args;
}

function toSingleLineJs(source) {
  const normalized = source.replace(/\r\n?/g, "\n");
  const lines = normalized.split("\n");
  const trimmed = lines.map((line) => line.trim()).filter((line) => line.length > 0);
  return trimmed.join(" ");
}

function printHelp() {
  const help = [
    "Usage:",
    "  node main.mjs [--in <path>] [--out <path>]",
    "",
    "Defaults:",
    "  --in  system-info.mjs (in this directory)",
    "  --out stdout",
  ].join("\n");
  process.stdout.write(help + "\n");
}

const args = parseArgs(process.argv.slice(2));
if (args.help) {
  printHelp();
  process.exit(0);
}

const here = dirname(fileURLToPath(import.meta.url));
const inPath = resolve(here, args.in ?? "system-info.mjs");
const outPath = args.out ? resolve(process.cwd(), args.out) : null;

const source = readFileSync(inPath, "utf8");
const singleLine = toSingleLineJs(source);

if (outPath) {
  writeFileSync(outPath, singleLine + "\n", "utf8");
} else {
  process.stdout.write(singleLine + "\n");
}
