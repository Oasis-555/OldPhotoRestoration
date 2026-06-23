const { spawn } = require("child_process");
const fs = require("fs");
const path = require("path");

const root = "D:/Engineering/PhotoProject";
const out = fs.openSync(path.join(root, "smolvlm_service.out.log"), "w");
const err = fs.openSync(path.join(root, "smolvlm_service.err.log"), "w");

const child = spawn("D:/Anaconda/envs/smolvlm/python.exe", ["vlm_server.py"], {
  cwd: root,
  detached: true,
  stdio: ["ignore", out, err],
  windowsHide: true,
});

child.unref();
console.log(`SmolVLM started with PID ${child.pid}`);
