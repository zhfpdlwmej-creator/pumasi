const STATUS = { reserved: "예약함", active: "참여 중", completed: "완료" };

// 이모지/이미지/글자 아바타 — avatar() 헬퍼가 알아서 분기
(function () {
  const old = document.getElementById("pfAv");
  if (old) old.outerHTML = avatar(window.ME.nickname, window.ME.avatarUrl, "lg");
})();

/* ───── 닉네임 인라인 편집 ───── */
const pfName = document.getElementById("pfName");
const renameBtn = document.getElementById("renameBtn");

renameBtn.addEventListener("click", () => {
  const current = window.ME.nickname;
  pfName.outerHTML =
    `<div class="rename-edit">
      <input type="text" id="renameInput" maxlength="14" value="${esc(current)}">
      <button class="btn btn-primary sm" id="renameSave">저장</button>
      <button class="btn btn-ghost sm" id="renameCancel">취소</button>
    </div>`;
  renameBtn.style.display = "none";
  const input = document.getElementById("renameInput");
  input.focus();
  input.select();
  document.getElementById("renameSave").addEventListener("click", saveName);
  document.getElementById("renameCancel").addEventListener("click", () =>
    location.reload()
  );
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter") saveName();
    if (e.key === "Escape") location.reload();
  });
});

async function saveName() {
  const v = document.getElementById("renameInput").value.trim();
  if (!v) return;
  const res = await post("/api/me/nickname", { nickname: v });
  if (res.ok) {
    location.reload();
  } else {
    alert("닉네임을 1~14자로 입력해 주세요.");
  }
}

/* ───── 별명 도감 모달 ───── */
const openCatalog = document.getElementById("openCatalog");
const catalogModal = document.getElementById("catalogModal");

openCatalog.addEventListener("click", openTitleCatalog);

async function openTitleCatalog() {
  const data = await api("/api/titles");
  if (!data) return;
  const cur = window.ME.title;
  const honor = renderSection("명예 별명 — 완료를 쌓을수록", data.honor, "honor", cur);
  const shame = renderSection("쉬어간 별명 — 가끔은 쉬어가도 괜찮아요", data.shame, "shame", cur);
  catalogModal.innerHTML =
    `<div class="modal-bg" id="catBg">
      <div class="modal modal-tall" onclick="event.stopPropagation()">
        <div class="head">
          <h2>🗂 별명 도감</h2>
          <button class="close-x" id="catClose">✕</button>
        </div>
        <div class="body">
          ${honor}
          ${shame}
          <p class="cat-foot">현재 별명은 <b>${esc(cur)}</b> — 같은 티어 안에서 다른 분들과 별명이 겹칠 수도 있어요.</p>
        </div>
      </div>
    </div>`;
  document.getElementById("catBg").addEventListener("click", closeCatalog);
  document.getElementById("catClose").addEventListener("click", closeCatalog);
}
function closeCatalog() { catalogModal.innerHTML = ""; }

function renderSection(heading, tiers, kind, cur) {
  return `<div class="cat-section ${esc(kind)}">
    <h3>${esc(heading)}</h3>
    ${tiers
      .map(
        (t) => `<div class="cat-tier">
          <div class="label">${esc(t.label)}</div>
          <div class="vars">
            ${t.titles
              .map(
                (n) =>
                  `<span class="cat-var ${n === cur ? "now" : ""}">${esc(n)}</span>`
              )
              .join("")}
          </div>
        </div>`
      )
      .join("")}
  </div>`;
}

/* ───── 갓생이력 (필터: 전체 / 완료만) ───── */
let allHist = [];
let currentFilter = "all";

function renderHist() {
  const el = document.getElementById("hist");
  const list = currentFilter === "completed"
    ? allHist.filter((r) => r.phase === "completed")
    : allHist;
  if (list.length === 0) {
    el.innerHTML = currentFilter === "completed"
      ? '<div class="empty">아직 완료한 품앗이가 없어요.<br>인증샷 한 장으로 시작해 봐요!</div>'
      : '<div class="empty">아직 기록이 없어요. 첫 품앗이를 시작해보세요!</div>';
    return;
  }
  el.innerHTML = list
    .map((r) => {
      const st = r.phase;
      const cls = st === "completed" ? "done" : "other";
      return `<a class="hrow" href="/room/${esc(r.id)}">
        <span class="e">${esc(r.categoryEmoji)}</span>
        <div class="body">
          <b>${esc(r.title)}</b>
          <small>${esc(fmtTime(r.startTime))} · ${r.duration | 0}분</small>
        </div>
        <span class="badge ${cls}">${esc(STATUS[st] || st)}</span>
      </a>`;
    })
    .join("");
}

function setFilter(f) {
  currentFilter = f;
  document.querySelectorAll(".stat[data-filter]").forEach((b) => {
    b.classList.toggle("active", b.dataset.filter === f);
  });
  renderHist();
}

document.querySelectorAll(".stat[data-filter]").forEach((btn) => {
  btn.addEventListener("click", () => setFilter(btn.dataset.filter));
});

(async function () {
  allHist = (await api("/api/profile/history")) || [];
  document.getElementById("histCount").textContent = allHist.length;
  setFilter("all"); // 기본은 전체
})();
