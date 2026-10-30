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

import org.craftercms.commons.upgrade.exception.UpgradeException;
import org.craftercms.studio.api.v2.utils.StudioConfiguration;
import org.craftercms.studio.impl.v2.upgrade.StudioUpgradeContext;
import org.craftercms.studio.impl.v2.upgrade.operations.AbstractUpgradeOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static org.craftercms.studio.api.v2.utils.StudioConfiguration.DB_SCHEMA;

/**
 * Sets the database default character set and collation to those of the {@code site} table, then converts any table
 * that still uses a different collation.
 *
 * <p>Runs before new tables are created, so those tables inherit {@code site}'s collation. Tables which already
 * match are not rebuilt, so an interrupted upgrade resumes with the remaining tables.</p>
 */
public class DatabaseCollationUpgradeOperation extends AbstractUpgradeOperation {

	private static final Logger logger = LoggerFactory.getLogger(DatabaseCollationUpgradeOperation.class);

	static final String FIND_SITE_COLLATION_SQL = """
		SELECT c.character_set_name, t.table_collation
		FROM information_schema.tables t
		INNER JOIN information_schema.collations c ON c.collation_name = t.table_collation
		WHERE t.table_schema = ?
		  AND t.table_name = 'site'
		""";
	static final String FIND_TABLES_SQL = """
		SELECT table_name
		FROM information_schema.tables
		WHERE table_schema = ?
		  AND table_type = 'BASE TABLE'
		  AND table_collation <> ?
		""";
	static final String ALTER_DATABASE_SQL = "ALTER DATABASE %s CHARACTER SET %s COLLATE %s";
	static final String ALTER_TABLE_SQL = "ALTER TABLE %s CONVERT TO CHARACTER SET %s COLLATE %s";

	public DatabaseCollationUpgradeOperation(StudioConfiguration studioConfiguration) {
		super(studioConfiguration);
	}

	@Override
	protected void doExecute(StudioUpgradeContext context) throws UpgradeException {
		String schema = studioConfiguration.getProperty(DB_SCHEMA);
		try (Connection connection = context.getConnection()) {
			SchemaDefaults defaults = readSiteDefaults(connection, schema);
			try (Statement statement = connection.createStatement()) {
				logger.info("Set database '{}' default character set to '{}' and collation to '{}', matching table 'site'",
					schema, defaults.characterSet(), defaults.collation());
				statement.execute(format(ALTER_DATABASE_SQL, quoteIdentifier(schema),
					quoteIdentifier(defaults.characterSet()), quoteIdentifier(defaults.collation())));
				statement.execute("SET FOREIGN_KEY_CHECKS = 0");
				SQLException conversionFailure = null;
				try {
					for (String table : getTablesToConvert(connection, schema, defaults.collation())) {
						logger.info("Convert table '{}.{}' to collation '{}'", schema, table, defaults.collation());
						statement.execute(format(ALTER_TABLE_SQL, quoteIdentifier(schema) + "." + quoteIdentifier(table),
							quoteIdentifier(defaults.characterSet()), quoteIdentifier(defaults.collation())));
					}
				} catch (SQLException e) {
					conversionFailure = e;
					throw e;
				} finally {
					try {
						statement.execute("SET FOREIGN_KEY_CHECKS = 1");
					} catch (SQLException resetFailure) {
						logger.error("Failed to re-enable foreign key checks after converting database '{}' to collation '{}'",
							schema, defaults.collation(), resetFailure);
						if (conversionFailure != null) {
							conversionFailure.addSuppressed(resetFailure);
						} else {
							throw resetFailure;
						}
					}
				}
			}
		} catch (SQLException e) {
			throw new UpgradeException(format("Failed to align database '%s' with the collation of table 'site'", schema), e);
		}
	}

	protected SchemaDefaults readSiteDefaults(Connection connection, String schema) throws SQLException, UpgradeException {
		try (PreparedStatement statement = connection.prepareStatement(FIND_SITE_COLLATION_SQL)) {
			statement.setString(1, schema);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new UpgradeException(format("Could not read the collation of table 'site' in database '%s'", schema));
				}
				return new SchemaDefaults(resultSet.getString(1), resultSet.getString(2));
			}
		}
	}

	protected List<String> getTablesToConvert(Connection connection, String schema, String collation) throws SQLException {
		List<String> tables = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(FIND_TABLES_SQL)) {
			statement.setString(1, schema);
			statement.setString(2, collation);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					tables.add(resultSet.getString(1));
				}
			}
		}
		return tables;
	}

	static String quoteIdentifier(String identifier) {
		return "`" + identifier.replace("`", "``") + "`";
	}

	private record SchemaDefaults(String characterSet, String collation) {
	}
}
