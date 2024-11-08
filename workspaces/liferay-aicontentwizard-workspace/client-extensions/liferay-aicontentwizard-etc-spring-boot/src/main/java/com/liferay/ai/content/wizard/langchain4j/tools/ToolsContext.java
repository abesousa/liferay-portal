/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.content.wizard.langchain4j.tools;

import java.net.URL;

/**
 * @author Keven Leone
 * @author Brian Wing Shun Chan
 */
public class ToolsContext {

	public ToolsContext(String authorization, URL liferayDXPURL, long siteId) {
		this.authorization = authorization;
		this.liferayDXPURL = liferayDXPURL;
		this.siteId = siteId;
	}

	protected String authorization;
	protected URL liferayDXPURL;
	protected long siteId;

}