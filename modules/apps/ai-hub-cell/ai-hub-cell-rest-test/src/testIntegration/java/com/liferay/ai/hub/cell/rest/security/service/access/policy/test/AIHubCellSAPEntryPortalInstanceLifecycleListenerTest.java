/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.cell.rest.security.service.access.policy.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.portal.instance.lifecycle.PortalInstanceLifecycleListener;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.security.service.access.policy.model.SAPEntry;
import com.liferay.portal.security.service.access.policy.service.SAPEntryLocalService;
import com.liferay.portal.test.rule.FeatureFlag;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Alberto Sousa
 */
@FeatureFlag("LPD-62272")
@RunWith(Arquillian.class)
public class AIHubCellSAPEntryPortalInstanceLifecycleListenerTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		_company = _companyLocalService.getCompany(
			TestPropsValues.getCompanyId());

		_deleteSAPEntry();
	}

	@After
	public void tearDown() throws Exception {
		_deleteSAPEntry();
	}

	@Test
	public void testPortalInstanceRegisteredAddsEntry() throws Exception {
		Assert.assertNull(
			_sapEntryLocalService.fetchSAPEntry(
				_company.getCompanyId(), _SAP_ENTRY_NAME));

		_portalInstanceLifecycleListener.portalInstanceRegistered(_company);

		SAPEntry sapEntry = _sapEntryLocalService.fetchSAPEntry(
			_company.getCompanyId(), _SAP_ENTRY_NAME);

		String allowedServiceSignatures =
			sapEntry.getAllowedServiceSignatures();

		Assert.assertTrue(
			allowedServiceSignatures.contains(
				"AuthorizationTokenResourceImpl#postAuthorizationToken"));
		Assert.assertTrue(
			allowedServiceSignatures.contains(
				"ObjectEntryResourceImpl#getObjectEntry"));
		Assert.assertTrue(
			allowedServiceSignatures.contains(
				"ObjectEntryResourceImpl#postObjectEntry"));
		Assert.assertTrue(
			allowedServiceSignatures.contains(
				"PageSpecificationResourceImpl#getSitePageSpecification"));
		Assert.assertTrue(
			allowedServiceSignatures.contains(
				"PageSpecificationResourceImpl#putSitePageSpecification"));
		Assert.assertTrue(
			allowedServiceSignatures.contains(
				"SearchResultResourceImpl#getSearchPage"));
		Assert.assertTrue(
			allowedServiceSignatures.contains(
				"SitePageResourceImpl#getSiteSitePagePermissionsPage"));
	}

	@Test
	public void testPortalInstanceRegisteredRewritesStaleEntry()
		throws Exception {

		_portalInstanceLifecycleListener.portalInstanceRegistered(_company);

		SAPEntry sapEntry = _sapEntryLocalService.fetchSAPEntry(
			_company.getCompanyId(), _SAP_ENTRY_NAME);

		String staleSignatures = RandomTestUtil.randomString();

		sapEntry.setAllowedServiceSignatures(staleSignatures);

		_sapEntryLocalService.updateSAPEntry(sapEntry);

		_portalInstanceLifecycleListener.portalInstanceRegistered(_company);

		sapEntry = _sapEntryLocalService.fetchSAPEntry(
			_company.getCompanyId(), _SAP_ENTRY_NAME);

		String allowedServiceSignatures =
			sapEntry.getAllowedServiceSignatures();

		Assert.assertNotEquals(staleSignatures, allowedServiceSignatures);
		Assert.assertTrue(
			allowedServiceSignatures.contains(
				"AuthorizationTokenResourceImpl#postAuthorizationToken"));
	}

	private void _deleteSAPEntry() throws Exception {
		SAPEntry sapEntry = _sapEntryLocalService.fetchSAPEntry(
			_company.getCompanyId(), _SAP_ENTRY_NAME);

		if (sapEntry != null) {
			_sapEntryLocalService.deleteSAPEntry(sapEntry);
		}
	}

	private static final String _SAP_ENTRY_NAME = "AI_HUB_CELL_TOKEN";

	private Company _company;

	@Inject
	private CompanyLocalService _companyLocalService;

	@Inject(
		filter = "component.name=com.liferay.ai.hub.cell.internal.security.service.access.policy.AIHubCellSAPEntryPortalInstanceLifecycleListener"
	)
	private PortalInstanceLifecycleListener _portalInstanceLifecycleListener;

	@Inject
	private SAPEntryLocalService _sapEntryLocalService;

}