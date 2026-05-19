/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.workflow.kaleo.runtime.node.util;

import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.rest.dto.v1_0.ObjectEntry;
import com.liferay.object.rest.manager.v1_0.ObjectEntryManager;
import com.liferay.object.service.ObjectDefinitionLocalServiceUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserServiceUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;
import com.liferay.portal.vulcan.pagination.Page;
import com.liferay.portal.workflow.kaleo.runtime.ExecutionContext;

import java.io.Serializable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * @author Alberto Sousa
 */
public class PromptUtilTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@BeforeClass
	public static void setUpClass() {
		_objectDefinitionLocalServiceUtilMockedStatic = Mockito.mockStatic(
			ObjectDefinitionLocalServiceUtil.class);
		_userServiceUtilMockedStatic = Mockito.mockStatic(
			UserServiceUtil.class);

		_objectDefinitionLocalServiceUtilMockedStatic.when(
			() ->
				ObjectDefinitionLocalServiceUtil.
					fetchObjectDefinitionByExternalReferenceCode(
						Mockito.eq("L_AI_HUB_INSTRUCTION_DEFINITION"),
						Mockito.anyLong())
		).thenReturn(
			Mockito.mock(ObjectDefinition.class)
		);

		_userServiceUtilMockedStatic.when(
			() -> UserServiceUtil.getUserById(Mockito.anyLong())
		).thenReturn(
			Mockito.mock(User.class)
		);
	}

	@AfterClass
	public static void tearDownClass() {
		_objectDefinitionLocalServiceUtilMockedStatic.close();
		_userServiceUtilMockedStatic.close();
	}

	@Before
	public void setUp() throws Exception {
		_objectEntryManager = Mockito.mock(ObjectEntryManager.class);

		_serviceContext = Mockito.mock(ServiceContext.class);

		Mockito.when(
			_serviceContext.getLocale()
		).thenReturn(
			LocaleUtil.US
		);

		Mockito.when(
			_serviceContext.getUserId()
		).thenReturn(
			1L
		);
	}

	@Test
	public void testComposePromptWithDifferentScope() throws Exception {
		_mockObjectEntryManager(
			Arrays.asList(_createObjectEntry("system instruction text")),
			Collections.emptyList());

		for (String scope :
				new String[] {null, "clickToChat", "CMS", "everywhere"}) {

			PromptUtil.composePrompt(
				1L, null, _createExecutionContext(scope),
				Collections.emptyMap(), _objectEntryManager);
		}

		ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(
			String.class);

		Mockito.verify(
			_objectEntryManager, Mockito.atLeastOnce()
		).getObjectEntries(
			Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(),
			Mockito.any(), argumentCaptor.capture(), Mockito.any(),
			Mockito.any(), Mockito.any()
		);

		List<String> filters = argumentCaptor.getAllValues();

		String expectedSystemFilter =
			"active eq true and system eq true and " +
				"r_accountToAIHubInstructionDefinitions_accountEntryERC eq " +
					"'L_AI_HUB'";

		Assert.assertEquals(
			filters.toString(),
			Arrays.asList(
				expectedSystemFilter,
				"active eq true and scope eq 'everywhere' and system eq false",
				expectedSystemFilter,
				"active eq true and scope in ('everywhere', 'clickToChat') " +
					"and system eq false",
				expectedSystemFilter,
				"active eq true and scope in ('everywhere', 'CMS') and " +
					"system eq false",
				expectedSystemFilter,
				"active eq true and scope in ('everywhere', 'everywhere') " +
					"and system eq false"),
			filters);
	}

	@Test
	public void testComposePromptWithoutCustomerInstructions()
		throws Exception {

		_mockObjectEntryManager(
			Arrays.asList(_createObjectEntry("system instruction text")),
			Collections.emptyList());

		String prompt = PromptUtil.composePrompt(
			1L, null, _createExecutionContext(null),
			HashMapBuilder.put(
				"prompt", "agent prompt text"
			).build(),
			_objectEntryManager);

		int systemIndex = prompt.indexOf("system instruction text");
		int agentPromptIndex = prompt.indexOf("agent prompt text");

		Assert.assertTrue(prompt, systemIndex >= 0);
		Assert.assertTrue(prompt, systemIndex < agentPromptIndex);
	}

	@Test
	public void testComposePromptWithoutSystemInstructions() throws Exception {
		_mockObjectEntryManager(
			Collections.emptyList(),
			Arrays.asList(_createObjectEntry("customer instruction")));

		String prompt = PromptUtil.composePrompt(
			1L, null, _createExecutionContext(null),
			HashMapBuilder.put(
				"prompt", "agent prompt"
			).build(),
			_objectEntryManager);

		Assert.assertEquals("", prompt);
	}

	@Test
	public void testComposePromptWithSystemAndCustomerInstructions()
		throws Exception {

		_mockObjectEntryManager(
			Arrays.asList(_createObjectEntry("system instruction text")),
			Arrays.asList(_createObjectEntry("customer instruction text")));

		String prompt = PromptUtil.composePrompt(
			1L, null, _createExecutionContext(null),
			HashMapBuilder.put(
				"prompt", "agent prompt text"
			).build(),
			_objectEntryManager);

		int systemIndex = prompt.indexOf("system instruction text");
		int customerIndex = prompt.indexOf("customer instruction text");
		int agentPromptIndex = prompt.indexOf("agent prompt text");

		Assert.assertTrue(prompt, systemIndex >= 0);
		Assert.assertTrue(prompt, systemIndex < customerIndex);
		Assert.assertTrue(prompt, customerIndex < agentPromptIndex);
	}

	private ExecutionContext _createExecutionContext(String scope) {
		Map<String, Serializable> workflowContext = new HashMap<>();

		if (scope != null) {
			workflowContext.put("instructionDefinitionScope", scope);
		}

		return new ExecutionContext(null, workflowContext, _serviceContext);
	}

	private ObjectEntry _createObjectEntry(String instruction) {
		ObjectEntry objectEntry = new ObjectEntry();

		objectEntry.setProperties(
			HashMapBuilder.<String, Object>put(
				"instruction", instruction
			).build());

		return objectEntry;
	}

	private void _mockObjectEntryManager(
			List<ObjectEntry> systemObjectEntries,
			List<ObjectEntry> customerObjectEntries)
		throws Exception {

		Mockito.when(
			_objectEntryManager.getObjectEntries(
				Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(),
				Mockito.any(), Mockito.anyString(), Mockito.any(),
				Mockito.any(), Mockito.any())
		).thenAnswer(
			invocation -> {
				String filterString = invocation.getArgument(5);

				if (filterString.contains("system eq true")) {
					return Page.of(systemObjectEntries);
				}

				return Page.of(customerObjectEntries);
			}
		);
	}

	private static MockedStatic<ObjectDefinitionLocalServiceUtil>
		_objectDefinitionLocalServiceUtilMockedStatic;
	private static MockedStatic<UserServiceUtil> _userServiceUtilMockedStatic;

	private ObjectEntryManager _objectEntryManager;
	private ServiceContext _serviceContext;

}