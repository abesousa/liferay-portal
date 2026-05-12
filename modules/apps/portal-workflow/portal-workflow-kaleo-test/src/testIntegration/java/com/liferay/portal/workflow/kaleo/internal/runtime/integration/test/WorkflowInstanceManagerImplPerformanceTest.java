/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.workflow.kaleo.internal.runtime.integration.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.security.auth.CompanyInheritableThreadLocalCallable;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.performance.PerformanceTimer;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.AssumeTestRule;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.PropertiesUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.kernel.workflow.WorkflowDefinition;
import com.liferay.portal.kernel.workflow.WorkflowInstanceManager;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.workflow.manager.WorkflowDefinitionManager;

import java.io.Closeable;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Feliphe Marinho
 */
@RunWith(Arquillian.class)
public class WorkflowInstanceManagerImplPerformanceTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new AssumeTestRule("assume"), new LiferayIntegrationTestRule());

	public static void assume() {
		Assume.assumeTrue(Validator.isNull(System.getenv("JENKINS_HOME")));
	}

	@BeforeClass
	public static void setUpClass() throws Exception {
		Class<?> clazz = WorkflowInstanceManagerImplPerformanceTest.class;

		_properties = PropertiesUtil.load(
			clazz.getResourceAsStream(
				"dependencies/workflow-instance-performance.properties"),
			"UTF-8");

		_workflowDefinitionStateNodeCount = GetterUtil.getInteger(
			_properties.getProperty("workflow.definition.state.node.count"));
		_workflowInstancesCount = GetterUtil.getInteger(
			_properties.getProperty("workflow.instances.count"));
		_workflowInstancesThreadCount = GetterUtil.getInteger(
			_properties.getProperty("workflow.instances.thread.count"));
	}

	@Test
	public void testStartWorkflowInstance() throws Exception {
		String workflowDefinitionName = RandomTestUtil.randomString();

		WorkflowDefinition workflowDefinition =
			_workflowDefinitionManager.deployWorkflowDefinition(
				_createWorkflowDefinitionBytes(),
				TestPropsValues.getCompanyId(), null, workflowDefinitionName,
				workflowDefinitionName, TestPropsValues.getUserId());

		ExecutorService executorService = Executors.newFixedThreadPool(
			_workflowInstancesThreadCount);

		try {
			List<Future<Void>> futures = new ArrayList<>(
				_workflowInstancesCount);

			try (Closeable closeable = new PerformanceTimer(
					GetterUtil.getInteger(
						_properties.getProperty(
							"workflow.instances.start.max.time")),
					StringBundler.concat(
						"Start ", _workflowInstancesCount,
						" workflow instances in parallel across ",
						_workflowInstancesThreadCount, " threads"))) {

				for (int i = 0; i < _workflowInstancesCount; i++) {
					futures.add(
						executorService.submit(
							new CompanyInheritableThreadLocalCallable<>(
								() -> {
									_workflowInstanceManager.
										startWorkflowInstance(
											TestPropsValues.getCompanyId(), 0,
											TestPropsValues.getUserId(),
											workflowDefinition.getName(),
											workflowDefinition.getVersion(),
											null,
											HashMapBuilder.
												<String, Serializable>put(
													WorkflowConstants.
														CONTEXT_SERVICE_CONTEXT,
													new ServiceContext()
												).build());

									return null;
								})));
				}

				for (Future<Void> future : futures) {
					future.get();
				}
			}
		}
		finally {
			executorService.shutdown();
		}
	}

	private JSONObject _createStateJSONObject(
		String name, String transitionTargetName) {

		return JSONUtil.put(
			"#child-nodes",
			JSONUtil.putAll(
				JSONUtil.put(
					"#tag-name", "name"
				).put(
					"#value", name
				),
				JSONUtil.put(
					"#tag-name", "description"
				).put(
					"#value", "Intermediate state."
				),
				JSONUtil.put(
					"#child-nodes",
					JSONUtil.putAll(
						JSONUtil.put(
							"#child-nodes",
							JSONUtil.putAll(
								JSONUtil.put(
									"#tag-name", "name"
								).put(
									"#value", "next"
								),
								JSONUtil.put(
									"#tag-name", "target"
								).put(
									"#value", transitionTargetName
								),
								JSONUtil.put(
									"#tag-name", "default"
								).put(
									"#value", "true"
								))
						).put(
							"#tag-name", "transition"
						))
				).put(
					"#tag-name", "transitions"
				))
		).put(
			"#tag-name", "state"
		);
	}

	private byte[] _createWorkflowDefinitionBytes() throws Exception {
		Class<?> clazz = getClass();

		ClassLoader classLoader = clazz.getClassLoader();

		byte[] bytes = FileUtil.getBytes(
			classLoader.getResourceAsStream(
				"com/liferay/portal/workflow/kaleo/dependencies" +
					"/multiple-state-nodes-workflow-definition.json"));

		if (_workflowDefinitionStateNodeCount <= 0) {
			return bytes;
		}

		JSONObject workflowDefinitionJSONObject = _jsonFactory.createJSONObject(
			new String(bytes));

		JSONArray childNodesJSONArray =
			workflowDefinitionJSONObject.getJSONArray("#child-nodes");

		_updateStartStateTransitionTarget(childNodesJSONArray, "state1");

		for (int i = 1; i <= _workflowDefinitionStateNodeCount; i++) {
			String nextStateName = "state" + (i + 1);

			if (i == _workflowDefinitionStateNodeCount) {
				nextStateName = "end";
			}

			childNodesJSONArray.put(
				_createStateJSONObject("state" + i, nextStateName));
		}

		String workflowDefinitionString =
			workflowDefinitionJSONObject.toString();

		return workflowDefinitionString.getBytes();
	}

	private void _updateStartStateTransitionTarget(
		JSONArray childNodesJSONArray, String targetName) {

		JSONObject startStateJSONObject = childNodesJSONArray.getJSONObject(2);

		JSONArray startStateChildNodesJSONArray =
			startStateJSONObject.getJSONArray("#child-nodes");

		for (int i = 0; i < startStateChildNodesJSONArray.length(); i++) {
			JSONObject childJSONObject =
				startStateChildNodesJSONArray.getJSONObject(i);

			String tagName = childJSONObject.getString("#tag-name");

			if (!tagName.equals("transitions")) {
				continue;
			}

			JSONArray transitionsChildNodesJSONArray =
				childJSONObject.getJSONArray("#child-nodes");

			JSONObject transitionJSONObject =
				transitionsChildNodesJSONArray.getJSONObject(0);

			JSONArray transitionChildNodesJSONArray =
				transitionJSONObject.getJSONArray("#child-nodes");

			for (int j = 0; j < transitionChildNodesJSONArray.length(); j++) {
				JSONObject transitionChildJSONObject =
					transitionChildNodesJSONArray.getJSONObject(j);

				String transitionChildTagName =
					transitionChildJSONObject.getString("#tag-name");

				if (transitionChildTagName.equals("target")) {
					transitionChildJSONObject.put("#value", targetName);

					return;
				}
			}
		}
	}

	private static Properties _properties;
	private static int _workflowDefinitionStateNodeCount;
	private static int _workflowInstancesCount;
	private static int _workflowInstancesThreadCount;

	@Inject
	private JSONFactory _jsonFactory;

	@Inject
	private WorkflowDefinitionManager _workflowDefinitionManager;

	@Inject
	private WorkflowInstanceManager _workflowInstanceManager;

}