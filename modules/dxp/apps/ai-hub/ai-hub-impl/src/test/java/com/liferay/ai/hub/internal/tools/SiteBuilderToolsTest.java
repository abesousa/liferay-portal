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
public class SiteBuilderToolsTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@BeforeClass
	public static void setUpClass() {
		_setUpJSONFactoryUtil();
	}

	@Test
	public void testAppendDraftSuffixRecursesIntoChildren() {
		JSONArray elementsJSONArray = JSONUtil.putAll(
			JSONUtil.put(
				"externalReferenceCode", "parent"
			).put(
				"pageElements",
				JSONUtil.putAll(JSONUtil.put("externalReferenceCode", "child"))
			));

		_invokeAppendDraftSuffix(elementsJSONArray);

		JSONObject parentJSONObject = elementsJSONArray.getJSONObject(0);

		Assert.assertEquals(
			"parent-draft",
			parentJSONObject.getString("externalReferenceCode"));

		JSONObject childJSONObject = parentJSONObject.getJSONArray(
			"pageElements"
		).getJSONObject(
			0
		);

		Assert.assertEquals(
			"child-draft", childJSONObject.getString("externalReferenceCode"));
	}

	@Test
	public void testAppendDraftSuffixRewritesFragmentInstanceERC() {
		JSONArray elementsJSONArray = JSONUtil.putAll(
			JSONUtil.put(
				"externalReferenceCode", "page-1"
			).put(
				"pageElementDefinition",
				JSONUtil.put(
					"fragmentInstance",
					JSONUtil.put(
						"fragmentInstanceExternalReferenceCode", "inst-1"))
			));

		_invokeAppendDraftSuffix(elementsJSONArray);

		JSONObject fragmentInstanceJSONObject = elementsJSONArray.getJSONObject(
			0
		).getJSONObject(
			"pageElementDefinition"
		).getJSONObject(
			"fragmentInstance"
		);

		Assert.assertEquals(
			"inst-1-draft",
			fragmentInstanceJSONObject.getString(
				"fragmentInstanceExternalReferenceCode"));
	}

	@Test
	public void testAppendDraftSuffixRewritesParentERC() {
		JSONArray elementsJSONArray = JSONUtil.putAll(
			JSONUtil.put(
				"externalReferenceCode", "child"
			).put(
				"parentExternalReferenceCode", "parent"
			));

		_invokeAppendDraftSuffix(elementsJSONArray);

		JSONObject elementJSONObject = elementsJSONArray.getJSONObject(0);

		Assert.assertEquals(
			"child-draft",
			elementJSONObject.getString("externalReferenceCode"));
		Assert.assertEquals(
			"parent-draft",
			elementJSONObject.getString("parentExternalReferenceCode"));
	}

	@Test
	public void testDetectLanguagesFallsBackToFileName() {
		String result = _invokeDetectLanguages(
			JSONFactoryUtil.createJSONArray(), "blog-posts-pt-BR.json");

		Assert.assertEquals("pt", result);
	}

	@Test
	public void testDetectLanguagesReadsI18nKeys() {
		JSONArray itemsJSONArray = JSONUtil.putAll(
			JSONUtil.put(
				"title_i18n",
				JSONUtil.put(
					"en_US", "Hello"
				).put(
					"pt_BR", "Olá"
				)));

		String result = _invokeDetectLanguages(itemsJSONArray, "items.json");

		Assert.assertEquals("en,pt", result);
	}

	@Test
	public void testDetectLanguagesReturnsEmptyWhenNoLocaleFound() {
		String result = _invokeDetectLanguages(
			JSONFactoryUtil.createJSONArray(), "items.json");

		Assert.assertEquals("", result);
	}

	@Test
	public void testFixDuplicateEditableIdsRenamesDuplicates()
		throws Exception {

		JSONObject resultJSONObject = JSONFactoryUtil.createJSONObject(
			_invokeFixDuplicateEditableIds(
				JSONUtil.put(
					"customFragments",
					JSONUtil.putAll(
						JSONUtil.put(
							"html",
							"<p data-lfr-editable-id=\"title\">A</p>" +
								"<p data-lfr-editable-id=\"title\">B</p>"
						).put(
							"key", "f1"
						))
				).toString()));

		String html = resultJSONObject.getJSONArray(
			"customFragments"
		).getJSONObject(
			0
		).getString(
			"html"
		);

		Assert.assertTrue(html.contains("data-lfr-editable-id=\"title\""));
		Assert.assertTrue(html.contains("data-lfr-editable-id=\"title-2\""));
	}

	@Test
	public void testFixDuplicateEditableIdsWhenCustomFragmentsAreEmpty()
		throws Exception {

		String enrichedSitePlan = JSONUtil.put(
			"customFragments", JSONFactoryUtil.createJSONArray()
		).toString();

		Assert.assertEquals(
			enrichedSitePlan, _invokeFixDuplicateEditableIds(enrichedSitePlan));
	}

	@Test
	public void testFixDuplicateEditableIdsWhenNoDuplicatesArePresent()
		throws Exception {

		JSONObject resultJSONObject = JSONFactoryUtil.createJSONObject(
			_invokeFixDuplicateEditableIds(
				JSONUtil.put(
					"customFragments",
					JSONUtil.putAll(
						JSONUtil.put(
							"html",
							"<p data-lfr-editable-id=\"title\">A</p>" +
								"<p data-lfr-editable-id=\"subtitle\">B</p>"
						).put(
							"key", "f1"
						))
				).toString()));

		String html = resultJSONObject.getJSONArray(
			"customFragments"
		).getJSONObject(
			0
		).getString(
			"html"
		);

		Assert.assertTrue(html.contains("data-lfr-editable-id=\"title\""));
		Assert.assertTrue(html.contains("data-lfr-editable-id=\"subtitle\""));
		Assert.assertFalse(html.contains("title-2"));
	}

	@Test
	public void testFixImageEditablesWhenHTMLContainsDivEditable() {
		String result = _invokeFixImageEditables(
			"<div data-lfr-editable-id=\"hero\" " +
				"data-lfr-editable-type=\"image\"></div>");

		Assert.assertEquals(
			"<img data-lfr-editable-id=\"hero\" " +
				"data-lfr-editable-type=\"image\" alt=\"\" src=\"\"></div>",
			result);
	}

	@Test
	public void testFixImageEditablesWhenHTMLIsNull() {
		Assert.assertNull(_invokeFixImageEditables(null));
	}

	@Test
	public void testRepairJSONWhenJSONBracesAreUnclosed() throws Exception {
		String repaired = _invokeRepairJSON("{\"a\": {\"b\": 1");

		JSONObject jsonObject = JSONFactoryUtil.createJSONObject(repaired);

		Assert.assertEquals(
			1,
			jsonObject.getJSONObject(
				"a"
			).getInt(
				"b"
			));
	}

	@Test
	public void testRepairJSONWhenJSONIsValid() {
		String json = "{\"a\": 1, \"b\": 2}";

		Assert.assertEquals(json, _invokeRepairJSON(json));
	}

	@Test
	public void testRepairJSONWhenJSONStringIsTruncatedByNewline()
		throws Exception {

		String repaired = _invokeRepairJSON("{\"a\": \"hello\n}");

		JSONObject jsonObject = JSONFactoryUtil.createJSONObject(repaired);

		Assert.assertEquals("hello", jsonObject.getString("a"));
	}

	@Test
	public void testRepairJSONWhenJSONTrailingCommaIsPresent()
		throws Exception {

		String repaired = _invokeRepairJSON("{\"a\": 1, \"b\": 2,}");

		JSONObject jsonObject = JSONFactoryUtil.createJSONObject(repaired);

		Assert.assertEquals(1, jsonObject.getInt("a"));
		Assert.assertEquals(2, jsonObject.getInt("b"));
	}

	@Test
	public void testSanitizeBlogKeywordsWhenKeywordsContainInvalidChars() {
		JSONArray blogsJSONArray = JSONUtil.putAll(
			JSONUtil.put(
				"keywords",
				JSONUtil.putAll("tech & innovation", "AI/ML", "javascript")));

		_invokeSanitizeBlogKeywords(blogsJSONArray);

		JSONArray keywordsJSONArray = blogsJSONArray.getJSONObject(
			0
		).getJSONArray(
			"keywords"
		);

		Assert.assertEquals(3, keywordsJSONArray.length());
		Assert.assertEquals("tech  innovation", keywordsJSONArray.getString(0));
		Assert.assertEquals("AIML", keywordsJSONArray.getString(1));
		Assert.assertEquals("javascript", keywordsJSONArray.getString(2));
	}

	@Test
	public void testSanitizeBlogKeywordsWhenKeywordsResultInBlank() {
		JSONArray blogsJSONArray = JSONUtil.putAll(
			JSONUtil.put("keywords", JSONUtil.putAll("&&&", "valid", "///")));

		_invokeSanitizeBlogKeywords(blogsJSONArray);

		JSONArray keywordsJSONArray = blogsJSONArray.getJSONObject(
			0
		).getJSONArray(
			"keywords"
		);

		Assert.assertEquals(1, keywordsJSONArray.length());
		Assert.assertEquals("valid", keywordsJSONArray.getString(0));
	}

	@Test
	public void testStripMarkdownFencesWhenTextFencesArePresent() {
		Assert.assertEquals(
			"{\"a\": 1}",
			_invokeStripMarkdownFences("```json\n{\"a\": 1}\n```"));
	}

	@Test
	public void testStripMarkdownFencesWhenTextHasNoFences() {
		String text = "{\"a\": 1}";

		Assert.assertEquals(text, _invokeStripMarkdownFences(text));
	}

	@Test
	public void testStripMarkdownFencesWhenTextIsNull() {
		Assert.assertNull(_invokeStripMarkdownFences(null));
	}

	@Test
	public void testStripMetadataRemovesGeneratedFields() {
		JSONObject strippedJSONObject = _invokeStripMetadata(
			JSONUtil.put(
				"actions", "x"
			).put(
				"createDate", "2026-01-01"
			).put(
				"externalReferenceCode", "L_X"
			).put(
				"id", 1
			).put(
				"name", "Item"
			).put(
				"status", "approved"
			));

		Assert.assertEquals("Item", strippedJSONObject.getString("name"));
		Assert.assertFalse(strippedJSONObject.has("actions"));
		Assert.assertFalse(strippedJSONObject.has("createDate"));
		Assert.assertFalse(strippedJSONObject.has("externalReferenceCode"));
		Assert.assertFalse(strippedJSONObject.has("id"));
		Assert.assertFalse(strippedJSONObject.has("status"));
	}

	private static void _setUpJSONFactoryUtil() {
		JSONFactoryUtil jsonFactoryUtil = new JSONFactoryUtil();

		jsonFactoryUtil.setJSONFactory(new JSONFactoryImpl());
	}

	private void _invokeAppendDraftSuffix(JSONArray elementsJSONArray) {
		ReflectionTestUtil.invoke(
			_newSiteBuilderTools(), "_appendDraftSuffix",
			new Class<?>[] {JSONArray.class}, new Object[] {elementsJSONArray});
	}

	private String _invokeDetectLanguages(
		JSONArray itemsJSONArray, String fileName) {

		return ReflectionTestUtil.invoke(
			_newSiteBuilderTools(), "_detectLanguages",
			new Class<?>[] {JSONArray.class, String.class},
			new Object[] {itemsJSONArray, fileName});
	}

	private String _invokeFixDuplicateEditableIds(String enrichedSitePlan) {
		return ReflectionTestUtil.invoke(
			_newSiteBuilderTools(), "_fixDuplicateEditableIds",
			new Class<?>[] {String.class}, new Object[] {enrichedSitePlan});
	}

	private String _invokeFixImageEditables(String html) {
		return ReflectionTestUtil.invoke(
			_newSiteBuilderTools(), "_fixImageEditables",
			new Class<?>[] {String.class}, new Object[] {html});
	}

	private String _invokeRepairJSON(String json) {
		return ReflectionTestUtil.invoke(
			_newSiteBuilderTools(), "_repairJSON",
			new Class<?>[] {String.class}, new Object[] {json});
	}

	private void _invokeSanitizeBlogKeywords(JSONArray blogJSONArray) {
		ReflectionTestUtil.invoke(
			_newSiteBuilderTools(), "_sanitizeBlogKeywords",
			new Class<?>[] {JSONArray.class}, new Object[] {blogJSONArray});
	}

	private String _invokeStripMarkdownFences(String text) {
		return ReflectionTestUtil.invoke(
			_newSiteBuilderTools(), "_stripMarkdownFences",
			new Class<?>[] {String.class}, new Object[] {text});
	}

	private JSONObject _invokeStripMetadata(JSONObject itemJSONObject) {
		return ReflectionTestUtil.invoke(
			_newSiteBuilderTools(), "_stripMetadata",
			new Class<?>[] {JSONObject.class}, new Object[] {itemJSONObject});
	}

	private SiteBuilderTools _newSiteBuilderTools() {
		return new SiteBuilderTools(null, 0L, null, null);
	}

}