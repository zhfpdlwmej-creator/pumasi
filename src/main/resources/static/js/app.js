// 공통 유틸 + 실시간 소켓
const PALETTE = ["#fb7185","#38bdf8","#fbbf24","#34d399","#a78bfa","#fb923c"];

// HTML escape — innerHTML 에 사용자 입력을 끼워 넣을 때 항상 통과시킬 것
function esc(s) {
  return String(s == null ? "" : s).replace(/[&<>"']/g, (c) => (
    { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]
  ));
}

// 이미지 URL 화이트리스트 — data:image/ 또는 https:// 만 허용 (javascript: 등 차단)
function safeImgSrc(url) {
  if (!url) return "";
  return /^(data:image\/|https:\/\/)/i.test(url) ? esc(url) : "";
}

function avatarColor(name) {
  const s = name || "?";
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
  return PALETTE[h % PALETTE.length];
}

function avatar(nickname, url, size) {
  const initial = (nickname || "?").trim().charAt(0) || "?";
  if (url) {
    const safeUrl = safeImgSrc(url);
    if (safeUrl) {
      // 진짜 이미지 URL (data:image/ 또는 https://) — img 태그로
      return `<div class="av ${esc(size)}"><img src="${safeUrl}" alt="" style="width:100%;height:100%;object-fit:cover"></div>`;
    }
    // URL 이 아닌 짧은 값 — 이모지 아바타로 간주 (welcome 에서 고른 동물 이모지 등)
    return `<div class="av ${esc(size)} emoji">${esc(url)}</div>`;
  }
  // 폴백 — 닉네임 첫 글자 + 색상 버블
  return `<div class="av ${esc(size)}" style="background:${avatarColor(nickname)}">${esc(initial)}</div>`;
}

function getCookie(name) {
  const m = document.cookie.match(new RegExp("(?:^|;\\s*)" + name + "=([^;]+)"));
  return m ? decodeURIComponent(m[1]) : null;
}

/** 짧은 토스트 메시지 — 클립보드 복사·실패 알림용 */
function toast(msg, ms) {
  ms = ms || 2400;
  let t = document.getElementById("__toast");
  if (!t) {
    t = document.createElement("div");
    t.id = "__toast";
    t.className = "toast";
    document.body.appendChild(t);
  }
  t.textContent = msg;
  // 강제 reflow 로 클래스 토글이 트랜지션을 다시 타게 함
  t.className = "toast";
  void t.offsetWidth;
  t.className = "toast on";
  clearTimeout(t.__hideTimer);
  t.__hideTimer = setTimeout(function () { t.className = "toast"; }, ms);
}

async function api(path, opts) {
  const res = await fetch(path, Object.assign({ headers: {} }, opts || {}));
  if (res.status === 204 || res.status === 404) return null;
  const ct = res.headers.get("content-type") || "";
  if (!ct.includes("application/json")) return res.ok ? {} : null;
  return res.json();
}

function post(path, body) {
  const form = new URLSearchParams(body || {});
  const headers = { "Content-Type": "application/x-www-form-urlencoded" };
  const xsrf = getCookie("XSRF-TOKEN");
  if (xsrf) headers["X-XSRF-TOKEN"] = xsrf;
  return fetch(path, {
    method: "POST",
    headers,
    body: form,
    credentials: "same-origin",
  });
}

function fmtClock(totalSec) {
  totalSec = Math.max(0, Math.floor(totalSec));
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  const p = (n) => String(n).padStart(2, "0");
  return h > 0 ? `${p(h)}:${p(m)}:${p(s)}` : `${p(m)}:${p(s)}`;
}

function fmtTime(iso) {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2,"0")}:${String(d.getMinutes()).padStart(2,"0")}`;
}

function relStart(iso) {
  const mins = Math.round((new Date(iso).getTime() - Date.now()) / 60000);
  if (mins <= 0) return "진행 중";
  if (mins < 60) return `${mins}분 후 시작`;
  return `${Math.floor(mins/60)}시간 ${mins%60}분 후`;
}

// 서버 시간과 클라이언트 시간 차이 보정 (타이머 동기화)
let SERVER_SKEW = 0;
async function syncServerTime() {
  try {
    const r = await api("/api/time");
    if (r && r.now) SERVER_SKEW = new Date(r.now).getTime() - Date.now();
  } catch (e) {}
}
function serverNow() { return Date.now() + SERVER_SKEW; }

// 실시간 소켓: 변경 신호를 받으면 콜백 호출 (콜백이 REST로 최신화)
function connectRealtime(onChange) {
  const proto = location.protocol === "https:" ? "wss" : "ws";
  let ws;
  function open() {
    ws = new WebSocket(`${proto}://${location.host}/ws/realtime`);
    ws.onmessage = (ev) => {
      try { onChange(JSON.parse(ev.data)); } catch (e) {}
    };
    ws.onclose = () => setTimeout(open, 2000);
  }
  open();
  return () => ws && ws.close();
}

/* 방 시작 10분 전 브라우저 알림 — 탭이 열려 있는 동안만 동작 (push 아님) */
const __scheduledNotif = new Set();
function schedulePreStartNotifications(rooms, joinedIds) {
  if (typeof Notification === "undefined") return;
  if (Notification.permission === "default") {
    // 첫 방문 시 한 번만 권한 요청 — 결과 무시
    try { Notification.requestPermission().catch(() => {}); } catch (e) {}
  }
  if (Notification.permission !== "granted") return;

  const now = serverNow();
  rooms.forEach((r) => {
    if (!joinedIds.has(r.id)) return;
    const start = new Date(r.startTime).getTime();
    const target = start - 10 * 60 * 1000;   // 시작 10분 전
    const ms = target - now;
    if (ms <= 0 || ms > 12 * 3600 * 1000) return; // 너무 멀거나 이미 지났으면 스킵
    const key = "notif:" + r.id + ":" + r.startTime;
    if (__scheduledNotif.has(key)) return;
    if (localStorage.getItem(key)) return; // 다른 탭/세션에서 이미 발사됨
    __scheduledNotif.add(key);
    setTimeout(() => {
      try {
        new Notification("품앗이 곧 시작!", {
          body: r.title + " — 10분 후 시작이에요",
          tag: r.id,
        });
        localStorage.setItem(key, String(Date.now()));
      } catch (e) {}
    }, ms);
  });
}
