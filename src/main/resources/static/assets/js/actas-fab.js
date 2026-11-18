/**
 * Floating act-as control: appears after Claim, shows remaining grant time, drag + actions.
 * Leave parks the chip (reclaim possible); End removes it.
 */
import { api, formatRemaining, h } from './api.js';

const STORAGE_KEY = 'console.actasFab';
const EVENT = 'actas-changed';

let root = null;
let open = false;
let tickTimer = null;
let drag = null;
let suppressClick = false;
const DRAG_THRESHOLD_PX = 14;

function readState() {
  try {
    return JSON.parse(sessionStorage.getItem(STORAGE_KEY) || 'null');
  } catch {
    return null;
  }
}

function writeState(state) {
  if (!state) {
    sessionStorage.removeItem(STORAGE_KEY);
    return;
  }
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

const CHIP_SIZE = 76;

function setPos(el, x, y) {
  const maxX = Math.max(8, window.innerWidth - CHIP_SIZE - 8);
  const maxY = Math.max(8, window.innerHeight - CHIP_SIZE - 8);
  const nx = Math.min(maxX, Math.max(8, x));
  const ny = Math.min(maxY, Math.max(8, y));
  el.style.setProperty('--fab-x', `${nx}px`);
  el.style.setProperty('--fab-y', `${ny}px`);
  el.classList.add('is-placed');
  return { x: nx, y: ny };
}

/** Prefer menu above the chip; flip below only when there is not enough room above. */
function placeMenuDirection() {
  const host = ensureRoot();
  host.classList.remove('actas-fab--menu-down');
  if (!open) return;
  const rect = host.getBoundingClientRect();
  const menu = host.querySelector('.actas-fab-menu');
  const menuHeight = menu ? menu.getBoundingClientRect().height : 180;
  if (rect.top < menuHeight + 16) {
    host.classList.add('actas-fab--menu-down');
  }
}

function ensureRoot() {
  if (root) return root;
  root = document.createElement('div');
  root.id = 'actas-fab';
  root.className = 'actas-fab hidden';
  document.body.append(root);
  const saved = readState();
  if (saved?.x != null && saved?.y != null) {
    setPos(root, saved.x, saved.y);
  }
  return root;
}

function stopTick() {
  if (tickTimer) {
    clearInterval(tickTimer);
    tickTimer = null;
  }
}

/** Compact mm:ss for the round chip. */
function chipTime(expiresAt) {
  if (!expiresAt) return '—';
  const end = new Date(expiresAt).getTime();
  if (Number.isNaN(end)) return '—';
  const seconds = Math.max(0, Math.round((end - Date.now()) / 1000));
  if (seconds === 0) return 'bitti';
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}

function updateTimeLabel() {
  const state = readState();
  if (!state?.expiresAt || !root) return;
  for (const el of root.querySelectorAll('[data-actas-time]')) {
    el.textContent = el.dataset.actasTime === 'chip'
      ? chipTime(state.expiresAt)
      : formatRemaining(state.expiresAt);
  }
  if (chipTime(state.expiresAt) === 'bitti') {
    refreshActAsFab();
  }
}

function render() {
  const state = readState();
  const host = ensureRoot();
  if (!state?.grantId) {
    stopTick();
    host.classList.add('hidden');
    host.replaceChildren();
    return;
  }

  host.classList.remove('hidden');
  const mode = state.mode === 'active' ? 'active' : 'parked';
  const title = mode === 'active'
    ? `Act-as · #${state.grantId}`
    : `Grant #${state.grantId} (çıkıldı)`;

  const chip = h('button', {
    type: 'button',
    class: `actas-fab-chip actas-fab-chip--${mode}`,
    dataset: { actasChip: '1' },
    title: `${title} · ${state.customerEmail || ''}`,
  },
    h('span', { class: 'actas-fab-chip-label' }, mode === 'active' ? 'ACT' : 'GRN'),
    h('span', { class: 'actas-fab-chip-time', dataset: { actasTime: 'chip' } },
      chipTime(state.expiresAt)));

  const menu = h('div', { class: `actas-fab-menu${open ? '' : ' hidden'}` },
    h('p', { class: 'actas-fab-menu-title' }, title),
    state.customerEmail
      ? h('p', { class: 'actas-fab-menu-sub' }, state.customerEmail)
      : null,
    h('p', { class: 'actas-fab-menu-sub' },
      'Kalan: ',
      h('strong', { dataset: { actasTime: 'full' } }, formatRemaining(state.expiresAt))),
    h('div', { class: 'actas-fab-actions' },
      h('button', {
        type: 'button', class: 'small', dataset: { actasAction: 'extend' },
      }, 'Uzatma iste'),
      mode === 'active'
        ? h('button', {
          type: 'button', class: 'secondary small', dataset: { actasAction: 'leave' },
        }, 'Claim bırak')
        : h('button', {
          type: 'button', class: 'small', dataset: { actasAction: 'claim' },
        }, 'Claim geri al'),
      h('button', {
        type: 'button', class: 'danger secondary small', dataset: { actasAction: 'end' },
      }, 'Bitir')),
    h('p', { class: 'actas-fab-status muted small hidden', dataset: { actasStatus: '1' } }));

  host.replaceChildren(chip, menu);
  bindDrag(chip);
  stopTick();
  tickTimer = setInterval(updateTimeLabel, 1000);
  updateTimeLabel();
  placeMenuDirection();
}

function setStatus(text, isError) {
  const el = root?.querySelector('[data-actas-status]');
  if (!el) return;
  el.textContent = text || '';
  el.classList.toggle('hidden', !text);
  el.classList.toggle('actas-fab-status--err', Boolean(isError));
}

async function runAction(action) {
  const state = readState();
  if (!state?.grantId) return;
  setStatus('…');
  let response;
  if (action === 'leave') {
    response = await api.post('/api/support/session/leave');
    if (response.ok) {
      writeState({ ...state, mode: 'parked' });
      open = false;
      setStatus('Claim bırakıldı; grant açık.');
      render();
      window.dispatchEvent(new CustomEvent(EVENT));
      return;
    }
  } else if (action === 'claim') {
    response = await api.post(`/api/support/grants/${state.grantId}/claim`);
    if (response.ok && response.data?.grant) {
      const g = response.data.grant;
      writeState({
        grantId: g.id,
        expiresAt: g.expiresAt || response.data.supportAccessExpiresAt || state.expiresAt,
        customerEmail: g.customerEmail || state.customerEmail,
        mode: 'active',
        x: state.x,
        y: state.y,
      });
      open = false;
      setStatus('Claim alındı.');
      render();
      window.dispatchEvent(new CustomEvent(EVENT));
      return;
    }
    if (response.ok && response.data?.supportAccessToken) {
      writeState({ ...state, mode: 'active', expiresAt: response.data.supportAccessExpiresAt || state.expiresAt });
      open = false;
      render();
      window.dispatchEvent(new CustomEvent(EVENT));
      return;
    }
  } else if (action === 'extend') {
    response = await api.post(`/api/support/grants/${state.grantId}/extensions`);
    if (response.ok) {
      setStatus(`Uzatma istendi (${response.data?.status || 'ok'}).`);
      window.dispatchEvent(new CustomEvent(EVENT));
      return;
    }
  } else if (action === 'end') {
    response = await api.post(`/api/support/grants/${state.grantId}/end`);
    if (response.ok) {
      writeState(null);
      open = false;
      render();
      window.dispatchEvent(new CustomEvent(EVENT));
      return;
    }
  }
  setStatus(response?.code || `Hata ${response?.status || ''}`, true);
}

function bindDrag(chip) {
  chip.addEventListener('pointerdown', (event) => {
    if (event.button !== 0) return;
    const host = ensureRoot();
    const rect = host.getBoundingClientRect();
    drag = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      origX: rect.left,
      origY: rect.top,
      moved: false,
    };
    chip.setPointerCapture(event.pointerId);
  });

  chip.addEventListener('pointermove', (event) => {
    if (!drag || drag.pointerId !== event.pointerId) return;
    const dx = event.clientX - drag.startX;
    const dy = event.clientY - drag.startY;
    if (Math.hypot(dx, dy) > DRAG_THRESHOLD_PX) drag.moved = true;
    if (!drag.moved) return;
    const pos = setPos(ensureRoot(), drag.origX + dx, drag.origY + dy);
    const state = readState();
    if (state) writeState({ ...state, x: pos.x, y: pos.y });
  });

  chip.addEventListener('pointerup', (event) => {
    if (!drag || drag.pointerId !== event.pointerId) return;
    if (drag.moved) suppressClick = true;
    drag = null;
  });

  chip.addEventListener('pointercancel', () => { drag = null; });

  chip.addEventListener('click', (event) => {
    event.preventDefault();
    if (suppressClick) {
      suppressClick = false;
      return;
    }
    open = !open;
    render();
  });
}

function onRootClick(event) {
  const btn = event.target.closest('[data-actas-action]');
  if (!btn || !root?.contains(btn)) return;
  event.preventDefault();
  runAction(btn.dataset.actasAction);
}

export async function refreshActAsFab() {
  const session = await api.get('/api/support/session', { label: 'act-as fab' });
  const prev = readState();
  if (session.ok && session.data?.active) {
    writeState({
      grantId: session.data.grantId,
      expiresAt: session.data.expiresAt,
      customerEmail: session.data.customerEmail,
      mode: 'active',
      x: prev?.x,
      y: prev?.y,
    });
    render();
    return;
  }

  if (prev?.grantId) {
    const detail = await api.get(`/api/support/grants/${prev.grantId}`, { label: 'act-as grant' });
    if (detail.ok
        && (detail.data.status === 'ACTIVE' || detail.data.status === 'PENDING_EXTENSION')) {
      writeState({
        grantId: detail.data.id,
        expiresAt: detail.data.expiresAt,
        customerEmail: detail.data.customerEmail || prev.customerEmail,
        mode: 'parked',
        x: prev.x,
        y: prev.y,
      });
      render();
      return;
    }
  }

  writeState(null);
  render();
}

export function startActAsFab() {
  if (!document.querySelector('.topbar')) return;
  ensureRoot();
  if (!root.dataset.bound) {
    root.dataset.bound = '1';
    root.addEventListener('click', onRootClick);
    window.addEventListener(EVENT, () => { refreshActAsFab(); });
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') refreshActAsFab();
    });
  }
  refreshActAsFab();
}

export function notifyActAsChanged() {
  window.dispatchEvent(new CustomEvent(EVENT));
}
