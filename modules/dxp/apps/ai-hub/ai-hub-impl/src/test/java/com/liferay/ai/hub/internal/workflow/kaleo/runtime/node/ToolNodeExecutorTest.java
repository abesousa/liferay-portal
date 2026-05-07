/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.workflow.kaleo.runtime.node;

import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import dev.langchain4j.agent.tool.Tool;

import java.io.Serializable;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * @author Alberto Sousa
 */
public class ToolNodeExecutorTest {

	@ClassRule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Test
	public void testInvokeToolWhenMethodIsMissing() {
		try {
			ReflectionTestUtil.invoke(
				new ToolNodeExecutor(), "_invokeTool",
				new Class<?>[] {
					Object[].class, String.class, Map.class, Map.class
				},
				new Object[] {new TestTool()}, "missingMethod", new HashMap<>(),
				new HashMap<String, Serializable>());

			Assert.fail();
		}
		catch (IllegalArgumentException illegalArgumentException) {
			Assert.assertEquals(
				"Tool method \"missingMethod\" not found",
				illegalArgumentException.getMessage());
		}
	}

	@Test
	public void testInvokeToolWhenResultIsNotNull() {
		Assert.assertEquals(
			"hello",
			ReflectionTestUtil.invoke(
				new ToolNodeExecutor(), "_invokeTool",
				new Class<?>[] {
					Object[].class, String.class, Map.class, Map.class
				},
				new Object[] {new TestTool()}, "greet", new HashMap<>(),
				new HashMap<String, Serializable>()));
	}

	@Test
	public void testInvokeToolWhenResultIsNull() {
		Assert.assertEquals(
			"",
			ReflectionTestUtil.invoke(
				new ToolNodeExecutor(), "_invokeTool",
				new Class<?>[] {
					Object[].class, String.class, Map.class, Map.class
				},
				new Object[] {new TestTool()}, "doNothing", new HashMap<>(),
				new HashMap<String, Serializable>()));
	}

	public static class TestTool {

		@Tool("Returns null")
		public String doNothing() {
			return null;
		}

		@Tool("Returns a greeting")
		public String greet() {
			return "hello";
		}

	}

}