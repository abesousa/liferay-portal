/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.workflow.kaleo.runtime.integration.internal.instance.lifecycle;

import com.liferay.account.constants.AccountRoleConstants;
import com.liferay.portal.instance.lifecycle.BasePortalInstanceLifecycleListener;
import com.liferay.portal.instance.lifecycle.PortalInstanceLifecycleListener;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.service.ResourceActionLocalService;
import com.liferay.portal.kernel.service.ResourcePermissionLocalService;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.util.Arrays;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Alberto Sousa
 */
@Component(
	property = "service.ranking:Integer=100",
	service = PortalInstanceLifecycleListener.class
)
public class WorkflowAccountRolePortalInstanceLifecycleListener
	extends BasePortalInstanceLifecycleListener {

	@Override
	public void portalInstanceRegistered(Company company) throws Exception {
		Role role = _roleLocalService.fetchRole(
			company.getCompanyId(),
			AccountRoleConstants.REQUIRED_ROLE_NAME_ACCOUNT_ADMINISTRATOR);

		if (role == null) {
			return;
		}

		_resourceActionLocalService.checkResourceActions(
			WorkflowConstants.RESOURCE_NAME,
			Arrays.asList(ActionKeys.ADD_DEFINITION));

		_resourcePermissionLocalService.addResourcePermission(
			company.getCompanyId(), WorkflowConstants.RESOURCE_NAME,
			ResourceConstants.SCOPE_COMPANY,
			String.valueOf(company.getCompanyId()), role.getRoleId(),
			ActionKeys.ADD_DEFINITION);
	}

	@Reference
	private ResourceActionLocalService _resourceActionLocalService;

	@Reference
	private ResourcePermissionLocalService _resourcePermissionLocalService;

	@Reference
	private RoleLocalService _roleLocalService;

}