alter table videos
    add column active_video_file_id bigint null,
    add column published_media_package_id bigint null;

create table media_processing_jobs (
    id bigint not null auto_increment primary key,
    job_key varchar(200) not null,
    video_id bigint not null,
    video_file_id bigint not null,
    profile_version varchar(80) not null,
    status varchar(50) not null,
    stage varchar(50) not null,
    progress_percent integer not null,
    processed_ms bigint null,
    duration_ms bigint null,
    attempt integer not null,
    generation integer not null,
    worker_id varchar(200) null,
    lease_until datetime(6) null,
    last_heartbeat_at datetime(6) null,
    next_run_at datetime(6) not null,
    error_code varchar(100) null,
    error_message varchar(1000) null,
    created_at datetime(6) not null,
    started_at datetime(6) null,
    completed_at datetime(6) null,
    updated_at datetime(6) not null,
    unique key uk_media_processing_jobs_job_key (job_key),
    index ix_media_processing_jobs_queue (status, next_run_at, created_at),
    index ix_media_processing_jobs_lease (status, lease_until),
    index ix_media_processing_jobs_video_file_id (video_file_id),
    constraint fk_media_processing_jobs_video foreign key (video_id) references videos(id),
    constraint fk_media_processing_jobs_video_file foreign key (video_file_id) references video_files(id)
) engine = InnoDB;

create table media_packages (
    id bigint not null auto_increment primary key,
    video_id bigint not null,
    source_video_file_id bigint not null,
    profile_version varchar(80) not null,
    root_key varchar(512) not null,
    master_manifest_key varchar(512) not null,
    duration_ms bigint not null,
    status varchar(50) not null,
    created_at datetime(6) not null,
    index ix_media_packages_video_id (video_id),
    index ix_media_packages_source_video_file_id (source_video_file_id),
    constraint fk_media_packages_video foreign key (video_id) references videos(id),
    constraint fk_media_packages_video_file foreign key (source_video_file_id) references video_files(id)
) engine = InnoDB;

create table media_renditions (
    id bigint not null auto_increment primary key,
    media_package_id bigint not null,
    name varchar(80) not null,
    width integer not null,
    height integer not null,
    video_codec varchar(80) not null,
    audio_codec varchar(80) not null,
    video_bitrate integer null,
    audio_bitrate integer null,
    playlist_key varchar(512) not null,
    unique key uk_media_renditions_package_name (media_package_id, name),
    constraint fk_media_renditions_package foreign key (media_package_id) references media_packages(id)
) engine = InnoDB;

alter table videos
    add constraint fk_videos_active_video_file foreign key (active_video_file_id) references video_files(id),
    add constraint fk_videos_published_media_package foreign key (published_media_package_id) references media_packages(id);
