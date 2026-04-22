const fs = require("node:fs");
const path = require("node:path");

const rootDir = process.cwd();
const standaloneDir = path.join(rootDir, ".next", "standalone");
const standaloneNextDir = path.join(standaloneDir, ".next");
const sourceStaticDir = path.join(rootDir, ".next", "static");
const targetStaticDir = path.join(standaloneNextDir, "static");
const sourcePublicDir = path.join(rootDir, "public");
const targetPublicDir = path.join(standaloneDir, "public");

function ensureExists(targetPath, label) {
  if (!fs.existsSync(targetPath)) {
    throw new Error(`${label} not found: ${targetPath}`);
  }
}

function replaceDirectory(sourceDir, targetDir) {
  fs.rmSync(targetDir, { recursive: true, force: true });
  fs.mkdirSync(path.dirname(targetDir), { recursive: true });
  fs.cpSync(sourceDir, targetDir, { recursive: true });
}

ensureExists(standaloneDir, "Standalone build output");
ensureExists(sourceStaticDir, "Next static assets");

replaceDirectory(sourceStaticDir, targetStaticDir);

if (fs.existsSync(sourcePublicDir)) {
  replaceDirectory(sourcePublicDir, targetPublicDir);
}

console.log("Prepared standalone assets:");
console.log(`- ${path.relative(rootDir, targetStaticDir)}`);
if (fs.existsSync(sourcePublicDir)) {
  console.log(`- ${path.relative(rootDir, targetPublicDir)}`);
}
