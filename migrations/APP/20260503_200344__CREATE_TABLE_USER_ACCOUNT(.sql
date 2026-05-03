-- DDL Change Tracker
-- Timestamp  : 2026-05-03T20:03:44
-- User       : wildanaizzaddin
-- Datasource : DB App
-- Schema     : APP

create table app.user_account( id uuid primary key, user_name varchar, email varchar, password varchar, age numeric, created_at timestamp, created_by timestamp )