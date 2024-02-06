create table "projects" (
   "id" uuid not null
 , "name" text not null
 , "domain" text
 , "data" jsonb
 , "tags" jsonb
 , "created_at" timestamptz(6) not null default current_timestamp
 , "updated_at" timestamptz(6) not null default current_timestamp
 , constraint "projects_pk" primary key ("id")
);

create table "users" (
    "id" text not null
  , "project_id" uuid not null
  , "email" text
  , "first_name" text
  , "last_name" text
  , "name" text
  , "org_id" text
  , "org_name" text
  , "data" jsonb
  , "tags" jsonb
  , "created_at" timestamptz(6) not null default current_timestamp
  , "updated_at" timestamptz(6) not null default current_timestamp
  , constraint "users_pk" primary key ("id", "project_id")
);

create table "sessions" (
   "id" uuid not null
 , "project_id" uuid not null
 , "user_id" text
 , "project_version_id" text
 , "hostname" text
 , "user_agent" text
 , "user_agent_data" jsonb
 , "device_id" text
 , "screen_width" int
 , "screen_height" int
 , "language" text
 , "ip_address" text
 , "geo_data" jsonb
 , "started_at" timestamptz(6) not null
 , "ended_at" timestamptz(6)
 , "data" jsonb
 , "tags" jsonb
 , "created_at" timestamptz(6) not null default current_timestamp
 , "updated_at" timestamptz(6) not null default current_timestamp
 , constraint "sessions_pk" primary key ("id")
);


create table "events" (
   "id" uuid not null
 , "project_id" uuid not null
 , "session_id" uuid not null
 , "name" text not null
 , "url_path" text not null
 , "url_query" text
 , "referrer_path" text
 , "referrer_query" text
 , "referrer_host" text
 , "page_title" text
 , "occurred_at" timestamptz(6) not null default current_timestamp
 , "data" jsonb
 , "tags" jsonb
 , "created_at" timestamptz(6) not null default current_timestamp
 , constraint "events_pk" primary key ("id")
);


create unique index "users__project_email_ak" on "users"("project_id", "email");
create index "sessions__project_user_ix" on "sessions"("project_id", "user_id");
create index "sessions__created_ix" on "sessions"("created_at");
create index "events__project_session_name_ix" on "events"("project_id", "session_id", "name");
create index "events__created_ix" on "events"("created_at");
