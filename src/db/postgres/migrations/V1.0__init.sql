create table "project" (
   "id" uuid not null
 , "name" varchar(100) not null
 , "domain" varchar(500)
 , "created_at" timestamptz(6) not null default current_timestamp
 , "updated_at" timestamptz(6) not null default current_timestamp
 , constraint "project_pk" primary key ("id")
);

create table "user" (
    "id" varchar(100) not null
  , "project_id" uuid not null
  , "email" varchar(100)
  , "first_name" varchar(100)
  , "last_name" varchar(100)
  , "name" varchar(200)
  , "org_id" varchar(100)
  , "org_name" varchar(100)
  , "tags" jsonb
  , "created_at" timestamptz(6) not null default current_timestamp
  , "updated_at" timestamptz(6) not null default current_timestamp
  , constraint "user_pk" primary key ("id", "project_id")
);

create table "session" (
   "id" uuid not null
 , "project_id" uuid not null
 , "user_id" varchar(100)
 , "project_version_id" varchar(50)
 , "hostname" varchar(100)
 , "browser" varchar(20)
 , "os" varchar(20)
 , "device" varchar(20)
 , "screen_width" int
 , "screen_height" int
 , "language" varchar(35)
 , "ip_address" varchar(50)
 , "country" char(2)
 , "city" varchar(50)
 , "subdivision_1" varchar(20)
 , "subdivision_2" varchar(50)
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
 , "name" varchar(100) not null
 , "url_path" varchar(500) not null
 , "url_query" varchar(500)
 , "referrer_path" varchar(500)
 , "referrer_query" varchar(500)
 , "referrer_host" varchar(500)
 , "page_title" varchar(500)
 , "occurred_at" timestamptz(6) not null default current_timestamp
 , "created_at" timestamptz(6) not null default current_timestamp
 , constraint "event_pk" primary key ("id")
);


create table "event_data" (
   "event_id" uuid not null
 , "key" varchar(500) not null
 , "string_value" varchar(500)
 , "int_value" integer
 , "decimal_value" decimal(19,4)
 , "timestamp_value" timestamptz(6)
 , "json_value" jsonb
 , "created_at" timestamptz(6) not null default current_timestamp
 , constraint "event_data_pk" primary key ("event_id", "key")
);


create table "session_data" (
   "session_id" uuid not null
 , "key" varchar(500) not null
 , "string_value" varchar(500)
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
