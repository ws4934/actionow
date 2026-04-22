const fs = require("node:fs");
const path = require("node:path");

const readEnvFile = (filePath) => {
  if (!fs.existsSync(filePath)) {
    return {};
  }

  return fs
    .readFileSync(filePath, "utf8")
    .split(/\r?\n/)
    .reduce((env, line) => {
      const trimmed = line.trim();

      if (!trimmed || trimmed.startsWith("#")) {
        return env;
      }

      const separatorIndex = trimmed.indexOf("=");

      if (separatorIndex === -1) {
        return env;
      }

      const key = trimmed.slice(0, separatorIndex).trim();
      let value = trimmed.slice(separatorIndex + 1).trim();

      if (
        (value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))
      ) {
        value = value.slice(1, -1);
      }

      env[key] = value;
      return env;
    }, {});
};

const envFromFile = readEnvFile(path.join(__dirname, ".env.production"));
const runtimeEnv = {
  ...envFromFile,
  ...process.env,
};
const instances = Number(runtimeEnv.PM2_INSTANCES || 1);

module.exports = {
  apps: [
    {
      name: "actionow-web",
      script: ".next/standalone/server.js",
      cwd: __dirname,
      exec_mode: instances > 1 ? "cluster" : "fork",
      instances,
      autorestart: true,
      max_memory_restart: process.env.PM2_MAX_MEMORY_RESTART || "1G",
      listen_timeout: 10000,
      kill_timeout: 5000,
      env: {
        ...runtimeEnv,
        NODE_ENV: "production",
        HOSTNAME: runtimeEnv.HOSTNAME || "0.0.0.0",
        PORT: runtimeEnv.PORT || 3000,
      },
    },
  ],
};
