const CACHE_NAME = 'my-sales-diary-pwa-v2';

function getBaseUrl() {
  return (self.registration && self.registration.scope)
    ? self.registration.scope
    : new URL('./', self.location.href).href;
}

const RELATIVE_ASSETS = [
  './',
  './index.html',
  './styles.css',
  './app.js',
  './manifest.json',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-512.png'
];

self.addEventListener('install', (event) => {
  const baseUrl = getBaseUrl();
  const precacheUrls = RELATIVE_ASSETS.map((path) => new URL(path, baseUrl).href);
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(precacheUrls))
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys
          .filter((key) => key !== CACHE_NAME)
          .map((key) => caches.delete(key))
      )
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;

  const baseUrl = getBaseUrl();
  const requestUrl = new URL(event.request.url);

  // Only handle requests within this GitHub Pages project base path
  if (!requestUrl.href.startsWith(baseUrl)) return;

  const indexUrl = new URL('./index.html', baseUrl).href;

  if (event.request.mode === 'navigate') {
    event.respondWith(
      caches.match(indexUrl).then((cached) => {
        const networkFetch = fetch(event.request)
          .then((response) => {
            if (response && response.ok) {
              const copy = response.clone();
              caches.open(CACHE_NAME).then((cache) => cache.put(indexUrl, copy));
            }
            return response;
          })
          .catch(() => cached);
        return cached || networkFetch;
      })
    );
    return;
  }

  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request).then((response) => {
        if (response && response.ok && response.type === 'basic') {
          const copy = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy));
        }
        return response;
      });
    })
  );
});
