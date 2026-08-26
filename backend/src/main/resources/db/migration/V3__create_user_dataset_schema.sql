create table user_dataset_upload (
    id uuid primary key,
    base_dataset_version varchar(64) not null references dataset_version(version),
    access_token_sha256 char(64) not null unique
        check (access_token_sha256 ~ '^[0-9a-f]{64}$'),
    original_filename text not null check (length(btrim(original_filename)) > 0),
    object_key text not null unique check (length(btrim(object_key)) > 0),
    file_sha256 char(64) not null check (file_sha256 ~ '^[0-9a-f]{64}$'),
    byte_size integer not null check (byte_size > 0 and byte_size <= 1048576),
    schema_version varchar(32) not null check (schema_version = 'user-offer-v1'),
    row_count integer not null check (row_count between 1 and 500),
    isbn_count integer not null check (isbn_count between 1 and 100),
    reuse_consent boolean not null default false,
    reuse_review_status varchar(32) not null
        check (reuse_review_status in ('NOT_REQUESTED', 'PENDING_REVIEW')),
    consent_text_version varchar(32),
    consent_at timestamptz,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    check (expires_at > created_at),
    check (
        (reuse_consent and reuse_review_status = 'PENDING_REVIEW'
            and consent_text_version is not null and consent_at is not null)
        or
        (not reuse_consent and reuse_review_status = 'NOT_REQUESTED'
            and consent_text_version is null and consent_at is null)
    )
);

create index user_dataset_upload_expires_at_idx
    on user_dataset_upload(expires_at);

create table user_dataset_book (
    upload_id uuid not null references user_dataset_upload(id) on delete cascade,
    isbn varchar(13) not null check (isbn ~ '^[0-9]{13}$'),
    title text not null check (length(btrim(title)) between 1 and 200),
    quantity integer not null check (quantity between 1 and 100),
    primary key (upload_id, isbn)
);

create table user_dataset_offer (
    upload_id uuid not null references user_dataset_upload(id) on delete cascade,
    isbn varchar(13) not null,
    platform_id varchar(64) not null references platform(id),
    status varchar(16) not null
        check (status in ('ACCEPTED', 'REJECTED', 'UNKNOWN')),
    unit_price_cents bigint not null,
    repeat_policy varchar(32) not null
        check (repeat_policy in ('INHERIT_PLATFORM', 'ONE_PER_ORDER', 'UP_TO_INVENTORY')),
    primary key (upload_id, isbn, platform_id),
    foreign key (upload_id, isbn)
        references user_dataset_book(upload_id, isbn) on delete cascade,
    check (
        (status = 'ACCEPTED' and unit_price_cents > 0)
        or (status <> 'ACCEPTED' and unit_price_cents = 0)
    )
);

create index user_dataset_offer_upload_platform_idx
    on user_dataset_offer(upload_id, platform_id, isbn);
