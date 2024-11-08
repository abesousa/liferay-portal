/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.content.wizard.langchain4j.tools;

import com.liferay.ai.content.wizard.langchain4j.descriptions.SiteDescriptions;
import com.liferay.headless.site.client.dto.v1_0.Site;
import com.liferay.headless.site.client.resource.v1_0.SiteResource;

import dev.langchain4j.agent.tool.Tool;

import org.json.JSONObject;

import org.springframework.http.HttpHeaders;

/**
 * @author Keven Leone
 * @author Brian Wing Shun Chan
 */
public class SiteTools extends BaseTools {

	public SiteTools(ToolsContext toolsContext) {
		super(toolsContext);

		_siteResource = SiteResource.builder(
		).endpoint(
			toolsContext.liferayDXPURL
		).header(
			HttpHeaders.AUTHORIZATION, toolsContext.authorization
		).build();
	}

	@Tool("Create Site")
	public Site postSite(SiteDescriptions siteDescriptions) throws Exception {
		String templateType = "site-template";

		String templateKeyString = siteDescriptions.templateKey.toString();

		if (templateKeyString.contains("site.initializer") ||
			templateKeyString.contains("site-initializer")) {

			templateType = "site-initializer";
		}

		return _siteResource.postSite(
			Site.toDTO(
				new JSONObject(
				).put(
					"externalReferenceCode",
					siteDescriptions.externalReferenceCode
				).put(
					"membershipType", siteDescriptions.membershipType
				).put(
					"name", siteDescriptions.name
				).put(
					"templateKey", templateKeyString
				).put(
					"templateType", templateType
				).toString()));
	}

	private final SiteResource _siteResource;

}