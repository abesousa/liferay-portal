/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.tools;

import com.liferay.petra.string.StringBundler;
import com.liferay.portal.json.JSONFactoryImpl;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.io.Serializable;

import java.util.HashMap;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Mario Gomes
 */
public class SiteFragmentToolsTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@BeforeClass
	public static void setUpClass() {
		_setUpJSONFactoryUtil();
	}

	@Test
	public void testExtractEditablesJSONArrayMultiple() {
		JSONArray editablesJSONArray = _invokeExtractEditablesJSONArray(
			StringBundler.concat(
				"<p data-lfr-editable-id=\"title\" ",
				"data-lfr-editable-type=\"text\">x</p>",
				"<img data-lfr-editable-id=\"logo\" ",
				"data-lfr-editable-type=\"image\" src=\"\" />"));

		Assert.assertEquals(2, editablesJSONArray.length());

		JSONObject firstJSONObject = editablesJSONArray.getJSONObject(0);

		Assert.assertEquals("title", firstJSONObject.getString("id"));
		Assert.assertEquals("text", firstJSONObject.getString("type"));

		JSONObject secondJSONObject = editablesJSONArray.getJSONObject(1);

		Assert.assertEquals("logo", secondJSONObject.getString("id"));
		Assert.assertEquals("image", secondJSONObject.getString("type"));
	}

	@Test
	public void testExtractEditablesJSONArrayNoMatch() {
		JSONArray editablesJSONArray = _invokeExtractEditablesJSONArray(
			"<div>nothing here</div>");

		Assert.assertEquals(0, editablesJSONArray.length());
	}

	@Test
	public void testExtractEditablesJSONArrayNullHtml() {
		JSONArray editablesJSONArray = _invokeExtractEditablesJSONArray(null);

		Assert.assertEquals(0, editablesJSONArray.length());
	}

	@Test
	public void testExtractEditablesJSONArraySingle() {
		JSONArray editablesJSONArray = _invokeExtractEditablesJSONArray(
			"<p data-lfr-editable-id=\"title\" " +
				"data-lfr-editable-type=\"text\">x</p>");

		Assert.assertEquals(1, editablesJSONArray.length());

		JSONObject editableJSONObject = editablesJSONArray.getJSONObject(0);

		Assert.assertEquals("title", editableJSONObject.getString("id"));
		Assert.assertEquals("text", editableJSONObject.getString("type"));
	}

	@Test
	public void testMinifyHtmlCollapsesWhitespace() {
		Assert.assertEquals(
			"<div> hello world </div>",
			_invokeMinifyHtml("<div>   hello\n\nworld   </div>"));
	}

	@Test
	public void testMinifyHtmlEmpty() {
		Assert.assertEquals("", _invokeMinifyHtml(""));
	}

	@Test
	public void testMinifyHtmlNull() {
		Assert.assertEquals("", _invokeMinifyHtml(null));
	}

	@Test
	public void testMinifyHtmlTrims() {
		Assert.assertEquals("hello", _invokeMinifyHtml("   hello   "));
	}

	@Test
	public void testSelectVersionEmpty() {
		Assert.assertNull(
			_invokeSelectVersion(JSONFactoryUtil.createJSONArray()));
	}

	@Test
	public void testSelectVersionFallsBackToFirst() {
		JSONArray versionsJSONArray = JSONUtil.putAll(
			JSONUtil.put(
				"html", "draft"
			).put(
				"status", "Draft"
			),
			JSONUtil.put(
				"html", "pending"
			).put(
				"status", "Pending"
			));

		JSONObject selectedJSONObject = _invokeSelectVersion(versionsJSONArray);

		Assert.assertEquals("draft", selectedJSONObject.getString("html"));
	}

	@Test
	public void testSelectVersionNull() {
		Assert.assertNull(_invokeSelectVersion(null));
	}

	@Test
	public void testSelectVersionPicksApproved() {
		JSONArray versionsJSONArray = JSONUtil.putAll(
			JSONUtil.put(
				"html", "draft"
			).put(
				"status", "Draft"
			),
			JSONUtil.put(
				"html", "approved"
			).put(
				"status", "Approved"
			));

		JSONObject selectedJSONObject = _invokeSelectVersion(versionsJSONArray);

		Assert.assertEquals("approved", selectedJSONObject.getString("html"));
	}

	private static void _setUpJSONFactoryUtil() {
		JSONFactoryUtil jsonFactoryUtil = new JSONFactoryUtil();

		jsonFactoryUtil.setJSONFactory(new JSONFactoryImpl());
	}

	private JSONArray _invokeExtractEditablesJSONArray(String html) {
		return ReflectionTestUtil.invoke(
			_newSiteFragmentTools(), "_extractEditablesJSONArray",
			new Class<?>[] {String.class}, html);
	}

	private String _invokeMinifyHtml(String html) {
		return ReflectionTestUtil.invoke(
			_newSiteFragmentTools(), "_minifyHtml",
			new Class<?>[] {String.class}, html);
	}

	private JSONObject _invokeSelectVersion(JSONArray versionsJSONArray) {
		return ReflectionTestUtil.invoke(
			_newSiteFragmentTools(), "_selectVersion",
			new Class<?>[] {JSONArray.class}, versionsJSONArray);
	}

	private SiteFragmentTools _newSiteFragmentTools() {
		return new SiteFragmentTools(
			null, 0L, null, new HashMap<String, Serializable>());
	}

}