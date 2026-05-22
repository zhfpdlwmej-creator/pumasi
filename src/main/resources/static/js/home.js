const CATS = ["전체", "청소", "집중", "집밥", "운동", "공부", "독서", "기상", "명상", "취미", "기타"];
const EMOJI = {
  청소: "🧹", 집중: "🔥", 집밥: "🍚", 운동: "💪", 공부: "📚",
  독서: "📖", 기상: "🌅", 명상: "🧘", 취미: "🎨", 기타: "✨",
};
let filter = "전체";
let myRooms = new Set();

if (window.ME) {
  const a = document.getElementById("hdrAv");
  if (a) {
    // 이모지 아바타면 그걸 보여주고, 없으면 닉네임 첫 글자 + 색
    a.outerHTML = avatar(window.ME.nickname, window.ME.avatarUrl, "md");
  }
}

function renderTabs() {
  document.getElementById("tabs").innerHTML = CATS.map(
    (c) => `<button class="tab ${c === filter ? "on" : ""}" data-c="${c}">${
      c === "전체" ? "" : EMOJI[c] + " "
    }${c}</button>`
  ).join("");
  document.querySelectorAll(".tab").forEach((b) =>
    b.addEventListener("click", () => {
      filter = b.dataset.c;
      renderTabs();
      load();
    })
  );
}

function card(r) {
  const full = r.capacity != null && r.participantCount >= r.capacity;
  const ended = r.phase === "ended";
  const joined = myRooms.has(r.id);
  // 종료된 방은 참여했던 사람만 다시 들어갈 수 있음 — 비참여자는 카드 자체 클릭 막음
  const locked = ended && !joined;

  let cta;
  if (joined) {
    cta = `<a class="btn btn-soft sm" href="/room/${esc(r.id)}">입장하기</a>`;
  } else if (ended) {
    cta = `<button class="btn btn-soft sm" disabled>종료됨</button>`;
  } else {
    const dis = full ? "disabled" : "";
    const label = full ? "정원 마감" : "참여 예약";
    cta = `<button class="btn btn-primary sm reserve" data-id="${esc(r.id)}" ${dis}>${label}</button>`;
  }

  const navAttr = locked ? "" : `data-go="/room/${esc(r.id)}"`;
  const cls = locked ? "card card-locked" : "card";
  return `<div class="${cls}" ${navAttr}>
    <div class="card-top">
      <div class="card-emoji">${esc(r.categoryEmoji)}</div>
      <div style="flex:1;min-width:0">
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:2px;flex-wrap:wrap">
          <span class="chip ${esc(r.category)}">${esc(r.category)}</span>
          ${r.phase === "live" ? '<span class="live"><span class="dot"></span>LIVE</span>' : ""}
          ${joined && !ended ? '<span class="chip joined">참여 중</span>' : ""}
        </div>
        <h3>${esc(r.title)}</h3>
        <div class="sub">${esc(fmtTime(r.startTime))} · ${ended ? "종료" : esc(relStart(r.startTime))}</div>
      </div>
    </div>
    <div class="card-bot">
      <div class="count"><b>${r.participantCount | 0}</b> / ${r.capacity == null ? "∞" : (r.capacity | 0)}명</div>
      <div onclick="event.stopPropagation()">${cta}</div>
    </div>
  </div>`;
}

async function loadMyJoined() {
  if (!window.ME) return;
  const ids = await api("/api/me/joined");
  if (Array.isArray(ids)) myRooms = new Set(ids);
}

let lastCardsKey = null;
async function load() {
  await loadMyJoined();
  let rooms = await api("/api/rooms");
  if (!rooms) rooms = [];
  schedulePreStartNotifications(rooms, myRooms);
  if (filter !== "전체") rooms = rooms.filter((r) => r.category === filter);

  // 변동 없으면 재렌더 스킵 — 폴링 깜박임 방지
  const key = filter + "|" +
    Array.from(myRooms).sort().join(",") + "|" +
    rooms.map((r) => `${r.id}/${r.phase}/${r.participantCount}/${r.capacity ?? ""}/${r.title}`).join("&");
  if (key === lastCardsKey) return;
  lastCardsKey = key;

  const el = document.getElementById("cards");
  if (rooms.length === 0) {
    el.innerHTML =
      '<div class="empty" style="padding:40px">🍃<br><br>아직 방이 없어요. 첫 품앗이를 열어볼까요?</div>';
    return;
  }
  el.innerHTML = rooms.map(card).join("");
  el.querySelectorAll("[data-go]").forEach((c) =>
    c.addEventListener("click", () => (location.href = c.dataset.go))
  );
  el.querySelectorAll(".reserve").forEach((b) =>
    b.addEventListener("click", () => reserve(b.dataset.id))
  );
}

async function reserve(id) {
  if (!window.ME) {
    location.href = "/auth/kakao?next=/room/" + id;
    return;
  }
  const res = await post(`/api/rooms/${id}/join`);
  if (res.ok) {
    myRooms.add(id);
    location.href = "/room/" + id;
  }
}

renderTabs();
load();
// 폴링 빈도를 줄임 — 변화는 거의 WebSocket 으로 들어옴
connectRealtime(() => load());
setInterval(load, 30000);
