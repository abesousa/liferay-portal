/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.workflow.kaleo.runtime.node.util;

import com.liferay.ai.hub.internal.assistant.tool.SitePageTools;
import com.liferay.ai.hub.internal.assistant.tool.WorkflowNodeTools;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.workflow.WorkflowNodeManager;
import com.liferay.portal.test.rule.LiferayUnitTestRule;
import com.liferay.portal.workflow.kaleo.definition.NodeType;
import com.liferay.portal.workflow.kaleo.model.KaleoNode;

import java.io.Serializable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

import org.mockito.Mockito;

/**
 * @author Alberto Sousa
 */
public class ToolsUtilTest {

	@ClassRule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Test
	public void testGetToolsWhenNodeIsUnknown() {
		KaleoNode kaleoNode = Mockito.mock(KaleoNode.class);

		Mockito.when(
			kaleoNode.getType()
		).thenReturn(
			NodeType.STATE.name()
		);

		Mockito.when(
			kaleoNode.getName()
		).thenReturn(
			RandomTestUtil.randomString()
		);

		Object[] tools = ToolsUtil.getTools(
			RandomTestUtil.randomLong(), kaleoNode, new HashMap<>(),
			Mockito.mock(WorkflowNodeManager.class));

		Assert.assertEquals(Arrays.toString(tools), 0, tools.length);
	}

	@Test
	public void testGetToolsWhenNodeNameIsPageBuilder() {
		KaleoNode kaleoNode = Mockito.mock(KaleoNode.class);

		Mockito.when(
			kaleoNode.getType()
		).thenReturn(
			NodeType.TOOL.name()
		);

		Mockito.when(
			kaleoNode.getName()
		).thenReturn(
			"pageBuilder"
		);

		Map<String, Serializable> workflowContext =
			HashMapBuilder.<String, Serializable>put(
				"accessToken", "Bearer access"
			).put(
				"userToken", "user"
			).build();

		Object[] tools = ToolsUtil.getTools(
			RandomTestUtil.randomLong(), kaleoNode, workflowContext,
			Mockito.mock(WorkflowNodeManager.class));

		Assert.assertEquals(Arrays.toString(tools), 1, tools.length);
		Assert.assertTrue(tools[0] instanceof SitePageTools);
	}

	@Test
	public void testGetToolsWhenNodeTypeIsAIDecision() {
		KaleoNode kaleoNode = Mockito.mock(KaleoNode.class);

		Mockito.when(
			kaleoNode.getType()
		).thenReturn(
			NodeType.AI_DECISION.name()
		);

		Object[] tools = ToolsUtil.getTools(
			RandomTestUtil.randomLong(), kaleoNode, new HashMap<>(),
			Mockito.mock(WorkflowNodeManager.class));

		Assert.assertEquals(Arrays.toString(tools), 1, tools.length);
		Assert.assertTrue(tools[0] instanceof WorkflowNodeTools);
	}

}