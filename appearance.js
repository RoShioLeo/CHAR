(() => {
    const key = 'lemonslice-color-mode';
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    let mode = 'system';
    try {
        const saved = localStorage.getItem(key);
        if (['light', 'dark', 'system'].includes(saved)) mode = saved;
    } catch (_) { /* The current page can still change appearance. */ }
    function apply() {
        const scheme = mode === 'system' ? (media.matches ? 'dark' : 'light') : mode;
        document.documentElement.dataset.colorMode = mode;
        document.documentElement.dataset.colorScheme = scheme;
        document.documentElement.style.colorScheme = scheme;
        document.querySelector('meta[name="theme-color"]').content = scheme === 'dark' ? '#15171b' : '#ffffff';
        document.querySelectorAll('[data-appearance]').forEach(button => {
            button.setAttribute('aria-pressed', String(button.dataset.appearance === mode));
        });
    }
    apply();
    media.addEventListener('change', apply);
    window.addEventListener('storage', event => {
        if (event.key === key) {
            mode = ['light', 'dark', 'system'].includes(event.newValue) ? event.newValue : 'system';
            apply();
        }
    });
    document.addEventListener('DOMContentLoaded', () => {
        apply();
        document.querySelectorAll('[data-appearance]').forEach(button => {
            button.addEventListener('click', () => {
                mode = button.dataset.appearance;
                try { localStorage.setItem(key, mode); } catch (_) { /* Keep the in-memory setting. */ }
                apply();
                document.querySelector('.appearance').open = false;
            });
        });
        document.addEventListener('click', event => {
            if (!event.target.closest('.appearance')) document.querySelector('.appearance').open = false;
        });
        document.addEventListener('keydown', event => {
            if (event.key === 'Escape') document.querySelector('.appearance').open = false;
        });
    });
})();
