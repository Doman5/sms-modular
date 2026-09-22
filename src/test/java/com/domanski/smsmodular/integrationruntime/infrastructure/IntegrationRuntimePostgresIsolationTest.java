package com.domanski.smsmodular.integrationruntime.infrastructure;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class IntegrationRuntimePostgresIsolationTest extends PostgresIntegrationTest {

	private static final String ROLE = "integration_runtime_probe";
	private static final String PASSWORD = "integration_runtime_probe_password";
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
			statement.execute("GRANT SELECT, INSERT, UPDATE ON TABLE outbox_messages TO " + ROLE);
			statement.execute("GRANT SELECT, INSERT ON TABLE inbox_receipts TO " + ROLE);
			tenantA = insertTenant(connection, "ir-rls-a-" + shortId());
			tenantB = insertTenant(connection, "ir-rls-b-" + shortId());
		}
	}

	@BeforeEach
	void clearTenantRows() throws Exception {
		try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
			statement.execute("DELETE FROM inbox_receipts WHERE tenant_id IN ('" + tenantA + "', '" + tenantB + "')");
			statement.execute("DELETE FROM outbox_messages WHERE tenant_id IN ('" + tenantA + "', '" + tenantB + "')");
		}
	}

	@AfterAll
	static void cleanSchema() throws Exception {
		try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
			statement.execute("DROP OWNED BY " + ROLE);
			statement.execute("DROP ROLE IF EXISTS " + ROLE);
			statement.execute("DELETE FROM inbox_receipts WHERE tenant_id IN ('" + tenantA + "', '" + tenantB + "')");
			statement.execute("DELETE FROM outbox_messages WHERE tenant_id IN ('" + tenantA + "', '" + tenantB + "')");
			statement.execute("DELETE FROM tenants WHERE id IN ('" + tenantA + "', '" + tenantB + "')");
		}
	}

	@Test
	void rlsSeparatesTwoTenantsAndRejectsMissingOrForeignContext() throws Exception {
		assertThat(queryOutboxCount(null)).isZero();
		insertOutbox(tenantA, "rls-a-" + UUID.randomUUID());
		insertOutbox(tenantB, "rls-b-" + UUID.randomUUID());
		assertThat(queryOutboxCount(tenantA)).isEqualTo(1);
		assertThat(queryOutboxCount(tenantB)).isEqualTo(1);
		assertThatThrownBy(() -> insertAs(tenantA, tenantB, "foreign-" + UUID.randomUUID()))
			.isInstanceOf(SQLException.class)
			.hasMessageContaining("row-level security");
	}

	@Test
	void skipLockedLeasesDifferentRowsForParallelWorkers() throws Exception {
		insertOutbox(tenantA, "parallel-a-" + UUID.randomUUID());
		insertOutbox(tenantA, "parallel-b-" + UUID.randomUUID());
		CountDownLatch bothLeased = new CountDownLatch(2);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService workers = Executors.newFixedThreadPool(2);
		try {
			Future<UUID> first = workers.submit(() -> leaseAndHold("worker-a", bothLeased, release));
			Future<UUID> second = workers.submit(() -> leaseAndHold("worker-b", bothLeased, release));
			assertThat(bothLeased.await(5, TimeUnit.SECONDS)).isTrue();
			release.countDown();
			assertThat(first.get()).isNotEqualTo(second.get());
		} finally {
			release.countDown();
			workers.shutdownNow();
		}
	}

	@Test
	void expiredLeaseIsReclaimedByTheNextWorker() throws Exception {
		UUID id = insertProcessing(tenantA, "old-owner", Instant.now().minusSeconds(30));
		UUID leased = leaseOnce("new-owner");
		assertThat(leased).isEqualTo(id);
		assertThat(queryOwner(id)).isEqualTo("new-owner");
	}

	@Test
	void inboxDuplicateDeliveryIsInsertOnce() throws Exception {
		UUID eventId = UUID.randomUUID();
		assertThat(insertInboxIfNew(tenantA, eventId, "consumer-a")).isTrue();
		assertThat(insertInboxIfNew(tenantA, eventId, "consumer-a")).isFalse();
		assertThat(queryInboxCount(tenantA, eventId, "consumer-a")).isEqualTo(1);
	}

	private static UUID leaseAndHold(String owner, CountDownLatch bothLeased, CountDownLatch release) throws Exception {
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			setTenant(connection, tenantA);
			UUID id;
			try (PreparedStatement statement = connection.prepareStatement(leaseSql())) {
				Instant now = Instant.now();
				statement.setObject(1, tenantA);
				statement.setTimestamp(2, Timestamp.from(now));
				statement.setTimestamp(3, Timestamp.from(now));
				statement.setInt(4, 1);
				statement.setString(5, owner);
				statement.setTimestamp(6, Timestamp.from(now.plusSeconds(30)));
				statement.setTimestamp(7, Timestamp.from(now));
				try (ResultSet result = statement.executeQuery()) {
					if (!result.next()) {
						throw new IllegalStateException("No row was leased");
					}
					id = result.getObject(1, UUID.class);
				}
			}
			bothLeased.countDown();
			if (!release.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Lease test release timed out");
			}
			connection.commit();
			return id;
		}
	}

	private static UUID leaseOnce(String owner) throws SQLException {
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			setTenant(connection, tenantA);
			try (PreparedStatement statement = connection.prepareStatement(leaseSql())) {
				Instant now = Instant.now();
				statement.setObject(1, tenantA);
				statement.setTimestamp(2, Timestamp.from(now));
				statement.setTimestamp(3, Timestamp.from(now));
				statement.setInt(4, 1);
				statement.setString(5, owner);
				statement.setTimestamp(6, Timestamp.from(now.plusSeconds(30)));
				statement.setTimestamp(7, Timestamp.from(now));
				try (ResultSet result = statement.executeQuery()) {
					if (!result.next()) {
						return null;
					}
					UUID id = result.getObject(1, UUID.class);
					connection.commit();
					return id;
				}
			}
		}
	}

	private static String leaseSql() {
		return "WITH candidates AS ("
			+ " SELECT id FROM outbox_messages WHERE tenant_id = ?"
			+ " AND ((status IN ('PENDING','RETRY_WAIT') AND available_at <= ?)"
			+ " OR (status = 'PROCESSING' AND lease_until <= ?))"
			+ " ORDER BY available_at, id FOR UPDATE SKIP LOCKED LIMIT ?)"
			+ " UPDATE outbox_messages message SET status='PROCESSING', attempt=message.attempt+1,"
			+ " lease_owner=?, lease_until=?, updated_at=?"
			+ " FROM candidates WHERE message.id=candidates.id RETURNING message.id";
	}

	private static UUID insertProcessing(UUID tenantId, String owner, Instant leaseUntil) throws SQLException {
		UUID id = UUID.randomUUID();
		insertOutboxRow(id, tenantId, "processing-" + id, "PROCESSING", owner, leaseUntil, 1);
		return id;
	}

	private static void insertOutbox(UUID tenantId, String idempotencyKey) throws SQLException {
		insertOutboxRow(UUID.randomUUID(), tenantId, idempotencyKey, "PENDING", null, null, 0);
	}

	private static void insertOutboxRow(
		UUID id, UUID tenantId, String idempotencyKey, String status, String owner, Instant leaseUntil, int attempt
	) throws SQLException {
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			setTenant(connection, tenantId);
			Instant now = Instant.now();
			try (PreparedStatement statement = connection.prepareStatement(
				"INSERT INTO outbox_messages (id, tenant_id, topic, aggregate_type, aggregate_id, payload_version, payload, status, attempt, available_at, lease_owner, lease_until, correlation_id, idempotency_key, created_at, updated_at) "
					+ "VALUES (?, ?, 'TEST.EVENT', 'TEST', ?, 1, '{}'::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
				statement.setObject(1, id);
				statement.setObject(2, tenantId);
				statement.setObject(3, UUID.randomUUID());
				statement.setString(4, status);
				statement.setInt(5, attempt);
				statement.setTimestamp(6, Timestamp.from(now.minusSeconds(1)));
				statement.setString(7, owner);
				if (leaseUntil == null) statement.setTimestamp(8, null); else statement.setTimestamp(8, Timestamp.from(leaseUntil));
				statement.setString(9, "ir-correlation-" + id);
				statement.setString(10, idempotencyKey);
				statement.setTimestamp(11, Timestamp.from(now));
				statement.setTimestamp(12, Timestamp.from(now));
				statement.executeUpdate();
			}
			connection.commit();
		}
	}

	private static boolean insertInboxIfNew(UUID tenantId, UUID eventId, String consumer) throws SQLException {
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			setTenant(connection, tenantId);
			try (PreparedStatement statement = connection.prepareStatement(
				"INSERT INTO inbox_receipts (id, tenant_id, consumer, event_id, received_at, correlation_id) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (tenant_id, consumer, event_id) DO NOTHING")) {
				statement.setObject(1, UUID.randomUUID());
				statement.setObject(2, tenantId);
				statement.setString(3, consumer);
				statement.setObject(4, eventId);
				statement.setTimestamp(5, Timestamp.from(Instant.now()));
				statement.setString(6, "ir-inbox-correlation");
				boolean inserted = statement.executeUpdate() == 1;
				connection.commit();
				return inserted;
			}
		}
	}

	private static int queryOutboxCount(UUID tenantId) throws SQLException {
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			if (tenantId != null) setTenant(connection, tenantId);
			try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT count(*) FROM outbox_messages")) {
				result.next();
				int count = result.getInt(1);
				connection.commit();
				return count;
			}
		}
	}

	private static int queryInboxCount(UUID tenantId, UUID eventId, String consumer) throws SQLException {
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			setTenant(connection, tenantId);
			try (PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM inbox_receipts WHERE tenant_id=? AND event_id=? AND consumer=?")) {
				statement.setObject(1, tenantId);
				statement.setObject(2, eventId);
				statement.setString(3, consumer);
				try (ResultSet result = statement.executeQuery()) {
					result.next();
					int count = result.getInt(1);
					connection.commit();
					return count;
				}
			}
		}
	}

	private static void insertAs(UUID contextTenant, UUID rowTenant, String idempotencyKey) throws SQLException {
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			setTenant(connection, contextTenant);
			try {
				insertOutboxUsingConnection(connection, rowTenant, idempotencyKey);
				connection.commit();
			} catch (SQLException exception) {
				connection.rollback();
				throw exception;
			}
		}
	}

	private static void insertOutboxUsingConnection(Connection connection, UUID tenantId, String idempotencyKey) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
			"INSERT INTO outbox_messages (id, tenant_id, topic, aggregate_type, aggregate_id, payload_version, payload, status, attempt, available_at, correlation_id, idempotency_key, created_at, updated_at) VALUES (?, ?, 'TEST.EVENT', 'TEST', ?, 1, '{}'::jsonb, 'PENDING', 0, ?, 'ir-foreign-correlation', ?, ?, ?)")) {
			Instant now = Instant.now();
			statement.setObject(1, UUID.randomUUID());
			statement.setObject(2, tenantId);
			statement.setObject(3, UUID.randomUUID());
			statement.setTimestamp(4, Timestamp.from(now));
			statement.setString(5, idempotencyKey);
			statement.setTimestamp(6, Timestamp.from(now));
			statement.setTimestamp(7, Timestamp.from(now));
			statement.executeUpdate();
		}
	}

	private static String queryOwner(UUID id) throws SQLException {
		try (Connection connection = probeConnection()) {
			connection.setAutoCommit(false);
			setTenant(connection, tenantA);
			try (PreparedStatement statement = connection.prepareStatement("SELECT lease_owner FROM outbox_messages WHERE id=?")) {
				statement.setObject(1, id);
				try (ResultSet result = statement.executeQuery()) {
					result.next();
					String owner = result.getString(1);
					connection.commit();
					return owner;
				}
			}
		}
	}

	private static void setTenant(Connection connection, UUID tenantId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
			statement.setString(1, tenantId.toString());
			statement.executeQuery().close();
		}
	}

	private static UUID insertTenant(Connection connection, String slug) throws SQLException {
		UUID id = UUID.randomUUID();
		Instant now = Instant.now();
		try (PreparedStatement statement = connection.prepareStatement(
			"INSERT INTO tenants (id, slug, name, status, timezone, locale, created_at, updated_at) VALUES (?, ?, 'IR test', 'ACTIVE', 'UTC', 'en', ?, ?)")) {
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
		Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
		return new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(), database);
	}

	private static String shortId() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
