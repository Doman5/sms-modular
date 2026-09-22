-- Local-development roles only. Production deployments must provision
-- equivalent roles from a secret manager and must not reuse these passwords.
CREATE ROLE sms_modular_owner LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'sms_modular_owner_local';
CREATE ROLE sms_modular_runtime LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'sms_modular_runtime_local';

ALTER DATABASE sms_modular OWNER TO sms_modular_owner;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO sms_modular_owner;
GRANT USAGE ON SCHEMA public TO sms_modular_runtime;

ALTER DEFAULT PRIVILEGES FOR ROLE sms_modular_owner IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE ON TABLES TO sms_modular_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE sms_modular_owner IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO sms_modular_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE sms_modular_owner IN SCHEMA public
