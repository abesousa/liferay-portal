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
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectEntryService;
import com.liferay.petra.function.UnsafeRunnable;
import com.liferay.portal.kernel.exception.ModelListenerException;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auth.PrincipalException;
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
import com.liferay.portal.kernel.test.util.UserTestUtil;
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
import java.util.Map;

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
public class AIHubSystemInstructionsTest {

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
					"L_AI_HUB_INSTRUCTION_DEFINITION",
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
					"L_AI_HUB_INSTRUCTION_DEFINITION",
					TestPropsValues.getCompanyId());
	}

	@Test
	public void testAddSystemObjectEntryWithCustomerAccount() throws Exception {
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

		_assertThrows(
			ModelListenerException.class,
			() -> _objectEntryLocalService.addObjectEntry(
				0L, TestPropsValues.getUserId(),
				_objectDefinition.getObjectDefinitionId(), 0,
				LocaleUtil.toLanguageId(LocaleUtil.getDefault()),
				HashMapBuilder.<String, Serializable>put(
					"active", true
				).put(
					"externalReferenceCode", RandomTestUtil.randomString()
				).put(
					"instruction", RandomTestUtil.randomString()
				).put(
					"r_accountToAIHubInstructionDefinitions_accountEntryId",
					customerAccountEntry.getAccountEntryId()
				).put(
					"scope", "everywhere"
				).put(
					"system", true
				).put(
					"title_i18n",
					(Serializable)HashMapBuilder.<String, Serializable>put(
						LocaleUtil.toLanguageId(LocaleUtil.getDefault()),
						RandomTestUtil.randomString()
					).build()
				).build(),
				ServiceContextTestUtil.getServiceContext()));
	}

	@Test
	public void testDeleteSystemObjectEntryWithoutPermissions()
		throws Exception {

		ObjectEntry objectEntry = _fetchSystemObjectEntry("L_AI_HUB_SG_1");

		long objectEntryId = objectEntry.getObjectEntryId();

		User customerUser = UserTestUtil.addUser();

		PermissionChecker originalPermissionChecker =
			PermissionThreadLocal.getPermissionChecker();
		String originalName = PrincipalThreadLocal.getName();

		try {
			PermissionThreadLocal.setPermissionChecker(
				PermissionCheckerFactoryUtil.create(customerUser));
			PrincipalThreadLocal.setName(customerUser.getUserId());

			_assertThrows(
				PrincipalException.class,
				() -> _objectEntryService.deleteObjectEntry(objectEntryId));
		}
		finally {
			PermissionThreadLocal.setPermissionChecker(
				originalPermissionChecker);
			PrincipalThreadLocal.setName(originalName);
		}

		Assert.assertNotNull(
			_objectEntryLocalService.fetchObjectEntry(objectEntryId));
	}

	@Test
	public void testGetSystemObjectEntries() throws Exception {
		for (String externalReferenceCode :
				_systemInstructionExternalReferenceCodes) {

			ObjectEntry objectEntry = _fetchSystemObjectEntry(
				externalReferenceCode);

			Map<String, Serializable> values = objectEntry.getValues();

			Assert.assertEquals(
				objectEntry.toString(), Boolean.TRUE, values.get("active"));
			Assert.assertEquals(
				objectEntry.toString(), Boolean.TRUE, values.get("system"));
			Assert.assertEquals(
				objectEntry.toString(), "everywhere", values.get("scope"));
		}
	}

	@Test
	public void testUpdateSystemObjectEntryWithoutPermissions()
		throws Exception {

		ObjectEntry objectEntry = _fetchSystemObjectEntry("L_AI_HUB_SG_1");

		long objectEntryId = objectEntry.getObjectEntryId();

		Map<String, Serializable> originalValues = objectEntry.getValues();

		String originalInstruction = (String)originalValues.get("instruction");

		User customerUser = UserTestUtil.addUser();

		PermissionChecker originalPermissionChecker =
			PermissionThreadLocal.getPermissionChecker();
		String originalName = PrincipalThreadLocal.getName();

		try {
			PermissionThreadLocal.setPermissionChecker(
				PermissionCheckerFactoryUtil.create(customerUser));
			PrincipalThreadLocal.setName(customerUser.getUserId());

			_assertThrows(
				PrincipalException.class,
				() -> _objectEntryService.updateObjectEntry(
					objectEntryId, 0,
					HashMapBuilder.<String, Serializable>put(
						"instruction", RandomTestUtil.randomString()
					).build(),
					ServiceContextTestUtil.getServiceContext()));
		}
		finally {
			PermissionThreadLocal.setPermissionChecker(
				originalPermissionChecker);
			PrincipalThreadLocal.setName(originalName);
		}

		ObjectEntry objectEntryAfter = _fetchSystemObjectEntry("L_AI_HUB_SG_1");

		Map<String, Serializable> valuesAfter = objectEntryAfter.getValues();

		Assert.assertEquals(
			objectEntryAfter.toString(), originalInstruction,
			valuesAfter.get("instruction"));
	}

	private void _assertThrows(
			Class<? extends Exception> exceptionClass,
			UnsafeRunnable<Exception> unsafeRunnable)
		throws Exception {

		try {
			unsafeRunnable.run();

			Assert.fail("Expected " + exceptionClass.getName());
		}
		catch (Exception exception) {
			if (!exceptionClass.isInstance(exception)) {
				throw exception;
			}
		}
	}

	private ObjectEntry _fetchSystemObjectEntry(String externalReferenceCode) {
		return _objectEntryLocalService.fetchObjectEntry(
			externalReferenceCode, 0,
			_objectDefinition.getObjectDefinitionId());
	}

	private static ObjectDefinition _objectDefinition;

	@Inject
	private static ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private static SiteInitializerRegistry _siteInitializerRegistry;

	private static final List<String> _systemInstructionExternalReferenceCodes =
		Arrays.asList(
			"L_AI_HUB_SG_1", "L_AI_HUB_SG_2", "L_AI_HUB_SG_3", "L_AI_HUB_SG_4",
			"L_AI_HUB_SG_5", "L_AI_HUB_SG_6");

	@Inject
	private AccountEntryLocalService _accountEntryLocalService;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	@Inject
	private ObjectEntryService _objectEntryService;

}