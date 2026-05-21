const CATEGORIES = [
  { c: "청소", e: "🧹" }, { c: "집중", e: "🔥" },
  { c: "집밥", e: "🍚" }, { c: "운동", e: "💪" },
];
const DURS = [30, 60, 90, 120];
const CAPS = [{ l: "5명", v: 5 }, { l: "10명", v: 10 }, { l: "무제한", v: null }];

const state = { category: "청소", duration: 60, capacity: 5, slot: null };

// 로컬 시간 → 'YYYY-MM-DDTHH:mm:ss' (서버 LocalDateTime 파싱용)
function localIso(dt) {
  const p = (n) => String(n).padStart(2, "0");
  return `${dt.getFullYear()}-${p(dt.getMonth()+1)}-${p(dt.getDate())}T${p(dt.getHours())}:${p(dt.getMinutes())}:00`;
}

// 00:00 ~ 24:00, 1시간 간격 (24:00 = 다음날 00:00)
function slots() {
  const out = [];
  const base = new Date();
  base.setHours(0, 0, 0, 0);
  for (let h = 0; h <= 24; h++) {
    const dt = new Date(base);
    dt.setHours(h);
    const p = (n) => String(n).padStart(2, "0");
    out.push({ iso: localIso(dt), label: `${p(h)}:00` });
  }
  return out;
}

const SLOTS = slots();
// 기본값: 지금 이후 가장 가까운 정시 (없으면 마지막 24:00)
function defaultSlot() {
  const nowIso = localIso(new Date());
  const next = SLOTS.find((s) => s.iso > nowIso);
  return (next || SLOTS[SLOTS.length - 1]).iso;
}
state.slot = defaultSlot();

function paint() {
  document.getElementById("cat").innerHTML = CATEGORIES.map(
    (x) => `<button class="pick ${state.category === x.c ? "on" : ""}" data-c="${x.c}"><span class="e">${x.e}</span>${x.c}</button>`
  ).join("");
  const nowIso = localIso(new Date());
  document.getElementById("slots").innerHTML = SLOTS.map((s) => {
    const past = s.iso <= nowIso;
    const on = state.slot === s.iso ? "on" : "";
    return `<button class="pick brand ${on}" data-s="${s.iso}" ${past ? "disabled" : ""}>${s.label}</button>`;
  }).join("");
  document.getElementById("dur").innerHTML = DURS.map(
    (d) => `<button class="pick ${state.duration === d ? "on" : ""}" data-d="${d}">${d}분</button>`
  ).join("");
  document.getElementById("cap").innerHTML = CAPS.map(
    (c) => `<button class="pick ${state.capacity === c.v ? "on" : ""}" data-cap="${c.v}">${c.l}</button>`
  ).join("");

  bind("[data-c]", "c", "category");
  bind("[data-s]", "s", "slot");
  bind("[data-d]", "d", "duration", true);
  document.querySelectorAll("[data-cap]").forEach((b) =>
    b.addEventListener("click", () => {
      state.capacity = b.dataset.cap === "null" ? null : +b.dataset.cap;
      paint();
    })
  );
  validate();
}

function bind(sel, attr, key, num) {
  document.querySelectorAll(sel).forEach((b) =>
    b.addEventListener("click", () => {
      state[key] = num ? +b.dataset[attr] : b.dataset[attr];
      paint();
    })
  );
}

function validate() {
  const ok = document.getElementById("title").value.trim().length > 0 && state.slot;
  document.getElementById("submit").disabled = !ok;
}

document.getElementById("title").addEventListener("input", validate);

document.getElementById("submit").addEventListener("click", async () => {
  if (!window.ME) { location.href = "/auth/kakao?next=/create"; return; }
  const btn = document.getElementById("submit");
  btn.disabled = true;
  btn.textContent = "만드는 중...";
  const body = {
    title: document.getElementById("title").value.trim(),
    category: state.category,
    startTime: state.slot,
    duration: state.duration,
  };
  if (state.capacity != null) body.capacity = state.capacity;
  const res = await post("/api/rooms", body);
  if (res.ok) {
    const room = await res.json();
    location.href = "/room/" + room.id;
  } else {
    btn.disabled = false;
    btn.textContent = "방 만들기";
  }
});

paint();
