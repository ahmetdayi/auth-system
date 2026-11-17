import { $, api, clear, h, loadSession, onAction, showResult } from '../api.js';

async function loadCatalog() {
  const response = await api.get('/api/admin/permissions');
  const view = clear($('#catalog-view'));
  const result = clear($('#catalog-result'));
  if (!response.ok) {
    showResult(result, response, 'İzin kataloğu');
    view.append(h('p', { class: 'muted' }, 'Katalog yüklenemedi (admin girişi gerekir).'));
    return;
  }
  const rows = response.data;
  if (!rows.length) {
    view.append(h('p', { class: 'muted' }, 'Katalog boş.'));
    return;
  }
  const table = h('table', {},
    h('thead', {}, h('tr', {},
      h('th', {}, 'Grup'),
      h('th', {}, 'Kod'),
      h('th', {}, 'Açıklama'))),
    h('tbody', {}, rows.map((row) => h('tr', {},
      h('td', {}, h('span', { class: 'badge info' }, row.group)),
      h('td', {}, h('code', {}, row.name)),
      h('td', {}, row.description)))));
  view.append(h('div', { class: 'table-wrap' }, table),
    h('p', { class: 'small muted' }, `${rows.length} kayıt · /api/admin/permissions`));
}

onAction('load-catalog', loadCatalog);

await loadSession();
await loadCatalog();
