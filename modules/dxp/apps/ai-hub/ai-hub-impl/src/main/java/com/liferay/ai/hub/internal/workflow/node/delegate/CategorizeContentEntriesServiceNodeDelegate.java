/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.workflow.node.delegate;

import com.liferay.ai.hub.workflow.node.ServiceNodeDelegate;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.io.Serializable;

import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Alberto Sousa
 */
@Component(service = ServiceNodeDelegate.class)
public class CategorizeContentEntriesServiceNodeDelegate
	implements ServiceNodeDelegate {

	@Override
	public String execute(
			Map<String, String> inputVariables,
			Map<String, Serializable> workflowContext)
		throws Exception {

		String inputContentEntriesPayload = inputVariables.get(
			"contentEntriesPayload");

		JSONArray taxonomyCategoryIdsJSONArray =
			_getTaxonomyCategoryIdsJSONArray(
				inputVariables.get("taxonomyCategoryIds"));

		if (Validator.isNull(inputContentEntriesPayload) ||
			(taxonomyCategoryIdsJSONArray.length() == 0)) {

			workflowContext.put(
				"contentEntriesPayload", inputContentEntriesPayload);

			return inputContentEntriesPayload;
		}

		JSONArray contentEntriesPayloadJSONArray = _jsonFactory.createJSONArray(
			inputContentEntriesPayload);

		for (int i = 0; i < contentEntriesPayloadJSONArray.length(); i++) {
			JSONObject jsonObject =
				contentEntriesPayloadJSONArray.getJSONObject(i);

			jsonObject.put("taxonomyCategoryIds", taxonomyCategoryIdsJSONArray);
		}

		String outputContentEntriesPayload =
			contentEntriesPayloadJSONArray.toString();

		workflowContext.put(
			"contentEntriesPayload", outputContentEntriesPayload);

		return outputContentEntriesPayload;
	}

	@Override
	public String getKey() {
		return "javaDelegate#GenerateContent#categorizeContentEntries";
	}

	private JSONArray _getTaxonomyCategoryIdsJSONArray(
			String taxonomyCategoryIds)
		throws Exception {

		if (Validator.isNull(taxonomyCategoryIds)) {
			return _jsonFactory.createJSONArray();
		}

		String trimmedTaxonomyCategoryIds = taxonomyCategoryIds.trim();

		if (trimmedTaxonomyCategoryIds.startsWith(StringPool.OPEN_BRACKET) &&
			trimmedTaxonomyCategoryIds.endsWith(StringPool.CLOSE_BRACKET)) {

			return _jsonFactory.createJSONArray(trimmedTaxonomyCategoryIds);
		}

		JSONArray jsonArray = _jsonFactory.createJSONArray();

		for (String taxonomyCategoryId :
				StringUtil.split(trimmedTaxonomyCategoryIds)) {

			long taxonomyCategoryIdLong = GetterUtil.getLong(
				taxonomyCategoryId);

			if (taxonomyCategoryIdLong > 0) {
				jsonArray.put(taxonomyCategoryIdLong);
			}
		}

		return jsonArray;
	}

	@Reference
	private JSONFactory _jsonFactory;

}