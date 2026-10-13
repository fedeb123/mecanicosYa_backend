// Captura la consola web en uso, manejando Chrome headless por DevTools Protocol.
// Uso: node scripts/capturas-consola.mjs <directorio-salida>  (requiere Node 22+ y Google Chrome)
import { spawn } from 'node:child_process';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const OUT = process.argv[2];
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const PORT = 9333;
const sleep = ms => new Promise(r => setTimeout(r, ms));

const chrome = spawn(CHROME, ['--headless=new', '--disable-gpu', '--hide-scrollbars', `--remote-debugging-port=${PORT}`,
  `--user-data-dir=${mkdtempSync(join(tmpdir(), 'cdp-'))}`, 'about:blank'], { stdio: 'ignore' });

let target;
for (let i = 0; i < 50 && !target; i++) {
  try { target = (await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()).find(t => t.type === 'page'); }
  catch { await sleep(200); }
}
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise(r => ws.addEventListener('open', r));
let seq = 0;
const pending = new Map();
ws.addEventListener('message', e => {
  const msg = JSON.parse(e.data);
  if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); }
});
const send = (method, params = {}) => new Promise(r => { const id = ++seq; pending.set(id, r); ws.send(JSON.stringify({ id, method, params })); });
const run = async expr => {
  const res = await send('Runtime.evaluate', { expression: `(async () => { ${expr} })()`, awaitPromise: true, returnByValue: true });
  if (res.result?.exceptionDetails) throw new Error(JSON.stringify(res.result.exceptionDetails));
  return res.result?.result?.value;
};
const waitFor = async (cond, ms = 20000) => {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) { if (await run(`return !!(${cond})`)) return; await sleep(300); }
  throw new Error('timeout esperando ' + cond);
};
const shot = async name => {
  await sleep(400);
  const h = await run('return Math.max(document.documentElement.scrollHeight, 900)');
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: h, deviceScaleFactor: 1, mobile: false });
  await sleep(300);
  const { result } = await send('Page.captureScreenshot', { format: 'png' });
  writeFileSync(join(OUT, name), Buffer.from(result.data, 'base64'));
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });
  console.log('ok', name);
};
const tab = name => run(`document.querySelector('.tabs button[data-tab="${name}"]').click()`);

try {
  await send('Page.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: 'http://localhost:8080' });
  await sleep(2500);
  await run(`localStorage.clear(); location.reload()`).catch(() => {});
  await sleep(2500);

  // 1 · demo rápida
  await tab('demo');
  await run(`document.querySelector('#btn-demo').click()`);
  await waitFor(`document.querySelector('#demo-result')?.textContent.includes('Listo')`);
  await shot('11a-consola-demo.png');

  // 2 · mecánicos: buscar candidatos
  await tab('mechanics');
  await run(`[...document.querySelectorAll('button')].find(b => b.textContent.trim() === 'Buscar').click()`);
  await waitFor(`[...document.querySelectorAll('[data-panel="mechanics"] table')].length > 0`);
  await shot('11b-consola-mecanicos.png');

  // 3 · clientes y vehículos
  await tab('clients');
  await run(`const f = document.querySelector('#form-client'); f.elements.email.value = 'repartidor-' + Date.now() + '@example.com';
             f.requestSubmit();`);
  await sleep(1500);
  await run(`document.querySelector('#form-vehicle').requestSubmit()`);
  await sleep(1500);
  await run(`document.querySelector('#btn-list-vehicles').click()`);
  await sleep(1500);
  await shot('11c-consola-clientes.png');

  // 4 · asistencias: pedir, iniciar y completar
  await tab('assistances');
  await run(`const f = document.querySelector('#form-assistance');
             const c = f.elements.clientId; c.selectedIndex = c.options.length - 1; c.dispatchEvent(new Event('change'));
             const v = f.elements.vehicleId; v.selectedIndex = v.options.length - 1;
             f.requestSubmit();`);
  await waitFor(`document.querySelector('#track-result')?.textContent.includes('MATCHED')`);
  await run(`document.querySelector('#form-track [data-action="start"]').click()`);
  await waitFor(`document.querySelector('#track-result')?.textContent.includes('IN_PROGRESS')`);
  await run(`document.querySelector('#form-track [data-action="complete"]').click()`);
  await waitFor(`document.querySelector('#track-result')?.textContent.includes('COMPLETED')`);
  await shot('11d-consola-asistencias.png');

  // 5 · error: cancelar una asistencia completada (409)
  await run(`document.querySelector('#form-track [data-action="cancel"]').click()`);
  await waitFor(`document.querySelector('#track-result .message.error')`);
  await shot('11e-consola-error-409.png');
} finally {
  ws.close();
  chrome.kill();
}
