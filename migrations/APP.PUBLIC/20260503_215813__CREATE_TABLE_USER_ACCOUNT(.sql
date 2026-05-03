-- DDL Change Tracker
-- Timestamp  : 2026-05-03T21:58:13
-- User       : wildanaizzaddin
-- Datasource : 43.156.27.134:31214
-- Schema     : APP.PUBLIC

create table user_account( id uuid primary key, user_name varchar, email varchar, password varchar, age numeric, created_at timestamp, created_by timestamp )