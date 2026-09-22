package com.domanski.smsmodular.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domanski.smsmodular.support.PostgresIntegrationTest;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


class RlsPostgresIsolationTest extends PostgresIntegrationTest {

	private static final String PROBE_TABLE = "tenancy_rls_probe";
	private static final String PROBE_ROLE = "tenancy_probe_user";
	private static final String PROBE_PASSWORD = "tenancy_probe_password";
	private static UUID tenantA;
	private static UUID tenantB;

	@BeforeAll
	static void prepareSchema() throws Exception {
		try (Connection connection = adminConnection(); Liquibase liquibase = liquibase(connection)) {
			liquibase.update(new Contexts(), new LabelExpression());
		}
		try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
			statement.execute("CREATE ROLE " + PROBE_ROLE + " LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD '" + PROBE_PASSWORD + "'");
			statement.execute("GRANT USAGE ON SCHEMA public TO " + PROBE_ROLE);
			statement.execute("GRANT REFERENCES ON TABLE tenants TO " + PROBE_ROLE);
			tenantA = insertTenant(connection, "rls-a-" + UUID.randomUUID().toString().substring(0, 8));
			tenantB = insertTenant(connection, "rls-b-" + UUID.randomUUID().toString().substring(0, 8));
			statement.execute("CREATE TABLE " + PROBE_TABLE + " (id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id), marker varchar(64) NOT NULL)");
			statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE " + PROBE_TABLE + " TO " + PROBE_ROLE);
			statement.execute("ALTER TABLE " + PROBE_TABLE + " ENABLE ROW LEVEL SECURITY");
			statement.execute("ALTER TABLE " + PROBE_TABLE + " FORCE ROW LEVEL SECURITY");
			statement.execute("CREATE POLICY tenancy_probe_isolation ON " + PROBE_TABLE
				+ " USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)"
				+ " WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)");
		}
	}

	@AfterAll
	static void cleanSchema() throws Exception {
		try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS " + PROBE_TABLE);
			statement.execute("DROP OWNED BY " + PROBE_ROLE);
			statement.execute("REVOKE ALL PRIVILEGES ON TABLE tenants FROM " + PROBE_ROLE);
			statement.execute("REVOKE USAGE ON SCHEMA public FROM " + PROBE_ROLE);
			statement.execute("DROP ROLE IF EXISTS " + PROBE_ROLE);
		}
	}

	@Test
	void isolatesReadWriteByTenantAndDeniesMissingOrForeignContext() throws Exception {
		assertThat(queryAsProbe("SELECT count(*) FROM " + PROBE_TABLE)).isZero();

		withTenant(tenantA, connection -> insertAsProbe(connection, tenantA, "a-record"));
		withTenant(tenantB, connection -> insertAsProbe(connection, tenantB, "b-record"));

		assertThat(withTenantResult(tenantA, "SELECT count(*) FROM " + PROBE_TABLE)).isEqualTo(1);
		assertThat(withTenantResult(tenantB, "SELECT count(*) FROM " + PROBE_TABLE)).isEqualTo(1);
		assertThat(withTenantResult(tenantA, "SELECT count(*) FROM " + PROBE_TABLE + " WHERE tenant_id = '" + tenantB + "'")).isZero();

		assertThatThrownBy(() -> withTenant(tenantA, connection -> insertAsProbe(connection, tenantB, "cross-tenant")))
			.isInstanceOf(SQLException.class)
			.hasMessageContaining("row-level security");
	}

	@Test
	void setLocalDoesNotLeakThroughAReusedPoolConnection() throws Exception {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(POSTGRES.getJdbcUrl());
		config.setUsername(PROBE_ROLE);
		config.setPassword(PROBE_PASSWORD);
		config.setMaximumPoolSize(1);
		config.setMinimumIdle(1);
		try (HikariDataSource pool = new HikariDataSource(config)) {
			try (Connection connection = pool.getConnection()) {
				connection.setAutoCommit(false);
				setLocalTenant(connection, tenantA);
				connection.commit();
			}
			try (Connection connection = pool.getConnection();
				var statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT current_setting('app.tenant_id', true)")) {
				result.next();
				String currentTenant = result.getString(1);
				assertThat(currentTenant == null || currentTenant.isEmpty()).isTrue();
			}
		}
	}

	private static void insertAsProbe(Connection connection, UUID tenantId, String marker) throws SQLException {
		try (var statement = connection.prepareStatement(
			"INSERT INTO " + PROBE_TABLE + " (id, tenant_id, marker) VALUES (?, ?, ?)")) {
			statement.setObject(1, UUID.randomUUID());
			statement.setObject(2, tenantId);
			statement.setString(3, marker);
			statement.executeUpdate();
		}
	}

	private static int withTenantResult(UUID tenantId, String sql) throws SQLException {
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			setLocalTenant(connection, tenantId);
			try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
				result.next();
				int value = result.getInt(1);
				connection.commit();
				return value;
			} catch (SQLException exception) {
				connection.rollback();
				throw exception;
			}
		}
	}

	private static int queryAsProbe(String sql) throws SQLException {
		try (Connection connection = probeConnection(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
			result.next();
			return result.getInt(1);
		}
	}

	private static void withTenant(UUID tenantId, SqlOperation operation) throws SQLException {
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			setLocalTenant(connection, tenantId);
			try {
				operation.run(connection);
				connection.commit();
			} catch (SQLException exception) {
				connection.rollback();
				throw exception;
			}
		}
	}

	private static void setLocalTenant(Connection connection, UUID tenantId) throws SQLException {
		try (var statement = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
			statement.setString(1, tenantId.toString());
			statement.executeQuery().close();
		}
	}

	private static Connection probeConnection() throws SQLException {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), PROBE_ROLE, PROBE_PASSWORD);
	}

	private static Connection adminConnection() throws SQLException {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	private static UUID insertTenant(Connection connection, String slug) throws SQLException {
		UUID id = UUID.randomUUID();
		try (var statement = connection.prepareStatement(
			"INSERT INTO tenants (id, slug, name, status, timezone, locale, created_at, updated_at) VALUES (?, ?, 'RLS probe', 'ACTIVE', 'UTC', 'en', ?, ?)")) {
			Instant now = Instant.now();
			statement.setObject(1, id);
			statement.setString(2, slug);
			statement.setTimestamp(3, Timestamp.from(now));
			statement.setTimestamp(4, Timestamp.from(now));
			statement.executeUpdate();
		}
		return id;
	}

	@FunctionalInterface
	private interface SqlOperation {
		void run(Connection connection) throws SQLException;
	}

	private static Liquibase liquibase(Connection connection) throws Exception {
		Database database = DatabaseFactory.getInstance()
			.findCorrectDatabaseImplementation(new JdbcConnection(connection));
		return new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(), database);
	}
}
