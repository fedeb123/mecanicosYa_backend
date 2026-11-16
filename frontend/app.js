'use strict';

// Cada servicio se alcanza a través del proxy de nginx: /mechanic, /assistance y /dispatch.
const SERVICES = ['mechanic', 'assistance', 'dispatch'];

const LOCATIONS = [
  { name: 'Obelisco', lat: -34.6037, lon: -58.3816 },
  { name: 'Plaza de Mayo', lat: -34.6083, lon: -58.3712 },
  { name: 'Palermo', lat: -34.5795, lon: -58.4250 },
  { name: 'Caballito', lat: -34.6186, lon: -58.4413 },
  { name: 'Belgrano', lat: -34.5627, lon: -58.4563 },
  { name: 'Flores', lat: -34.6400, lon: -58.4400 },
];

const VEHICLE_LABELS = { BICYCLE: 'Bicicleta', E_BIKE: 'Bici eléctrica', MOTORCYCLE: 'Moto' };

// ---------- ids recordados en el navegador ----------

const STORE_KEY = 'mecanicosya-console-store';
const emptyStore = () => ({ mechanics: [], clients: [], vehicles: [], assistances: [] });

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
  store[kind] = [item, ...store[kind].filter(existing => existing.id !== item.id)].slice(0, 50);
  saveStore();
}

// ---------- llamadas HTTP ----------

const newCorrelationId = () => 'front-' + Math.random().toString(36).slice(2, 10);

// La app real genera la clave al crear el pedido y la repite en cada reintento.
const newIdempotencyKey = () => (crypto.randomUUID ? crypto.randomUUID() : 'front-' + Date.now() + '-' + Math.random().toString(36).slice(2));

async function api(service, method, path, body, correlationId = newCorrelationId(), extraHeaders = {}) {
  const headers = { 'X-Correlation-Id': correlationId, ...extraHeaders };
  if (body != null) headers['Content-Type'] = 'application/json';
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
  logRequest({ service, method, path, body, result, ms: Math.round(performance.now() - started) });
  return result;
}

function errorMessage(result) {
  if (result.status === 0) return 'No se pudo conectar con el proxy del front.';
  if (!result.data && [502, 503, 504].includes(result.status)) {
    return `El servicio no responde (HTTP ${result.status}). ¿Está levantado el contenedor?`;
  }
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
      return `<div>${o.ranking}. ${m ? escapeHtml(m.name) : `<span class="mono">${escapeHtml(o.mechanicId)}</span>`} a ${o.distanceKm.toFixed(2)} km ${statusBadge(o.status)}</div>`;
    }).join('');
    const until = d.status === 'OFFERED' && d.offersExpireAt ? ` · se puede aceptar hasta ${formatDate(d.offersExpireAt)}` : '';
    dispatchHtml = `${statusBadge(d.status)} ronda ${d.round}${until}${d.distanceKm != null ? ` · a ${d.distanceKm.toFixed(2)} km` : ''}${offers}`;
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

function logRequest({ service, method, path, body, result, ms }) {
  const li = document.createElement('li');
  const codeClass = result.ok ? 'ok' : 'err';
  const responseBody = result.data ? JSON.stringify(result.data, null, 2) : result.text;
  li.innerHTML = `
    <div class="line">
      <span class="code ${codeClass}">${result.status || 'ERR'}</span>
      <b>${escapeHtml(method)}</b>
      <span class="mono">/${escapeHtml(service)}${escapeHtml(path)}</span>
      <span class="cid">${ms} ms · ${escapeHtml(result.correlationId)}</span>
    </div>
    ${body !== undefined ? `<details><summary>request</summary><pre>${escapeHtml(JSON.stringify(body, null, 2))}</pre></details>` : ''}
    ${responseBody ? `<details ${result.ok ? '' : 'open'}><summary>response</summary><pre>${escapeHtml(responseBody)}</pre></details>` : ''}`;
  $('#log').prepend(li);
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
  $$('.select-mechanic').forEach(s => fillSelect(s, store.mechanics, m => `${m.name} (${m.id.slice(0, 8)})`, 'Elegí un mecánico'));
  $$('.select-client').forEach(s => fillSelect(s, store.clients, c => `${c.fullName} (${c.id.slice(0, 8)})`, 'Elegí un cliente'));
  $$('.select-assistance').forEach(s => fillSelect(s, store.assistances, a => `${a.label} (${a.id.slice(0, 8)})`, 'Elegí una asistencia'));
  refreshVehicleSelect();
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

// ---------- mecánicos ----------

onSubmit('#form-mechanic', async form => {
  const f = form.elements;
  const body = {
    name: f.name.value.trim(),
    email: f.email.value.trim(),
    latitude: num(f.latitude.value),
    longitude: num(f.longitude.value),
    skills: f.skills.value.split(',').map(s => s.trim()).filter(Boolean),
    vehicleTypes: $$('input[name=vehicleTypes]:checked', form).map(c => c.value),
  };
  const result = await api('mechanic', 'POST', '/api/mechanics', body);
  const box = ensureResultBox(form);
  if (!result.ok) return showError(box, result);
  remember('mechanics', { id: result.data.id, name: result.data.name });
  f.email.value = uniqueEmail('mecanico');
  showInfo(box, `Mecánico registrado. Arranca <b>no disponible</b>: activalo en "Disponibilidad".${renderMechanics([result.data])}`);
});

onSubmit('#form-availability', async form => {
  const f = form.elements;
  const box = ensureResultBox(form);
  if (!f.mechanicId.value) return showInfo(box, 'Elegí un mecánico.');
  const body = { available: f.available.checked, latitude: num(f.latitude.value), longitude: num(f.longitude.value) };
  const result = await api('mechanic', 'PATCH', `/api/mechanics/${f.mechanicId.value}/availability`, body);
  if (!result.ok) return showError(box, result);
  showInfo(box, `Actualizado.${renderMechanics([result.data])}`);
});

$('#btn-get-mechanic').addEventListener('click', async event => {
  const form = $('#form-availability');
  const box = ensureResultBox(form);
  const id = form.elements.mechanicId.value;
  if (!id) return showInfo(box, 'Elegí un mecánico.');
  const result = await withBusy(event.target, () => api('mechanic', 'GET', `/api/mechanics/${id}`));
  if (!result.ok) return showError(box, result);
  box.innerHTML = renderMechanics([result.data]);
});

onSubmit('#form-candidates', async form => {
  const f = form.elements;
  const params = new URLSearchParams({ skill: f.skill.value.trim() });
  if (f.vehicleType.value) params.set('vehicleType', f.vehicleType.value);
  const result = await api('mechanic', 'GET', `/api/mechanics/candidates?${params}`);
  const box = $('#candidates-result');
  if (!result.ok) return showError(box, result);
  box.innerHTML = renderMechanics(result.data);
});

// ---------- clientes y vehículos ----------

onSubmit('#form-client', async form => {
  const f = form.elements;
  const body = { fullName: f.fullName.value.trim(), phone: f.phone.value.trim(), email: f.email.value.trim() };
  const result = await api('assistance', 'POST', '/api/clients', body);
  const box = ensureResultBox(form);
  if (!result.ok) return showError(box, result);
  remember('clients', { id: result.data.id, fullName: result.data.fullName });
  f.email.value = uniqueEmail('repartidor');
  $('#form-vehicle [name=clientId]').value = result.data.id;
  showInfo(box, `Cliente registrado: <span class="mono">${escapeHtml(result.data.id)}</span>. Ahora cargale un vehículo.`);
});

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
  const result = await api('assistance', 'POST', `/api/clients/${f.clientId.value}/vehicles`, body);
  if (!result.ok) return showError(box, result);
  const v = result.data;
  remember('vehicles', { id: v.id, clientId: v.clientId, type: v.type, label: `${VEHICLE_LABELS[v.type] || v.type} ${v.brand} ${v.model}` });
  showInfo(box, `Vehículo registrado: <span class="mono">${escapeHtml(v.id)}</span>`);
});

$('#btn-list-vehicles').addEventListener('click', async event => {
  const clientId = $('#form-vehicle [name=clientId]').value;
  const box = $('#vehicles-result');
  if (!clientId) return showInfo(box, 'Elegí un cliente.');
  const result = await withBusy(event.target, () => api('assistance', 'GET', `/api/clients/${clientId}/vehicles`));
  if (!result.ok) return showError(box, result);
  result.data.forEach(v => remember('vehicles', { id: v.id, clientId: v.clientId, type: v.type, label: `${VEHICLE_LABELS[v.type] || v.type} ${v.brand} ${v.model}` }));
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

async function loadAssistance(id, correlationId) {
  const [assistance, dispatch, history] = await Promise.all([
    api('assistance', 'GET', `/api/assistances/${id}`, undefined, correlationId),
    api('dispatch', 'GET', `/api/dispatches/${id}`, undefined, correlationId),
    api('assistance', 'GET', `/api/assistances/${id}/history`, undefined, correlationId),
  ]);
  return { assistance, dispatch, history };
}

async function showAssistance(id, box, correlationId) {
  const { assistance, dispatch, history } = await loadAssistance(id, correlationId);
  if (!assistance.ok) return showError(box, assistance);
  box.innerHTML = renderAssistance(assistance.data, dispatch, history);
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
  const vehicle = (store.vehicles || []).find(v => v.id === f.vehicleId.value);
  const result = await api('assistance', 'POST', '/api/triage', {
    description: f.description.value.trim(),
    vehicleType: vehicle?.type || null,
  });
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
  const correlationId = newCorrelationId();
  const body = {
    clientId: f.clientId.value,
    vehicleId: f.vehicleId.value,
    description: f.description.value.trim(),
    problemType: f.problemType.value.trim() || null,
    requiredSkill: f.requiredSkill.value.trim() || null,
    latitude: num(f.latitude.value),
    longitude: num(f.longitude.value),
    clientCreatedAt: new Date().toISOString(),
  };
  const result = await api('assistance', 'POST', '/api/assistances', body, correlationId,
    { 'Idempotency-Key': newIdempotencyKey() });
  if (!result.ok) {
    showError(box, result);
    return;
  }
  const id = result.data.id;
  remember('assistances', { id, label: `${result.data.problemType} · ${body.description.slice(0, 30)}` });
  $('#form-track [name=assistanceId]').value = id;
  const dispatched = await waitForOffers(id, correlationId);
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
async function waitForOffers(id, correlationId) {
  for (let attempt = 0; attempt < 20; attempt++) {
    const current = await api('assistance', 'GET', `/api/assistances/${id}`, undefined, correlationId);
    if (!current.ok || current.data.offersExpireAt || current.data.status !== 'REQUESTED') return current;
    await new Promise(resolve => setTimeout(resolve, 400));
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

$$('#form-track [data-action]').forEach(button => {
  button.addEventListener('click', () => withBusy(button, async () => {
    const box = $('#track-result');
    const id = selectedAssistanceId();
    if (!id) return showInfo(box, 'Elegí una asistencia o pegá un id.');
    const correlationId = newCorrelationId();
    const result = await api('assistance', 'POST', `/api/assistances/${id}/${button.dataset.action}`, undefined, correlationId);
    await showAssistance(id, box, correlationId);
    if (!result.ok) box.insertAdjacentHTML('afterbegin', `<div class="message error">${escapeHtml(errorMessage(result))}</div>`);
  }));
});

// ---------- ofertas ----------

async function showOffers(mechanicId, box) {
  const result = await api('dispatch', 'GET', `/api/dispatches/offers?mechanicId=${mechanicId}`);
  if (!result.ok) return showError(box, result);
  if (result.data.length === 0) return showInfo(box, 'Este mecánico no tiene ofertas abiertas.');
  box.innerHTML = table(['Asistencia', 'Habilidad / vehículo', 'Distancia', 'Orden', 'Vence', ''], result.data.map(o => [
    `<span class="mono">${escapeHtml(o.assistanceId)}</span>`,
    `${escapeHtml(o.requiredSkill)} / ${escapeHtml(VEHICLE_LABELS[o.vehicleType] || o.vehicleType)}`,
    `${o.distanceKm.toFixed(2)} km`,
    String(o.ranking),
    formatDate(o.expiresAt),
    `<button type="button" data-offer="accept" data-id="${escapeHtml(o.assistanceId)}">Aceptar</button>
     <button type="button" data-offer="decline" data-id="${escapeHtml(o.assistanceId)}" class="ghost">Pasar</button>`,
  ]));
  box.querySelectorAll('[data-offer]').forEach(button => button.addEventListener('click', () => withBusy(button, async () => {
    const answer = await api('dispatch', 'POST', `/api/dispatches/${button.dataset.id}/${button.dataset.offer}`, { mechanicId });
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
  const ada = await api('mechanic', 'POST', '/api/mechanics', {
    name: 'Ada Mecánica', email: uniqueEmail('ada'), latitude: -34.5800, longitude: -58.4255,
    skills: ['CHAIN_REPAIR', 'TIRE_REPAIR'], vehicleTypes: ['BICYCLE', 'E_BIKE'],
  }, cid);
  if (!ada.ok) return fail(ada);
  remember('mechanics', { id: ada.data.id, name: ada.data.name });
  const linus = await api('mechanic', 'POST', '/api/mechanics', {
    name: 'Linus Motos', email: uniqueEmail('linus'), latitude: -34.6400, longitude: -58.4400,
    skills: ['BRAKE_REPAIR'], vehicleTypes: ['MOTORCYCLE'],
  }, cid);
  if (!linus.ok) return fail(linus);
  remember('mechanics', { id: linus.data.id, name: linus.data.name });
  const grace = await api('mechanic', 'POST', '/api/mechanics', {
    name: 'Grace Rodados', email: uniqueEmail('grace'), latitude: -34.5840, longitude: -58.4300,
    skills: ['CHAIN_REPAIR'], vehicleTypes: ['BICYCLE'],
  }, cid);
  if (!grace.ok) return fail(grace);
  remember('mechanics', { id: grace.data.id, name: grace.data.name });
  step('✓ Registré a Ada Mecánica, Linus Motos y Grace Rodados');

  for (const m of [ada.data, linus.data, grace.data]) {
    const r = await api('mechanic', 'PATCH', `/api/mechanics/${m.id}/availability`,
      { available: true, latitude: m.latitude, longitude: m.longitude }, cid);
    if (!r.ok) return fail(r);
  }
  step('✓ Los tres quedaron disponibles');

  const client = await api('assistance', 'POST', '/api/clients',
    { fullName: 'Repartidor Demo', phone: '1155550000', email: uniqueEmail('repartidor') }, cid);
  if (!client.ok) return fail(client);
  remember('clients', { id: client.data.id, fullName: client.data.fullName });
  const vehicle = await api('assistance', 'POST', `/api/clients/${client.data.id}/vehicles`,
    { type: 'BICYCLE', brand: 'Trek', model: 'FX 2', color: 'Negra' }, cid);
  if (!vehicle.ok) return fail(vehicle);
  remember('vehicles', { id: vehicle.data.id, clientId: client.data.id, type: 'BICYCLE', label: 'Bicicleta Trek FX 2' });
  step('✓ Registré al repartidor y su bicicleta');

  const key = newIdempotencyKey();
  const request = {
    clientId: client.data.id, vehicleId: vehicle.data.id, description: 'Se cortó la cadena en Palermo',
    latitude: -34.5795, longitude: -58.4250,
    clientCreatedAt: new Date().toISOString(),
  };
  const assistance = await api('assistance', 'POST', '/api/assistances', request, cid, { 'Idempotency-Key': key });
  if (!assistance.ok) return fail(assistance);
  remember('assistances', { id: assistance.data.id, label: 'CHAIN · Se cortó la cadena en Palermo' });
  const retry = await api('assistance', 'POST', '/api/assistances', request, cid, { 'Idempotency-Key': key });
  step(`✓ Pedí la asistencia (${statusBadge(assistance.data.status)}) escribiendo sólo el problema. La clasificación lo marcó como <span class="mono">${escapeHtml(assistance.data.problemType)}</span> / <span class="mono">${escapeHtml(assistance.data.requiredSkill)}</span>, urgencia ${triageSummary(assistance.data)}.`);
  step(`✓ Un reintento con la misma clave devolvió HTTP ${retry.status} y el mismo pedido.`);
  const offered = await waitForOffers(assistance.data.id, cid);
  if (!offered?.data?.offersExpireAt) return fail(offered || { status: 0, data: { detail: 'El despacho no respondió por Kafka' } });
  step('✓ dispatch-service recibió el pedido por Kafka y se lo ofreció a Ada y a Grace; la oferta volvió por Kafka a assistance-service.');

  const [adaAnswer, graceAnswer] = await Promise.all([
    api('dispatch', 'POST', `/api/dispatches/${assistance.data.id}/accept`, { mechanicId: ada.data.id }, cid),
    api('dispatch', 'POST', `/api/dispatches/${assistance.data.id}/accept`, { mechanicId: grace.data.id }, cid),
  ]);
  const answer = (name, r) => r.ok ? `${name} la tomó (HTTP ${r.status})` : `${name} llegó tarde (HTTP ${r.status} ${escapeHtml(r.data?.code || '')})`;
  step(`✓ Ada y Grace aceptaron al mismo tiempo: ${answer('Ada', adaAnswer)}; ${answer('Grace', graceAnswer)}.`);

  const { assistance: current, dispatch, history } = await loadAssistance(assistance.data.id, cid);
  if (!current.ok) return fail(current);
  box.insertAdjacentHTML('beforeend', renderAssistance(current.data, dispatch, history));
  $('#form-track [name=assistanceId]').value = assistance.data.id;

  // Los mecánicos de la demo se desconectan para no recibir los pedidos de la próxima corrida.
  for (const m of [ada.data, linus.data, grace.data]) {
    await api('mechanic', 'PATCH', `/api/mechanics/${m.id}/availability`,
      { available: false, latitude: m.latitude, longitude: m.longitude }, cid);
  }
}));

$('#btn-clear-store').addEventListener('click', () => {
  store = emptyStore();
  saveStore();
  showInfo($('#demo-result'), 'Listo, se olvidaron los ids guardados.');
});

$('#btn-clear-log').addEventListener('click', () => { $('#log').innerHTML = ''; });

// ---------- tabs e inicio ----------

$$('.tabs button').forEach(button => button.addEventListener('click', () => {
  $$('.tabs button').forEach(b => b.classList.toggle('active', b === button));
  $$('.panel').forEach(p => p.classList.toggle('active', p.dataset.panel === button.dataset.tab));
}));

$('#refresh-health').addEventListener('click', checkHealth);

$('#form-mechanic').elements.email.value = uniqueEmail('mecanico');
$('#form-client').elements.email.value = uniqueEmail('repartidor');
setupLocationPresets();
refreshSelects();
checkHealth();
setInterval(checkHealth, 10000);
