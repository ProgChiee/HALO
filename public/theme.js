// Start public pages in Light before React renders. Account binding happens after authentication.
(() => {
  const valid = value => ['light', 'dark', 'system'].includes(value) ? value : 'light';
  const media = window.matchMedia?.('(prefers-color-scheme: dark)');
  let key = null;
  let preference = 'light';
  const read = () => { try { return key ? valid(localStorage.getItem(key)) : 'light'; } catch { return 'light'; } };
  function apply() {
    document.documentElement.dataset.theme = preference === 'system' ? (media?.matches ? 'dark' : 'light') : preference;
    window.dispatchEvent(new Event('halo:theme-change'));
  }
  window.haloTheme = {
    get: () => preference,
    bindAccount(account) {
      const nextKey = account ? `halo-theme-${account}` : null;
      if (key === nextKey) return;
      key = nextKey;
      preference = read();
      apply();
    },
    set(value) {
      if (!key) return;
      preference = valid(value);
      try { localStorage.setItem(key, preference); } catch { /* Keep the in-memory choice when storage is unavailable. */ }
      apply();
    },
  };
  media?.addEventListener('change', () => { if (preference === 'system') apply(); });
  window.addEventListener('storage', event => {
    if (key && (event.key === key || event.key === null)) { preference = read(); apply(); }
  });
  apply();
})();
