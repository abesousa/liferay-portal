/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.workflow.kaleo.definition.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.test.AssertUtils;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.workflow.WorkflowDefinition;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.workflow.kaleo.definition.NodeType;
import com.liferay.portal.workflow.kaleo.definition.exception.KaleoDefinitionValidationException;
import com.liferay.portal.workflow.kaleo.model.KaleoDefinitionVersion;
import com.liferay.portal.workflow.kaleo.model.KaleoNode;
import com.liferay.portal.workflow.kaleo.model.KaleoNodeSetting;
import com.liferay.portal.workflow.kaleo.service.KaleoDefinitionVersionLocalService;
import com.liferay.portal.workflow.kaleo.service.KaleoNodeLocalService;
import com.liferay.portal.workflow.kaleo.service.KaleoNodeSettingLocalService;
import com.liferay.portal.workflow.manager.WorkflowDefinitionManager;

import java.io.InputStream;

import java.util.List;
import java.util.Objects;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Mario Gomes
 */
@RunWith(Arquillian.class)
public class ToolNodeWorkflowDefinitionTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Test
	public void testDeployWorkflowDefinitionWithToolNode() throws Exception {
		WorkflowDefinition workflowDefinition =
			_workflowDefinitionManager.deployWorkflowDefinition(
				FileUtil.getBytes(
					_getResourceInputStream(
						"tool-node-workflow-definition.xml")),
				TestPropsValues.getCompanyId(), null,
				RandomTestUtil.randomString(), StringPool.BLANK,
				TestPropsValues.getUserId());

		Assert.assertNotNull(workflowDefinition);

		KaleoDefinitionVersion kaleoDefinitionVersion =
			_kaleoDefinitionVersionLocalService.getLatestKaleoDefinitionVersion(
				workflowDefinition.getCompanyId(),
				workflowDefinition.getName());

		KaleoNode toolKaleoNode = _getKaleoNode(
			kaleoDefinitionVersion.getKaleoDefinitionVersionId(),
			"fragmentLoader");

		Assert.assertNotNull(toolKaleoNode);
		Assert.assertEquals(NodeType.TOOL.name(), toolKaleoNode.getType());

		List<KaleoNodeSetting> kaleoNodeSettings =
			_kaleoNodeSettingLocalService.getKaleoNodeSettings(
				toolKaleoNode.getKaleoNodeId());

		Assert.assertEquals(
			"getFragments", _getSettingValue(kaleoNodeSettings, "toolName"));
		Assert.assertEquals(
			"[{\"name\":\"siteERC\"}]",
			_getSettingValue(kaleoNodeSettings, "inputVariables"));
		Assert.assertEquals(
			"[{\"name\":\"fragments\"}]",
			_getSettingValue(kaleoNodeSettings, "outputVariables"));
	}

	@Test
	public void testDeployWorkflowDefinitionWithToolNodeMultipleTransitions()
		throws Exception {

		AssertUtils.assertFailure(
			KaleoDefinitionValidationException.
				MustNotSetMultipleOutgoingTransitions.class,
			"The fragmentLoader node cannot have multiple outgoing transitions",
			() -> _workflowDefinitionManager.deployWorkflowDefinition(
				FileUtil.getBytes(
					_getResourceInputStream(
						"tool-node-multiple-transitions-workflow-" +
							"definition.xml")),
				TestPropsValues.getCompanyId(), null,
				RandomTestUtil.randomString(), StringPool.BLANK,
				TestPropsValues.getUserId()));
	}

	private KaleoNode _getKaleoNode(
		long kaleoDefinitionVersionId, String name) {

		for (KaleoNode kaleoNode :
				_kaleoNodeLocalService.getKaleoDefinitionVersionKaleoNodes(
					kaleoDefinitionVersionId)) {

			if (Objects.equals(name, kaleoNode.getName())) {
				return kaleoNode;
			}
		}

		return null;
	}

	private InputStream _getResourceInputStream(String name) {
		Class<?> clazz = getClass();

		ClassLoader classLoader = clazz.getClassLoader();

		return classLoader.getResourceAsStream(
			"com/liferay/portal/workflow/kaleo/dependencies/" + name);
	}

	private String _getSettingValue(
		List<KaleoNodeSetting> kaleoNodeSettings, String name) {

		for (KaleoNodeSetting kaleoNodeSetting : kaleoNodeSettings) {
			if (Objects.equals(name, kaleoNodeSetting.getName())) {
				return kaleoNodeSetting.getValue();
			}
		}

		return null;
	}

	@Inject
	private KaleoDefinitionVersionLocalService
		_kaleoDefinitionVersionLocalService;

	@Inject
	private KaleoNodeLocalService _kaleoNodeLocalService;

	@Inject
	private KaleoNodeSettingLocalService _kaleoNodeSettingLocalService;

	@Inject
	private WorkflowDefinitionManager _workflowDefinitionManager;

}