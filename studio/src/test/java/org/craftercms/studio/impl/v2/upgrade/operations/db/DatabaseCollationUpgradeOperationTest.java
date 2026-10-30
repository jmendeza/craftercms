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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.craftercms.studio.api.v2.utils.StudioConfiguration.DB_SCHEMA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DatabaseCollationUpgradeOperationTest {

	@Mock
	private StudioConfiguration studioConfiguration;
	@Mock
	private StudioUpgradeContext context;
	@Mock
	private Connection connection;
	@Mock
	private Statement statement;
	@Mock
	private PreparedStatement siteStatement;
	@Mock
	private PreparedStatement tablesStatement;
	@Mock
	private ResultSet siteResultSet;
	@Mock
	private ResultSet tablesResultSet;

	@Test
	public void alignsDatabaseAndOtherTablesWithSite() throws Exception {
		DatabaseCollationUpgradeOperation operation = new DatabaseCollationUpgradeOperation(studioConfiguration);
		when(studioConfiguration.getProperty(DB_SCHEMA)).thenReturn("crafter");
		when(context.getConnection()).thenReturn(connection);
		when(connection.createStatement()).thenReturn(statement);
		when(connection.prepareStatement(DatabaseCollationUpgradeOperation.FIND_SITE_COLLATION_SQL)).thenReturn(siteStatement);
		when(connection.prepareStatement(DatabaseCollationUpgradeOperation.FIND_TABLES_SQL)).thenReturn(tablesStatement);
		when(siteStatement.executeQuery()).thenReturn(siteResultSet);
		when(tablesStatement.executeQuery()).thenReturn(tablesResultSet);
		when(siteResultSet.next()).thenReturn(true);
		when(siteResultSet.getString(1)).thenReturn("utf8mb3");
		when(siteResultSet.getString(2)).thenReturn("utf8mb3_general_ci");
		when(tablesResultSet.next()).thenReturn(true, true, false);
		when(tablesResultSet.getString(1)).thenReturn("workflow", "item");

		operation.doExecute(context);

		verify(siteStatement).setString(1, "crafter");
		verify(tablesStatement).setString(1, "crafter");
		verify(tablesStatement).setString(2, "utf8mb3_general_ci");

		InOrder statements = inOrder(statement);
		statements.verify(statement).execute("ALTER DATABASE `crafter` CHARACTER SET `utf8mb3` COLLATE `utf8mb3_general_ci`");
		statements.verify(statement).execute("ALTER TABLE `crafter`.`workflow` CONVERT TO CHARACTER SET `utf8mb3` COLLATE `utf8mb3_general_ci`");
		statements.verify(statement).execute("ALTER TABLE `crafter`.`item` CONVERT TO CHARACTER SET `utf8mb3` COLLATE `utf8mb3_general_ci`");
	}

	@Test(expected = UpgradeException.class)
	public void failsWhenSiteTableIsMissing() throws Exception {
		DatabaseCollationUpgradeOperation operation = new DatabaseCollationUpgradeOperation(studioConfiguration);
		when(studioConfiguration.getProperty(DB_SCHEMA)).thenReturn("crafter");
		when(context.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(DatabaseCollationUpgradeOperation.FIND_SITE_COLLATION_SQL)).thenReturn(siteStatement);
		when(siteStatement.executeQuery()).thenReturn(siteResultSet);
		when(siteResultSet.next()).thenReturn(false);

		operation.doExecute(context);
	}

	@Test
	public void quotesBackticksInIdentifiers() {
		Assert.assertEquals("`cra``fter`", DatabaseCollationUpgradeOperation.quoteIdentifier("cra`fter"));
	}
}
