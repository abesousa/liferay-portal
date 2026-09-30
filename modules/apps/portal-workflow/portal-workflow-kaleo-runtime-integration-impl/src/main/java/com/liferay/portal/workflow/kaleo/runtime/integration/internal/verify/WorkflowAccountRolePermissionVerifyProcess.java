/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.workflow.kaleo.runtime.integration.internal.verify;

import com.liferay.account.constants.AccountRoleConstants;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.ResourceActionLocalService;
import com.liferay.portal.kernel.service.ResourcePermissionLocalService;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.util.LoggingTimer;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.verify.VerifyProcess;

import java.util.Arrays;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Alberto Sousa
 */
@Component(property = "initial.deployment=true", service = VerifyProcess.class)
public class WorkflowAccountRolePermissionVerifyProcess extends VerifyProcess {

	@Override
	protected void doVerify() throws Exception {
		try (LoggingTimer loggingTimer = new LoggingTimer()) {
			_companyLocalService.forEachCompanyId(
				companyId -> _checkResourcePermission(companyId));
		}
	}

	private void _checkResourcePermission(long companyId) throws Exception {
		Role role = _roleLocalService.fetchRole(
			companyId,
			AccountRoleConstants.REQUIRED_ROLE_NAME_ACCOUNT_ADMINISTRATOR);

		if (role == null) {
			return;
		}

		_resourceActionLocalService.checkResourceActions(
			WorkflowConstants.RESOURCE_NAME,
			Arrays.asList(ActionKeys.ADD_DEFINITION));

		_resourcePermissionLocalService.addResourcePermission(
			companyId, WorkflowConstants.RESOURCE_NAME,
			ResourceConstants.SCOPE_COMPANY, String.valueOf(companyId),
			role.getRoleId(), ActionKeys.ADD_DEFINITION);
	}

	@Reference
	private CompanyLocalService _companyLocalService;

	@Reference
	private ResourceActionLocalService _resourceActionLocalService;

	@Reference
	private ResourcePermissionLocalService _resourcePermissionLocalService;

	@Reference
	private RoleLocalService _roleLocalService;

}