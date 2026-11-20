// 오프라인에서도 열리도록 앱 파일을 기기에 보관한다. 파일을 고치면 VERSION을 올린다.
const VERSION = "v3";
const CACHE = "daily-log-" + VERSION;
const CORE = ["./", "index.html", "manifest.webmanifest", "icon-192.png", "icon-512.png"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(CORE)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
// 보관본을 먼저 보여 주고, 연결돼 있으면 뒤에서 새 파일로 바꿔 둔다.
self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET") return;
  e.respondWith(caches.open(CACHE).then(async (cache) => {
    const hit = await cache.match(e.request, { ignoreSearch: e.request.mode === "navigate" });
    const fresh = fetch(e.request).then((res) => {
      if (res && (res.ok || res.type === "opaque")) cache.put(e.request, res.clone());
      return res;
    }).catch(() => hit);
    return hit || fresh;
  }));
});
