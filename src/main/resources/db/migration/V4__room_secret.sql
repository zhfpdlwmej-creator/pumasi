-- 비밀방 — 홈 목록에 안 보이고 공유 링크로만 입장
alter table rooms
    add column if not exists secret boolean not null default false;
