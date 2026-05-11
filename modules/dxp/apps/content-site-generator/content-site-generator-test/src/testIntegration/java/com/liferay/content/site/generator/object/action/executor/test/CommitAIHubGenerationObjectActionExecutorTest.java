/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.content.site.generator.object.action.executor.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.document.library.kernel.model.DLFolderConstants;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.list.type.model.ListTypeDefinition;
import com.liferay.list.type.service.ListTypeDefinitionLocalService;
import com.liferay.object.action.engine.ObjectActionEngine;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.Time;
import com.liferay.portal.test.rule.FeatureFlag;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import java.io.Serializable;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Alberto Sousa
 */
@FeatureFlag("LPD-85514")
@RunWith(Arquillian.class)
public class CommitAIHubGenerationObjectActionExecutorTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@BeforeClass
	public static void setUpClass() throws Exception {
		_user = TestPropsValues.getUser();

		_generationObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_AI_HUB_GENERATION", _user.getCompanyId());
		_generationItemObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_AI_HUB_GENERATION_ITEM", _user.getCompanyId());

		_group = GroupTestUtil.addGroup();

		_serviceContext = ServiceContextTestUtil.getServiceContext(
			_group.getGroupId(), _user.getUserId());
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		for (String externalReferenceCode :
				_createdListTypeDefinitionExternalReferenceCodes) {

			ListTypeDefinition listTypeDefinition =
				_listTypeDefinitionLocalService.
					fetchListTypeDefinitionByExternalReferenceCode(
						externalReferenceCode, _user.getCompanyId());

			if (listTypeDefinition != null) {
				_listTypeDefinitionLocalService.deleteListTypeDefinition(
					listTypeDefinition.getListTypeDefinitionId());
			}
		}

		GroupTestUtil.deleteGroup(_group);
	}

	@Test
	public void testExecute() throws Exception {
		ObjectEntry generationObjectEntry =
			_objectEntryLocalService.addObjectEntry(
				0L, _user.getUserId(),
				_generationObjectDefinition.getObjectDefinitionId(), 0L, null,
				HashMapBuilder.<String, Serializable>put(
					"generationStatus", "ready"
				).put(
					"prompt", RandomTestUtil.randomString()
				).put(
					"title", RandomTestUtil.randomString()
				).build(),
				_serviceContext);

		long generationObjectEntryId = generationObjectEntry.getObjectEntryId();

		String externalReferenceCode1 = RandomTestUtil.randomString();
		String externalReferenceCode2 = RandomTestUtil.randomString();

		_createdListTypeDefinitionExternalReferenceCodes.add(
			externalReferenceCode1);
		_createdListTypeDefinitionExternalReferenceCodes.add(
			externalReferenceCode2);

		_addGenerationItemObjectEntry(
			externalReferenceCode1, "ltd-1.json", generationObjectEntryId, 1);
		_addGenerationItemObjectEntry(
			externalReferenceCode2, "ltd-2.json", generationObjectEntryId, 2);

		_objectActionEngine.executeObjectAction(
			"commit", "standalone",
			_generationObjectDefinition.getObjectDefinitionId(),
			JSONUtil.put(
				"classPK", generationObjectEntryId
			).put(
				"objectEntry",
				HashMapBuilder.putAll(
					generationObjectEntry.getModelAttributes()
				).put(
					"values", generationObjectEntry.getValues()
				).build()
			),
			_user.getUserId());

		Map<String, Serializable> values = _awaitCompletion(
			generationObjectEntryId);

		Assert.assertNotNull(values.get("commitDate"));
		Assert.assertEquals("", String.valueOf(values.get("failureReason")));
		Assert.assertEquals("committed", values.get("generationStatus"));

		Assert.assertNotNull(
			_listTypeDefinitionLocalService.
				fetchListTypeDefinitionByExternalReferenceCode(
					externalReferenceCode1, _user.getCompanyId()));
		Assert.assertNotNull(
			_listTypeDefinitionLocalService.
				fetchListTypeDefinitionByExternalReferenceCode(
					externalReferenceCode2, _user.getCompanyId()));
	}

	private void _addGenerationItemObjectEntry(
			String externalReferenceCode, String fileName,
			long generationObjectEntryId, int loadOrder)
		throws Exception {

		FileEntry fileEntry = _dlAppLocalService.addFileEntry(
			null, _user.getUserId(), _group.getGroupId(),
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID, fileName,
			ContentTypes.APPLICATION_JSON,
			_createBatchFileJSON(
				externalReferenceCode
			).getBytes(
				StandardCharsets.UTF_8
			),
			null, null, null, _serviceContext);

		_objectEntryLocalService.addObjectEntry(
			0L, _user.getUserId(),
			_generationItemObjectDefinition.getObjectDefinitionId(), 0L, null,
			HashMapBuilder.<String, Serializable>put(
				"batchFile", fileEntry.getFileEntryId()
			).put(
				"fileName", fileName
			).put(
				"itemCount", 1
			).put(
				"loadOrder", loadOrder
			).put(
				"r_items_l_aiHubGenerationId", generationObjectEntryId
			).build(),
			_serviceContext);
	}

	private Map<String, Serializable> _awaitCompletion(
			long generationObjectEntryId)
		throws Exception {

		long deadline = System.currentTimeMillis() + Time.MINUTE;

		while (System.currentTimeMillis() < deadline) {
			Thread.sleep(Time.SECOND / 2);

			Map<String, Serializable> values =
				_objectEntryLocalService.getValues(generationObjectEntryId);

			String generationStatus = MapUtil.getString(
				values, "generationStatus");

			if (generationStatus.equals("committed") ||
				generationStatus.equals("failed")) {

				return values;
			}
		}

		throw new AssertionError("Generation completion takes too long");
	}

	private String _createBatchFileJSON(String externalReferenceCode) {
		return JSONUtil.put(
			"configuration",
			JSONUtil.put(
				"className",
				"com.liferay.headless.admin.list.type.dto.v1_0." +
					"ListTypeDefinition"
			).put(
				"multiCompany", true
			).put(
				"parameters",
				JSONUtil.put(
					"containsHeaders", "true"
				).put(
					"createStrategy", "UPSERT"
				).put(
					"importStrategy", "ON_ERROR_FAIL"
				).put(
					"updateStrategy", "UPDATE"
				)
			).put(
				"taskItemDelegateName", "DEFAULT"
			)
		).put(
			"items",
			JSONUtil.putAll(
				JSONUtil.put(
					"externalReferenceCode", externalReferenceCode
				).put(
					"listTypeEntries", JSONFactoryUtil.createJSONArray()
				).put(
					"name", RandomTestUtil.randomString()
				).put(
					"system", true
				))
		).toString();
	}

	private static final List<String>
		_createdListTypeDefinitionExternalReferenceCodes = new ArrayList<>();

	@Inject
	private static DLAppLocalService _dlAppLocalService;

	private static ObjectDefinition _generationItemObjectDefinition;
	private static ObjectDefinition _generationObjectDefinition;
	private static Group _group;

	@Inject
	private static ListTypeDefinitionLocalService
		_listTypeDefinitionLocalService;

	@Inject
	private static ObjectActionEngine _objectActionEngine;

	@Inject
	private static ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private static ObjectEntryLocalService _objectEntryLocalService;

	private static ServiceContext _serviceContext;
	private static User _user;

}