package com.domanski.smsmodular.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.domanski.smsmodular.support.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;


class LiquibasePostgresMigrationTest extends PostgresIntegrationTest {

	private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

	@Test
	void appliesTwiceRollsBackControlChangeAndAppliesAgain() throws Exception {
		update();
		assertThat(schemaExists("foundation_control")).isTrue();
		int firstRunCount = changeSetCount();
		assertThat(firstRunCount).isGreaterThanOrEqualTo(3);
		assertThat(tableExists("tenants")).isTrue();
		assertThat(policyExists("audit_entries", "audit_entry_isolation")).isTrue();
		assertThat(policyExists("outbox_messages", "outbox_message_isolation")).isTrue();

		
		update();
		assertThat(changeSetCount()).isEqualTo(firstRunCount);
		assertThat(schemaExists("foundation_control")).isTrue();

		rollbackAllChangeSets(firstRunCount);
		assertThat(schemaExists("foundation_control")).isFalse();
		assertThat(tableExists("tenants")).isFalse();
		assertThat(changeSetCount()).isZero();

		update();
		assertThat(schemaExists("foundation_control")).isTrue();
		assertThat(changeSetCount()).isEqualTo(firstRunCount);
	}

	private void update() throws Exception {
		try (Connection connection = connection(); Liquibase liquibase = liquibase(connection)) {
			liquibase.update(new Contexts(), new LabelExpression());
		}
	}

	private void rollbackAllChangeSets(int count) throws Exception {
		try (Connection connection = connection(); Liquibase liquibase = liquibase(connection)) {
			liquibase.rollback(count, new Contexts(), new LabelExpression());
		}
	}

	private Connection connection() throws SQLException {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	private Liquibase liquibase(Connection connection) throws Exception {
		Database database = DatabaseFactory.getInstance()
			.findCorrectDatabaseImplementation(new JdbcConnection(connection));
		return new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
	}

	private int changeSetCount() throws SQLException {
		try (Connection connection = connection();
			var statement = connection.createStatement();
			ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM databasechangelog")) {
			result.next();
			return result.getInt(1);
		}
	}

	private boolean schemaExists(String schema) throws SQLException {
		try (Connection connection = connection();
			var statement = connection.prepareStatement(
				"SELECT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = ?)")) {
			statement.setString(1, schema);
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				return result.getBoolean(1);
			}
		}
	}

	private boolean tableExists(String table) throws SQLException {
		try (Connection connection = connection();
			var statement = connection.prepareStatement(
				"SELECT to_regclass(?) IS NOT NULL")) {
			statement.setString(1, "public." + table);
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				return result.getBoolean(1);
			}
		}
	}

	private boolean policyExists(String tableName, String policyName) throws SQLException {
		try (Connection connection = connection();
			var statement = connection.prepareStatement(
				"SELECT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename=? AND policyname=?)")) {
			statement.setString(1, tableName);
			statement.setString(2, policyName);
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				return result.getBoolean(1);
			}
		}
	}
}
