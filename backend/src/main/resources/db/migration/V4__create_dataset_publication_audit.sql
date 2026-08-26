alter table dataset_book
    add column title_snapshot text;

update dataset_book db
set title_snapshot = b.title
from book b
where b.isbn = db.isbn;

alter table dataset_book
    alter column title_snapshot set not null,
    add constraint dataset_book_title_snapshot_not_blank
        check (length(btrim(title_snapshot)) > 0);

alter table user_dataset_upload
    drop constraint user_dataset_upload_reuse_review_status_check;

do $$
declare
    review_constraint_name text;
begin
    select conname
    into review_constraint_name
    from pg_constraint
    where conrelid = 'user_dataset_upload'::regclass
      and contype = 'c'
      and pg_get_constraintdef(oid) like '%reuse_consent%';

    if review_constraint_name is null then
        raise exception 'existing user dataset consent/review constraint was not found';
    end if;
    execute format(
        'alter table user_dataset_upload drop constraint %I',
        review_constraint_name
    );
end $$;

alter table user_dataset_upload
    add column reviewed_by varchar(128),
    add column reviewed_at timestamptz,
    add column published_dataset_version varchar(64) references dataset_version(version),
    add column rejection_reason varchar(500),
    add constraint user_dataset_upload_reuse_review_status_check
        check (reuse_review_status in (
            'NOT_REQUESTED', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED'
        )),
    add constraint user_dataset_upload_review_state_check
        check (
            (not reuse_consent
                and reuse_review_status = 'NOT_REQUESTED'
                and consent_text_version is null
                and consent_at is null
                and reviewed_by is null
                and reviewed_at is null
                and published_dataset_version is null
                and rejection_reason is null)
            or
            (reuse_consent
                and consent_text_version is not null
                and consent_at is not null
                and (
                    (reuse_review_status = 'PENDING_REVIEW'
                        and reviewed_by is null
                        and reviewed_at is null
                        and published_dataset_version is null
                        and rejection_reason is null)
                    or
                    (reuse_review_status = 'PUBLISHED'
                        and reviewed_by is not null
                        and reviewed_at is not null
                        and published_dataset_version is not null
                        and rejection_reason is null)
                    or
                    (reuse_review_status = 'REJECTED'
                        and reviewed_by is not null
                        and reviewed_at is not null
                        and published_dataset_version is null
                        and length(btrim(rejection_reason)) > 0)
                ))
        );

create index user_dataset_upload_pending_review_idx
    on user_dataset_upload(created_at, id)
    where reuse_review_status = 'PENDING_REVIEW';

create table dataset_publication_audit (
    dataset_version varchar(64) primary key references dataset_version(version),
    source_upload_id uuid not null,
    source_file_sha256 char(64) not null
        check (source_file_sha256 ~ '^[0-9a-f]{64}$'),
    base_dataset_version varchar(64) not null references dataset_version(version),
    source_batch varchar(128) not null check (length(btrim(source_batch)) > 0),
    created_by varchar(128) not null check (length(btrim(created_by)) > 0),
    created_at timestamptz not null,
    published_by varchar(128) not null check (length(btrim(published_by)) > 0),
    published_at timestamptz not null,
    status varchar(32) not null check (status = 'PUBLISHED')
);
