/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.dynamic.data.mapping.service.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBManagerUtil;
import com.liferay.portal.kernel.dao.db.DBType;
import com.liferay.portal.kernel.dao.jdbc.AutoBatchPreparedStatementUtil;
import com.liferay.portal.kernel.dao.jdbc.DataAccess;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Alberto Sousa
 */
@RunWith(Arquillian.class)
public class DDMFieldAttributeFinderPerformanceTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		DB db = DBManagerUtil.getDB();

		Assume.assumeTrue(db.getDBType() == DBType.POSTGRESQL);

		_addDDMFieldAttributes();
	}

	@After
	public void tearDown() throws Exception {
		DB db = DBManagerUtil.getDB();

		if (db.getDBType() != DBType.POSTGRESQL) {
			return;
		}

		_deleteDDMFieldAttributes();
	}

	@Test
	public void testFetchByF_AN_L() throws Exception {
		int blockCount = _getBlockCount();

		Assert.assertTrue(
			StringBundler.concat(
				"The F_AN_L lookup read ", blockCount, " blocks to return a ",
				"single row because no index on DDMFieldAttribute leads with ",
				"fieldId"),
			blockCount <= _MAX_BLOCK_COUNT);
	}

	private void _addDDMFieldAttributes() throws Exception {
		try (Connection connection = DataAccess.getConnection();

			PreparedStatement preparedStatement =
				AutoBatchPreparedStatementUtil.autoBatch(
					connection,
					StringBundler.concat(
						"insert into DDMFieldAttribute (mvccVersion, ",
						"ctCollectionId, fieldAttributeId, companyId, ",
						"fieldId, storageId, attributeName, languageId) ",
						"values (0, 0, ?, ?, ?, ?, ?, ?)"))) {

			for (int i = 0; i < _ROW_COUNT; i++) {
				long fieldAttributeId = _FIELD_ATTRIBUTE_ID_OFFSET + i;

				preparedStatement.setLong(1, fieldAttributeId);
				preparedStatement.setLong(3, fieldAttributeId);
				preparedStatement.setLong(4, fieldAttributeId);

				preparedStatement.setLong(2, _COMPANY_ID);

				// Four of every five rows carry a blank attribute name, which
				// is the distribution the storage layer writes and the one the
				// production call sites look up

				if ((i % 5) == 0) {
					preparedStatement.setString(5, "attribute" + (i % 97));
					preparedStatement.setString(6, "en_US");
				}
				else {
					preparedStatement.setNull(5, Types.VARCHAR);
					preparedStatement.setNull(6, Types.VARCHAR);
				}

				preparedStatement.addBatch();
			}

			preparedStatement.executeBatch();
		}

		_analyzeTable();
	}

	private void _analyzeTable() throws Exception {
		try (Connection connection = DataAccess.getConnection();

			Statement statement = connection.createStatement()) {

			statement.execute("analyze DDMFieldAttribute");
		}
	}

	private void _deleteDDMFieldAttributes() throws Exception {
		try (Connection connection = DataAccess.getConnection();

			PreparedStatement preparedStatement = connection.prepareStatement(
				"delete from DDMFieldAttribute where fieldAttributeId >= ?")) {

			preparedStatement.setLong(1, _FIELD_ATTRIBUTE_ID_OFFSET);

			preparedStatement.executeUpdate();
		}

		_analyzeTable();
	}

	/**
	 * Returns the number of shared buffer blocks PostgreSQL touches to resolve
	 * the query the <code>F_AN_L</code> finder generates when it is called with
	 * the blank arguments the production call sites pass. An index leading with
	 * <code>fieldId</code> resolves it in single digits regardless of table
	 * size. Without one the predicate offers no access path, so the block count
	 * grows with the table.
	 *
	 * <p>
	 * The first reported count belongs to the root node of the plan and already
	 * accounts for its children. The counts reported after the plan belong to
	 * planning rather than execution, and they are an order of magnitude larger
	 * than an indexed lookup's.
	 * </p>
	 */
	private int _getBlockCount() throws Exception {
		try (Connection connection = DataAccess.getConnection();

			PreparedStatement preparedStatement = connection.prepareStatement(
				StringBundler.concat(
					"explain (analyze, buffers) select * from ",
					"DDMFieldAttribute where fieldId = ? and (attributeName ",
					"is null or attributeName = '') and (languageId is null ",
					"or languageId = '') and ctCollectionId = 0"))) {

			// Probe a row whose ordinal is not a multiple of five, so that it
			// carries the blank attribute name the finder looks up

			preparedStatement.setLong(
				1, _FIELD_ATTRIBUTE_ID_OFFSET + (_ROW_COUNT / 2) + 1);

			try (ResultSet resultSet = preparedStatement.executeQuery()) {
				while (resultSet.next()) {
					Matcher matcher = _bufferPattern.matcher(
						resultSet.getString("QUERY PLAN"));

					if (!matcher.find()) {
						continue;
					}

					int blockCount = Integer.valueOf(matcher.group(1));

					String read = matcher.group(2);

					if (read != null) {
						blockCount += Integer.valueOf(read);
					}

					return blockCount;
				}
			}
		}

		throw new IllegalStateException("Unable to read the query plan");
	}

	private static final long _COMPANY_ID = 1;

	private static final long _FIELD_ATTRIBUTE_ID_OFFSET = 900000000;

	/**
	 * A lookup served by an index reads single digit block counts, while a
	 * lookup that scans the table reads hundreds at this row count and more as
	 * the table grows. Any bound between the two separates them.
	 */
	private static final int _MAX_BLOCK_COUNT = 100;

	private static final int _ROW_COUNT = 50000;

	private static final Pattern _bufferPattern = Pattern.compile(
		"shared hit=(\\d+)(?: read=(\\d+))?");

}