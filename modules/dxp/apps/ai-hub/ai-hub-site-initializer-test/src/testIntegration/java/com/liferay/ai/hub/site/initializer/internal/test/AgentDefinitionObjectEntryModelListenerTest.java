/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.site.initializer.internal.test;

import com.liferay.account.constants.AccountConstants;
import com.liferay.account.model.AccountEntry;
import com.liferay.account.service.AccountEntryLocalService;
import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectRelationship;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectEntryService;
import com.liferay.object.service.ObjectRelationshipLocalService;
import com.liferay.petra.function.transform.TransformUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.security.auth.PrincipalThreadLocal;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DataGuard;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.test.rule.FeatureFlag;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;
import com.liferay.site.initializer.SiteInitializer;
import com.liferay.site.initializer.SiteInitializerRegistry;

import java.io.Serializable;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Alberto Sousa
 */
@DataGuard(scope = DataGuard.Scope.METHOD)
@FeatureFlag("LPD-62272")
@RunWith(Arquillian.class)
public class AgentDefinitionObjectEntryModelListenerTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@BeforeClass
	public static void setUpClass() throws Exception {
		_objectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_AI_HUB_AGENT_DEFINITION",
					TestPropsValues.getCompanyId());

		if (_objectDefinition != null) {
			return;
		}

		PermissionChecker originalPermissionChecker =
			PermissionThreadLocal.getPermissionChecker();
		String originalName = PrincipalThreadLocal.getName();

		try {
			PermissionThreadLocal.setPermissionChecker(
				PermissionCheckerFactoryUtil.create(TestPropsValues.getUser()));
			PrincipalThreadLocal.setName(TestPropsValues.getUserId());

			ServiceContextThreadLocal.pushServiceContext(
				ServiceContextTestUtil.getServiceContext(
					TestPropsValues.getGroupId(), TestPropsValues.getUserId()));

			SiteInitializer siteInitializer =
				_siteInitializerRegistry.getSiteInitializer(
					"com.liferay.ai.hub.site.initializer");

			siteInitializer.initialize(TestPropsValues.getGroupId());
		}
		finally {
			ServiceContextThreadLocal.popServiceContext();

			PermissionThreadLocal.setPermissionChecker(
				originalPermissionChecker);
			PrincipalThreadLocal.setName(originalName);
		}

		_objectDefinition =
			_objectDefinitionLocalService.
				getObjectDefinitionByExternalReferenceCode(
					"L_AI_HUB_AGENT_DEFINITION",
					TestPropsValues.getCompanyId());
	}

	@Test
	public void testOnAfterCreate() throws Exception {
		AccountEntry customerAccountEntry =
			_accountEntryLocalService.addAccountEntry(
				RandomTestUtil.randomString(), TestPropsValues.getUserId(),
				AccountConstants.PARENT_ACCOUNT_ENTRY_ID_DEFAULT,
				RandomTestUtil.randomString(), RandomTestUtil.randomString(),
				null, RandomTestUtil.randomString() + "@liferay.com", null,
				RandomTestUtil.randomString(),
				AccountConstants.ACCOUNT_ENTRY_TYPE_BUSINESS,
				WorkflowConstants.STATUS_APPROVED,
				ServiceContextTestUtil.getServiceContext());

		ObjectEntry agentObjectEntry = _objectEntryLocalService.addObjectEntry(
			0L, TestPropsValues.getUserId(),
			_objectDefinition.getObjectDefinitionId(), 0,
			LocaleUtil.toLanguageId(LocaleUtil.getDefault()),
			HashMapBuilder.<String, Serializable>put(
				"active", true
			).put(
				"externalReferenceCode", RandomTestUtil.randomString()
			).put(
				"inputVariables", RandomTestUtil.randomString()
			).put(
				"outputVariable", RandomTestUtil.randomString()
			).put(
				"r_accountToAIHubAgentDefinitions_accountEntryId",
				customerAccountEntry.getAccountEntryId()
			).put(
				"workflowDefinitionName", RandomTestUtil.randomString()
			).build(),
			ServiceContextTestUtil.getServiceContext());

		ObjectRelationship objectRelationship =
			_objectRelationshipLocalService.
				fetchObjectRelationshipByExternalReferenceCode(
					"L_AI_HUB_AGENT_DEFINITIONS_TO_L_AI_HUB_MODEL_ARMOR_" +
						"TEMPLATES",
					_objectDefinition.getObjectDefinitionId());

		List<ObjectEntry> modelArmorTemplateObjectEntries =
			_objectEntryService.getManyToManyObjectEntries(
				0L, objectRelationship.getObjectRelationshipId(),
				agentObjectEntry.getObjectEntryId(), true, false, null,
				QueryUtil.ALL_POS, QueryUtil.ALL_POS);

		Assert.assertEquals(
			modelArmorTemplateObjectEntries.toString(),
			_systemGuardrailExternalReferenceCodes.size(),
			modelArmorTemplateObjectEntries.size());

		List<String> externalReferenceCodes = TransformUtil.transform(
			modelArmorTemplateObjectEntries,
			ObjectEntry::getExternalReferenceCode);

		Assert.assertTrue(
			externalReferenceCodes.toString(),
			externalReferenceCodes.containsAll(
				_systemGuardrailExternalReferenceCodes));
	}

	private static ObjectDefinition _objectDefinition;

	@Inject
	private static ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private static SiteInitializerRegistry _siteInitializerRegistry;

	private static final List<String> _systemGuardrailExternalReferenceCodes =
		Arrays.asList(
			"L_AI_HUB_SG_3_OUTPUT_FILTER", "L_AI_HUB_SG_6_OUTPUT_FILTER");

	@Inject
	private AccountEntryLocalService _accountEntryLocalService;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	@Inject
	private ObjectEntryService _objectEntryService;

	@Inject
	private ObjectRelationshipLocalService _objectRelationshipLocalService;

}