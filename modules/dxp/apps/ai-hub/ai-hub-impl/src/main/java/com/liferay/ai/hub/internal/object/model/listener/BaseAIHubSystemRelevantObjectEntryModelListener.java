/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.object.model.listener;

import com.liferay.account.model.AccountEntry;
import com.liferay.account.service.AccountEntryLocalService;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectField;
import com.liferay.object.model.listener.RelevantObjectEntryModelListener;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.exception.ModelListenerException;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.util.GetterUtil;

import java.io.Serializable;

import java.util.Map;
import java.util.Objects;

import org.osgi.service.component.annotations.Reference;

/**
 * @author Alberto Sousa
 */
public abstract class BaseAIHubSystemRelevantObjectEntryModelListener
	extends BaseModelListener<ObjectEntry>
	implements RelevantObjectEntryModelListener {

	@Override
	public final void onBeforeCreate(ObjectEntry objectEntry)
		throws ModelListenerException {

		Map<String, Serializable> values = objectEntry.getValues();

		if (!GetterUtil.getBoolean(values.get("system"))) {
			return;
		}

		AccountEntry accountEntry = _getAccountEntry(objectEntry);

		if (Objects.equals(
				accountEntry.getExternalReferenceCode(), "L_AI_HUB")) {

			return;
		}

		throw new AIHubSystemEntryModelListenerException(
			StringBundler.concat(
				"Unable to create AI Hub system entry ",
				objectEntry.getExternalReferenceCode(),
				" outside the L_AI_HUB account"));
	}

	@Override
	public final void onBeforeUpdate(
			ObjectEntry originalObjectEntry, ObjectEntry objectEntry)
		throws ModelListenerException {

		Map<String, Serializable> values = objectEntry.getValues();

		if (!values.containsKey("system")) {
			return;
		}

		Map<String, Serializable> originalValues =
			originalObjectEntry.getValues();

		if (GetterUtil.getBoolean(values.get("system")) ==
				GetterUtil.getBoolean(originalValues.get("system"))) {

			return;
		}

		throw new AIHubSystemEntryModelListenerException(
			"Unable to modify the system field of AI Hub entry " +
				objectEntry.getExternalReferenceCode());
	}

	@Reference
	protected AccountEntryLocalService accountEntryLocalService;

	@Reference
	protected ObjectDefinitionLocalService objectDefinitionLocalService;

	@Reference
	protected ObjectFieldLocalService objectFieldLocalService;

	private AccountEntry _getAccountEntry(ObjectEntry objectEntry)
		throws ModelListenerException {

		try {
			ObjectDefinition objectDefinition =
				objectDefinitionLocalService.getObjectDefinition(
					objectEntry.getObjectDefinitionId());

			ObjectField objectField = objectFieldLocalService.getObjectField(
				objectDefinition.getAccountEntryRestrictedObjectFieldId());

			Map<String, Serializable> values = objectEntry.getValues();

			return accountEntryLocalService.getAccountEntry(
				GetterUtil.getLong(values.get(objectField.getName())));
		}
		catch (Exception exception) {
			throw new ModelListenerException(exception);
		}
	}

}