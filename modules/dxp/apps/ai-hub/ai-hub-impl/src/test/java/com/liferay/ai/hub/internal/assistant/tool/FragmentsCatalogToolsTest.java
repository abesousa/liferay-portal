/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.assistant.tool;

import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * @author Alberto Sousa
 */
public class FragmentsCatalogToolsTest {

	@ClassRule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() {
		_originalCatalogCache = ReflectionTestUtil.getAndSetFieldValue(
			FragmentsCatalogTools.class, "_catalogCache",
			new ConcurrentHashMap<String, String>());
	}

	@After
	public void tearDown() {
		ReflectionTestUtil.setFieldValue(
			FragmentsCatalogTools.class, "_catalogCache",
			_originalCatalogCache);
	}

	@Test
	public void testGenerateFragmentsCatalogReturnsCachedValue() {
		String siteExternalReferenceCode = RandomTestUtil.randomString();
		String cachedCatalog = RandomTestUtil.randomString();

		Map<String, String> catalogCache = ReflectionTestUtil.getFieldValue(
			FragmentsCatalogTools.class, "_catalogCache");

		catalogCache.put(siteExternalReferenceCode, cachedCatalog);

		FragmentsCatalogTools fragmentsCatalogTools = new FragmentsCatalogTools(
			RandomTestUtil.randomLong());

		Assert.assertEquals(
			cachedCatalog,
			fragmentsCatalogTools.generateFragmentsCatalog(
				siteExternalReferenceCode, RandomTestUtil.randomString()));
	}

	private Object _originalCatalogCache;

}