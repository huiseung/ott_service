create table seasons (
    id bigint not null auto_increment primary key,
    series_content_id bigint not null,
    season_number integer not null,
    status varchar(50) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    unique key uk_seasons_series_number (series_content_id, season_number),
    constraint fk_seasons_series_content foreign key (series_content_id) references contents(id) on delete cascade,
    constraint ck_seasons_number_positive check (season_number >= 1)
) engine = InnoDB;

create table episodes (
    id bigint not null auto_increment primary key,
    season_id bigint not null,
    episode_number integer not null,
    status varchar(50) not null,
    release_at datetime(6) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    unique key uk_episodes_season_number (season_id, episode_number),
    constraint fk_episodes_season foreign key (season_id) references seasons(id) on delete cascade,
    constraint ck_episodes_number_positive check (episode_number >= 1)
) engine = InnoDB;

create table episode_localizations (
    id bigint not null auto_increment primary key,
    episode_id bigint not null,
    locale varchar(20) not null,
    title varchar(300) not null,
    description varchar(4000) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    unique key uk_episode_localizations_episode_locale (episode_id, locale),
    index ix_episode_localizations_title (title),
    constraint fk_episode_localizations_episode foreign key (episode_id) references episodes(id) on delete cascade
) engine = InnoDB;

create table media_versions (
    id bigint not null auto_increment primary key,
    content_id bigint null,
    episode_id bigint null,
    video_id bigint null,
    version_type varchar(50) not null,
    status varchar(50) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    unique key uk_media_versions_content_type (content_id, version_type),
    unique key uk_media_versions_episode_type (episode_id, version_type),
    unique key uk_media_versions_video (video_id),
    constraint fk_media_versions_content foreign key (content_id) references contents(id) on delete cascade,
    constraint fk_media_versions_episode foreign key (episode_id) references episodes(id) on delete cascade,
    constraint fk_media_versions_video foreign key (video_id) references videos(id),
    constraint ck_media_versions_owner_xor check (
        (content_id is not null and episode_id is null)
        or (content_id is null and episode_id is not null)
    )
) engine = InnoDB;
