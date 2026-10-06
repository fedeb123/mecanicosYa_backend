CREATE USER mechanics_user WITH PASSWORD 'mechanics_pass';
CREATE USER assistances_user WITH PASSWORD 'assistances_pass';
CREATE USER dispatch_user WITH PASSWORD 'dispatch_pass';

CREATE DATABASE mechanics_db OWNER mechanics_user;
CREATE DATABASE assistances_db OWNER assistances_user;
CREATE DATABASE dispatch_db OWNER dispatch_user;

\connect mechanics_db
CREATE EXTENSION IF NOT EXISTS postgis;

\connect assistances_db
CREATE EXTENSION IF NOT EXISTS postgis;

\connect dispatch_db
CREATE EXTENSION IF NOT EXISTS postgis;

