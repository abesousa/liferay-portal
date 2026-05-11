/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.tools;

import com.liferay.ai.hub.internal.memory.SessionVariablesUtil;
import com.liferay.ai.hub.rest.resource.v1_0.util.SseUtil;
import com.liferay.oauth2.provider.model.OAuth2Application;
import com.liferay.oauth2.provider.model.OAuth2Authorization;
import com.liferay.oauth2.provider.service.OAuth2ApplicationLocalServiceUtil;
import com.liferay.oauth2.provider.service.OAuth2AuthorizationLocalServiceUtil;
import com.liferay.petra.lang.SafeCloseable;
import com.liferay.petra.reflect.ReflectionUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal;
import com.liferay.portal.kernel.servlet.HttpHeaders;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.Http;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.kernel.util.Validator;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.nio.charset.StandardCharsets;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Mahmoud Tayem
 */
public class SiteBuilderTools {

	public SiteBuilderTools(
		String accessToken, long companyId, String sseEventSinkKey,
		String userToken) {

		_accessToken = accessToken;
		_companyId = companyId;
		_sseEventSinkKey = sseEventSinkKey;
		_userToken = userToken;
	}

	@Tool(
		"Cache the enriched site plan with generated fragments in the current session"
	)
	public String cacheFragments(
		@P("Enriched site plan JSON with html/css/js in customFragments") String
			enrichedSitePlan) {

		_emitToolProgress("Designing the fragments...");

		enrichedSitePlan = _stripMarkdownFences(enrichedSitePlan);
		enrichedSitePlan = _repairJSON(enrichedSitePlan);

		try {
			enrichedSitePlan = _fixDuplicateEditableIds(enrichedSitePlan);
		}
		catch (Exception exception) {
			_log.error("Failed to fix duplicate editable IDs", exception);
		}

		SessionVariablesUtil.putVariable(
			_sseEventSinkKey, "enrichedSitePlan", enrichedSitePlan);

		return enrichedSitePlan;
	}

	@Tool("Cache the site plan JSON in the current session")
	public String cacheSitePlan(@P("Site plan JSON") String sitePlan) {
		_emitToolProgress("Drafting the site plan...");

		sitePlan = _stripMarkdownFences(sitePlan);
		sitePlan = _repairJSON(sitePlan);

		SessionVariablesUtil.putVariable(
			_sseEventSinkKey, "sitePlan", sitePlan);

		return sitePlan;
	}

	@Tool(
		"Create the fragment set and all fragments on the site using the cached enriched site plan"
	)
	public String createFragments() {
		_emitToolProgress("Generating the fragments...");

		try (SafeCloseable safeCloseable =
				CompanyThreadLocal.setCompanyIdWithSafeCloseable(_companyId)) {

			return "Fragments will be created via batch files.";
		}
		catch (Exception exception) {
			return ReflectionUtil.throwException(exception);
		}
	}

	@Tool(
		"Create all site pages from the cached enriched site plan, converting each page IR to a Liferay page specification"
	)
	public String createPages(
		@P("Blog entries JSON array") String blogEntries) {

		_emitToolProgress("Building the pages...");

		try (SafeCloseable safeCloseable =
				CompanyThreadLocal.setCompanyIdWithSafeCloseable(_companyId)) {

			return _createPages(blogEntries);
		}
		catch (Exception exception) {
			return ReflectionUtil.throwException(exception);
		}
	}

	@Tool(
		"Create a new Liferay site using the cached site plan. Returns the created site JSON."
	)
	public String createSite() {
		_emitToolProgress("Creating the site...");

		try (SafeCloseable safeCloseable =
				CompanyThreadLocal.setCompanyIdWithSafeCloseable(_companyId)) {

			return _createSite();
		}
		catch (Exception exception) {
			return ReflectionUtil.throwException(exception);
		}
	}

	@Tool("Retrieve the cached site plan JSON from the current session")
	public String getCachedSitePlan() {
		String value = SessionVariablesUtil.getVariable(
			_sseEventSinkKey, "sitePlan");

		if (value == null) {
			return "";
		}

		return value;
	}

	@Tool(
		"Mark the current run as ready. Call this once after every artifact has been posted, signalling the user can press Generate."
	)
	public String markRunReady() {
		try (SafeCloseable safeCloseable =
				CompanyThreadLocal.setCompanyIdWithSafeCloseable(_companyId)) {

			_patchRunStatus("ready");

			SseUtil.send("ready", "Run Updated", null, _sseEventSinkKey);

			return "Run marked ready.";
		}
		catch (Exception exception) {
			return ReflectionUtil.throwException(exception);
		}
	}

	@Tool(
		"Post batch engine artifacts for site, asset library, connected site, fragment set, fragments, and pages to the current run."
	)
	public String writeBatchFiles() {
		try (SafeCloseable safeCloseable =
				CompanyThreadLocal.setCompanyIdWithSafeCloseable(_companyId)) {

			return _writeBatchFiles(null);
		}
		catch (Exception exception) {
			return ReflectionUtil.throwException(exception);
		}
	}

	private void _appendDraftSuffix(JSONArray elementsJSONArray) {
		for (int i = 0; i < elementsJSONArray.length(); i++) {
			JSONObject elementJSONObject = elementsJSONArray.getJSONObject(i);

			String erc = elementJSONObject.getString("externalReferenceCode");

			if (Validator.isNotNull(erc)) {
				elementJSONObject.put("externalReferenceCode", erc + "-draft");
			}

			String parentERC = elementJSONObject.getString(
				"parentExternalReferenceCode");

			if (Validator.isNotNull(parentERC)) {
				elementJSONObject.put(
					"parentExternalReferenceCode", parentERC + "-draft");
			}

			JSONObject definitionJSONObject = elementJSONObject.getJSONObject(
				"pageElementDefinition");

			if (definitionJSONObject != null) {
				JSONObject fragmentInstanceJSONObject =
					definitionJSONObject.getJSONObject("fragmentInstance");

				if (fragmentInstanceJSONObject != null) {
					String instERC = fragmentInstanceJSONObject.getString(
						"fragmentInstanceExternalReferenceCode");

					if (Validator.isNotNull(instERC)) {
						fragmentInstanceJSONObject.put(
							"fragmentInstanceExternalReferenceCode",
							instERC + "-draft");
					}
				}
			}

			JSONArray childrenJSONArray = elementJSONObject.getJSONArray(
				"pageElements");

			if ((childrenJSONArray != null) &&
				(childrenJSONArray.length() > 0)) {

				_appendDraftSuffix(childrenJSONArray);
			}
		}
	}

	private JSONObject _createBatchWrapper(
			String className, boolean includeSiteERC, String siteERC,
			boolean includePrivateLayout)
		throws Exception {

		JSONObject parametersJSONObject = JSONUtil.put(
			"containsHeaders", "true"
		).put(
			"createStrategy", "UPSERT"
		).put(
			"featureFlag", "LPD-39244"
		).put(
			"importStrategy", "ON_ERROR_FAIL"
		);

		if (includeSiteERC) {
			parametersJSONObject.put("siteExternalReferenceCode", siteERC);
		}

		if (includePrivateLayout) {
			parametersJSONObject.put("privateLayout", "false");
		}

		return JSONUtil.put(
			"configuration",
			JSONUtil.put(
				"className", className
			).put(
				"multiCompany", true
			).put(
				"parameters", parametersJSONObject
			).put(
				"taskItemDelegateName", "DEFAULT"
			));
	}

	private JSONObject _createI18nJSON(String locale, String value) {
		return JSONUtil.put(locale, value);
	}

	private String _createPages(String blogEntries) throws Exception {
		return _writeBatchFiles(blogEntries);
	}

	private String _createSite() throws Exception {
		String sitePlan = SessionVariablesUtil.getVariable(
			_sseEventSinkKey, "sitePlan");

		if (Validator.isNull(sitePlan)) {
			return "Error: No cached site plan found in session.";
		}

		sitePlan = _stripMarkdownFences(sitePlan);

		JSONObject sitePlanJSONObject;

		try {
			sitePlanJSONObject = JSONFactoryUtil.createJSONObject(sitePlan);
		}
		catch (Exception exception) {
			_log.error(
				StringBundler.concat(
					"Failed to parse sitePlan JSON: ", exception.getMessage(),
					"\nRaw sitePlan:\n", sitePlan));

			return "Error: Site plan is not valid JSON - " +
				exception.getMessage();
		}

		JSONObject siteJSONObject = sitePlanJSONObject.getJSONObject("site");

		if (siteJSONObject == null) {
			return "Error: Site plan does not contain a 'site' object.";
		}

		JSONObject bodyJSONObject = JSONUtil.put(
			"active", true
		).put(
			"description", siteJSONObject.getString("description")
		).put(
			"externalReferenceCode",
			siteJSONObject.getString("externalReferenceCode")
		).put(
			"membershipType", "open"
		).put(
			"name", siteJSONObject.getString("name")
		);

		String location = _getBaseURL() + "/o/headless-admin-site/v1.0/sites";

		Http.Options options = new Http.Options();

		options.addHeader("Authorization", _accessToken);
		options.addHeader(
			HttpHeaders.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);
		options.addHeader("Liferay-AI-Hub-Cell-On-Behalf-Of", _userToken);
		options.setBody(
			bodyJSONObject.toString(), ContentTypes.APPLICATION_JSON, "UTF-8");
		options.setLocation(location);
		options.setMethod(Http.Method.POST);

		String responseBody = HttpUtil.URLtoString(options);

		int responseCode = options.getResponse(
		).getResponseCode();

		if ((responseCode < 200) || (responseCode >= 300)) {
			_log.error(
				StringBundler.concat(
					"createSite failed with HTTP ", responseCode,
					". Request body: ", bodyJSONObject, ". Response body: ",
					responseBody));

			return StringBundler.concat(
				"Error: HTTP ", responseCode, ". ", responseBody);
		}

		return responseBody;
	}

	private JSONObject _createThemeSettings() throws Exception {
		return JSONUtil.put(
			"colorSchemeName", "01"
		).put(
			"themeName", "classic_WAR_classictheme"
		).put(
			"themeSettings",
			JSONUtil.put(
				"lfr-theme:regular:show-footer", "false"
			).put(
				"lfr-theme:regular:show-header", "false"
			).put(
				"lfr-theme:regular:show-header-search", "false"
			).put(
				"lfr-theme:regular:wrap-widget-page-content", "false"
			)
		);
	}

	private String _detectLanguages(JSONArray itemsJSONArray, String fileName) {
		Set<String> locales = new TreeSet<>();

		if ((itemsJSONArray != null) && (itemsJSONArray.length() > 0)) {
			Matcher i18nMatcher = _i18nBlockPattern.matcher(
				itemsJSONArray.toString());

			while (i18nMatcher.find()) {
				Matcher localeMatcher = _localeKeyPattern.matcher(
					i18nMatcher.group(1));

				while (localeMatcher.find()) {
					locales.add(
						localeMatcher.group(
							1
						).toLowerCase());
				}
			}
		}

		if (locales.isEmpty()) {
			Matcher matcher = _fileNameLanguagePattern.matcher(fileName);

			if (matcher.find()) {
				locales.add(
					matcher.group(
						1
					).toLowerCase());
			}
		}

		return String.join(",", locales);
	}

	private void _emitToolProgress(String label) {
		SseUtil.send(label, "Tool Progress", null, _sseEventSinkKey);
	}

	private void _fixContentReferences(
		JSONArray elementsJSONArray, String fragmentKey,
		Map<String, Integer> totalOccurrences) {

		if (elementsJSONArray == null) {
			return;
		}

		for (int i = 0; i < elementsJSONArray.length(); i++) {
			JSONObject elementJSONObject = elementsJSONArray.getJSONObject(i);

			if (elementJSONObject == null) {
				continue;
			}

			String type = elementJSONObject.getString("type");

			if (type.equals("fragment") &&
				fragmentKey.equals(elementJSONObject.getString("key"))) {

				JSONObject contentJSONObject = elementJSONObject.getJSONObject(
					"content");

				if (contentJSONObject != null) {
					JSONObject fixedContentJSONObject =
						JSONFactoryUtil.createJSONObject();

					for (String editableId : contentJSONObject.keySet()) {
						fixedContentJSONObject.put(
							editableId, contentJSONObject.get(editableId));

						if (totalOccurrences.getOrDefault(editableId, 0) > 1) {
							int total = totalOccurrences.get(editableId);

							for (int d = 2; d <= total; d++) {
								fixedContentJSONObject.put(
									editableId + "-" + d,
									contentJSONObject.get(editableId));
							}
						}
					}

					elementJSONObject.put("content", fixedContentJSONObject);
				}
			}

			// Recurse into children/columns

			JSONArray childrenJSONArray = elementJSONObject.getJSONArray(
				"children");

			if (childrenJSONArray != null) {
				_fixContentReferences(
					childrenJSONArray, fragmentKey, totalOccurrences);
			}

			JSONArray columnsJSONArray = elementJSONObject.getJSONArray(
				"columns");

			if (columnsJSONArray != null) {
				for (int c = 0; c < columnsJSONArray.length(); c++) {
					JSONObject columnJSONObject =
						columnsJSONArray.getJSONObject(c);

					if (columnJSONObject != null) {
						_fixContentReferences(
							columnJSONObject.getJSONArray("children"),
							fragmentKey, totalOccurrences);
					}
				}
			}
		}
	}

	private String _fixDuplicateEditableIds(String enrichedSitePlan)
		throws Exception {

		JSONObject planJSONObject = JSONFactoryUtil.createJSONObject(
			enrichedSitePlan);

		JSONArray customFragmentsJSONArray = planJSONObject.getJSONArray(
			"customFragments");

		if ((customFragmentsJSONArray == null) ||
			(customFragmentsJSONArray.length() == 0)) {

			return enrichedSitePlan;
		}

		JSONArray pagesJSONArray = planJSONObject.getJSONArray("pages");

		for (int i = 0; i < customFragmentsJSONArray.length(); i++) {
			JSONObject fragmentJSONObject =
				customFragmentsJSONArray.getJSONObject(i);

			String html = fragmentJSONObject.getString("html");

			if (Validator.isNull(html)) {
				continue;
			}

			Matcher matcher = _editableIdPattern.matcher(html);

			Map<String, Integer> totalOccurrences = new HashMap<>();

			while (matcher.find()) {
				totalOccurrences.merge(matcher.group(1), 1, Integer::sum);
			}

			boolean hasDuplicates = false;

			for (int count : totalOccurrences.values()) {
				if (count > 1) {
					hasDuplicates = true;

					break;
				}
			}

			if (!hasDuplicates) {
				continue;
			}

			String fragmentKey = fragmentJSONObject.getString("key");

			Map<String, Integer> idCounts = new HashMap<>();

			// Fix HTML — rename duplicate occurrences

			matcher = _editableIdPattern.matcher(html);

			StringBuffer sb = new StringBuffer();

			while (matcher.find()) {
				String id = matcher.group(1);

				if (totalOccurrences.getOrDefault(id, 0) <= 1) {
					matcher.appendReplacement(
						sb, Matcher.quoteReplacement(matcher.group()));

					continue;
				}

				int count = idCounts.getOrDefault(id, 0);

				idCounts.put(id, count + 1);

				if (count > 0) {
					String newId = id + "-" + (count + 1);

					matcher.appendReplacement(
						sb,
						Matcher.quoteReplacement(
							"data-lfr-editable-id=\"" + newId + "\""));
				}
				else {
					matcher.appendReplacement(
						sb, Matcher.quoteReplacement(matcher.group()));
				}
			}

			matcher.appendTail(sb);

			fragmentJSONObject.put("html", sb.toString());

			// Fix editables array — add new IDs

			JSONArray editablesJSONArray = fragmentJSONObject.getJSONArray(
				"editables");

			if (editablesJSONArray != null) {
				JSONArray fixedEditablesJSONArray =
					JSONFactoryUtil.createJSONArray();

				for (int e = 0; e < editablesJSONArray.length(); e++) {
					JSONObject editableJSONObject =
						editablesJSONArray.getJSONObject(e);

					fixedEditablesJSONArray.put(editableJSONObject);

					String editableId = editableJSONObject.getString("id");

					if (totalOccurrences.getOrDefault(editableId, 0) > 1) {
						int total = totalOccurrences.get(editableId);

						for (int d = 2; d <= total; d++) {
							fixedEditablesJSONArray.put(
								JSONUtil.put(
									"id", editableId + "-" + d
								).put(
									"type", editableJSONObject.getString("type")
								));
						}
					}
				}

				fragmentJSONObject.put("editables", fixedEditablesJSONArray);
			}

			// Fix page IR content references

			if (pagesJSONArray != null) {
				for (int p = 0; p < pagesJSONArray.length(); p++) {
					JSONObject pageJSONObject = pagesJSONArray.getJSONObject(p);

					JSONObject irJSONObject = pageJSONObject.getJSONObject(
						"ir");

					if (irJSONObject != null) {
						_fixContentReferences(
							irJSONObject.getJSONArray("elements"), fragmentKey,
							totalOccurrences);
					}
				}
			}
		}

		return planJSONObject.toString();
	}

	private String _fixImageEditables(String html) {
		if (html == null) {
			return html;
		}

		// Find image editable elements that are not <img> tags and
		// don't contain <img> tags — replace them with <img> tags

		Matcher matcher = _imageEditableTagPattern.matcher(html);

		StringBuffer sb = new StringBuffer();

		while (matcher.find()) {
			String editableId = matcher.group(2);

			String afterAttrs = matcher.group(3);

			String replacement = StringBundler.concat(
				"<img data-lfr-editable-id=\"", editableId,
				"\" data-lfr-editable-type=\"image\"", afterAttrs,
				" alt=\"\" src=\"\">");

			matcher.appendReplacement(
				sb, Matcher.quoteReplacement(replacement));
		}

		matcher.appendTail(sb);

		return sb.toString();
	}

	private String _getBaseURL() throws Exception {
		if (Validator.isNull(_accessToken) ||
			!_accessToken.startsWith("Bearer ")) {

			throw new IllegalArgumentException("Invalid access token");
		}

		OAuth2Authorization oAuth2Authorization =
			OAuth2AuthorizationLocalServiceUtil.
				getOAuth2AuthorizationByAccessTokenContent(
					_accessToken.substring(7));

		OAuth2Application oAuth2Application =
			OAuth2ApplicationLocalServiceUtil.getOAuth2Application(
				oAuth2Authorization.getOAuth2ApplicationId());

		return oAuth2Application.getHomePageURL();
	}

	private void _patchRunStatus(String runStatus) throws Exception {
		JSONObject bodyJSONObject = JSONUtil.put("runStatus", runStatus);

		Http.Options options = new Http.Options();

		options.addHeader(
			HttpHeaders.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);
		options.addHeader("Liferay-AI-Hub-Cell-On-Behalf-Of", _userToken);
		options.setBody(
			bodyJSONObject.toString(), ContentTypes.APPLICATION_JSON, "UTF-8");
		options.setLocation(
			StringBundler.concat(
				_getBaseURL(),
				"/o/content-site-generator/runs/by-external-reference-code/",
				_sseEventSinkKey));
		options.setMethod(Http.Method.PATCH);

		String responseBody = HttpUtil.URLtoString(options);

		int responseCode = options.getResponse(
		).getResponseCode();

		if ((responseCode < 200) || (responseCode >= 300)) {
			throw new Exception(
				StringBundler.concat(
					"PATCH run ", _sseEventSinkKey, " runStatus=", runStatus,
					" failed with HTTP ", responseCode, ". Response: ",
					responseBody));
		}
	}

	private String _postArtifact(
			int loadOrder, String fileName, JSONObject envelopeJSONObject)
		throws Exception {

		JSONObject configurationJSONObject = envelopeJSONObject.getJSONObject(
			"configuration");

		JSONArray itemsJSONArray = envelopeJSONObject.getJSONArray("items");

		int itemCount = (itemsJSONArray != null) ? itemsJSONArray.length() : 0;

		String previewItem = "";

		if (itemCount > 0) {
			JSONObject firstItemJSONObject = itemsJSONArray.getJSONObject(0);

			previewItem = _stripMetadata(
				firstItemJSONObject
			).toString();
		}

		String languages = _detectLanguages(itemsJSONArray, fileName);

		String uniqueFileName = StringBundler.concat(
			_sseEventSinkKey, "-", System.currentTimeMillis(), "-", fileName);

		JSONObject artifactBodyJSONObject = JSONUtil.put(
			"className", configurationJSONObject.getString("className")
		).put(
			"delegateName",
			configurationJSONObject.getString("taskItemDelegateName")
		).put(
			"fileName", fileName
		).put(
			"itemCount", itemCount
		).put(
			"json",
			JSONUtil.put(
				"fileBase64",
				Base64.getEncoder(
				).encodeToString(
					envelopeJSONObject.toString(
					).getBytes(
						StandardCharsets.UTF_8
					)
				)
			).put(
				"name", uniqueFileName
			)
		).put(
			"languages", languages
		).put(
			"loadOrder", loadOrder
		).put(
			"previewItem", previewItem
		).put(
			"r_artifacts_l_contentGeneratorRunERC", _sseEventSinkKey
		);

		Http.Options options = new Http.Options();

		options.addHeader(
			HttpHeaders.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);
		options.addHeader("Liferay-AI-Hub-Cell-On-Behalf-Of", _userToken);
		options.setBody(
			artifactBodyJSONObject.toString(), ContentTypes.APPLICATION_JSON,
			"UTF-8");
		options.setLocation(
			_getBaseURL() + "/o/content-site-generator/artifacts/");
		options.setMethod(Http.Method.POST);

		String responseBody = HttpUtil.URLtoString(options);

		int responseCode = options.getResponse(
		).getResponseCode();

		if ((responseCode < 200) || (responseCode >= 300)) {
			throw new Exception(
				StringBundler.concat(
					"POST artifact ", fileName, " failed with HTTP ",
					responseCode, ". Response: ", responseBody));
		}

		JSONObject responseJSONObject = JSONFactoryUtil.createJSONObject(
			responseBody);

		SseUtil.send(fileName, "Artifacts Updated", null, _sseEventSinkKey);

		_emitToolProgress(
			StringBundler.concat(
				"Posted artifact ", loadOrder, ": ", fileName));

		return responseJSONObject.getString("externalReferenceCode");
	}

	private String _repairJSON(String json) {
		try {
			JSONFactoryUtil.createJSONObject(json);

			return json;
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug("Attempting to repair malformed JSON", exception);
			}
		}

		// Step 1: Fix truncated strings (unclosed quotes)

		StringBuilder fixed = new StringBuilder();

		boolean inString = false;
		char prev = 0;

		for (int i = 0; i < json.length(); i++) {
			char c = json.charAt(i);

			if (inString) {
				if ((c == '"') && (prev != '\\')) {
					inString = false;
				}
				else if ((c == '\n') || (c == '\r')) {

					// Newline inside a string means truncated — close it

					fixed.append('"');

					inString = false;
				}
			}
			else if (c == '"') {
				inString = true;
			}

			fixed.append(c);

			prev = c;
		}

		// If still in a string at end, close it

		if (inString) {
			fixed.append('"');
		}

		String repaired = fixed.toString();

		// Step 2: Remove trailing commas before } or ]

		repaired = _trailingCommaPattern.matcher(
			repaired
		).replaceAll(
			"$1"
		);

		// Step 3: Add missing commas between "value""key" patterns

		repaired = _missingCommaPattern.matcher(
			repaired
		).replaceAll(
			"$1,$2"
		);

		// Step 4: Balance unclosed braces and brackets

		int braces = 0;
		int brackets = 0;

		inString = false;
		prev = 0;

		for (int i = 0; i < repaired.length(); i++) {
			char c = repaired.charAt(i);

			if (inString) {
				if ((c == '"') && (prev != '\\')) {
					inString = false;
				}
			}
			else {
				if (c == '"') {
					inString = true;
				}
				else if (c == '{') {
					braces++;
				}
				else if (c == '}') {
					braces--;
				}
				else if (c == '[') {
					brackets++;
				}
				else if (c == ']') {
					brackets--;
				}
			}

			prev = c;
		}

		StringBuilder sb = new StringBuilder(repaired);

		while (brackets > 0) {
			sb.append(']');
			brackets--;
		}

		while (braces > 0) {
			sb.append('}');
			braces--;
		}

		repaired = sb.toString();

		try {
			JSONFactoryUtil.createJSONObject(repaired);

			if (_log.isWarnEnabled()) {
				_log.warn("Repaired malformed JSON");
			}

			return repaired;
		}
		catch (Exception exception) {
			_log.warn("JSON repair failed: " + exception.getMessage());

			return json;
		}
	}

	private void _sanitizeBlogKeywords(JSONArray blogJSONArray) {

		// Asset tag validation rejects ~26 special characters (see
		// AssetTagLocalServiceImpl). LLM-emitted keywords with characters like
		// '&', '/', or "'" would otherwise fail the entire batch under
		// ON_ERROR_FAIL. Strip invalid characters; drop keywords that go blank.

		for (int i = 0; i < blogJSONArray.length(); i++) {
			JSONObject blogJSONObject = blogJSONArray.getJSONObject(i);

			if (blogJSONObject == null) {
				continue;
			}

			JSONArray keywordsJSONArray = blogJSONObject.getJSONArray(
				"keywords");

			if (keywordsJSONArray == null) {
				continue;
			}

			JSONArray sanitizedJSONArray = JSONFactoryUtil.createJSONArray();

			for (int j = 0; j < keywordsJSONArray.length(); j++) {
				String keyword = keywordsJSONArray.getString(j);

				if (Validator.isNull(keyword)) {
					continue;
				}

				StringBuilder sb = new StringBuilder(keyword.length());

				for (int k = 0; k < keyword.length(); k++) {
					char c = keyword.charAt(k);

					if (_INVALID_ASSET_TAG_CHARS.indexOf(c) < 0) {
						sb.append(c);
					}
				}

				String cleaned = sb.toString(
				).trim();

				if (!cleaned.isEmpty()) {
					sanitizedJSONArray.put(cleaned);
				}
			}

			blogJSONObject.put("keywords", sanitizedJSONArray);
		}
	}

	private String _stripMarkdownFences(String text) {
		if (text == null) {
			return text;
		}

		text = text.trim();

		if (text.startsWith("```")) {
			int firstNewline = text.indexOf('\n');

			if (firstNewline != -1) {
				text = text.substring(firstNewline + 1);
			}
		}

		if (text.endsWith("```")) {
			text = text.substring(0, text.length() - 3);
		}

		return text.trim();
	}

	private JSONObject _stripMetadata(JSONObject itemJSONObject) {
		JSONObject strippedJSONObject = JSONFactoryUtil.createJSONObject();

		for (String key : itemJSONObject.keySet()) {
			if (_metadataKeys.contains(key)) {
				continue;
			}

			strippedJSONObject.put(key, itemJSONObject.get(key));
		}

		return strippedJSONObject;
	}

	private String _writeBatchFiles(String blogEntries) throws Exception {
		String enrichedSitePlan = SessionVariablesUtil.getVariable(
			_sseEventSinkKey, "enrichedSitePlan");

		if (Validator.isNull(enrichedSitePlan)) {
			return "Error: No cached enriched site plan found in session.";
		}

		JSONObject planJSONObject = JSONFactoryUtil.createJSONObject(
			enrichedSitePlan);

		JSONObject siteJSONObject = planJSONObject.getJSONObject("site");

		if (siteJSONObject == null) {
			return "Error: Enriched site plan does not contain a 'site' " +
				"object.";
		}

		String siteERC = siteJSONObject.getString("externalReferenceCode");
		String siteTitle = siteJSONObject.getString("name");

		StringBuilder results = new StringBuilder();

		// 01-site

		JSONObject siteBatchJSONObject = _createBatchWrapper(
			"com.liferay.headless.admin.site.dto.v1_0.Site", false, siteERC,
			false);

		siteBatchJSONObject.put(
			"items",
			JSONFactoryUtil.createJSONArray(
			).put(
				JSONUtil.put(
					"active", true
				).put(
					"description", siteJSONObject.getString("description")
				).put(
					"externalReferenceCode", siteERC
				).put(
					"membershipType", "open"
				).put(
					"name", siteTitle
				)
			));

		_postArtifact(1, "01-site.batch-engine-data.json", siteBatchJSONObject);

		results.append("Posted 01-site.batch-engine-data.json\n");

		// 02-asset-library

		String assetLibraryERC = siteERC + "-space";

		JSONObject assetLibraryBatchJSONObject = _createBatchWrapper(
			"com.liferay.headless.asset.library.dto.v1_0.AssetLibrary", false,
			siteERC, false);

		// AssetLibraryResourceImpl._putUnicodeProperties returns null when
		// settings is null; the upsert path then NPEs in
		// UnicodePropertiesBuilder.putAll. Send an empty settings object to
		// take the non-null branch with default values.

		assetLibraryBatchJSONObject.put(
			"items",
			JSONFactoryUtil.createJSONArray(
			).put(
				JSONUtil.put(
					"assetLibraryKey", assetLibraryERC
				).put(
					"externalReferenceCode", assetLibraryERC
				).put(
					"name", siteTitle + " Space"
				).put(
					"name_i18n", _createI18nJSON("en-US", siteTitle + " Space")
				).put(
					"settings", JSONFactoryUtil.createJSONObject()
				).put(
					"type", "Space"
				)
			));

		_postArtifact(
			2, "02-asset-library.batch-engine-data.json",
			assetLibraryBatchJSONObject);

		results.append("Posted 02-asset-library.batch-engine-data.json\n");

		// 03-connected-site

		_postArtifact(
			3, "03-connected-site.batch-engine-data.json",
			JSONUtil.put(
				"configuration",
				JSONUtil.put(
					"className",
					"com.liferay.headless.asset.library.dto.v1_0.ConnectedSite"
				).put(
					"multiCompany", true
				).put(
					"parameters",
					JSONUtil.put(
						"assetLibraryExternalReferenceCode", assetLibraryERC
					).put(
						"containsHeaders", "true"
					).put(
						"createStrategy", "UPSERT"
					).put(
						"importStrategy", "ON_ERROR_FAIL"
					)
				).put(
					"taskItemDelegateName", "DEFAULT"
				)
			).put(
				"items",
				JSONFactoryUtil.createJSONArray(
				).put(
					JSONUtil.put(
						"descriptiveName", siteTitle
					).put(
						"externalReferenceCode", siteERC
					).put(
						"name", siteTitle
					).put(
						"searchable", true
					)
				)
			));

		results.append("Posted 03-connected-site.batch-engine-data.json\n");

		// 04-fragment-set

		String fragmentSetERC = siteERC + "-fragments";

		JSONObject fragmentSetBatchJSONObject = _createBatchWrapper(
			"com.liferay.headless.admin.fragment.dto.v1_0.FragmentSet", true,
			siteERC, false);

		fragmentSetBatchJSONObject.put(
			"items",
			JSONFactoryUtil.createJSONArray(
			).put(
				JSONUtil.put(
					"externalReferenceCode", fragmentSetERC
				).put(
					"key", fragmentSetERC
				).put(
					"name", siteTitle + " Fragments"
				)
			));

		_postArtifact(
			4, "04-fragment-set.batch-engine-data.json",
			fragmentSetBatchJSONObject);

		results.append("Posted 04-fragment-set.batch-engine-data.json\n");

		// 05-fragments

		JSONArray customFragmentsJSONArray = planJSONObject.getJSONArray(
			"customFragments");

		JSONObject fragmentsBatchJSONObject = _createBatchWrapper(
			"com.liferay.headless.admin.fragment.dto.v1_0.Fragment", true,
			siteERC, false);

		JSONArray fragmentItemsJSONArray = JSONFactoryUtil.createJSONArray();

		if ((customFragmentsJSONArray != null) &&
			(customFragmentsJSONArray.length() > 0)) {

			for (int i = 0; i < customFragmentsJSONArray.length(); i++) {
				JSONObject fragmentJSONObject =
					customFragmentsJSONArray.getJSONObject(i);

				String fragmentKey = fragmentJSONObject.getString("key");

				JSONObject fragmentItemJSONObject = JSONUtil.put(
					"externalReferenceCode", fragmentKey
				).put(
					"fragmentSetExternalReferenceCode", fragmentSetERC
				).put(
					"key", fragmentKey
				).put(
					"name", fragmentJSONObject.getString("name")
				).put(
					"type", "Component"
				);

				JSONObject approvedVersionJSONObject = JSONUtil.put(
					"css", fragmentJSONObject.getString("css")
				).put(
					"html",
					_fixImageEditables(fragmentJSONObject.getString("html"))
				).put(
					"js", fragmentJSONObject.getString("js")
				).put(
					"status", "Approved"
				);

				if (fragmentJSONObject.getBoolean("isNavigationMenu")) {
					approvedVersionJSONObject.put(
						"configuration", _NAV_MENU_CONFIGURATION);
				}

				JSONArray fragmentVersionsJSONArray = JSONUtil.put(
					approvedVersionJSONObject);

				fragmentItemJSONObject.put(
					"fragmentVersions", fragmentVersionsJSONArray);

				fragmentItemsJSONArray.put(fragmentItemJSONObject);
			}
		}

		fragmentsBatchJSONObject.put("items", fragmentItemsJSONArray);

		_postArtifact(
			5, "05-fragments.batch-engine-data.json", fragmentsBatchJSONObject);

		results.append("Posted 05-fragments.batch-engine-data.json\n");

		// 06-pages

		JSONArray pagesJSONArray = planJSONObject.getJSONArray("pages");

		JSONObject pagesBatchJSONObject = _createBatchWrapper(
			"com.liferay.headless.admin.site.dto.v1_0.SitePage", true, siteERC,
			true);

		JSONArray pageItemsJSONArray = JSONFactoryUtil.createJSONArray();

		if ((pagesJSONArray != null) && (pagesJSONArray.length() > 0)) {
			String fragmentsCatalog = "[]";

			if ((customFragmentsJSONArray != null) &&
				(customFragmentsJSONArray.length() > 0)) {

				JSONArray catalogJSONArray = JSONFactoryUtil.createJSONArray();

				for (int f = 0; f < customFragmentsJSONArray.length(); f++) {
					JSONObject fragmentJSONObject =
						customFragmentsJSONArray.getJSONObject(f);

					JSONObject catalogEntryJSONObject =
						JSONFactoryUtil.createJSONObject();

					if (fragmentJSONObject.getBoolean("isNavigationMenu")) {
						catalogEntryJSONObject.put(
							"configuration", _NAV_MENU_CONFIGURATION);
					}

					catalogEntryJSONObject.put(
						"css", fragmentJSONObject.getString("css")
					).put(
						"editables",
						fragmentJSONObject.getJSONArray("editables")
					).put(
						"externalReferenceCode",
						fragmentJSONObject.getString("key")
					).put(
						"html", fragmentJSONObject.getString("html")
					).put(
						"js", fragmentJSONObject.getString("js")
					);

					catalogJSONArray.put(catalogEntryJSONObject);
				}

				fragmentsCatalog = catalogJSONArray.toString();
			}

			IRToPageSpecTools irToPageSpecTools = new IRToPageSpecTools(
				fragmentsCatalog);

			for (int i = 0; i < pagesJSONArray.length(); i++) {
				JSONObject pageJSONObject = pagesJSONArray.getJSONObject(i);

				String pageTitle = pageJSONObject.getString("title");
				String pageERC = pageJSONObject.getString(
					"externalReferenceCode");

				JSONObject irJSONObject = pageJSONObject.getJSONObject("ir");

				JSONArray pageElementsJSONArray =
					JSONFactoryUtil.createJSONArray();

				if (irJSONObject != null) {
					String approvedSpecStr =
						irToPageSpecTools.convertToPageSpec(
							irJSONObject.toString(), pageERC,
							pageERC + "-default", "en-US");

					if (!approvedSpecStr.startsWith("Error")) {
						JSONObject specJSONObject =
							JSONFactoryUtil.createJSONObject(approvedSpecStr);

						JSONArray experiencesJSONArray =
							specJSONObject.getJSONArray("pageExperiences");

						if ((experiencesJSONArray != null) &&
							(experiencesJSONArray.length() > 0)) {

							pageElementsJSONArray =
								experiencesJSONArray.getJSONObject(
									0
								).getJSONArray(
									"pageElements"
								);
						}
					}
				}

				// Approved spec

				JSONObject approvedExpJSONObject = JSONUtil.put(
					"externalReferenceCode", pageERC + "-default"
				).put(
					"key", "DEFAULT"
				).put(
					"name_i18n", _createI18nJSON("en-US", "Default")
				).put(
					"pageElements", pageElementsJSONArray
				).put(
					"priority", 0
				);

				JSONObject approvedSpecJSONObject = JSONUtil.put(
					"draftContentPageSpecificationExternalReferenceCode",
					pageERC + "-draft"
				).put(
					"externalReferenceCode", pageERC
				).put(
					"pageExperiences",
					JSONFactoryUtil.createJSONArray(
					).put(
						approvedExpJSONObject
					)
				).put(
					"settings", _createThemeSettings()
				).put(
					"status", "Approved"
				).put(
					"type", "ContentPageSpecification"
				);

				// Draft spec

				JSONArray draftPageElementsJSONArray =
					JSONFactoryUtil.createJSONArray();

				if (pageElementsJSONArray.length() > 0) {
					draftPageElementsJSONArray =
						JSONFactoryUtil.createJSONArray(
							pageElementsJSONArray.toString());

					_appendDraftSuffix(draftPageElementsJSONArray);
				}

				JSONObject draftSpecJSONObject = JSONUtil.put(
					"externalReferenceCode", pageERC + "-draft"
				).put(
					"pageExperiences",
					JSONFactoryUtil.createJSONArray(
					).put(
						JSONUtil.put(
							"externalReferenceCode", pageERC + "-draft-default"
						).put(
							"key", "DEFAULT"
						).put(
							"name_i18n", _createI18nJSON("en-US", "Default")
						).put(
							"pageElements", draftPageElementsJSONArray
						).put(
							"priority", 0
						)
					)
				).put(
					"settings", _createThemeSettings()
				).put(
					"status", "Draft"
				).put(
					"type", "ContentPageSpecification"
				);

				// Page body

				pageItemsJSONArray.put(
					JSONUtil.put(
						"externalReferenceCode", pageERC
					).put(
						"name_i18n", _createI18nJSON("en-US", pageTitle)
					).put(
						"pageSettings",
						JSONUtil.put("type", "ContentPageSettings")
					).put(
						"pageSpecifications",
						JSONFactoryUtil.createJSONArray(
						).put(
							approvedSpecJSONObject
						).put(
							draftSpecJSONObject
						)
					).put(
						"type", "ContentPage"
					));
			}
		}

		pagesBatchJSONObject.put("items", pageItemsJSONArray);

		_postArtifact(
			6, "06-pages.batch-engine-data.json", pagesBatchJSONObject);

		results.append("Posted 06-pages.batch-engine-data.json\n");

		// 07-blogs

		if (Validator.isNotNull(blogEntries)) {
			blogEntries = _stripMarkdownFences(blogEntries);
			blogEntries = _repairJSON(blogEntries);

			String trimmed = blogEntries.trim();

			JSONArray blogJSONArray = null;

			try {
				if (trimmed.startsWith("[")) {
					blogJSONArray = JSONFactoryUtil.createJSONArray(trimmed);
				}
				else if (trimmed.startsWith("{")) {
					JSONObject wrapperJSONObject =
						JSONFactoryUtil.createJSONObject(trimmed);

					for (String key : wrapperJSONObject.keySet()) {
						Object value = wrapperJSONObject.get(key);

						if (value instanceof JSONArray) {
							blogJSONArray = (JSONArray)value;

							break;
						}
					}
				}
			}
			catch (Exception exception) {
				_log.error(
					"Unable to parse blogEntries as JSON; skipping 07-blogs",
					exception);
			}

			if (blogJSONArray != null) {
				_sanitizeBlogKeywords(blogJSONArray);

				_postArtifact(
					7, "07-blogs.batch-engine-data.json",
					JSONUtil.put(
						"configuration",
						JSONUtil.put(
							"className",
							"com.liferay.object.rest.dto.v1_0.ObjectEntry"
						).put(
							"multiCompany", true
						).put(
							"parameters",
							JSONUtil.put(
								"containsHeaders", "true"
							).put(
								"createStrategy", "UPSERT"
							).put(
								"featureFlag", "LPD-17564"
							).put(
								"importStrategy", "ON_ERROR_FAIL"
							).put(
								"scopeKey", siteERC + "-space"
							).put(
								"updateStrategy", "UPDATE"
							)
						).put(
							"taskItemDelegateName", "CMSBlog"
						)
					).put(
						"items", blogJSONArray
					));

				results.append("Posted 07-blogs.batch-engine-data.json\n");
			}
			else {
				_log.error(
					"Failed to parse blogEntries as JSON array. Raw:\n" +
						blogEntries);
			}
		}

		return results.toString();
	}

	private static final String _INVALID_ASSET_TAG_CHARS =
		"&'@\\]}:,=>/<\n[{%|+#`?\"\r;*~";

	private static final String _NAV_MENU_CONFIGURATION =
		"{\"fieldSets\":[{\"fields\":[{\"name\":\"source\"," +
			"\"label\":\"source\",\"type\":\"navigationMenuSelector\"}]}]}";

	private static final Log _log = LogFactoryUtil.getLog(
		SiteBuilderTools.class);

	private static final Pattern _editableIdPattern = Pattern.compile(
		"data-lfr-editable-id=\"([^\"]+)\"");
	private static final Pattern _fileNameLanguagePattern = Pattern.compile(
		"-([a-z]{2})(?:[-_][A-Z]{2})?\\.json$");
	private static final Pattern _i18nBlockPattern = Pattern.compile(
		"\"[a-zA-Z]+_i18n\"\\s*:\\s*\\{([^{}]*)\\}");
	private static final Pattern _imageEditableTagPattern = Pattern.compile(
		"<(?!img)(\\w+)\\s+[^>]*?data-lfr-editable-id=\"([^\"]+)\"\\s+" +
			"data-lfr-editable-type=\"image\"([^>]*?)>");
	private static final Pattern _localeKeyPattern = Pattern.compile(
		"\"([a-z]{2})(?:_[A-Z]{2})?\"\\s*:");
	private static final Set<String> _metadataKeys = Set.of(
		"actions", "classNameId", "classPK", "createDate", "creator",
		"dateCreated", "dateModified", "externalReferenceCode", "groupId", "id",
		"modifiedDate", "parentExternalReferenceCode", "priority", "siteId",
		"sortOrder", "status", "userId");
	private static final Pattern _missingCommaPattern = Pattern.compile(
		"(\"\\s*(?:\"[^\"]*\"|\\d+(?:\\.\\d+)?|true|false|null|\\}|\\]))" +
			"\\s*(\"[^\"]*\"\\s*:)");
	private static final Pattern _trailingCommaPattern = Pattern.compile(
		",\\s*([}\\]])");

	private final String _accessToken;
	private final long _companyId;
	private final String _sseEventSinkKey;
	private final String _userToken;

}