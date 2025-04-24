/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.reports.engine.console.internal.messaging.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.io.unsync.UnsyncByteArrayInputStream;
import com.liferay.portal.kernel.messaging.BaseMessageListener;
import com.liferay.portal.kernel.messaging.DestinationNames;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageListener;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.SynchronousDestinationTestRule;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.reports.engine.console.model.Definition;
import com.liferay.portal.reports.engine.console.service.DefinitionLocalServiceUtil;
import com.liferay.portal.reports.engine.console.service.EntryLocalServiceUtil;
import com.liferay.portal.reports.engine.console.service.test.EntryServiceTest;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import java.io.InputStream;

import java.util.Locale;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;

/**
 * @author Alberto Sousa
 */
@RunWith(Arquillian.class)
public class MailSentMessageListenerTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			SynchronousDestinationTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		setUpDefinition();
	}

	@Test
	public void testRun() throws Exception {
		Bundle bundle = FrameworkUtil.getBundle(
			MailSentMessageListenerTest.class);

		BundleContext bundleContext = bundle.getBundleContext();

		TestMessageListener testMessageListener = new TestMessageListener();

		ServiceRegistration<?> serviceRegistration =
			bundleContext.registerService(
				MessageListener.class, testMessageListener,
				MapUtil.singletonDictionary(
					"destination.name", DestinationNames.MAIL_SENT));

		Assert.assertEquals(0, testMessageListener.getCount());

		try {
			EntryLocalServiceUtil.addEntry(
				TestPropsValues.getUserId(), TestPropsValues.getGroupId(),
				_definition.getDefinitionId(), "txt", false, null, null, false,
				StringPool.BLANK, StringPool.BLANK,
				RandomTestUtil.randomString() + "@liferay.com",
				StringPool.BLANK, StringPool.BLANK,
				RandomTestUtil.randomString(), StringPool.BLANK,
				ServiceContextTestUtil.getServiceContext());

			Assert.assertEquals(1, testMessageListener.getCount());
		}
		finally {
			serviceRegistration.unregister();
		}
	}

	protected void setUpDefinition() throws Exception {
		try (InputStream inputStream =
				EntryServiceTest.class.getResourceAsStream(
					"dependencies/" + _TEMPLATE_NAME + ".jrxml")) {

			Map<Locale, String> nameMap = HashMapBuilder.put(
				LocaleUtil.US, RandomTestUtil.randomString()
			).build();

			String content = StringUtil.read(inputStream);

			_definition = DefinitionLocalServiceUtil.addDefinition(
				TestPropsValues.getUserId(), TestPropsValues.getGroupId(),
				nameMap, null, 0, null, _TEMPLATE_NAME,
				new UnsyncByteArrayInputStream(
					content.getBytes(StringPool.DEFAULT_CHARSET_NAME)),
				ServiceContextTestUtil.getServiceContext());
		}
	}

	private static final String _TEMPLATE_NAME =
		"reports_admin_template_source_sample_list_type";

	private Definition _definition;

	private class TestMessageListener extends BaseMessageListener {

		public int getCount() {
			return _count;
		}

		@Override
		protected void doReceive(Message message) {
			_count++;
		}

		private int _count;

	}

}