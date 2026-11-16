-- Crea la base de identity-service si no existe. Corre en cada docker compose up (servicio postgres-setup), así
-- también funciona con un volumen de Postgres creado antes de que existiera identity-service: el script de
-- init-databases.sql sólo corre la primera vez.
SELECT 'CREATE ROLE identity_user LOGIN PASSWORD ''identity_pass'''
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'identity_user')\gexec

SELECT 'CREATE DATABASE identity_db OWNER identity_user'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'identity_db')\gexec
