/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.workflow.node.delegate;

import com.liferay.portal.json.JSONFactoryImpl;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.io.Serializable;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Alberto Sousa
 */
public class CategorizeContentEntriesServiceNodeDelegateTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() {
		ReflectionTestUtil.setFieldValue(
			_categorizeContentEntriesServiceNodeDelegate, "_jsonFactory",
			_jsonFactory);
	}

	@Test
	public void testExecute() throws Exception {
		long taxonomyCategoryId1 = RandomTestUtil.randomLong();
		long taxonomyCategoryId2 = RandomTestUtil.randomLong();

		_testExecute(
			taxonomyCategoryId1, taxonomyCategoryId2,
			JSONUtil.putAll(
				taxonomyCategoryId1, taxonomyCategoryId2
			).toString());
		_testExecute(
			taxonomyCategoryId1, taxonomyCategoryId2,
			taxonomyCategoryId1 + "," + taxonomyCategoryId2);

		_testExecuteWithoutTaxonomyCategoryIds();
	}

	private void _assertTaxonomyCategoryIds(
			String contentEntriesPayload, long taxonomyCategoryId1,
			long taxonomyCategoryId2)
		throws Exception {

		JSONArray jsonArray = _jsonFactory.createJSONArray(
			contentEntriesPayload);

		Assert.assertEquals(jsonArray.toString(), 2, jsonArray.length());

		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject jsonObject = jsonArray.getJSONObject(i);

			JSONArray taxonomyCategoryIdsJSONArray = jsonObject.getJSONArray(
				"taxonomyCategoryIds");

			Assert.assertEquals(2, taxonomyCategoryIdsJSONArray.length());
			Assert.assertEquals(
				taxonomyCategoryId1, taxonomyCategoryIdsJSONArray.getLong(0));
			Assert.assertEquals(
				taxonomyCategoryId2, taxonomyCategoryIdsJSONArray.getLong(1));
		}
	}

	private void _testExecute(
			long taxonomyCategoryId1, long taxonomyCategoryId2,
			String taxonomyCategoryIds)
		throws Exception {

		Map<String, Serializable> workflowContext = new HashMap<>();

		String contentEntriesPayload =
			_categorizeContentEntriesServiceNodeDelegate.execute(
				HashMapBuilder.put(
					"contentEntriesPayload", _CONTENT_ENTRIES_PAYLOAD
				).put(
					"taxonomyCategoryIds", taxonomyCategoryIds
				).build(),
				workflowContext);

		Assert.assertEquals(
			contentEntriesPayload,
			workflowContext.get("contentEntriesPayload"));
		_assertTaxonomyCategoryIds(
			contentEntriesPayload, taxonomyCategoryId1, taxonomyCategoryId2);
	}

	private void _testExecuteWithoutTaxonomyCategoryIds() throws Exception {
		String contentEntriesPayload =
			_categorizeContentEntriesServiceNodeDelegate.execute(
				HashMapBuilder.put(
					"contentEntriesPayload", _CONTENT_ENTRIES_PAYLOAD
				).build(),
				new HashMap<>());

		Assert.assertEquals(_CONTENT_ENTRIES_PAYLOAD, contentEntriesPayload);

		JSONArray jsonArray = _jsonFactory.createJSONArray(
			contentEntriesPayload);

		JSONObject jsonObject = jsonArray.getJSONObject(0);

		Assert.assertFalse(jsonObject.has("taxonomyCategoryIds"));
	}

	private static final String _CONTENT_ENTRIES_PAYLOAD =
		"[{\"externalReferenceCode\": \"entry-1\", \"properties\": " +
			"{\"title\": \"Entry 1\"}}, {\"externalReferenceCode\": " +
				"\"entry-2\", \"properties\": {\"title\": \"Entry 2\"}}]";

	private final CategorizeContentEntriesServiceNodeDelegate
		_categorizeContentEntriesServiceNodeDelegate =
			new CategorizeContentEntriesServiceNodeDelegate();
	private final JSONFactory _jsonFactory = new JSONFactoryImpl();

}