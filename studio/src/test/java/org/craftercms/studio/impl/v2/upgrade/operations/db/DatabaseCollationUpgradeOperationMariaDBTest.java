/*
 * Copyright (C) 2007-2026 Crafter Software Corporation. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.craftercms.studio.impl.v2.upgrade.operations.db;

import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.craftercms.studio.api.v2.utils.StudioConfiguration;
import org.craftercms.studio.impl.v2.upgrade.StudioUpgradeContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.craftercms.studio.api.v2.utils.StudioConfiguration.DB_CHARACTER_SET;
import static org.craftercms.studio.api.v2.utils.StudioConfiguration.DB_COLLATION;
import static org.craftercms.studio.api.v2.utils.StudioConfiguration.DB_SCHEMA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link DatabaseCollationUpgradeOperation} against embedded MariaDB.
 * <p>
 * {@code publish_request.package_id} starts as {@code utf8mb3_general_ci} and
 * {@code publish_package.old_package_id} starts as {@code utf8mb3_uca1400_ai_ci}. That mix rejects
 * the publisher upgrade join {@code old_package_id = package_id}. After the operation, every collation
 * reported by {@code information_schema} is the target collation and the join returns the row.
 */
public class DatabaseCollationUpgradeOperationMariaDBTest {

	private static final String DATABASE = "collation_upgrade_test";
	private static final String CHARACTER_SET = "utf8mb3";
	private static final String LEGACY_COLLATION = "utf8mb3_general_ci";
	private static final String TARGET_COLLATION = "utf8mb3_uca1400_ai_ci";
	private static final String PACKAGE_ID = "pkg-1";
	private static final String JOIN_SQL = """
		SELECT pr.package_id
		FROM publish_request pr
		INNER JOIN publish_package pp ON pp.old_package_id = pr.package_id
		""";
	private static final String SCHEMA_COLLATION_SQL = """
		SELECT default_collation_name
		FROM information_schema.schemata
		WHERE schema_name = ?
		""";
	private static final String TABLE_COLLATION_SQL = """
		SELECT table_collation
		FROM information_schema.tables
		WHERE table_schema = ?
		  AND table_type = 'BASE TABLE'
		""";
	private static final String COLUMN_COLLATION_SQL = """
		SELECT collation_name
		FROM information_schema.columns
		WHERE table_schema = ?
		  AND collation_name IS NOT NULL
		""";

	private static DB database;
	private static DataSource dataSource;

	@BeforeClass
	public static void startDatabase() throws Exception {
		DBConfigurationBuilder config = DBConfigurationBuilder.newBuilder();
		config.setPort(0);
		config.setSecurityDisabled(true);
		config.setDeletingTemporaryBaseAndDataDirsOnShutdown(true);
		database = DB.newEmbeddedDB(config.build());
		database.start();
		database.createDB(DATABASE);

		UnpooledDataSource unpooled = new UnpooledDataSource();
		unpooled.setDriver("org.mariadb.jdbc.Driver");
		unpooled.setUrl(config.getURL(DATABASE));
		unpooled.setUsername("root");
		unpooled.setPassword("");
		dataSource = unpooled;

		try (Connection connection = dataSource.getConnection();
		     Statement statement = connection.createStatement()) {
			connection.setAutoCommit(true);
			statement.execute("ALTER DATABASE `" + DATABASE + "` CHARACTER SET " + CHARACTER_SET
				+ " COLLATE " + LEGACY_COLLATION);
			statement.execute("""
				CREATE TABLE publish_request (
					package_id VARCHAR(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci
				""");
			statement.execute("""
				CREATE TABLE publish_package (
					old_package_id VARCHAR(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_uca1400_ai_ci
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_uca1400_ai_ci
				""");
			insertPackageId(connection, "INSERT INTO publish_request (package_id) VALUES (?)");
			insertPackageId(connection, "INSERT INTO publish_package (old_package_id) VALUES (?)");
		}
	}

	@AfterClass
	public static void stopDatabase() throws Exception {
		if (database != null) {
			database.stop();
		}
	}

	@Test
	public void convertsMixedCollationsSoPackageIdJoinWorks() throws Exception {
		assertEquals(Set.of(LEGACY_COLLATION), queryStrings(SCHEMA_COLLATION_SQL));
		assertEquals(Set.of(LEGACY_COLLATION, TARGET_COLLATION), queryStrings(TABLE_COLLATION_SQL));
		assertEquals(Set.of(LEGACY_COLLATION, TARGET_COLLATION), queryStrings(COLUMN_COLLATION_SQL));
		assertPackageIdJoinRejected();

		StudioConfiguration studioConfiguration = mock(StudioConfiguration.class);
		when(studioConfiguration.getProperty(DB_SCHEMA)).thenReturn(DATABASE);
		when(studioConfiguration.getProperty(DB_CHARACTER_SET)).thenReturn(CHARACTER_SET);
		when(studioConfiguration.getProperty(DB_COLLATION)).thenReturn(TARGET_COLLATION);

		DatabaseCollationUpgradeOperation operation = new DatabaseCollationUpgradeOperation(studioConfiguration);
		operation.doExecute(new StudioUpgradeContext(DATABASE, studioConfiguration, dataSource, null, null));

		assertEquals(Set.of(TARGET_COLLATION), queryStrings(SCHEMA_COLLATION_SQL));
		assertEquals(Set.of(TARGET_COLLATION), queryStrings(TABLE_COLLATION_SQL));
		assertEquals(Set.of(TARGET_COLLATION), queryStrings(COLUMN_COLLATION_SQL));
		assertEquals(PACKAGE_ID, joinedPackageId());
	}

	private static void insertPackageId(Connection connection, String sql) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, PACKAGE_ID);
			statement.executeUpdate();
		}
	}

	private static void assertPackageIdJoinRejected() throws SQLException {
		try (Connection connection = dataSource.getConnection();
		     Statement statement = connection.createStatement();
		     ResultSet resultSet = statement.executeQuery(JOIN_SQL)) {
			resultSet.next();
			fail("old_package_id = package_id should fail while the columns use different collations");
		} catch (SQLException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("Illegal mix of collations"));
		}
	}

	private static String joinedPackageId() throws SQLException {
		try (Connection connection = dataSource.getConnection();
		     Statement statement = connection.createStatement();
		     ResultSet resultSet = statement.executeQuery(JOIN_SQL)) {
			assertTrue(resultSet.next());
			String packageId = resultSet.getString(1);
			assertFalse(resultSet.next());
			return packageId;
		}
	}

	private static Set<String> queryStrings(String sql) throws SQLException {
		Set<String> values = new HashSet<>();
		try (Connection connection = dataSource.getConnection();
		     PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, DATABASE);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					values.add(resultSet.getString(1));
				}
			}
		}
		return values;
	}
}
