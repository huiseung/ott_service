create table users (
    id bigint not null auto_increment primary key,
    login_id varchar(100) not null,
    display_name varchar(100) not null,
    password_hash varchar(255) not null,
    role varchar(50) not null,
    enabled boolean not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    unique key uk_users_login_id (login_id)
) engine = InnoDB;

create table user_sessions (
    id bigint not null auto_increment primary key,
    session_token_hash varchar(128) not null,
    user_id bigint not null,
    expires_at datetime(6) not null,
    invalidated_at datetime(6) null,
    created_at datetime(6) not null,
    last_used_at datetime(6) not null,
    unique key uk_user_sessions_token_hash (session_token_hash),
    index ix_user_sessions_user_id (user_id),
    index ix_user_sessions_expires_at (expires_at),
    constraint fk_user_sessions_user foreign key (user_id) references users(id)
) engine = InnoDB;

create table playback_sessions (
    id bigint not null auto_increment primary key,
    session_token varchar(80) not null,
    user_id bigint not null,
    video_id bigint not null,
    media_package_id bigint not null,
    expires_at datetime(6) not null,
    created_at datetime(6) not null,
    last_accessed_at datetime(6) not null,
    unique key uk_playback_sessions_token (session_token),
    index ix_playback_sessions_user_video (user_id, video_id),
    index ix_playback_sessions_expires_at (expires_at),
    constraint fk_playback_sessions_user foreign key (user_id) references users(id),
    constraint fk_playback_sessions_video foreign key (video_id) references videos(id),
    constraint fk_playback_sessions_media_package foreign key (media_package_id) references media_packages(id)
) engine = InnoDB;

create table watch_progress (
    user_id bigint not null,
    video_id bigint not null,
    media_package_id bigint not null,
    playback_session_id bigint not null,
    position_ms bigint not null,
    duration_ms bigint not null,
    client_event_seq bigint not null,
    occurred_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (user_id, video_id),
    index ix_watch_progress_video_id (video_id),
    constraint fk_watch_progress_user foreign key (user_id) references users(id),
    constraint fk_watch_progress_video foreign key (video_id) references videos(id),
    constraint fk_watch_progress_media_package foreign key (media_package_id) references media_packages(id),
    constraint fk_watch_progress_playback_session foreign key (playback_session_id) references playback_sessions(id)
) engine = InnoDB;
