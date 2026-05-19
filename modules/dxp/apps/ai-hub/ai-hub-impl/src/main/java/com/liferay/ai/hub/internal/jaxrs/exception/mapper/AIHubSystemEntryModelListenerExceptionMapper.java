/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.jaxrs.exception.mapper;

import com.liferay.ai.hub.internal.object.model.listener.AIHubSystemEntryModelListenerException;
import com.liferay.portal.vulcan.jaxrs.exception.mapper.BaseExceptionMapper;
import com.liferay.portal.vulcan.jaxrs.exception.mapper.Problem;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

import org.osgi.service.component.annotations.Component;

/**
 * @author Alberto Sousa
 */
@Component(
	property = {
		"osgi.jaxrs.application.select=(liferay.objects=true)",
		"osgi.jaxrs.extension=true",
		"osgi.jaxrs.name=Liferay.AIHub.SystemEntryModelListenerExceptionMapper"
	},
	service = ExceptionMapper.class
)
public class AIHubSystemEntryModelListenerExceptionMapper
	extends BaseExceptionMapper<AIHubSystemEntryModelListenerException> {

	@Override
	protected Problem getProblem(
		AIHubSystemEntryModelListenerException
			aiHubSystemEntryModelListenerException) {

		return new Problem(
			null, Response.Status.FORBIDDEN,
			aiHubSystemEntryModelListenerException.getMessage(),
			AIHubSystemEntryModelListenerException.class.getName());
	}

}