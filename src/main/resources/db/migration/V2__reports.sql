-- 신고(reports) 테이블 — photo / user / room 대상
create table if not exists reports (
    id           bigserial primary key,
    reporter_id  varchar(36) not null,
    target_type  varchar(16) not null,           -- photo | user | room
    target_id    varchar(64) not null,           -- photo id(bigint as string) 또는 uuid
    reason       text,
    status       varchar(16) not null default 'pending',  -- pending | resolved | dismissed
    created_at   timestamp   not null default now(),
    constraint fk_reports_reporter foreign key (reporter_id)
        references users(id) on delete cascade,
    constraint uq_reports_one_per_user unique (reporter_id, target_type, target_id)
);
create index if not exists idx_reports_target on reports(target_type, target_id);
create index if not exists idx_reports_status on reports(status);
