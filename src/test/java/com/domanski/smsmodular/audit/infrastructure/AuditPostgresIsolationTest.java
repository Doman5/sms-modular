package com.domanski.smsmodular.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domanski.smsmodular.support.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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


class AuditPostgresIsolationTest extends PostgresIntegrationTest {

	private static final String ROLE = "audit_probe_user";
	private static final String PASSWORD = "audit_probe_password";
	private static UUID tenantA;
	private static UUID tenantB;

	@BeforeAll
	static void prepareSchema() throws Exception {
		try (Connection connection = adminConnection(); Liquibase liquibase = liquibase(connection)) {
			liquibase.update(new Contexts(), new LabelExpression());
		}
		try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
			statement.execute("DROP ROLE IF EXISTS " + ROLE);
			statement.execute("CREATE ROLE " + ROLE + " LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD '" + PASSWORD + "'");
			statement.execute("GRANT USAGE ON SCHEMA public TO " + ROLE);
			statement.execute("GRANT REFERENCES ON TABLE tenants TO " + ROLE);
			statement.execute("GRANT SELECT, INSERT ON TABLE audit_entries TO " + ROLE);
			tenantA = insertTenant(connection, "audit-rls-a-" + shortId());
			tenantB = insertTenant(connection, "audit-rls-b-" + shortId());
		}
	}

	@AfterAll
	static void cleanSchema() throws Exception {
		try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
			statement.execute("DROP OWNED BY " + ROLE);
			statement.execute("DROP ROLE IF EXISTS " + ROLE);
		}
	}

	@Test
	void isolatesTwoTenantsAndDeniesMissingOrForeignContext() throws Exception {
		assertThat(queryCount(null)).isZero();
		insertForTenant(tenantA, "a-record");
		insertForTenant(tenantB, "b-record");

		assertThat(queryCount(tenantA)).isEqualTo(1);
		assertThat(queryCount(tenantB)).isEqualTo(1);
		assertThatThrownBy(() -> insertAs(tenantA, tenantB, "cross-tenant"))
			.isInstanceOf(SQLException.class)
			.hasMessageContaining("row-level security");
		assertThatThrownBy(() -> insertAs(null, tenantA, "without-context"))
			.isInstanceOf(SQLException.class)
			.hasMessageContaining("row-level security");
	}

	@Test
	void platformNullRowsAreInsertOnlyAndNeverCreateAReadBypass() throws Exception {
		UUID platformId = UUID.randomUUID();
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			setConfig(connection, "app.tenant_id", "");
			setConfig(connection, "app.audit_platform", "true");
			insertAudit(connection, null, platformId, "PLATFORM", "platform-global");
			connection.commit();
		}

		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			setConfig(connection, "app.audit_platform", "true");
			try (PreparedStatement statement = connection.prepareStatement(
				"SELECT count(*) FROM audit_entries WHERE tenant_id IS NULL");
				ResultSet result = statement.executeQuery()) {
				result.next();
				assertThat(result.getInt(1)).isZero();
			}
			connection.rollback();
		}
	}

	@Test
	void updateAndDeleteAreRejectedByRuntimeRoleGrant() throws Exception {
		UUID id = insertForTenant(tenantA, "append-only");
		assertThatThrownBy(() -> mutate(id, "UPDATE audit_entries SET outcome = 'FAILURE' WHERE id = ?"))
			.isInstanceOf(SQLException.class)
			.hasMessageContaining("permission denied");
		assertThatThrownBy(() -> mutate(id, "DELETE FROM audit_entries WHERE id = ?"))
			.isInstanceOf(SQLException.class)
			.hasMessageContaining("permission denied");
	}

	@Test
	void auditInsertRollsBackWithTheOwningTransaction() throws Exception {
		UUID id;
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			setConfig(connection, "app.tenant_id", tenantA.toString());
			setConfig(connection, "app.audit_platform", "false");
			id = insertAudit(connection, tenantA, UUID.randomUUID(), "SYSTEM", "rolled-back");
			connection.rollback();
		}

		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			setConfig(connection, "app.tenant_id", tenantA.toString());
			setConfig(connection, "app.audit_platform", "false");
			try (PreparedStatement statement = connection.prepareStatement(
				"SELECT count(*) FROM audit_entries WHERE id = ?")) {
				statement.setObject(1, id);
				try (ResultSet result = statement.executeQuery()) {
					result.next();
					assertThat(result.getInt(1)).isZero();
				}
			}
			connection.rollback();
		}
	}

	private static UUID insertForTenant(UUID tenantId, String marker) throws SQLException {
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			setConfig(connection, "app.tenant_id", tenantId.toString());
			setConfig(connection, "app.audit_platform", "false");
			UUID id = insertAudit(connection, tenantId, UUID.randomUUID(), "SYSTEM", marker);
			connection.commit();
			return id;
		}
	}

	private static void insertAs(UUID contextTenant, UUID rowTenant, String marker) throws SQLException {
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			if (contextTenant != null) {
				setConfig(connection, "app.tenant_id", contextTenant.toString());
			}
			setConfig(connection, "app.audit_platform", "false");
			try {
				insertAudit(connection, rowTenant, UUID.randomUUID(), "SYSTEM", marker);
				connection.commit();
			} catch (SQLException exception) {
				connection.rollback();
				throw exception;
			}
		}
	}

	private static int queryCount(UUID tenantId) throws SQLException {
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			if (tenantId != null) {
				setConfig(connection, "app.tenant_id", tenantId.toString());
			}
			setConfig(connection, "app.audit_platform", "false");
			try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
				"SELECT count(*) FROM audit_entries")) {
				result.next();
				int count = result.getInt(1);
				connection.commit();
				return count;
			}
		}
	}

	private static void mutate(UUID id, String sql) throws SQLException {
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			setConfig(connection, "app.tenant_id", tenantA.toString());
			setConfig(connection, "app.audit_platform", "false");
			try (PreparedStatement statement = connection.prepareStatement(sql)) {
				statement.setObject(1, id);
				statement.executeUpdate();
			}
		}
	}

	private static UUID insertAudit(
		Connection connection,
		UUID tenantId,
		UUID actorId,
		String actorType,
		String marker
	) throws SQLException {
		UUID id = UUID.randomUUID();
		try (PreparedStatement statement = connection.prepareStatement(
			"INSERT INTO audit_entries "
				+ "(id, tenant_id, actor_type, actor_id, module, action, subject_type, subject_id, outcome, occurred_at, correlation_id, metadata) "
				+ "VALUES (?, ?, ?, ?, 'TEST', 'RECORDED', 'TEST', ?, 'SUCCESS', ?, ?, '{}'::jsonb)")) {
			statement.setObject(1, id);
			statement.setObject(2, tenantId);
			statement.setString(3, actorType);
			statement.setObject(4, actorId);
			statement.setObject(5, UUID.randomUUID());
			statement.setTimestamp(6, Timestamp.from(Instant.now()));
			statement.setString(7, "audit-" + marker);
			statement.executeUpdate();
		}
		return id;
	}

	private static void setConfig(Connection connection, String key, String value) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT set_config(?, ?, true)")) {
			statement.setString(1, key);
			statement.setString(2, value);
			statement.executeQuery().close();
		}
	}

	private static UUID insertTenant(Connection connection, String slug) throws SQLException {
		UUID id = UUID.randomUUID();
		Instant now = Instant.now();
		try (PreparedStatement statement = connection.prepareStatement(
			"INSERT INTO tenants (id, slug, name, status, timezone, locale, created_at, updated_at) VALUES (?, ?, 'Audit RLS', 'ACTIVE', 'UTC', 'en', ?, ?)")) {
			statement.setObject(1, id);
			statement.setString(2, slug);
			statement.setTimestamp(3, Timestamp.from(now));
			statement.setTimestamp(4, Timestamp.from(now));
			statement.executeUpdate();
		}
		return id;
	}

	private static Connection probeConnection() throws SQLException {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), ROLE, PASSWORD);
	}

	private static Connection adminConnection() throws SQLException {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	private static Liquibase liquibase(Connection connection) throws Exception {
		Database database = DatabaseFactory.getInstance()
			.findCorrectDatabaseImplementation(new JdbcConnection(connection));
		return new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(), database);
	}

	private static String shortId() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
