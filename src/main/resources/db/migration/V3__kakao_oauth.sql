-- 진짜 카카오 OAuth 도입 — 사용자 식별자(kakao_id) + 첫 로그인 여부(setup_complete)
alter table users
    add column if not exists kakao_id varchar(64);

-- 시드/기존 유저는 신경 끄게 default true. 카카오 신규 유저는 코드에서 명시적으로 false 로 만든다.
alter table users
    add column if not exists setup_complete boolean not null default true;

-- 같은 카카오 계정 두 번 가입 방지. NULL 은 여러 개 허용해야 시드/고스트가 충돌 안 함 → partial unique
create unique index if not exists uq_users_kakao_id
    on users(kakao_id)
    where kakao_id is not null;
