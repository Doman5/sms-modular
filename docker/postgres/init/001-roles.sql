CREATE ROLE sms_modular_owner LOGIN NOSUPERUSER PASSWORD 'sms_modular_owner_local';
CREATE ROLE sms_modular_runtime LOGIN NOSUPERUSER PASSWORD 'sms_modular_runtime_local';

ALTER DATABASE sms_modular OWNER TO sms_modular_owner;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO sms_modular_owner;
GRANT USAGE ON SCHEMA public TO sms_modular_runtime;

ALTER DEFAULT PRIVILEGES FOR ROLE sms_modular_owner IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE ON TABLES TO sms_modular_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE sms_modular_owner IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO sms_modular_runtime;
