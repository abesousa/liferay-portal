/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.list.type.service;

import com.liferay.counter.kernel.service.CounterLocalService;
import com.liferay.list.type.exception.NoSuchListTypeEntryException;
import com.liferay.list.type.model.ListTypeDefinition;
import com.liferay.list.type.model.ListTypeEntry;
import com.liferay.list.type.model.impl.ListTypeEntryImpl;
import com.liferay.list.type.service.impl.ListTypeEntryLocalServiceImpl;
import com.liferay.list.type.service.persistence.ListTypeDefinitionPersistence;
import com.liferay.list.type.service.persistence.ListTypeEntryPersistence;
import com.liferay.petra.lang.SafeCloseable;
import com.liferay.portal.kernel.lazy.referencing.LazyReferencingThreadLocal;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.test.TestInfo;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import org.mockito.Mockito;

import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author Alberto Sousa
 */
public class ListTypeEntryLocalServiceTest {

	@ClassRule
	public static LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() throws Exception {
		_listTypeEntryLocalService = new ListTypeEntryLocalServiceImpl();

		ListTypeDefinitionPersistence listTypeDefinitionPersistence =
			_mockListTypeDefinitionPersistence();

		ListTypeEntryPersistence listTypeEntryPersistence =
			_mockListTypeEntryPersistence();

		User user = _mockUser();

		UserLocalService userLocalService = _mockUserLocalService(user);

		ReflectionTestUtils.setField(
			_listTypeEntryLocalService, "counterLocalService",
			Mockito.mock(CounterLocalService.class));
		ReflectionTestUtils.setField(
			_listTypeEntryLocalService, "_listTypeDefinitionPersistence",
			listTypeDefinitionPersistence);
		ReflectionTestUtils.setField(
			_listTypeEntryLocalService, "listTypeEntryPersistence",
			listTypeEntryPersistence);
		ReflectionTestUtils.setField(
			_listTypeEntryLocalService, "_userLocalService", userLocalService);
	}

	@Test
	@TestInfo("LPD-55656")
	public void testGetOrAddIncompleteListTypeEntry() throws Exception {

		// Lazy referencing disabled

		try {
			_listTypeEntryLocalService.getOrAddIncompleteListTypeEntry(
				RandomTestUtil.randomLong(),
				_listTypeDefinition.getListTypeDefinitionId(),
				RandomTestUtil.randomString());

			Assert.fail();
		}
		catch (NoSuchListTypeEntryException noSuchListTypeEntryException) {
			Assert.assertNotNull(noSuchListTypeEntryException);
		}

		// Lazy referencing enabled

		try (SafeCloseable safeCloseable =
				LazyReferencingThreadLocal.setEnabledWithSafeCloseable(true)) {

			ListTypeEntry listTypeEntry =
				_listTypeEntryLocalService.getOrAddIncompleteListTypeEntry(
					RandomTestUtil.randomLong(),
					_listTypeDefinition.getListTypeDefinitionId(),
					RandomTestUtil.randomString());

			Assert.assertEquals(
				WorkflowConstants.STATUS_INCOMPLETE, listTypeEntry.getStatus());
		}
	}

	@Test
	@TestInfo("LPD-55656")
	public void testUpdateListTypeEntry() throws Exception {
		try (SafeCloseable safeCloseable =
				LazyReferencingThreadLocal.setEnabledWithSafeCloseable(true)) {

			ListTypeEntry listTypeEntry =
				_listTypeEntryLocalService.updateListTypeEntry(
					RandomTestUtil.randomString(), RandomTestUtil.randomLong(),
					RandomTestUtil.randomLocaleStringMap());

			Assert.assertEquals(
				WorkflowConstants.STATUS_APPROVED, listTypeEntry.getStatus());
		}
	}

	private ListTypeDefinitionPersistence _mockListTypeDefinitionPersistence()
		throws Exception {

		ListTypeDefinition listTypeDefinition = Mockito.mock(
			ListTypeDefinition.class);

		Mockito.when(
			listTypeDefinition.isSystem()
		).thenReturn(
			false//RandomTestUtil.randomBoolean()
		);

		ListTypeDefinitionPersistence listTypeDefinitionPersistence =
			Mockito.mock(ListTypeDefinitionPersistence.class);

		Mockito.when(
			listTypeDefinitionPersistence.findByPrimaryKey(Mockito.anyLong())
		).thenReturn(
			listTypeDefinition
		);

		return listTypeDefinitionPersistence;
	}

	private ListTypeEntryPersistence _mockListTypeEntryPersistence()
		throws Exception {

		ListTypeEntry listTypeEntry = Mockito.mock(ListTypeEntryImpl.class);

		Mockito.doCallRealMethod(
		).when(
			listTypeEntry
		).setStatus(
			Mockito.anyInt()
		);

		listTypeEntry.setStatus(WorkflowConstants.STATUS_INCOMPLETE);

		ListTypeEntryPersistence listTypeEntryPersistence = Mockito.mock(
			ListTypeEntryPersistence.class);

		Mockito.when(
			listTypeEntryPersistence.findByPrimaryKey(Mockito.anyLong())
		).thenReturn(
			listTypeEntry
		);

		Mockito.when(
			listTypeEntryPersistence.findByLTDI_K(
				Mockito.anyLong(), Mockito.anyString())
		).thenReturn(
			null
		);

		Mockito.when(
			listTypeEntryPersistence.create(Mockito.anyLong())
		).thenReturn(
			new ListTypeEntryImpl()
		);

		Mockito.when(
			listTypeEntryPersistence.update(Mockito.any(ListTypeEntry.class))
		).thenAnswer(
			invocation -> invocation.getArgument(0)
		);

		return listTypeEntryPersistence;
	}

	private User _mockUser() {
		User user = Mockito.mock(User.class);

		Mockito.when(
			user.getCompanyId()
		).thenReturn(
			RandomTestUtil.randomLong()
		);

		Mockito.when(
			user.getUserId()
		).thenReturn(
			RandomTestUtil.randomLong()
		);

		Mockito.when(
			user.getFullName()
		).thenReturn(
			RandomTestUtil.randomString()
		);

		return user;
	}

	private UserLocalService _mockUserLocalService(User user) throws Exception {
		UserLocalService userLocalService = Mockito.mock(
			UserLocalService.class);

		Mockito.when(
			userLocalService.getUser(Mockito.anyLong())
		).thenReturn(
			user
		);

		return userLocalService;
	}

	private final ListTypeDefinition _listTypeDefinition = Mockito.mock(
		ListTypeDefinition.class);
	private ListTypeEntryLocalService _listTypeEntryLocalService;

}