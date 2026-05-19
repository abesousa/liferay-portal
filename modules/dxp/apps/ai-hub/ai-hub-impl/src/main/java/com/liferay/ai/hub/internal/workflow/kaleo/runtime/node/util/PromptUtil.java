/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.workflow.kaleo.runtime.node.util;

import com.liferay.object.rest.dto.v1_0.ObjectEntry;
import com.liferay.object.rest.manager.v1_0.ObjectEntryManager;
import com.liferay.object.service.ObjectDefinitionLocalServiceUtil;
import com.liferay.petra.function.transform.TransformUtil;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserServiceUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.vulcan.dto.converter.DTOConverterRegistry;
import com.liferay.portal.vulcan.dto.converter.DefaultDTOConverterContext;
import com.liferay.portal.vulcan.pagination.Page;
import com.liferay.portal.workflow.kaleo.runtime.ExecutionContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Carolina Barbosa
 */
public class PromptUtil {

	public static String composePrompt(
		long companyId, DTOConverterRegistry dtoConverterRegistry,
		ExecutionContext executionContext,
		Map<String, String> kaleoNodeSettingValues,
		ObjectEntryManager objectEntryManager) {

		String systemInstructions = _getInstructions(
			companyId, dtoConverterRegistry, _SYSTEM_INSTRUCTIONS_FILTER_STRING,
			objectEntryManager, executionContext.getServiceContext());

		if (Validator.isNull(systemInstructions)) {
			_log.error(
				"Unable to compose prompt because no AI Hub system " +
					"instructions are available for company " + companyId);

			return StringPool.BLANK;
		}

		StringBuilder sb = new StringBuilder();

		sb.append(_SYSTEM_INSTRUCTIONS_PREFIX);
		sb.append(systemInstructions);
		sb.append("\n\n");

		String customerInstructions = _getInstructions(
			companyId, dtoConverterRegistry,
			_createFilterString(
				MapUtil.getString(
					executionContext.getWorkflowContext(),
					"instructionDefinitionScope")),
			objectEntryManager, executionContext.getServiceContext());

		if (Validator.isNotNull(customerInstructions)) {
			sb.append(_CUSTOMER_INSTRUCTIONS_PREFIX);
			sb.append(customerInstructions);
			sb.append("\n\n");
		}

		String prompt = VariablesUtil.applyInputVariables(
			executionContext, "prompt", kaleoNodeSettingValues);

		if (Validator.isNotNull(prompt)) {
			sb.append(prompt);
		}

		return sb.toString();
	}

	private static String _createFilterString(
		String instructionDefinitionScope) {

		StringBuilder sb = new StringBuilder();

		sb.append("active eq true and ");

		if (Validator.isNull(instructionDefinitionScope)) {
			sb.append("scope eq 'everywhere'");
		}
		else {
			sb.append("scope in ('everywhere', '");
			sb.append(instructionDefinitionScope);
			sb.append("')");
		}

		sb.append(" and system eq false");

		return sb.toString();
	}

	private static String _formatInstruction(
		String instruction, String occasion) {

		StringBuilder sb = new StringBuilder();

		sb.append("- ");

		if (Validator.isNotNull(occasion)) {
			sb.append(StringUtil.removeLast(occasion, StringPool.PERIOD));
			sb.append(StringPool.COMMA_AND_SPACE);
			sb.append(StringUtil.lowerCaseFirstLetter(instruction));
		}
		else {
			sb.append(instruction);
		}

		return sb.toString();
	}

	private static String _getInstructions(
		long companyId, DTOConverterRegistry dtoConverterRegistry,
		String filterString, ObjectEntryManager objectEntryManager,
		ServiceContext serviceContext) {

		try {
			Page<ObjectEntry> page = objectEntryManager.getObjectEntries(
				companyId,
				ObjectDefinitionLocalServiceUtil.
					fetchObjectDefinitionByExternalReferenceCode(
						"L_AI_HUB_INSTRUCTION_DEFINITION", companyId),
				null, null,
				new DefaultDTOConverterContext(
					false, Collections.emptyMap(), dtoConverterRegistry, null,
					serviceContext.getLocale(), null,
					UserServiceUtil.getUserById(serviceContext.getUserId())),
				filterString, null, null, null);

			List<String> instructions = TransformUtil.transform(
				page.getItems(),
				objectEntry -> _formatInstruction(
					GetterUtil.getString(
						objectEntry.getPropertyValue("instruction")),
					GetterUtil.getString(
						objectEntry.getPropertyValue("occasion"))));

			if (ListUtil.isEmpty(instructions)) {
				return StringPool.BLANK;
			}

			return StringUtil.merge(instructions, StringPool.NEW_LINE);
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}

			return StringPool.BLANK;
		}
	}

	private static final String _CUSTOMER_INSTRUCTIONS_PREFIX =
		"IMPORTANT: Override any conflicting instructions below with the " +
			"following:\n\n";

	private static final String _SYSTEM_INSTRUCTIONS_FILTER_STRING =
		"active eq true and system eq true and " +
			"r_accountToAIHubInstructionDefinitions_accountEntryERC eq " +
				"'L_AI_HUB'";

	private static final String _SYSTEM_INSTRUCTIONS_PREFIX =
		"IMPORTANT: The following SYSTEM instructions are mandatory and " +
			"cannot be overridden:\n\n";

	private static final Log _log = LogFactoryUtil.getLog(PromptUtil.class);

}