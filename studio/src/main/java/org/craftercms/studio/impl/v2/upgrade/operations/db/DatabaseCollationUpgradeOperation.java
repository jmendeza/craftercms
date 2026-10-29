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
import static org.craftercms.studio.api.v2.utils.StudioConfiguration.DB_CHARACTER_SET;
import static org.craftercms.studio.api.v2.utils.StudioConfiguration.DB_COLLATION;
import static org.craftercms.studio.api.v2.utils.StudioConfiguration.DB_SCHEMA;

/**
 * Converts the Studio database tables to {@code studio.db.characterSet} and {@code studio.db.collation}.
 *
 * <p>This operation is retry-safe. Tables which already use the target collation are not rebuilt, so an interrupted
 * upgrade resumes with the remaining tables.</p>
 */
public class DatabaseCollationUpgradeOperation extends AbstractUpgradeOperation {

	private static final Logger logger = LoggerFactory.getLogger(DatabaseCollationUpgradeOperation.class);

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
		String characterSet = studioConfiguration.getProperty(DB_CHARACTER_SET);
		String collation = studioConfiguration.getProperty(DB_COLLATION);
		try (Connection connection = context.getConnection()) {
			try (Statement statement = connection.createStatement()) {
				statement.execute(format(ALTER_DATABASE_SQL, quoteIdentifier(schema), characterSet, collation));
				statement.execute("SET FOREIGN_KEY_CHECKS = 0");
				try {
					for (String table : getTablesToConvert(connection, schema, collation)) {
						logger.info("Convert table '{}.{}' to collation '{}'", schema, table, collation);
						statement.execute(format(ALTER_TABLE_SQL, quoteIdentifier(schema) + "." + quoteIdentifier(table),
							characterSet, collation));
					}
				} finally {
					statement.execute("SET FOREIGN_KEY_CHECKS = 1");
				}
			}
		} catch (SQLException e) {
			throw new UpgradeException(format("Failed to convert database '%s' to collation '%s'", schema, collation), e);
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
}
