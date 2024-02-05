create table "project" (
   "id" uuid not null
 , "name" text not null
 , "domain" text
 , "created_at" timestamptz(6) not null default current_timestamp
 , "updated_at" timestamptz(6) not null default current_timestamp
 , constraint "project_pk" primary key ("id")
);

create table "user" (
    "id" text not null
  , "project_id" uuid not null
  , "email" text
  , "first_name" text
  , "last_name" text
  , "name" text
  , "org_id" text
  , "org_name" text
  , "tags" jsonb
  , "created_at" timestamptz(6) not null default current_timestamp
  , "updated_at" timestamptz(6) not null default current_timestamp
  , constraint "user_pk" primary key ("id", "project_id")
);

create table "session" (
   "id" uuid not null
 , "project_id" uuid not null
 , "user_id" text
 , "project_version_id" text
 , "hostname" text
 , "user_agent" text
 , "user_agent_family" text
 , "user_agent_major" text
 , "user_agent_minor" text
 , "os_family" text
 , "os_major" text
 , "os_minor" text
 , "device_family" text
 , "device_id" text
 , "screen_width" int
 , "screen_height" int
 , "language" text
 , "ip_address" text
 , "iso_country_code" text
 , "least_specific_subdivision" text
 , "most_specific_subdivision" text
 , "city" text
 , "postal_code" text
 , "latitude" decimal(7,4)
 , "longitude" decimal(7,4)
 , "started_at" timestamptz(6) not null
 , "ended_at" timestamptz(6)
 , "created_at" timestamptz(6) not null default current_timestamp
 , "updated_at" timestamptz(6) not null default current_timestamp
 , constraint "session_pk" primary key ("id")
);


create table "event" (
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
 , "created_at" timestamptz(6) not null default current_timestamp
 , constraint "event_pk" primary key ("id")
);


create table "event_data" (
   "event_id" uuid not null
 , "key" text not null
 , "string_value" text
 , "int_value" integer
 , "decimal_value" decimal(19,4)
 , "timestamp_value" timestamptz(6)
 , "json_value" jsonb
 , "created_at" timestamptz(6) not null default current_timestamp
 , constraint "event_data_pk" primary key ("event_id", "key")
);


create table "session_data" (
   "session_id" uuid not null
 , "key" text not null
 , "string_value" text
 , "int_value" integer
 , "decimal_value" decimal(19,4)
 , "timestamp_value" timestamptz(6)
 , "json_value" jsonb
 , "created_at" timestamptz(6) not null default current_timestamp
 , constraint "session_data_pk" primary key ("session_id", "key")
);


create unique index "user__project_email_ak" on "user"("project_id", "email");
create index "session__project_user_ix" on "session"("project_id", "user_id");
create index "session__created_ix" on "session"("created_at");
create index "event__project_session_name_ix" on "event"("project_id", "session_id", "name");
create index "event__created_ix" on "event"("created_at");
create index "event_data__created_ix" on "event_data"("created_at");
create index "session_data__created_ix" on "session_data"("created_at");
