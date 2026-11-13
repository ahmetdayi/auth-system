import { $, api, clear, formatInstant, h, loadSession, onAction, showResult } from '../api.js';

async function loadArticles() {
  const response = await api.get('/api/content/articles');
  const view = clear($('#articles-view'));
  if (!response.ok) {
    showResult(view, response);
    return;
  }
  view.append(h('table', {},
    h('thead', {}, h('tr', {}, ['Makale', 'Gereken', 'Yayın', ''].map((t) => h('th', {}, t)))),
    h('tbody', {}, response.data.map((article) => h('tr', {},
      h('td', {}, h('strong', {}, article.title), h('div', { class: 'small muted' }, article.summary)),
      h('td', {}, h('span', { class: `badge ${article.locked ? 'warn' : 'ok'}` },
        `${article.requiredTier}${article.locked ? ' · kilitli' : ''}`)),
      h('td', { class: 'small' }, formatInstant(article.publishedAt)),
      h('td', {}, h('button', {
        type: 'button', class: 'small', dataset: { action: 'read-article', id: article.id },
      }, 'Oku')))))));
}

onAction('load-articles', loadArticles);

onAction('read-article', async (button) => {
  const response = await api.get(`/api/content/articles/${button.dataset.id}`);
  const box = $('#article-result');
  if (response.ok) {
    clear(box).append(h('div', { class: 'result' },
      h('div', { class: 'result-head' }, h('span', { class: 'badge ok' }, '200'), response.data.title),
      h('div', { class: 'article-body' }, response.data.body)));
  } else {
    showResult(box, response, `Makale ${button.dataset.id}`);
  }
});

onAction('tier', async (button) => {
  const tier = button.dataset.tier;
  showResult($('#tier-result'), await api.get(`/api/content/tiers/${tier}/articles`), `${tier} listesi`);
});

onAction('premium', async () => {
  showResult($('#premium-result'), await api.get('/api/content/premium/articles'));
});

onAction('discount', async () => {
  showResult($('#discount-result'), await api.get('/api/content/discount'));
});

loadSession().then(loadArticles);
