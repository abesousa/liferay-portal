/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.tools;

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
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal;
import com.liferay.portal.kernel.servlet.HttpHeaders;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.Http;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.kernel.util.URLCodec;
import com.liferay.portal.kernel.util.Validator;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.Serializable;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Mahmoud Tayem
 */
public class SiteFragmentTools {

	public SiteFragmentTools(
		String accessToken, long companyId, String userToken,
		Map<String, Serializable> workflowContext) {

		_accessToken = accessToken;
		_companyId = companyId;
		_userToken = userToken;
		_workflowContext = workflowContext;
	}

	@Tool(
		"List all custom fragments available on a site with their editable fields"
	)
	public String getFragments(
		@P("Site external reference code") String siteExternalReferenceCode) {

		try (SafeCloseable safeCloseable =
				CompanyThreadLocal.setCompanyIdWithSafeCloseable(_companyId)) {

			return _getFragments(siteExternalReferenceCode);
		}
		catch (Exception exception) {
			return ReflectionUtil.throwException(exception);
		}
	}

	private JSONArray _buildMinifiedCatalogJSONArray(
			JSONArray fullCatalogJSONArray)
		throws Exception {

		JSONArray minifiedCatalogJSONArray = JSONFactoryUtil.createJSONArray();

		for (int i = 0; i < fullCatalogJSONArray.length(); i++) {
			JSONObject fullJSONObject = fullCatalogJSONArray.getJSONObject(i);

			minifiedCatalogJSONArray.put(
				JSONUtil.put(
					"editables", fullJSONObject.getJSONArray("editables")
				).put(
					"externalReferenceCode",
					fullJSONObject.getString("externalReferenceCode")
				).put(
					"html", _minifyHtml(fullJSONObject.getString("html"))
				).put(
					"key", fullJSONObject.getString("key")
				).put(
					"name", fullJSONObject.getString("name")
				));
		}

		return minifiedCatalogJSONArray;
	}

	private JSONArray _extractEditablesJSONArray(String html) throws Exception {
		JSONArray editablesJSONArray = JSONFactoryUtil.createJSONArray();

		if (Validator.isNull(html)) {
			return editablesJSONArray;
		}

		Matcher matcher = _editablePattern.matcher(html);

		while (matcher.find()) {
			editablesJSONArray.put(
				JSONUtil.put(
					"id", matcher.group(1)
				).put(
					"type", matcher.group(2)
				));
		}

		return editablesJSONArray;
	}

	private String _get(String location) throws Exception {
		Http.Options options = new Http.Options();

		options.addHeader(
			HttpHeaders.CONTENT_TYPE, ContentTypes.APPLICATION_JSON);
		options.addHeader("Liferay-AI-Hub-Cell-On-Behalf-Of", _userToken);
		options.setLocation(location);
		options.setMethod(Http.Method.GET);

		String responseBody = HttpUtil.URLtoString(options);

		int responseCode = options.getResponse(
		).getResponseCode();

		if ((responseCode < 200) || (responseCode >= 300)) {
			throw new Exception(
				StringBundler.concat(
					"HTTP ", responseCode, " from ", location, ": ",
					responseBody));
		}

		return responseBody;
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

	private String _getFragments(String siteExternalReferenceCode)
		throws Exception {

		String cachedFull = (String)_workflowContext.get(
			"fullFragmentsCatalog");

		if (Validator.isNotNull(cachedFull)) {
			return _buildMinifiedCatalogJSONArray(
				JSONFactoryUtil.createJSONArray(cachedFull)
			).toString();
		}

		String baseURL = _getBaseURL();

		String encodedSiteERC = URLCodec.encodeURL(siteExternalReferenceCode);

		JSONArray fragmentSetsJSONArray = _getPagedItemsJSONArray(
			StringBundler.concat(
				baseURL, "/o/headless-admin-fragment/v1.0/sites/",
				encodedSiteERC, "/fragment-sets"));

		JSONArray fullCatalogJSONArray = JSONFactoryUtil.createJSONArray();
		JSONArray minifiedCatalogJSONArray = JSONFactoryUtil.createJSONArray();

		for (int i = 0; i < fragmentSetsJSONArray.length(); i++) {
			JSONObject fragmentSetJSONObject =
				fragmentSetsJSONArray.getJSONObject(i);

			String setERC = fragmentSetJSONObject.getString(
				"externalReferenceCode");

			JSONArray fragmentsJSONArray = _getPagedItemsJSONArray(
				StringBundler.concat(
					baseURL, "/o/headless-admin-fragment/v1.0/sites/",
					encodedSiteERC, "/fragment-sets/",
					URLCodec.encodeURL(setERC), "/fragments?type=Component"));

			for (int j = 0; j < fragmentsJSONArray.length(); j++) {
				JSONObject fragmentJSONObject =
					fragmentsJSONArray.getJSONObject(j);

				String erc = fragmentJSONObject.getString(
					"externalReferenceCode");
				String key = fragmentJSONObject.getString("key");
				String name = fragmentJSONObject.getString("name");

				String fragmentSetName = fragmentSetJSONObject.getString(
					"name");

				String html = null;
				String css = null;
				String js = null;
				String configuration = null;

				JSONArray versionsJSONArray = fragmentJSONObject.getJSONArray(
					"fragmentVersions");

				JSONObject selectedVersionJSONObject = _selectVersion(
					versionsJSONArray);

				if (selectedVersionJSONObject != null) {
					html = selectedVersionJSONObject.getString("html");
					css = selectedVersionJSONObject.getString("css");
					js = selectedVersionJSONObject.getString("js");
					configuration = selectedVersionJSONObject.getString(
						"configuration");
				}

				JSONArray editablesJSONArray = _extractEditablesJSONArray(html);

				fullCatalogJSONArray.put(
					JSONUtil.put(
						"configuration", configuration
					).put(
						"css", css
					).put(
						"editables", editablesJSONArray
					).put(
						"externalReferenceCode", erc
					).put(
						"fragmentSetName", fragmentSetName
					).put(
						"html", html
					).put(
						"js", js
					).put(
						"key", key
					).put(
						"name", name
					));

				minifiedCatalogJSONArray.put(
					JSONUtil.put(
						"editables", editablesJSONArray
					).put(
						"externalReferenceCode", erc
					).put(
						"html", _minifyHtml(html)
					).put(
						"key", key
					).put(
						"name", name
					));
			}
		}

		_workflowContext.put(
			"fullFragmentsCatalog", fullCatalogJSONArray.toString());

		return minifiedCatalogJSONArray.toString();
	}

	private JSONArray _getPagedItemsJSONArray(String baseLocation)
		throws Exception {

		JSONArray allItemsJSONArray = JSONFactoryUtil.createJSONArray();

		int page = 1;

		while (true) {
			String location = HttpComponentsUtil.addParameter(
				baseLocation, "page", String.valueOf(page));

			location = HttpComponentsUtil.addParameter(
				location, "pageSize", "100");

			String responseBody = _get(location);

			JSONObject responseJSONObject = JSONFactoryUtil.createJSONObject(
				responseBody);

			JSONArray itemsJSONArray = responseJSONObject.getJSONArray("items");

			if ((itemsJSONArray == null) || (itemsJSONArray.length() == 0)) {
				break;
			}

			for (int i = 0; i < itemsJSONArray.length(); i++) {
				allItemsJSONArray.put(itemsJSONArray.getJSONObject(i));
			}

			int lastPage = responseJSONObject.getInt("lastPage", page);

			if (page >= lastPage) {
				break;
			}

			page++;
		}

		return allItemsJSONArray;
	}

	private String _minifyHtml(String html) {
		if (Validator.isNull(html)) {
			return "";
		}

		return html.replaceAll(
			"\\s+", " "
		).trim();
	}

	private JSONObject _selectVersion(JSONArray versionsJSONArray) {
		if ((versionsJSONArray == null) || (versionsJSONArray.length() == 0)) {
			return null;
		}

		for (int i = 0; i < versionsJSONArray.length(); i++) {
			JSONObject versionJSONObject = versionsJSONArray.getJSONObject(i);

			if (Objects.equals(
					versionJSONObject.getString("status"), "Approved")) {

				return versionJSONObject;
			}
		}

		return versionsJSONArray.getJSONObject(0);
	}

	private static final Pattern _editablePattern = Pattern.compile(
		"data-lfr-editable-id=\"([^\"]+)\"\\s+" +
			"data-lfr-editable-type=\"([^\"]+)\"");

	private final String _accessToken;
	private final long _companyId;
	private final String _userToken;
	private final Map<String, Serializable> _workflowContext;

}