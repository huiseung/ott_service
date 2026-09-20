create table videos (
    id bigint not null auto_increment primary key,
    title varchar(300) not null,
    status varchar(50) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null
) engine = InnoDB;

create table video_files (
    id bigint not null auto_increment primary key,
    video_id bigint not null,
    generation integer not null,
    original_filename varchar(500) not null,
    content_type varchar(255) not null,
    file_size bigint not null,
    fingerprint varchar(255) not null,
    object_key varchar(512) not null,
    multipart_upload_id varchar(1024),
    part_size bigint not null,
    total_parts integer not null,
    status varchar(50) not null,
    checksum_sha256 varchar(128),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    completed_at datetime(6),
    unique (video_id, generation),
    unique (object_key),
    constraint fk_video_files_video foreign key (video_id) references videos(id)
) engine = InnoDB;

create index ix_video_files_video_id on video_files(video_id);
create index ix_video_files_status_updated_at on video_files(status, updated_at);

create table video_file_parts (
    video_file_id bigint not null,
    part_number integer not null,
    etag varchar(512) not null,
    size bigint not null,
    uploaded_at datetime(6) not null,
    primary key (video_file_id, part_number),
    constraint fk_video_file_parts_video_file foreign key (video_file_id) references video_files(id) on delete cascade
) engine = InnoDB;

create table upload_idempotency_keys (
    idempotency_key varchar(200) primary key,
    request_hash varchar(128) not null,
    video_id bigint not null,
    video_file_id bigint not null,
    created_at datetime(6) not null,
    constraint fk_upload_idempotency_keys_video foreign key (video_id) references videos(id),
    constraint fk_upload_idempotency_keys_video_file foreign key (video_file_id) references video_files(id)
) engine = InnoDB;
