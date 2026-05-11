/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.tools;

import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.workflow.WorkflowNodeManager;
import com.liferay.portal.workflow.kaleo.model.KaleoNode;

import java.io.Serializable;

import java.util.Map;
import java.util.Set;

/**
 * @author Feliphe Marinho
 */
public class ToolsUtil {

	public static Object[] getTools(
		long companyId, KaleoNode currentKaleoNode, JSONFactory jsonFactory,
		Map<String, String> kaleoNodeSettingValues,
		Map<String, Serializable> workflowContext,
		WorkflowNodeManager workflowNodeManager) {

		if (_sitePageToolsNodeNames.contains(currentKaleoNode.getName())) {
			return new Object[] {
				new SitePageTools(
					GetterUtil.getString(workflowContext.get("accessToken")),
					companyId,
					GetterUtil.getString(workflowContext.get("userToken")))
			};
		}

		if (_catalogBuilderNodeNames.contains(currentKaleoNode.getName())) {
			return new Object[] {new CatalogBuilderTools(companyId)};
		}

		if (_fragmentLoaderNodeNames.contains(currentKaleoNode.getName())) {
			return new Object[] {
				new SiteFragmentTools(
					GetterUtil.getString(workflowContext.get("accessToken")),
					companyId,
					GetterUtil.getString(workflowContext.get("userToken")),
					workflowContext)
			};
		}

		if (_siteBuilderNodeNames.contains(currentKaleoNode.getName())) {
			return new Object[] {
				new SiteBuilderTools(
					GetterUtil.getString(workflowContext.get("accessToken")),
					companyId,
					GetterUtil.getString(
						workflowContext.get("sseEventSinkKey")),
					GetterUtil.getString(workflowContext.get("userToken")))
			};
		}

		return new Object[0];
	}

	private static final Set<String> _catalogBuilderNodeNames = Set.of(
		"catalogBuilder");
	private static final Set<String> _fragmentLoaderNodeNames = Set.of(
		"fragmentLoader");
	private static final Set<String> _siteBuilderNodeNames = Set.of(
		"cacheFragments", "cacheSitePlan", "createFragments", "createPages",
		"createSite", "markRunReady");
	private static final Set<String> _sitePageToolsNodeNames = Set.of(
		"pageFetcher", "pageUpdater");

}