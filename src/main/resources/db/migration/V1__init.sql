-- pumasi 초기 스키마
-- 모든 PK 는 UUID 문자열(varchar 36), 사진은 별도 테이블로 분리

create table if not exists users (
    id              varchar(36) primary key,
    nickname        varchar(64) not null,
    avatar_url      text,
    success_count   integer     not null default 0,
    fail_count      integer     not null default 0,
    created_at      timestamp   not null default now()
);

create table if not exists rooms (
    id          varchar(36) primary key,
    title       varchar(120) not null,
    category    varchar(20)  not null,
    start_time  timestamp    not null,
    duration    integer      not null,
    capacity    integer,
    created_by  varchar(36)  not null,
    created_at  timestamp    not null default now(),
    constraint fk_rooms_created_by foreign key (created_by)
        references users(id) on delete cascade
);
create index if not exists idx_rooms_start_time on rooms(start_time);

create table if not exists participants (
    id          varchar(36) primary key,
    room_id     varchar(36) not null,
    user_id     varchar(36) not null,
    status      varchar(16) not null,
    nickname    varchar(64) not null,
    avatar_url  text,
    joined_at   timestamp   not null default now(),
    constraint fk_participants_room foreign key (room_id)
        references rooms(id) on delete cascade,
    constraint fk_participants_user foreign key (user_id)
        references users(id) on delete cascade,
    constraint uq_participants_room_user unique (room_id, user_id)
);
create index if not exists idx_participants_room on participants(room_id);
create index if not exists idx_participants_user on participants(user_id);

create table if not exists participant_photos (
    id              bigserial primary key,
    participant_id  varchar(36) not null,
    data_url        text        not null,
    created_at      timestamp   not null default now(),
    constraint fk_photos_participant foreign key (participant_id)
        references participants(id) on delete cascade
);
create index if not exists idx_photos_participant on participant_photos(participant_id);
