import http from "node:http";
import { spawn } from "node:child_process";
import path from "node:path";

const HOST = process.env.HOST || "0.0.0.0";
const PORT = Number(process.env.PORT || 8787);
const TOKEN = process.env.NEXUS_BACKEND_TOKEN || "";
const WORKDIR = path.resolve(process.env.CLAUDE_CODE_WORKDIR || process.cwd());
const MODE = process.env.CLAUDE_CODE_PERMISSION_MODE || "plan";
const MAX_BODY = 32 * 1024;

if (!TOKEN) {
  console.error("Defina NEXUS_BACKEND_TOKEN antes de iniciar o servidor.");
  process.exit(1);
}

function send(res, status, data) {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff"
  });
  res.end(JSON.stringify(data));
}

const server = http.createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") return send(res, 200, { ok: true });
  if (req.method !== "POST" || req.url !== "/api/claude-code") return send(res, 404, { error: "Not found" });
  if (req.headers.authorization !== `Bearer ${TOKEN}`) return send(res, 401, { error: "Unauthorized" });

  let raw = "";
  try {
    for await (const chunk of req) {
      raw += chunk;
      if (raw.length > MAX_BODY) return send(res, 413, { error: "Prompt muito grande" });
    }
    const input = JSON.parse(raw || "{}");
    const prompt = String(input.prompt || "").trim();
    if (!prompt) return send(res, 400, { error: "Informe prompt" });
    if (prompt.length > 12000) return send(res, 413, { error: "Prompt limitado a 12000 caracteres" });

    const args = ["-p", prompt, "--output-format", "json", "--max-turns", "4", "--permission-mode", MODE];
    const child = spawn("claude", args, {
      cwd: WORKDIR,
      env: { ...process.env },
      shell: false,
      stdio: ["ignore", "pipe", "pipe"]
    });
    let stdout = "";
    let stderr = "";
    let finished = false;
    const timeout = setTimeout(() => child.kill("SIGTERM"), 150000);
    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", chunk => { if (stdout.length < 2_000_000) stdout += chunk; });
    child.stderr.on("data", chunk => { if (stderr.length < 100_000) stderr += chunk; });
    child.on("error", err => {
      if (finished) return;
      finished = true; clearTimeout(timeout);
      send(res, 500, { error: `Não foi possível iniciar Claude Code: ${err.message}` });
    });
    child.on("close", code => {
      if (finished) return;
      finished = true; clearTimeout(timeout);
      if (code !== 0) return send(res, 502, { error: (stderr || `Claude Code saiu com código ${code}`).slice(0, 1200) });
      try {
        const parsed = JSON.parse(stdout);
        return send(res, 200, { result: parsed.result || parsed.content || stdout });
      } catch {
        return send(res, 200, { result: stdout.trim() });
      }
    });
  } catch (err) {
    send(res, 400, { error: err.message || "Requisição inválida" });
  }
});

server.listen(PORT, HOST, () => {
  console.log(`Nexus Claude Code bridge em http://${HOST}:${PORT}`);
  console.log(`Workspace permitido: ${WORKDIR}`);
  console.log(`Modo de permissões: ${MODE}`);
});
