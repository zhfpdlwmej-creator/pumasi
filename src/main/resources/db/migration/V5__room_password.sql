-- 비밀방 비밀번호(선택) — BCrypt 해시 저장. null/빈값이면 링크 전용
alter table rooms
    add column if not exists password_hash varchar(100);
