'use strict';

// Cada servicio se alcanza a través del proxy de nginx: /identity, /mechanic, /assistance y /dispatch.
const SERVICES = ['identity', 'mechanic', 'assistance', 'dispatch'];

// El admin que crea identity-service al arrancar (ADMIN_EMAIL y ADMIN_PASSWORD de docker-compose.yml).
const DEFAULT_ADMIN = { email: 'admin@mecanicosya.local', password: 'admin-demo-123' };
const DEMO_PASSWORD = 'demo-pass-123';
const ROLE_LABELS = { RIDER: 'Repartidor', MECHANIC: 'Mecánico', ADMIN: 'Admin' };

const LOCATIONS = [
  { name: 'Obelisco', lat: -34.6037, lon: -58.3816 },
  { name: 'Plaza de Mayo', lat: -34.6083, lon: -58.3712 },
  { name: 'Palermo', lat: -34.5795, lon: -58.4250 },
  { name: 'Caballito', lat: -34.6186, lon: -58.4413 },
  { name: 'Belgrano', lat: -34.5627, lon: -58.4563 },
  { name: 'Flores', lat: -34.6400, lon: -58.4400 },
];

const VEHICLE_LABELS = { BICYCLE: 'Bicicleta', E_BIKE: 'Bici eléctrica', MOTORCYCLE: 'Moto' };

// ---------- ids y sesiones recordados en el navegador ----------

// Es una consola de pruebas: guarda los tokens en localStorage para poder actuar como cada usuario.
const STORE_KEY = 'mecanicosya-console-store-v2';
const emptyStore = () => ({ accounts: [], mechanics: [], clients: [], vehicles: [], assistances: [] });

function loadStore() {
  try {
    return { ...emptyStore(), ...JSON.parse(localStorage.getItem(STORE_KEY) || '{}') };
  } catch {
    return emptyStore();
  }
}

let store = loadStore();

function saveStore() {
  try {
    localStorage.setItem(STORE_KEY, JSON.stringify(store));
  } catch {
    // sin localStorage el front sigue andando, solo no recuerda entre recargas
  }
  refreshSelects();
}

function remember(kind, item) {
  const previous = store[kind].find(existing => existing.id === item.id) || {};
  store[kind] = [{ ...previous, ...item }, ...store[kind].filter(existing => existing.id !== item.id)].slice(0, 50);
  saveStore();
}

// ---------- cuentas y tokens ----------

const accountOf = userId => store.accounts.find(a => a.id === userId);
const adminAccount = () => store.accounts.find(a => a.role === 'ADMIN');
const accountsWithRole = role => store.accounts.filter(a => a.role === role);

// Guarda la sesión que devuelve identity-service (registro, login o refresh).
function rememberSession(session, extra = {}) {
  const account = {
    id: session.userId,
    role: session.role,
    verificationStatus: session.verificationStatus,
    accessToken: session.accessToken,
    refreshToken: session.refreshToken,
    expiresAt: session.expiresAt,
    ...extra,
  };
  remember('accounts', account);
  return accountOf(session.userId);
}

async function refreshSession(account, correlationId) {
  const result = await api('identity', 'POST', '/api/auth/refresh', { refreshToken: account.refreshToken }, correlationId);
  if (result.ok) rememberSession(result.data);
  return result;
}

// ---------- llamadas HTTP ----------

const newCorrelationId = () => 'front-' + Math.random().toString(36).slice(2, 10);

// La app real genera la clave al crear el pedido y la repite en cada reintento.
const newIdempotencyKey = () => (crypto.randomUUID ? crypto.randomUUID() : 'front-' + Date.now() + '-' + Math.random().toString(36).slice(2));

// as: la cuenta con cuyo token se hace el pedido. Si el token venció (401), se renueva una vez y se reintenta.
async function api(service, method, path, body, correlationId = newCorrelationId(), extraHeaders = {}, as = null) {
  const result = await send(service, method, path, body, correlationId, extraHeaders, as);
  if (result.status === 401 && as?.refreshToken) {
    const renewed = await refreshSession(as, correlationId);
    if (renewed.ok) return send(service, method, path, body, correlationId, extraHeaders, accountOf(as.id));
  }
  return result;
}

async function send(service, method, path, body, correlationId, extraHeaders, as) {
  const headers = { 'X-Correlation-Id': correlationId, ...extraHeaders };
  if (body != null) headers['Content-Type'] = 'application/json';
  if (as?.accessToken) headers.Authorization = `Bearer ${as.accessToken}`;
  const started = performance.now();
  let status = 0;
  let data = null;
  let text = '';
  try {
    const response = await fetch(`/${service}${path}`, {
      method,
      headers,
      body: body == null ? undefined : JSON.stringify(body),
    });
    status = response.status;
    text = await response.text();
    try { data = text ? JSON.parse(text) : null; } catch { data = null; }
  } catch (error) {
    text = String(error);
  }
  const result = { ok: status >= 200 && status < 300, status, data, text, correlationId };
  logRequest({ service, method, path, body, result, ms: Math.round(performance.now() - started), as });
  return result;
}

// Atajos: como el repartidor, el mecánico o el admin dueños del id.
const asUser = userId => accountOf(userId) || null;

function errorMessage(result) {
  if (result.status === 0) return 'No se pudo conectar con el proxy del front.';
  if (!result.data && [502, 503, 504].includes(result.status)) {
    return `El servicio no responde (HTTP ${result.status}). ¿Está levantado el contenedor?`;
  }
  if (result.status === 401) return 'HTTP 401: falta iniciar sesión con esa cuenta, o el token no sirve.';
  const problem = result.data || {};
  let message = `HTTP ${result.status}: ${problem.title || 'Error'}`;
  if (problem.detail) message += ` · ${problem.detail}`;
  return message;
}

// ---------- render ----------

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch]);
}

const statusBadge = status => `<span class="status ${escapeHtml(status)}">${escapeHtml(status)}</span>`;
const formatDate = value => value ? new Date(value).toLocaleString('es-AR') : '';

function showError(target, result) {
  const errors = result.data && result.data.errors;
  const list = errors && typeof errors === 'object'
    ? '<ul>' + Object.entries(errors).map(([field, msg]) => `<li><b>${escapeHtml(field)}</b>: ${escapeHtml(msg)}</li>`).join('') + '</ul>'
    : '';
  target.innerHTML = `<div class="message error">${escapeHtml(errorMessage(result))}${list}</div>`;
}

function showInfo(target, html) {
  target.innerHTML = `<div class="message info">${html}</div>`;
}

function table(headers, rows) {
  if (!rows.length) return '<div class="message info">Sin resultados.</div>';
  return `<table><thead><tr>${headers.map(h => `<th>${escapeHtml(h)}</th>`).join('')}</tr></thead>
    <tbody>${rows.map(r => `<tr>${r.map(c => `<td>${c}</td>`).join('')}</tr>`).join('')}</tbody></table>`;
}

function renderMechanics(mechanics) {
  return table(['Nombre', 'Disponible', 'Habilidades', 'Vehículos', 'Ubicación', 'Id'], mechanics.map(m => [
    escapeHtml(m.name),
    m.available ? 'Sí' : 'No',
    escapeHtml((m.skills || []).join(', ')),
    escapeHtml((m.vehicleTypes || []).map(v => VEHICLE_LABELS[v] || v).join(', ')),
    `${m.latitude}, ${m.longitude}`,
    `<span class="mono">${escapeHtml(m.id)}</span>`,
  ]));
}

function renderAssistance(assistance, dispatch, history) {
  const mechanic = store.mechanics.find(m => m.id === assistance.assignedMechanicId);
  const assigned = assistance.assignedMechanicId
    ? `${mechanic ? escapeHtml(mechanic.name) + ' · ' : ''}<span class="mono">${escapeHtml(assistance.assignedMechanicId)}</span>`
    : '—';
  let dispatchHtml;
  if (dispatch.ok) {
    const d = dispatch.data;
    const offers = (d.offers || []).map(o => {
      const m = store.mechanics.find(x => x.id === o.mechanicId);
      return `<div>${o.ranking}. ${m ? escapeHtml(m.name) : `<span class="mono">${escapeHtml(o.mechanicId)}</span>`} a ${o.distanceKm.toFixed(2)} km${eta(o)} ${statusBadge(o.status)}</div>`;
    }).join('');
    const until = d.status === 'OFFERED' && d.offersExpireAt ? ` · se puede aceptar hasta ${formatDate(d.offersExpireAt)}` : '';
    dispatchHtml = `${statusBadge(d.status)} ronda ${d.round}${until}${d.distanceKm != null ? ` · a ${d.distanceKm.toFixed(2)} km` : ''}${d.etaMinutes != null ? `, llega en ${d.etaMinutes} min` : ''}${offers}`;
  } else if (dispatch.status === 404) {
    dispatchHtml = 'Todavía no hay despacho registrado.';
  } else {
    dispatchHtml = escapeHtml(errorMessage(dispatch));
  }
  const historyRows = history.ok ? history.data.map(h => [
    h.fromStatus ? statusBadge(h.fromStatus) : '—',
    statusBadge(h.toStatus),
    formatDate(h.changedAt),
  ]) : [];
  return `
    <table>
      <tr><th>Estado</th><td>${statusBadge(assistance.status)}</td></tr>
      <tr><th>Id</th><td class="mono">${escapeHtml(assistance.id)}</td></tr>
      <tr><th>Descripción</th><td>${escapeHtml(assistance.description)}</td></tr>
      <tr><th>Problema / habilidad</th><td>${escapeHtml(assistance.problemType)} / ${escapeHtml(assistance.requiredSkill)}</td></tr>
      <tr><th>Urgencia</th><td>${triageSummary(assistance)}</td></tr>
      <tr><th>Vehículo</th><td>${escapeHtml(VEHICLE_LABELS[assistance.vehicleType] || assistance.vehicleType)}</td></tr>
      <tr><th>Ubicación</th><td>${assistance.latitude}, ${assistance.longitude}</td></tr>
      <tr><th>Mecánico asignado</th><td>${assigned}</td></tr>
      <tr><th>Despacho (dispatch-service)</th><td>${dispatchHtml}</td></tr>
      <tr><th>Creada</th><td>${formatDate(assistance.createdAt)}</td></tr>
    </table>
    <h2 style="margin-top:16px">Historial de estados</h2>
    ${history.ok ? table(['Desde', 'Hacia', 'Cuándo'], historyRows) : `<div class="message error">${escapeHtml(errorMessage(history))}</div>`}`;
}

// ---------- log de requests ----------

function logRequest({ service, method, path, body, result, ms, as }) {
  const li = document.createElement('li');
  const codeClass = result.ok ? 'ok' : 'err';
  const responseBody = result.data ? JSON.stringify(result.data, null, 2) : result.text;
  li.innerHTML = `
    <div class="line">
      <span class="code ${codeClass}">${result.status || 'ERR'}</span>
      <b>${escapeHtml(method)}</b>
      <span class="mono">/${escapeHtml(service)}${escapeHtml(path)}</span>
      <span class="cid">${as ? escapeHtml(accountLabel(as)) + ' · ' : ''}${ms} ms · ${escapeHtml(result.correlationId)}</span>
    </div>
    ${body !== undefined ? `<details><summary>request</summary><pre>${escapeHtml(JSON.stringify(body, null, 2))}</pre></details>` : ''}
    ${responseBody ? `<details ${result.ok ? '' : 'open'}><summary>response</summary><pre>${escapeHtml(responseBody)}</pre></details>` : ''}`;
  $('#log').prepend(li);
}

function accountLabel(account) {
  return `${ROLE_LABELS[account.role] || account.role}: ${account.name || account.email || account.id.slice(0, 8)}`;
}

// ---------- estado de servicios ----------

async function checkHealth() {
  await Promise.all(SERVICES.map(async service => {
    const badge = $(`.service[data-service="${service}"]`);
    try {
      const response = await fetch(`/${service}/actuator/health`);
      const data = await response.json().catch(() => ({}));
      const up = response.ok && data.status === 'UP';
      badge.classList.toggle('up', up);
      badge.classList.toggle('down', !up);
      badge.title = up ? 'UP' : `HTTP ${response.status}`;
    } catch {
      badge.classList.remove('up');
      badge.classList.add('down');
    }
  }));
}

// ---------- selects ----------

function fillSelect(select, items, label, placeholder) {
  const current = select.value;
  select.innerHTML = `<option value="">${placeholder}</option>` +
    items.map(item => `<option value="${escapeHtml(item.id)}">${escapeHtml(label(item))}</option>`).join('');
  if (items.some(item => item.id === current)) select.value = current;
}

function refreshSelects() {
  const withoutProfile = role => accountsWithRole(role).filter(a => !a.hasProfile);
  $$('.select-mechanic-account').forEach(s => fillSelect(s, withoutProfile('MECHANIC'), a => a.email || a.id, 'Elegí una cuenta de mecánico'));
  $$('.select-rider-account').forEach(s => fillSelect(s, withoutProfile('RIDER'), a => a.email || a.id, 'Elegí una cuenta de repartidor'));
  $$('.select-mechanic').forEach(s => fillSelect(s, store.mechanics, m => `${m.name} (${m.id.slice(0, 8)})`, 'Elegí un mecánico'));
  $$('.select-client').forEach(s => fillSelect(s, store.clients, c => `${c.fullName} (${c.id.slice(0, 8)})`, 'Elegí un cliente'));
  $$('.select-assistance').forEach(s => fillSelect(s, store.assistances, a => `${a.label} (${a.id.slice(0, 8)})`, 'Elegí una asistencia'));
  refreshVehicleSelect();
  renderAccounts();
}

function refreshVehicleSelect() {
  const clientId = $('#form-assistance [name=clientId]').value;
  const vehicles = store.vehicles.filter(v => v.clientId === clientId);
  fillSelect($('#form-assistance .select-vehicle'), vehicles, v => v.label, clientId ? 'Elegí un vehículo' : 'Primero elegí un cliente');
}

function setupLocationPresets() {
  $$('.location-preset').forEach(select => {
    select.innerHTML = '<option value="">(elegir)</option>' +
      LOCATIONS.map((l, i) => `<option value="${i}">${escapeHtml(l.name)}</option>`).join('');
    select.addEventListener('change', () => {
      const location = LOCATIONS[select.value];
      if (!location) return;
      const form = select.closest('form');
      form.elements[select.dataset.lat].value = location.lat;
      form.elements[select.dataset.lon].value = location.lon;
    });
  });
}

// ---------- helpers de formularios ----------

const uniqueEmail = prefix => `${prefix}-${Date.now().toString(36)}@example.com`;
const num = value => Number(value);

async function withBusy(button, work) {
  button.disabled = true;
  try { return await work(); } finally { button.disabled = false; }
}

function onSubmit(id, handler) {
  const form = $(id);
  form.addEventListener('submit', event => {
    event.preventDefault();
    withBusy(form.querySelector('button[type=submit]'), () => handler(form));
  });
}

function ensureResultBox(form) {
  let box = form.nextElementSibling;
  if (!box || !box.classList.contains('form-result')) {
    box = document.createElement('div');
    box.className = 'form-result';
    form.after(box);
  }
  return box;
}

// ---------- cuentas ----------

function renderAccounts() {
  const box = $('#accounts-result');
  if (!box) return;
  box.innerHTML = table(['Cuenta', 'Rol', 'Validación', 'Token vence', ''], store.accounts.map(a => [
    `${escapeHtml(a.name || a.email || '')}<div class="mono">${escapeHtml(a.id)}</div>`,
    escapeHtml(ROLE_LABELS[a.role] || a.role),
    a.verificationStatus ? statusBadge(a.verificationStatus) : '—',
    formatDate(a.expiresAt),
    `<button type="button" class="ghost" data-refresh="${escapeHtml(a.id)}">Renovar token</button>`,
  ]));
  box.querySelectorAll('[data-refresh]').forEach(button => button.addEventListener('click', () => withBusy(button, async () => {
    const result = await refreshSession(accountOf(button.dataset.refresh));
    if (!result.ok) showError($('#accounts-message'), result);
  })));
}

onSubmit('#form-signup', async form => {
  const f = form.elements;
  const result = await api('identity', 'POST', '/api/auth/register',
    { email: f.email.value.trim(), password: f.password.value, role: f.role.value });
  const box = ensureResultBox(form);
  if (!result.ok) return showError(box, result);
  rememberSession(result.data, { email: f.email.value.trim() });
  f.email.value = uniqueEmail(f.role.value === 'MECHANIC' ? 'mecanico' : 'repartidor');
  const next = result.data.role === 'MECHANIC'
    ? 'Ahora creá su perfil en "Mecánicos". Queda <b>pendiente de validación</b> hasta que un admin lo apruebe.'
    : 'Ahora creá su perfil en "Clientes y vehículos".';
  showInfo(box, `Cuenta creada (${escapeHtml(ROLE_LABELS[result.data.role])}). ${next}`);
});

onSubmit('#form-login', async form => {
  const f = form.elements;
  const result = await api('identity', 'POST', '/api/auth/login', { email: f.email.value.trim(), password: f.password.value });
  const box = ensureResultBox(form);
  if (!result.ok) return showError(box, result);
  rememberSession(result.data, { email: f.email.value.trim() });
  showInfo(box, `Sesión iniciada como ${escapeHtml(ROLE_LABELS[result.data.role] || result.data.role)}.`);
});

async function showPending(box) {
  const admin = adminAccount();
  if (!admin) return showInfo(box, 'Iniciá sesión como admin para ver y aprobar mecánicos.');
  const result = await api('mechanic', 'GET', '/api/mechanics?verificationStatus=PENDING_VERIFICATION', undefined, undefined, {}, admin);
  if (!result.ok) return showError(box, result);
  if (result.data.length === 0) return showInfo(box, 'No hay mecánicos pendientes.');
  box.innerHTML = table(['Nombre', 'Email', 'Habilidades', ''], result.data.map(m => [
    escapeHtml(m.name),
    escapeHtml(m.email),
    escapeHtml((m.skills || []).join(', ')),
    `<button type="button" data-approve="${escapeHtml(m.id)}">Aprobar</button>`,
  ]));
  box.querySelectorAll('[data-approve]').forEach(button => button.addEventListener('click', () => withBusy(button, async () => {
    const id = button.dataset.approve;
    const approved = await api('mechanic', 'POST', `/api/mechanics/${id}/approve`, undefined, undefined, {}, admin);
    if (!approved.ok) return showError(box, approved);
    const mechanic = accountOf(id);
    const renewed = mechanic ? await refreshUntilApproved(mechanic) : null;
    await showPending(box);
    box.insertAdjacentHTML('afterbegin', `<div class="message">Aprobado. ${renewed?.ok
      ? 'Su token ya dice APPROVED.'
      : 'Cuando renueve su token va a decir APPROVED.'}</div>`);
  })));
}

$('#btn-pending').addEventListener('click', event => withBusy(event.target, () => showPending($('#pending-result'))));

// La aprobación llega a identity-service por Kafka: se renueva hasta que el token la traiga.
async function refreshUntilApproved(account, correlationId) {
  let result = null;
  for (let attempt = 0; attempt < 20; attempt++) {
    result = await refreshSession(accountOf(account.id), correlationId);
    if (!result.ok || result.data.verificationStatus === 'APPROVED') return result;
    await sleep(400);
  }
  return result;
}

// ---------- mecánicos ----------

onSubmit('#form-mechanic', async form => {
  const f = form.elements;
  const box = ensureResultBox(form);
  const account = accountOf(f.accountId.value);
  if (!account) return showInfo(box, 'Elegí la cuenta del mecánico (se crea en "Cuentas").');
  const body = {
    name: f.name.value.trim(),
    latitude: num(f.latitude.value),
    longitude: num(f.longitude.value),
    skills: f.skills.value.split(',').map(s => s.trim()).filter(Boolean),
    vehicleTypes: $$('input[name=vehicleTypes]:checked', form).map(c => c.value),
  };
  const result = await api('mechanic', 'POST', '/api/mechanics', body, undefined, {}, account);
  if (!result.ok) return showError(box, result);
  remember('accounts', { id: account.id, name: result.data.name, hasProfile: true });
  remember('mechanics', { id: result.data.id, name: result.data.name });
  showInfo(box, `Perfil creado. Está <b>pendiente de validación</b>: un admin lo aprueba en "Cuentas" y recién ahí puede ponerse disponible.${renderMechanics([result.data])}`);
});

onSubmit('#form-availability', async form => {
  const f = form.elements;
  const box = ensureResultBox(form);
  if (!f.mechanicId.value) return showInfo(box, 'Elegí un mecánico.');
  const body = { available: f.available.checked, latitude: num(f.latitude.value), longitude: num(f.longitude.value) };
  const result = await api('mechanic', 'PATCH', `/api/mechanics/${f.mechanicId.value}/availability`, body, undefined, {}, asUser(f.mechanicId.value));
  if (!result.ok) return showError(box, result);
  showInfo(box, `Actualizado.${renderMechanics([result.data])}`);
});

$('#btn-get-mechanic').addEventListener('click', async event => {
  const form = $('#form-availability');
  const box = ensureResultBox(form);
  const id = form.elements.mechanicId.value;
  if (!id) return showInfo(box, 'Elegí un mecánico.');
  const result = await withBusy(event.target, () => api('mechanic', 'GET', `/api/mechanics/${id}`, undefined, undefined, {}, asUser(id)));
  if (!result.ok) return showError(box, result);
  box.innerHTML = renderMechanics([result.data]);
});

onSubmit('#form-candidates', async form => {
  const f = form.elements;
  const box = $('#candidates-result');
  const admin = adminAccount();
  if (!admin) return showInfo(box, 'La búsqueda de candidatos la usa dispatch-service; desde la consola, iniciá sesión como admin.');
  const params = new URLSearchParams({ skill: f.skill.value.trim() });
  if (f.vehicleType.value) params.set('vehicleType', f.vehicleType.value);
  const result = await api('mechanic', 'GET', `/api/mechanics/candidates?${params}`, undefined, undefined, {}, admin);
  if (!result.ok) return showError(box, result);
  box.innerHTML = renderMechanics(result.data);
});

// ---------- clientes y vehículos ----------

onSubmit('#form-client', async form => {
  const f = form.elements;
  const box = ensureResultBox(form);
  const account = accountOf(f.accountId.value);
  if (!account) return showInfo(box, 'Elegí la cuenta del repartidor (se crea en "Cuentas").');
  const body = { fullName: f.fullName.value.trim(), phone: f.phone.value.trim() };
  const result = await api('assistance', 'POST', '/api/clients', body, undefined, {}, account);
  if (!result.ok) return showError(box, result);
  remember('accounts', { id: account.id, name: result.data.fullName, hasProfile: true });
  remember('clients', { id: result.data.id, fullName: result.data.fullName });
  $('#form-vehicle [name=clientId]').value = result.data.id;
  showInfo(box, `Cliente registrado: <span class="mono">${escapeHtml(result.data.id)}</span> (el mismo id que su cuenta). Ahora cargale un vehículo.`);
});

const vehicleLabel = v => `${VEHICLE_LABELS[v.type] || v.type} ${v.brand} ${v.model}`;

onSubmit('#form-vehicle', async form => {
  const f = form.elements;
  const box = $('#vehicles-result');
  if (!f.clientId.value) return showInfo(box, 'Elegí un cliente.');
  const body = {
    type: f.type.value,
    brand: f.brand.value.trim(),
    model: f.model.value.trim(),
    color: f.color.value.trim() || null,
    plate: f.plate.value.trim() || null,
  };
  const result = await api('assistance', 'POST', `/api/clients/${f.clientId.value}/vehicles`, body, undefined, {}, asUser(f.clientId.value));
  if (!result.ok) return showError(box, result);
  const v = result.data;
  remember('vehicles', { id: v.id, clientId: v.clientId, type: v.type, label: vehicleLabel(v) });
  showInfo(box, `Vehículo registrado: <span class="mono">${escapeHtml(v.id)}</span>`);
});

$('#btn-list-vehicles').addEventListener('click', async event => {
  const clientId = $('#form-vehicle [name=clientId]').value;
  const box = $('#vehicles-result');
  if (!clientId) return showInfo(box, 'Elegí un cliente.');
  const result = await withBusy(event.target, () => api('assistance', 'GET', `/api/clients/${clientId}/vehicles`, undefined, undefined, {}, asUser(clientId)));
  if (!result.ok) return showError(box, result);
  result.data.forEach(v => remember('vehicles', { id: v.id, clientId: v.clientId, type: v.type, label: vehicleLabel(v) }));
  box.innerHTML = table(['Tipo', 'Marca / modelo', 'Color', 'Patente', 'Id'], result.data.map(v => [
    escapeHtml(VEHICLE_LABELS[v.type] || v.type),
    escapeHtml(`${v.brand} ${v.model}`),
    escapeHtml(v.color),
    escapeHtml(v.plate),
    `<span class="mono">${escapeHtml(v.id)}</span>`,
  ]));
});

// ---------- asistencias ----------

$('#form-assistance [name=clientId]').addEventListener('change', refreshVehicleSelect);

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

// El pedido lo ve su repartidor; el estado del despacho, el admin (si hay uno con sesión).
async function loadAssistance(id, correlationId) {
  const owner = asUser(store.assistances.find(a => a.id === id)?.clientId) || adminAccount();
  const admin = adminAccount();
  const [assistance, dispatch, history] = await Promise.all([
    api('assistance', 'GET', `/api/assistances/${id}`, undefined, correlationId, {}, owner),
    admin
      ? api('dispatch', 'GET', `/api/dispatches/${id}`, undefined, correlationId, {}, admin)
      : Promise.resolve({ ok: false, status: 403, data: { title: 'Sólo el admin ve el despacho', detail: 'Iniciá sesión como admin en "Cuentas".' } }),
    api('assistance', 'GET', `/api/assistances/${id}/history`, undefined, correlationId, {}, owner),
  ]);
  return { assistance, dispatch, history };
}

async function showAssistance(id, box, correlationId) {
  const { assistance, dispatch, history } = await loadAssistance(id, correlationId);
  if (!assistance.ok) return showError(box, assistance);
  box.innerHTML = renderAssistance(assistance.data, dispatch, history);
}

// Tiempo de llegada: "por calle" si salió de OpenRouteService, "estimado" si es en línea recta.
function eta(o) {
  if (o.etaMinutes == null) return '';
  const source = o.etaSource === 'ROUTE' ? 'por calle' : o.etaSource === 'ESTIMATE' ? 'estimado' : '';
  return ` · ${o.etaMinutes} min${source ? ` ${source}` : ''}`;
}

const URGENCY_LABELS = { LOW: 'baja', MEDIUM: 'media', HIGH: 'alta' };
const TRIAGE_SOURCE_LABELS = { AI: 'IA', KEYWORDS: 'palabras clave' };

function triageSummary(t) {
  if (!t.urgency) return '—';
  const source = TRIAGE_SOURCE_LABELS[t.triageSource || t.source] || '';
  const confidence = t.triageConfidence ?? t.confidence;
  return `${escapeHtml(URGENCY_LABELS[t.urgency] || t.urgency)} · ${escapeHtml(source)}`
    + (confidence == null ? '' : ` · confianza ${Math.round(confidence * 100)}%`);
}

$('#btn-triage').addEventListener('click', async () => {
  const form = $('#form-assistance');
  const f = form.elements;
  const box = ensureResultBox(form);
  const rider = asUser(f.clientId.value);
  if (!rider) return showInfo(box, 'Elegí un cliente con sesión.');
  const vehicle = (store.vehicles || []).find(v => v.id === f.vehicleId.value);
  const result = await api('assistance', 'POST', '/api/triage', {
    description: f.description.value.trim(),
    vehicleType: vehicle?.type || null,
  }, undefined, {}, rider);
  if (!result.ok) return showError(box, result);
  f.problemType.value = result.data.problemType;
  f.requiredSkill.value = result.data.requiredSkill;
  showInfo(box, `Clasificación: <span class="mono">${escapeHtml(result.data.problemType)}</span> /
    <span class="mono">${escapeHtml(result.data.requiredSkill)}</span>, urgencia ${triageSummary(result.data)}.
    Podés cambiar los campos antes de pedir la asistencia.`);
});

onSubmit('#form-assistance', async form => {
  const f = form.elements;
  const box = ensureResultBox(form);
  if (!f.clientId.value || !f.vehicleId.value) return showInfo(box, 'Elegí cliente y vehículo.');
  const rider = asUser(f.clientId.value);
  const correlationId = newCorrelationId();
  // El cliente sale del token del repartidor: el cuerpo no lleva clientId.
  const body = {
    vehicleId: f.vehicleId.value,
    description: f.description.value.trim(),
    problemType: f.problemType.value.trim() || null,
    requiredSkill: f.requiredSkill.value.trim() || null,
    latitude: num(f.latitude.value),
    longitude: num(f.longitude.value),
    clientCreatedAt: new Date().toISOString(),
  };
  const result = await api('assistance', 'POST', '/api/assistances', body, correlationId,
    { 'Idempotency-Key': newIdempotencyKey() }, rider);
  if (!result.ok) {
    showError(box, result);
    return;
  }
  const id = result.data.id;
  remember('assistances', { id, clientId: f.clientId.value, label: `${result.data.problemType} · ${body.description.slice(0, 30)}` });
  $('#form-track [name=assistanceId]').value = id;
  const dispatched = await waitForOffers(id, correlationId, rider);
  await showAssistance(id, $('#track-result'), correlationId);
  const state = !dispatched
    ? 'Todavía no llegó la respuesta del despacho por Kafka; actualizá el seguimiento en unos segundos.'
    : dispatched.data?.status === 'UNMATCHED'
    ? 'No hay mecánicos disponibles: quedó UNMATCHED.'
    : 'Se ofreció a los mecánicos cercanos; aceptala desde "Ofertas del mecánico".';
  showInfo(box, `Asistencia creada. ${state} Correlation id: <span class="mono">${correlationId}</span>`);
});

// El despacho llega por Kafka: después del alta, se espera a que assistance-service reciba la oferta (o el aviso
// de que no hay mecánicos) antes de mostrar el pedido.
async function waitForOffers(id, correlationId, rider) {
  for (let attempt = 0; attempt < 20; attempt++) {
    const current = await api('assistance', 'GET', `/api/assistances/${id}`, undefined, correlationId, {}, rider);
    if (!current.ok || current.data.offersExpireAt || current.data.status !== 'REQUESTED') return current;
    await sleep(400);
  }
  return null;
}

function selectedAssistanceId() {
  const f = $('#form-track').elements;
  return f.manualId.value.trim() || f.assistanceId.value;
}

onSubmit('#form-track', async () => {
  const box = $('#track-result');
  const id = selectedAssistanceId();
  if (!id) return showInfo(box, 'Elegí una asistencia o pegá un id.');
  await showAssistance(id, box);
});

// Volver a publicar y cancelar los hace el repartidor; iniciar y completar, el mecánico asignado.
$$('#form-track [data-action]').forEach(button => {
  button.addEventListener('click', () => withBusy(button, async () => {
    const box = $('#track-result');
    const id = selectedAssistanceId();
    if (!id) return showInfo(box, 'Elegí una asistencia o pegá un id.');
    const correlationId = newCorrelationId();
    const action = button.dataset.action;
    const rider = asUser(store.assistances.find(a => a.id === id)?.clientId);
    let as = rider || adminAccount();
    if (action === 'start' || action === 'complete') {
      const current = await api('assistance', 'GET', `/api/assistances/${id}`, undefined, correlationId, {}, as);
      as = asUser(current.data?.assignedMechanicId) || adminAccount();
    }
    const result = await api('assistance', 'POST', `/api/assistances/${id}/${action}`, undefined, correlationId, {}, as);
    await showAssistance(id, box, correlationId);
    if (!result.ok) box.insertAdjacentHTML('afterbegin', `<div class="message error">${escapeHtml(errorMessage(result))}</div>`);
  }));
});

// ---------- ofertas ----------

async function showOffers(mechanicId, box) {
  const mechanic = asUser(mechanicId);
  const result = await api('dispatch', 'GET', '/api/dispatches/offers', undefined, undefined, {}, mechanic);
  if (!result.ok) return showError(box, result);
  if (result.data.length === 0) return showInfo(box, 'Este mecánico no tiene ofertas abiertas.');
  box.innerHTML = table(['Asistencia', 'Habilidad / vehículo', 'Distancia y llegada', 'Orden', 'Vence', ''], result.data.map(o => [
    `<span class="mono">${escapeHtml(o.assistanceId)}</span>`,
    `${escapeHtml(o.requiredSkill)} / ${escapeHtml(VEHICLE_LABELS[o.vehicleType] || o.vehicleType)}`,
    `${o.distanceKm.toFixed(2)} km${eta(o)}`,
    String(o.ranking),
    formatDate(o.expiresAt),
    `<button type="button" data-offer="accept" data-id="${escapeHtml(o.assistanceId)}">Aceptar</button>
     <button type="button" data-offer="decline" data-id="${escapeHtml(o.assistanceId)}" class="ghost">Pasar</button>`,
  ]));
  box.querySelectorAll('[data-offer]').forEach(button => button.addEventListener('click', () => withBusy(button, async () => {
    // El mecánico sale del token: no hace falta mandar su id.
    const answer = await api('dispatch', 'POST', `/api/dispatches/${button.dataset.id}/${button.dataset.offer}`, undefined, undefined, {}, accountOf(mechanicId));
    await showOffers(mechanicId, box);
    if (!answer.ok) {
      box.insertAdjacentHTML('afterbegin', `<div class="message error">${escapeHtml(errorMessage(answer))}</div>`);
    } else if (button.dataset.offer === 'accept') {
      $('#form-track [name=assistanceId]').value = button.dataset.id;
      box.insertAdjacentHTML('afterbegin', '<div class="message">Asistencia tomada. Mirala en "Seguimiento".</div>');
    }
  })));
}

onSubmit('#form-offers', async form => {
  const mechanicId = form.elements.mechanicId.value;
  if (!mechanicId) return showInfo($('#offers-result'), 'Elegí un mecánico.');
  await showOffers(mechanicId, $('#offers-result'));
});

// ---------- demo ----------

$('#btn-demo').addEventListener('click', event => withBusy(event.target, async () => {
  const box = $('#demo-result');
  const cid = 'demo-front-' + Date.now().toString(36);
  const steps = [];
  const step = text => { steps.push(text); showInfo(box, steps.map(s => `<div>${s}</div>`).join('')); };
  const fail = result => { showError(box, result); return null; };

  step(`Correlation id: <span class="mono">${cid}</span>`);
  const login = await api('identity', 'POST', '/api/auth/login', DEFAULT_ADMIN, cid);
  if (!login.ok) return fail(login);
  const admin = rememberSession(login.data, { email: DEFAULT_ADMIN.email });

  const signUp = async (prefix, role, name) => {
    const email = uniqueEmail(prefix);
    const result = await api('identity', 'POST', '/api/auth/register', { email, password: DEMO_PASSWORD, role }, cid);
    return result.ok ? { result, account: rememberSession(result.data, { email, name }) } : { result };
  };

  const profiles = [
    { prefix: 'ada', name: 'Ada Mecánica', phone: '11 5555 0101', latitude: -34.5800, longitude: -58.4255, skills: ['CHAIN_REPAIR', 'TIRE_REPAIR'], vehicleTypes: ['BICYCLE', 'E_BIKE'] },
    { prefix: 'linus', name: 'Linus Motos', phone: '11 5555 0102', latitude: -34.6400, longitude: -58.4400, skills: ['BRAKE_REPAIR'], vehicleTypes: ['MOTORCYCLE'] },
    { prefix: 'grace', name: 'Grace Rodados', phone: '11 5555 0103', latitude: -34.5840, longitude: -58.4300, skills: ['CHAIN_REPAIR'], vehicleTypes: ['BICYCLE'] },
  ];
  const mechanics = [];
  for (const { prefix, ...profile } of profiles) {
    const { result, account } = await signUp(prefix, 'MECHANIC', profile.name);
    if (!account) return fail(result);
    const created = await api('mechanic', 'POST', '/api/mechanics', profile, cid, {}, account);
    if (!created.ok) return fail(created);
    remember('accounts', { id: account.id, hasProfile: true });
    remember('mechanics', { id: created.data.id, name: created.data.name });
    mechanics.push(created.data);
  }
  const [ada, linus, grace] = mechanics;
  step('✓ Ada Mecánica, Linus Motos y Grace Rodados se registraron en identity-service y crearon su perfil (el id y el email salen del token)');

  const early = await api('mechanic', 'PATCH', `/api/mechanics/${ada.id}/availability`,
    { available: true, latitude: ada.latitude, longitude: ada.longitude }, cid, {}, accountOf(ada.id));
  step(`✓ Sin aprobar, Ada intentó ponerse disponible y recibió HTTP ${early.status}`);

  for (const m of mechanics) {
    const approved = await api('mechanic', 'POST', `/api/mechanics/${m.id}/approve`, undefined, cid, {}, admin);
    if (!approved.ok) return fail(approved);
    const renewed = await refreshUntilApproved(accountOf(m.id), cid);
    if (!renewed?.ok || renewed.data.verificationStatus !== 'APPROVED') {
      return fail(renewed || { status: 0, data: { detail: 'La aprobación no llegó a identity-service por Kafka' } });
    }
  }
  step('✓ El admin aprobó a los tres; la aprobación viajó por Kafka a identity-service y sus tokens renovados dicen APPROVED');

  for (const m of mechanics) {
    const r = await api('mechanic', 'PATCH', `/api/mechanics/${m.id}/availability`,
      { available: true, latitude: m.latitude, longitude: m.longitude }, cid, {}, accountOf(m.id));
    if (!r.ok) return fail(r);
  }
  step('✓ Los tres quedaron disponibles');

  const { result: riderResult, account: rider } = await signUp('repartidor', 'RIDER', 'Repartidor Demo');
  if (!rider) return fail(riderResult);
  const client = await api('assistance', 'POST', '/api/clients', { fullName: 'Repartidor Demo', phone: '1155550000' }, cid, {}, rider);
  if (!client.ok) return fail(client);
  remember('accounts', { id: rider.id, hasProfile: true });
  remember('clients', { id: client.data.id, fullName: client.data.fullName });
  const vehicle = await api('assistance', 'POST', `/api/clients/${client.data.id}/vehicles`,
    { type: 'BICYCLE', brand: 'Trek', model: 'FX 2', color: 'Negra' }, cid, {}, rider);
  if (!vehicle.ok) return fail(vehicle);
  remember('vehicles', { id: vehicle.data.id, clientId: client.data.id, type: 'BICYCLE', label: 'Bicicleta Trek FX 2' });
  step('✓ Registré al repartidor y su bicicleta');

  const key = newIdempotencyKey();
  const request = {
    vehicleId: vehicle.data.id, description: 'Se cortó la cadena en Palermo',
    latitude: -34.5795, longitude: -58.4250,
    clientCreatedAt: new Date().toISOString(),
  };
  const assistance = await api('assistance', 'POST', '/api/assistances', request, cid, { 'Idempotency-Key': key }, rider);
  if (!assistance.ok) return fail(assistance);
  remember('assistances', { id: assistance.data.id, clientId: client.data.id, label: 'CHAIN · Se cortó la cadena en Palermo' });
  const retry = await api('assistance', 'POST', '/api/assistances', request, cid, { 'Idempotency-Key': key }, rider);
  step(`✓ Pedí la asistencia (${statusBadge(assistance.data.status)}) escribiendo sólo el problema. La clasificación lo marcó como <span class="mono">${escapeHtml(assistance.data.problemType)}</span> / <span class="mono">${escapeHtml(assistance.data.requiredSkill)}</span>, urgencia ${triageSummary(assistance.data)}.`);
  step(`✓ Un reintento con la misma clave devolvió HTTP ${retry.status} y el mismo pedido.`);
  const offered = await waitForOffers(assistance.data.id, cid, rider);
  if (!offered?.data?.offersExpireAt) return fail(offered || { status: 0, data: { detail: 'El despacho no respondió por Kafka' } });
  step('✓ dispatch-service recibió el pedido por Kafka y se lo ofreció a Ada y a Grace; la oferta volvió por Kafka a assistance-service.');

  const [adaAnswer, graceAnswer] = await Promise.all([
    api('dispatch', 'POST', `/api/dispatches/${assistance.data.id}/accept`, undefined, cid, {}, accountOf(ada.id)),
    api('dispatch', 'POST', `/api/dispatches/${assistance.data.id}/accept`, undefined, cid, {}, accountOf(grace.id)),
  ]);
  const answer = (name, r) => r.ok ? `${name} la tomó (HTTP ${r.status})` : `${name} llegó tarde (HTTP ${r.status} ${escapeHtml(r.data?.code || '')})`;
  step(`✓ Ada y Grace aceptaron al mismo tiempo, cada una con su token: ${answer('Ada', adaAnswer)}; ${answer('Grace', graceAnswer)}.`);

  const { assistance: current, dispatch, history } = await loadAssistance(assistance.data.id, cid);
  if (!current.ok) return fail(current);
  box.insertAdjacentHTML('beforeend', renderAssistance(current.data, dispatch, history));
  $('#form-track [name=assistanceId]').value = assistance.data.id;

  // Los mecánicos de la demo se desconectan para no recibir los pedidos de la próxima corrida.
  for (const m of [ada, linus, grace]) {
    await api('mechanic', 'PATCH', `/api/mechanics/${m.id}/availability`,
      { available: false, latitude: m.latitude, longitude: m.longitude }, cid, {}, accountOf(m.id));
  }
}));

$('#btn-clear-store').addEventListener('click', () => {
  store = emptyStore();
  saveStore();
  showInfo($('#demo-result'), 'Listo, se olvidaron las cuentas y los ids guardados.');
});

$('#btn-clear-log').addEventListener('click', () => { $('#log').innerHTML = ''; });

// ---------- tabs e inicio ----------

$$('.tabs button').forEach(button => button.addEventListener('click', () => {
  $$('.tabs button').forEach(b => b.classList.toggle('active', b === button));
  $$('.panel').forEach(p => p.classList.toggle('active', p.dataset.panel === button.dataset.tab));
}));

$('#refresh-health').addEventListener('click', checkHealth);

$('#form-signup').elements.email.value = uniqueEmail('repartidor');
$('#form-signup').elements.role.addEventListener('change', event => {
  $('#form-signup').elements.email.value = uniqueEmail(event.target.value === 'MECHANIC' ? 'mecanico' : 'repartidor');
});
$('#form-login').elements.email.value = DEFAULT_ADMIN.email;
$('#form-login').elements.password.value = DEFAULT_ADMIN.password;
setupLocationPresets();
refreshSelects();
checkHealth();
setInterval(checkHealth, 10000);
