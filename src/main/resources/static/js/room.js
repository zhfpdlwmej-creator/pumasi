const RID = window.ROOM_ID;
const C = 2 * Math.PI * 130; // 816.81
const UPLOAD_OPEN_BEFORE_END_MS = 10 * 60 * 1000;   // 종료 10분 전부터 열림
const UPLOAD_CLOSE_AFTER_END_MS = 24 * 60 * 60 * 1000; // 종료 24시간 뒤 닫힘
let room = null;
let me = null;
let done = false;
let lastList = []; // 갤러리/신고 핸들러용 캐시

function phaseOf(r) {
  const start = new Date(r.startTime).getTime();
  const end = start + r.duration * 60000;
  const now = serverNow();
  if (now < start) return "before";
  if (now < end) return "live";
  return "ended";
}

function uploadWindowInfo() {
  if (!room) return { state: "before", untilOpenMs: Infinity };
  const start = new Date(room.startTime).getTime();
  const end = start + room.duration * 60000;
  const open = end - UPLOAD_OPEN_BEFORE_END_MS;
  const close = end + UPLOAD_CLOSE_AFTER_END_MS;
  const now = serverNow();
  if (now < open) return { state: "before", untilOpenMs: open - now };
  if (now > close) return { state: "closed", untilOpenMs: 0 };
  return { state: "open", untilOpenMs: 0 };
}

async function loadRoom() {
  room = await api(`/api/rooms/${RID}`);
  if (!room) { location.href = "/"; return; }
  document.getElementById("rTitle").textContent = room.title;
  const cat = document.getElementById("rCat");
  cat.className = "chip " + room.category;
  cat.textContent = room.categoryEmoji + " " + room.category;
  document.getElementById("rMeta").textContent =
    fmtTime(room.startTime) + " 시작 · " + room.duration + "분";
}

async function loadParticipants() {
  const list = (await api(`/api/rooms/${RID}/participants`)) || [];
  lastList = list;
  me = window.ME ? list.find((p) => p.userId === window.ME.id) || null : null;
  const active = list.filter((p) => p.status !== "completed");
  document.getElementById("pCount").textContent = active.length + "명";
  const wrap = document.getElementById("people");
  if (active.length === 0) {
    wrap.className = ""; // .people 그리드 스타일 끄기 — 빈 메시지가 세로로 깨지는 거 방지
    const isEnded = room && phaseOf(room) === "ended";
    const emptyMsg = isEnded
      ? "이 품앗이는 마무리됐어요."
      : "아직 아무도 없어요. 첫 번째로 함께해요 🙌";
    wrap.innerHTML = `<div class="empty">${esc(emptyMsg)}</div>`;
  } else {
    wrap.className = "people";
    wrap.innerHTML = active
      .map(
        (p) => `<div class="person">
          <div class="wrapav">${avatar(p.nickname, p.avatarUrl, "lg")}
          ${p.status === "active" ? '<span class="on-dot"></span>' : ""}</div>
          <span class="pn">${esc(p.nickname)}</span>
          ${p.title ? `<span class="pt">${esc(p.title)}</span>` : ""}
        </div>`
      )
      .join("");
  }
  renderGallery(list);
  document.getElementById("note").style.display = me ? "none" : "block";
  renderBar();
}

function renderGallery(list) {
  const shots = [];
  list.forEach((p) =>
    (p.photos || []).forEach((ph) =>
      shots.push({
        id: ph.id,
        src: ph.src,
        uploaderId: p.userId,
        nickname: p.nickname,
        avatarUrl: p.avatarUrl,
        title: p.title,
      })
    )
  );
  const panel = document.getElementById("galleryPanel");
  if (shots.length === 0) {
    panel.style.display = "none";
    return;
  }
  panel.style.display = "";
  document.getElementById("gCount").textContent = shots.length + "장";
  document.getElementById("gallery").innerHTML = shots
    .map((s) => {
      const src = safeImgSrc(s.src);
      if (!src) return "";
      const canReport = window.ME && window.ME.id !== s.uploaderId;
      return `<div class="gcard">
        <img class="gimg" src="${src}" alt="">
        <div class="gfoot">
          ${avatar(s.nickname, s.avatarUrl, "sm")}
          <div class="gmeta">
            <div class="gn">${esc(s.nickname)}</div>
            ${s.title ? `<div class="gt">${esc(s.title)}</div>` : ""}
          </div>
          ${canReport ? `<button class="report-x" data-pid="${esc(String(s.id))}" title="신고">⚐</button>` : ""}
        </div>
      </div>`;
    })
    .join("");
  document.querySelectorAll(".report-x").forEach((b) => {
    b.onclick = (e) => {
      e.stopPropagation();
      openReportDialog(b.dataset.pid);
    };
  });
}

/* ───── 부적절 콘텐츠(NSFW) 차단 — 브라우저 내 분류 ───── */
let nsfwModelPromise = null;
function loadNsfw() {
  if (typeof nsfwjs === "undefined") return null;
  if (!nsfwModelPromise) nsfwModelPromise = nsfwjs.load();
  return nsfwModelPromise;
}
async function isSafeImage(dataUrl) {
  const modelPromise = loadNsfw();
  if (!modelPromise) return true; // 라이브러리 로드 실패 시 차단 우회 — 서버측 추가 방어가 필요
  const model = await modelPromise;
  const img = await new Promise((res, rej) => {
    const i = new Image();
    i.onload = () => res(i);
    i.onerror = rej;
    i.src = dataUrl;
  });
  const preds = await model.classify(img);
  const badProb = preds
    .filter((p) => ["Porn", "Hentai", "Sexy"].indexOf(p.className) >= 0)
    .reduce((s, p) => s + p.probability, 0);
  return badProb < 0.5;
}

/* ───── 사진 업로드 ───── */
function readResized(file) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const max = 720;
      let { width: w, height: h } = img;
      if (w > h && w > max) { h = Math.round((h * max) / w); w = max; }
      else if (h > max) { w = Math.round((w * max) / h); h = max; }
      const cv = document.createElement("canvas");
      cv.width = w; cv.height = h;
      cv.getContext("2d").drawImage(img, 0, 0, w, h);
      resolve(cv.toDataURL("image/jpeg", 0.72));
    };
    img.onerror = reject;
    const fr = new FileReader();
    fr.onload = () => (img.src = fr.result);
    fr.onerror = reject;
    fr.readAsDataURL(file);
  });
}

const photoBtn = document.getElementById("photoBtn");
const photoInput = document.getElementById("photoInput");
photoBtn.onclick = () => photoInput.click();

async function startUpload() {
  photoInput.click();
}

photoInput.onchange = async () => {
  const files = Array.from(photoInput.files || []);
  photoInput.value = "";
  if (files.length === 0) return;
  const wasCompleted = !!(me && me.status === "completed");
  setUploadBusy(true);
  let lastUser = null;
  let blocked = 0;
  const failures = []; // {name, reason}
  try {
    for (const file of files) {
      // HEIC/HEIF 사전 차단 — 아이폰 기본 포맷, 브라우저 디코드 불가
      const isHeic =
        /heic|heif/i.test(file.type || "") ||
        /\.(heic|heif)$/i.test(file.name || "");
      if (isHeic) {
        failures.push({ name: file.name, reason: "HEIC 미지원" });
        continue;
      }
      let dataUrl;
      try {
        dataUrl = await readResized(file);
      } catch (e) {
        console.error("이미지 디코드 실패", file.name, file.type, e);
        failures.push({ name: file.name, reason: "이미지 디코드 실패" });
        continue;
      }
      let safe = true;
      try { safe = await isSafeImage(dataUrl); } catch (e) { safe = true; }
      if (!safe) { blocked++; continue; }
      let res;
      try {
        res = await post(`/api/rooms/${RID}/photo`, { dataUrl });
      } catch (e) {
        console.error("업로드 네트워크 실패", e);
        failures.push({ name: file.name, reason: "네트워크 오류" });
        continue;
      }
      if (res.status === 423) {
        alert("아직 인증 가능 시간이 아니에요. (종료 10분 전부터 가능)");
        break;
      }
      if (res.status === 403) { alert("방에 참여 중이 아니에요."); break; }
      if (res.status === 413) {
        failures.push({ name: file.name, reason: "용량 초과" });
        continue;
      }
      if (!res.ok) {
        console.error("업로드 응답 비정상", res.status);
        failures.push({ name: file.name, reason: "서버 응답 " + res.status });
        continue;
      }
      lastUser = await res.json();
    }
  } finally {
    setUploadBusy(false);
  }

  if (blocked > 0) {
    alert(blocked + "장이 부적절한 콘텐츠로 차단됐어요.");
  }
  if (failures.length > 0) {
    const heicCount = failures.filter((f) => f.reason === "HEIC 미지원").length;
    let msg = failures.length + "장 업로드 실패\n\n";
    msg += failures.map((f) => "• " + (f.name || "사진") + " — " + f.reason).join("\n");
    if (heicCount > 0) {
      msg += "\n\nHEIC 안내: 아이폰 → 설정 → 카메라 → 포맷 → '가장 호환성' 으로 바꾸면 JPEG으로 저장돼요. 이미 찍은 사진은 갤러리 공유 시 자동 변환되기도 합니다.";
    }
    alert(msg);
  }

  await loadParticipants();
  // 미완료 상태에서 업로드 성공 → 완료 모달
  if (lastUser && !wasCompleted && !done) {
    done = true;
    showDone(lastUser.successCount);
  }
};

function setUploadBusy(busy) {
  photoBtn.disabled = busy;
  photoBtn.textContent = busy ? "올리는 중…" : "📷 사진 추가";
  const primary = document.getElementById("primaryBtn");
  if (primary) {
    primary.disabled = busy;
    if (busy) primary.textContent = "올리는 중…";
  }
}

/* ───── 카카오톡 공유 (3단 폴백) ───── */
function formatStartKorean(iso) {
  const d = new Date(iso);
  const today = new Date();
  const tom = new Date(today.getTime() + 86400000);
  const sameDay = (a, b) =>
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate();
  const prefix = sameDay(d, today) ? "오늘"
              : sameDay(d, tom)   ? "내일"
              : `${d.getMonth() + 1}/${d.getDate()}`;
  const h = d.getHours();
  const m = d.getMinutes();
  const ampm = h < 12 ? "오전" : "오후";
  const h12 = h % 12 === 0 ? 12 : h % 12;
  const mm = String(m).padStart(2, "0");
  return `${prefix} ${ampm} ${h12}:${mm}`;
}

function buildShareText() {
  if (!room) return "";
  const startStr = formatStartKorean(room.startTime);
  const cnt = room.participantCount | 0;
  const emoji = room.categoryEmoji || "";
  return (
    `${emoji} 같이 품앗이 할 사람?\n\n` +
    `"${room.title}"\n\n` +
    `⏰ ${startStr}\n` +
    `👥 현재 ${cnt}명 참여 중\n\n` +
    `혼자 하면 미루니까 같이 끝내자 👇`
  );
}

async function shareRoom() {
  if (!room) return;
  const url = window.location.origin + "/room/" + RID;
  const text = buildShareText();
  const title = (room.categoryEmoji || "") + " 같이 품앗이 할 사람?";

  // 1) Kakao SDK — 가장 풍부한 미리보기
  if (window.Kakao && Kakao.isInitialized && Kakao.isInitialized() && Kakao.Share) {
    try {
      Kakao.Share.sendDefault({
        objectType: "text",
        text: text,
        link: { mobileWebUrl: url, webUrl: url },
        buttonTitle: "참여하러 가기",
      });
      return;
    } catch (e) { /* 폴백 진행 */ }
  }
  // 2) Web Share API — 주로 모바일 브라우저 시스템 공유 시트
  if (navigator.share) {
    try {
      await navigator.share({ title: title, text: text, url: url });
      return;
    } catch (e) {
      if (e && e.name === "AbortError") return; // 사용자가 닫은 것 — 폴백 X
      /* 다른 에러는 폴백 진행 */
    }
  }
  // 3) 클립보드 복사 폴백
  const full = text + "\n\n" + url;
  try {
    await navigator.clipboard.writeText(full);
    toast("링크가 복사됐어요. 카톡에 붙여 넣어 공유해 보세요.");
  } catch (e) {
    prompt("아래 주소를 복사해 공유하세요:", url);
  }
}

const kakaoShareBtn = document.getElementById("kakaoShareBtn");
if (kakaoShareBtn) kakaoShareBtn.onclick = shareRoom;

/* ───── 신고 모달 ───── */
function openReportDialog(photoId) {
  if (!window.ME) { location.href = "/auth/kakao?next=/room/" + RID; return; }
  document.getElementById("reportModal").innerHTML = `
    <div class="modal-bg" id="repBg">
      <div class="modal" onclick="event.stopPropagation()" style="text-align:left;padding:24px;max-width:380px">
        <h2 style="font-size:16px;margin:0 0 12px">신고 사유</h2>
        <select id="repReason" style="width:100%;padding:12px;border-radius:12px;box-shadow:inset 0 0 0 1px #e5e5e5;font-size:14px;background:#fff">
          <option value="inappropriate">부적절한/불건전 콘텐츠</option>
          <option value="spam">스팸/광고</option>
          <option value="irrelevant">품앗이와 무관한 사진</option>
          <option value="other">기타</option>
        </select>
        <textarea id="repNote" rows="3" maxlength="400" placeholder="상세 설명(선택)"
          style="width:100%;margin-top:8px;padding:12px;border-radius:12px;box-shadow:inset 0 0 0 1px #e5e5e5;font-family:inherit;font-size:14px;resize:vertical"></textarea>
        <div style="display:flex;gap:8px;margin-top:16px">
          <button class="btn btn-ghost" id="repCancel" style="flex:1">취소</button>
          <button class="btn btn-primary" id="repSubmit" style="flex:1">신고하기</button>
        </div>
      </div>
    </div>`;
  document.getElementById("repBg").onclick = closeReport;
  document.getElementById("repCancel").onclick = closeReport;
  document.getElementById("repSubmit").onclick = async () => {
    const reason = document.getElementById("repReason").value;
    const note = document.getElementById("repNote").value.trim();
    const full = reason + (note ? ": " + note : "");
    const res = await post("/api/reports", {
      targetType: "photo", targetId: String(photoId), reason: full,
    });
    if (res.status === 409) { alert("이미 신고하셨어요."); closeReport(); return; }
    if (!res.ok) { alert("신고 처리 실패"); return; }
    closeReport();
    await loadParticipants(); // 즉시 갤러리에서 사라짐
  };
}
function closeReport() {
  document.getElementById("reportModal").innerHTML = "";
}

/* ───── 하단 바 ───── */
function renderBar() {
  const bar = document.getElementById("bar");
  const phase = room ? phaseOf(room) : "before";
  const completed = (me && me.status === "completed") || done;
  const w = uploadWindowInfo();

  if (!me) {
    // 종료된 방은 새로 참여 불가 (서버에서도 막혀 있지만 UI 도 명시)
    if (phase === "ended") {
      bar.innerHTML = `<button class="btn btn-soft full" disabled>이미 종료된 방이에요</button>`;
    } else {
      bar.innerHTML = `<button class="btn btn-primary full" id="joinBtn">참여하기</button>`;
      document.getElementById("joinBtn").onclick = join;
    }
    photoBtn.style.display = "none";
    return;
  }

  // 사진 추가(부) 버튼 — 윈도우 열린 동안만 보임
  photoBtn.style.display = w.state === "open" ? "" : "none";

  let primaryHtml;
  if (completed && w.state === "open") {
    // 완료 후에도 추억 사진 추가 가능
    primaryHtml = `<button class="btn btn-soft full" id="primaryBtn">📷 사진 추가</button>`;
  } else if (completed) {
    primaryHtml = `<button class="btn btn-soft full" disabled>✓ 인증 완료 — 수고하셨어요!</button>`;
  } else if (w.state === "open") {
    primaryHtml = `<button class="btn btn-primary full" id="primaryBtn">📷 인증샷 올리기 (= 완료)</button>`;
  } else if (w.state === "before") {
    const mins = Math.ceil(w.untilOpenMs / 60000);
    const label =
      phase === "before" ? "곧 시작됩니다"
        : `인증 가능까지 ${mins}분`;
    primaryHtml = `<button class="btn btn-primary full" disabled>${esc(label)}</button>`;
  } else {
    primaryHtml = `<button class="btn btn-soft full" disabled>인증 가능 시간이 지났어요</button>`;
  }

  const showLeave = !completed;
  bar.innerHTML =
    (showLeave
      ? `<button class="btn btn-ghost" id="leaveBtn" style="padding-left:20px;padding-right:20px">그룹 탈출</button>`
      : "") + primaryHtml;

  if (showLeave) document.getElementById("leaveBtn").onclick = leave;
  const pbtn = document.getElementById("primaryBtn");
  if (pbtn) pbtn.onclick = startUpload;
}

async function join() {
  if (!window.ME) { location.href = "/auth/kakao?next=/room/" + RID; return; }
  await post(`/api/rooms/${RID}/join`);
  await loadParticipants();
}
async function leave() {
  const phase = room ? phaseOf(room) : "before";
  const completed = me && me.status === "completed";
  const w = uploadWindowInfo();

  if (phase === "live" && !completed) {
    if (!confirm("지금 나가면 '쉬어간 별명'이 잠시 붙을 수 있어요. 다음에 다시 함께해도 괜찮아요. 그래도 나가시겠어요?")) return;
  } else if (phase === "ended" && !completed && w.state === "open") {
    // 종료됐는데 아직 인증 안 한 상태 — 한 번 권유
    const action = await askLeaveOrUpload();
    if (action === "upload") {
      photoInput.click(); // 업로드 흐름이 알아서 완료 처리하고 done 모달 띄움
      return;
    }
    if (action !== "leave") return; // 백드롭 닫기 = 취소
  }

  const res = await post(`/api/rooms/${RID}/leave`);
  if (!res.ok) {
    alert("탈출 처리 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요.");
    return;
  }
  location.href = "/";
}

function askLeaveOrUpload() {
  return new Promise((resolve) => {
    const bg = document.createElement("div");
    bg.className = "modal-bg";
    bg.innerHTML = `
      <div class="modal" style="text-align:center;max-width:340px;padding:28px 24px">
        <div class="big">✨</div>
        <h2 style="margin-top:12px;font-size:17px;font-weight:800">갓생 기록을 해보아요</h2>
        <p style="margin-top:8px;font-size:13px;color:var(--muted);line-height:1.6">
          인증샷 한 장만 올리면<br>오늘의 품앗이가 완료로 남아요
        </p>
        <div style="display:flex;gap:8px;margin-top:22px">
          <button class="btn btn-ghost" id="justLeaveBtn" style="flex:1">그냥 나가기</button>
          <button class="btn btn-primary" id="uploadInsteadBtn" style="flex:1.3">📷 인증샷 올리기</button>
        </div>
      </div>`;
    bg.addEventListener("click", (e) => {
      if (e.target === bg) { bg.remove(); resolve("cancel"); }
    });
    document.body.appendChild(bg);
    bg.querySelector("#justLeaveBtn").addEventListener("click", () => {
      bg.remove(); resolve("leave");
    });
    bg.querySelector("#uploadInsteadBtn").addEventListener("click", () => {
      bg.remove(); resolve("upload");
    });
  });
}

/* 완료 모달 — 횟수 구간별로 톤이 변하는 유머 메시지 풀에서 랜덤 추첨 */
const DONE_POOLS = {
  // 1회 (첫 완료) — 따뜻하게 축하
  first: [
    { emoji: "🎉", text: "와 품앗이 성공 대단한데요!<br>첫 번째 완료, 시작이 반이에요" },
    { emoji: "🌱", text: "첫 인증샷 박았어요!<br>앞으로 자주 만나요" },
    { emoji: "🔥", text: "첫판부터 끝까지!<br>근성 인정합니다" },
    { emoji: "✨", text: "첫 완료 — 이게 시작이에요.<br>앞으로가 더 기대돼요" },
  ],
  // 2~5회 — 격려
  early: [
    { emoji: "💪", text: "또 해냈네요!<br>슬슬 습관 되겠어요" },
    { emoji: "🙌", text: "오늘도 잘했어요!<br>이젠 좀 익숙하시죠?" },
    { emoji: "🌿", text: "꾸준함이 이깁니다.<br>한 회 한 회 단단해지는 중" },
    { emoji: "👏", text: "자기 자신과의 약속,<br>오늘도 지켰어요" },
    { emoji: "📈", text: "성공 그래프 우상향!<br>이대로만 가요" },
  ],
  // 6~9회 — 어느 정도 단골 인정
  mid: [
    { emoji: "🏅", text: "{n}회나 했다고?!<br>품앗이 잘하는 사람으로 소문 나겠어요" },
    { emoji: "⚙️", text: "톱니바퀴처럼 돌아가는 일잘러" },
    { emoji: "🎯", text: "명중률 안 떨어지네요.<br>진짜 멋있다" },
    { emoji: "🧘", text: "어느 정도 도를 닦으셨군요" },
    { emoji: "🧱", text: "벽돌 한 장 한 장,<br>탑이 쌓이고 있어요" },
  ],
  // 10~19회 — 마스터 농담
  master: [
    { emoji: "🥷", text: "또 끝냈네요. 닌자세요?" },
    { emoji: "✨", text: "{n}회 완료…<br>이 정도면 사부님이라 부를게요" },
    { emoji: "🦾", text: "무결점, 무패, 무한.<br>경의를 표합니다" },
    { emoji: "🎖️", text: "품앗이 사범 클래스 진입" },
    { emoji: "🧙", text: "약속을 마법처럼 지키시네요" },
  ],
  // 20~49회 — 의심 시작
  legend: [
    { emoji: "👑", text: "{n}회. 진짜요? 사람 맞아요?" },
    { emoji: "🐉", text: "이쯤 되면 전설 아닌가요…" },
    { emoji: "🏆", text: "명예의 전당 영구결번 후보" },
    { emoji: "🌟", text: "살아있는 전설이라<br>책에 적어둘게요" },
    { emoji: "🛡️", text: "한국 품앗이계의 마지막 보루" },
  ],
  // 50회+ — 외계인 취급
  god: [
    { emoji: "🛸", text: "{n}회… 외계인 의심 신고 들어왔습니다" },
    { emoji: "♾️", text: "카운트가 무서워서 못 세겠어요" },
    { emoji: "🪐", text: "다른 차원에서 오신 분 같아요" },
    { emoji: "🌌", text: "{n}회. 잠은 자세요…?" },
    { emoji: "🤖", text: "혹시 봇이세요?" },
  ],
};

function pickDoneMessage(count) {
  let pool;
  if (count <= 1) pool = DONE_POOLS.first;
  else if (count <= 5) pool = DONE_POOLS.early;
  else if (count <= 9) pool = DONE_POOLS.mid;
  else if (count <= 19) pool = DONE_POOLS.master;
  else if (count <= 49) pool = DONE_POOLS.legend;
  else pool = DONE_POOLS.god;
  const p = pool[Math.floor(Math.random() * pool.length)];
  return { emoji: p.emoji, text: p.text.replace("{n}", String(count)) };
}

function showDone(count) {
  const c = count | 0;
  const m = pickDoneMessage(c);
  document.getElementById("doneModal").innerHTML = `
    <div class="modal-bg" onclick="location.href='/'">
      <div class="modal">
        <div class="big">${esc(m.emoji)}</div>
        <p>${m.text}</p>
        <p class="done-counter">총 ${c}회 완료</p>
        <button class="btn btn-primary full" style="margin-top:24px"
          onclick="location.href='/'">홈으로 돌아가기</button>
      </div>
    </div>`;
}

/* ───── 타이머/링 ───── */
let lastPhase = null;
function tick() {
  if (!room) return;
  const phase = phaseOf(room);
  const start = new Date(room.startTime).getTime();
  const end = start + room.duration * 60000;
  const now = serverNow();
  const target = phase === "before" ? start : end;
  const remain = Math.max(0, (target - now) / 1000);

  const label = document.getElementById("rLabel");
  label.textContent =
    phase === "before" ? "곧 시작됩니다" : phase === "live" ? "함께 집중 중" : "수고하셨어요";
  label.className = "ring-label" + (phase === "live" ? " on" : "");
  document.getElementById("rTime").textContent =
    phase === "ended" ? "00:00" : fmtClock(remain);
  document.getElementById("rHint").textContent =
    phase === "before" ? "시작까지 남은 시간"
      : phase === "live" ? "종료까지 남은 시간" : "품앗이가 끝났어요";

  const total = room.duration * 60;
  const prog = phase === "live" ? 1 - remain / total : phase === "ended" ? 1 : 0;
  const ring = document.getElementById("ring");
  ring.style.strokeDashoffset = C * (1 - prog);
  ring.style.stroke = phase === "before" ? "#d4d4d4" : "#10b981";
  ring.style.transition = "stroke-dashoffset 1s linear, stroke .3s";

  // 라이브 진입 시 내 상태 active 로 승격
  if (phase !== lastPhase) {
    if (phase === "live" && me && me.status === "reserved") {
      post(`/api/rooms/${RID}/activate`).then(loadParticipants);
    }
    if (lastPhase !== null) renderBar();
    lastPhase = phase;
  }
  // 카운트다운/윈도우 표시 갱신 (1초 단위로 거슬리지 않게 매 5초)
  if (Math.floor(remain) % 5 === 0) renderBar();
}

(async function init() {
  await syncServerTime();
  await loadRoom();
  await loadParticipants();
  if (me && room) {
    schedulePreStartNotifications([room], new Set([RID]));
  }
  tick();
  setInterval(tick, 1000);
  connectRealtime((m) => {
    if (m.scope === "participants" && (m.roomId === RID || m.roomId === ""))
      loadParticipants();
    if (m.scope === "rooms") loadRoom();
  });
  setInterval(loadParticipants, 10000);
})();
