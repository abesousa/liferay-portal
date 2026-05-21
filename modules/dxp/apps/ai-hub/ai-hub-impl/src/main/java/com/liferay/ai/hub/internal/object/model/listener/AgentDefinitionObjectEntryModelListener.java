/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.object.model.listener;

import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectRelationship;
import com.liferay.object.model.listener.RelevantObjectEntryModelListener;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectRelationshipLocalService;
import com.liferay.portal.kernel.exception.ModelListenerException;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;

import java.util.Arrays;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Alberto Sousa
 */
@Component(service = RelevantObjectEntryModelListener.class)
public class AgentDefinitionObjectEntryModelListener
	extends BaseModelListener<ObjectEntry>
	implements RelevantObjectEntryModelListener {

	@Override
	public String getObjectDefinitionExternalReferenceCode() {
		return "L_AI_HUB_AGENT_DEFINITION";
	}

	@Override
	public void onAfterCreate(ObjectEntry objectEntry)
		throws ModelListenerException {

		try {
			long companyId = objectEntry.getCompanyId();

			ObjectDefinition modelArmorTemplateObjectDefinition =
				_objectDefinitionLocalService.
					getObjectDefinitionByExternalReferenceCode(
						"L_AI_HUB_MODEL_ARMOR_TEMPLATE", companyId);

			ObjectRelationship objectRelationship =
				_objectRelationshipLocalService.
					getObjectRelationshipByExternalReferenceCode(
						"L_AI_HUB_AGENT_DEFINITIONS_TO_L_AI_HUB_MODEL_ARMOR_" +
							"TEMPLATES",
						companyId, objectEntry.getObjectDefinitionId());

			ServiceContext serviceContext =
				ServiceContextThreadLocal.getServiceContext();

			if (serviceContext == null) {
				serviceContext = new ServiceContext();

				serviceContext.setCompanyId(companyId);
				serviceContext.setUserId(objectEntry.getUserId());
			}

			for (String externalReferenceCode :
					_systemGuardrailExternalReferenceCodes) {

				ObjectEntry modelArmorTemplateObjectEntry =
					_objectEntryLocalService.getObjectEntry(
						externalReferenceCode, 0,
						modelArmorTemplateObjectDefinition.
							getObjectDefinitionId());

				_objectRelationshipLocalService.
					addObjectRelationshipMappingTableValues(
						objectEntry.getUserId(),
						objectRelationship.getObjectRelationshipId(),
						objectEntry.getObjectEntryId(),
						modelArmorTemplateObjectEntry.getObjectEntryId(),
						serviceContext);
			}
		}
		catch (Exception exception) {
			throw new ModelListenerException(exception);
		}
	}

	private static final List<String> _systemGuardrailExternalReferenceCodes =
		Arrays.asList(
			"L_AI_HUB_SG_3_OUTPUT_FILTER", "L_AI_HUB_SG_6_OUTPUT_FILTER");

	@Reference
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Reference
	private ObjectEntryLocalService _objectEntryLocalService;

	@Reference
	private ObjectRelationshipLocalService _objectRelationshipLocalService;

}