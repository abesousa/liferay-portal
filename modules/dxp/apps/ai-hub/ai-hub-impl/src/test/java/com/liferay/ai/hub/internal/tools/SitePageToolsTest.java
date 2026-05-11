/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.tools;

import com.liferay.portal.json.JSONFactoryImpl;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Mario Gomes
 */
public class SitePageToolsTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@BeforeClass
	public static void setUpClass() {
		_setUpJSONFactoryUtil();
	}

	@Test
	public void testAlignExternalReferenceCodesExactMatch() {
		JSONObject bodyJSONObject = JSONUtil.put(
			"fragmentExternalReferenceCode", "old-erc"
		).put(
			"name", "Page"
		);

		_invokeAlignExternalReferenceCodes(
			bodyJSONObject, "old-erc", "new-erc");

		Assert.assertEquals(
			"new-erc",
			bodyJSONObject.getString("fragmentExternalReferenceCode"));
	}

	@Test
	public void testAlignExternalReferenceCodesNested() {
		JSONObject bodyJSONObject = JSONUtil.put(
			"fragmentInstances",
			JSONUtil.putAll(
				JSONUtil.put(
					"fragmentExternalReferenceCode", "old-erc-default")));

		_invokeAlignExternalReferenceCodes(
			bodyJSONObject, "old-erc", "new-erc");

		JSONObject fragmentInstanceJSONObject = bodyJSONObject.getJSONArray(
			"fragmentInstances"
		).getJSONObject(
			0
		);

		Assert.assertEquals(
			"new-erc-default",
			fragmentInstanceJSONObject.getString(
				"fragmentExternalReferenceCode"));
	}

	@Test
	public void testAlignExternalReferenceCodesPrefixMatch() {
		JSONObject bodyJSONObject = JSONUtil.put(
			"fragmentExternalReferenceCode", "old-erc-default");

		_invokeAlignExternalReferenceCodes(
			bodyJSONObject, "old-erc", "new-erc");

		Assert.assertEquals(
			"new-erc-default",
			bodyJSONObject.getString("fragmentExternalReferenceCode"));
	}

	@Test
	public void testAlignExternalReferenceCodesUnrelatedKeyUnchanged() {
		JSONObject bodyJSONObject = JSONUtil.put("name", "old-erc");

		_invokeAlignExternalReferenceCodes(
			bodyJSONObject, "old-erc", "new-erc");

		Assert.assertEquals("old-erc", bodyJSONObject.getString("name"));
	}

	@Test
	public void testEnsurePageExperienceERCsAddsDefault() {
		JSONObject bodyJSONObject = JSONUtil.put(
			"pageExperiences",
			JSONUtil.putAll(JSONUtil.put("name", "Default")));

		_invokeEnsurePageExperienceERCs(bodyJSONObject, "spec-erc");

		JSONObject experienceJSONObject = bodyJSONObject.getJSONArray(
			"pageExperiences"
		).getJSONObject(
			0
		);

		Assert.assertEquals(
			"spec-erc-default",
			experienceJSONObject.getString("externalReferenceCode"));
	}

	@Test
	public void testEnsurePageExperienceERCsKeepsExisting() {
		JSONObject bodyJSONObject = JSONUtil.put(
			"pageExperiences",
			JSONUtil.putAll(
				JSONUtil.put("externalReferenceCode", "existing-erc")));

		_invokeEnsurePageExperienceERCs(bodyJSONObject, "spec-erc");

		JSONObject experienceJSONObject = bodyJSONObject.getJSONArray(
			"pageExperiences"
		).getJSONObject(
			0
		);

		Assert.assertEquals(
			"existing-erc",
			experienceJSONObject.getString("externalReferenceCode"));
	}

	@Test
	public void testEnsurePageExperienceERCsNullArray() {
		JSONObject bodyJSONObject = JSONUtil.put("name", "Page");

		_invokeEnsurePageExperienceERCs(bodyJSONObject, "spec-erc");

		Assert.assertFalse(bodyJSONObject.has("pageExperiences"));
	}

	@Test
	public void testPruneReadOnlyFieldsArrayElements() {
		JSONArray jsonArray = JSONUtil.putAll(
			JSONUtil.put(
				"html", "<p/>"
			).put(
				"name", "First"
			),
			JSONUtil.put(
				"name", "Second"
			).put(
				"uuid", "abc"
			));

		_invokePruneReadOnlyFields(jsonArray);

		JSONObject firstJSONObject = jsonArray.getJSONObject(0);

		Assert.assertFalse(firstJSONObject.has("html"));
		Assert.assertEquals("First", firstJSONObject.getString("name"));

		JSONObject secondJSONObject = jsonArray.getJSONObject(1);

		Assert.assertFalse(secondJSONObject.has("uuid"));
		Assert.assertEquals("Second", secondJSONObject.getString("name"));
	}

	@Test
	public void testPruneReadOnlyFieldsNested() {
		JSONObject bodyJSONObject = JSONUtil.put(
			"name", "Page"
		).put(
			"settings",
			JSONUtil.put(
				"html", "<p/>"
			).put(
				"theme", "default"
			)
		);

		_invokePruneReadOnlyFields(bodyJSONObject);

		JSONObject settingsJSONObject = bodyJSONObject.getJSONObject(
			"settings");

		Assert.assertFalse(settingsJSONObject.has("html"));
		Assert.assertEquals("default", settingsJSONObject.getString("theme"));
	}

	@Test
	public void testPruneReadOnlyFieldsTopLevel() {
		JSONObject bodyJSONObject = JSONUtil.put(
			"configuration", "{}"
		).put(
			"css", ".x{}"
		).put(
			"html", "<p/>"
		).put(
			"name", "Page"
		).put(
			"uuid", "abc"
		);

		_invokePruneReadOnlyFields(bodyJSONObject);

		Assert.assertFalse(bodyJSONObject.has("configuration"));
		Assert.assertFalse(bodyJSONObject.has("css"));
		Assert.assertFalse(bodyJSONObject.has("html"));
		Assert.assertFalse(bodyJSONObject.has("uuid"));
		Assert.assertEquals("Page", bodyJSONObject.getString("name"));
	}

	@Test
	public void testStripMarkdownFencesGenericFence() {
		Assert.assertEquals(
			"{\"key\": \"value\"}",
			_invokeStripMarkdownFences("```\n{\"key\": \"value\"}\n```"));
	}

	@Test
	public void testStripMarkdownFencesJSONFence() {
		Assert.assertEquals(
			"{\"key\": \"value\"}",
			_invokeStripMarkdownFences("```json\n{\"key\": \"value\"}\n```"));
	}

	@Test
	public void testStripMarkdownFencesNull() {
		Assert.assertNull(_invokeStripMarkdownFences(null));
	}

	@Test
	public void testStripMarkdownFencesPlain() {
		Assert.assertEquals(
			"plain body", _invokeStripMarkdownFences("  plain body  "));
	}

	private static void _setUpJSONFactoryUtil() {
		JSONFactoryUtil jsonFactoryUtil = new JSONFactoryUtil();

		jsonFactoryUtil.setJSONFactory(new JSONFactoryImpl());
	}

	private void _invokeAlignExternalReferenceCodes(
		Object node, String oldERC, String newERC) {

		ReflectionTestUtil.invoke(
			_newSitePageTools(), "_alignExternalReferenceCodes",
			new Class<?>[] {Object.class, String.class, String.class}, node,
			oldERC, newERC);
	}

	private void _invokeEnsurePageExperienceERCs(
		JSONObject bodyJSONObject, String specERC) {

		ReflectionTestUtil.invoke(
			_newSitePageTools(), "_ensurePageExperienceERCs",
			new Class<?>[] {JSONObject.class, String.class}, bodyJSONObject,
			specERC);
	}

	private void _invokePruneReadOnlyFields(Object value) {
		ReflectionTestUtil.invoke(
			_newSitePageTools(), "_pruneReadOnlyFields",
			new Class<?>[] {Object.class}, value);
	}

	private String _invokeStripMarkdownFences(String body) {
		return ReflectionTestUtil.invoke(
			_newSitePageTools(), "_stripMarkdownFences",
			new Class<?>[] {String.class}, body);
	}

	private SitePageTools _newSitePageTools() {
		return new SitePageTools(null, 0L, null);
	}

}