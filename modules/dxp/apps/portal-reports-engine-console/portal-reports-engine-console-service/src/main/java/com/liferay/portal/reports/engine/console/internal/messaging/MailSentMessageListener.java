/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.reports.engine.console.internal.messaging;

import com.liferay.mail.kernel.model.FileAttachment;
import com.liferay.mail.kernel.model.MailMessage;
import com.liferay.portal.kernel.messaging.BaseMessageListener;
import com.liferay.portal.kernel.messaging.Destination;
import com.liferay.portal.kernel.messaging.DestinationConfiguration;
import com.liferay.portal.kernel.messaging.DestinationFactory;
import com.liferay.portal.kernel.messaging.DestinationNames;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageListener;
import com.liferay.portal.kernel.util.MapUtil;

import java.io.File;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Alberto Sousa
 */
@Component(
	property = "destination.name=" + DestinationNames.MAIL_SENT,
	service = MessageListener.class
)
public class MailSentMessageListener extends BaseMessageListener {

	public static void registerFile(File file) {
		_files.add(file);
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		DestinationConfiguration destinationConfiguration =
			new DestinationConfiguration(
				DestinationConfiguration.DESTINATION_TYPE_SYNCHRONOUS,
				DestinationNames.MAIL_SENT);

		Destination destination = _destinationFactory.createDestination(
			destinationConfiguration);

		_serviceRegistration = bundleContext.registerService(
			Destination.class, destination,
			MapUtil.singletonDictionary(
				"destination.name", destination.getName()));
	}

	@Deactivate
	protected void deactivate() {
		_serviceRegistration.unregister();
	}

	@Override
	protected void doReceive(Message message) throws Exception {
		MailMessage mailMessage = (MailMessage)message.getPayload();

		List<FileAttachment> fileAttachments = mailMessage.getFileAttachments();

		for (FileAttachment fileAttachment : fileAttachments) {
			File file = fileAttachment.getFile();

			if (_files.contains(file)) {
				boolean deleted = file.delete();

				if (deleted) {
					_files.remove(file);
				}
			}
		}
	}

	private static final Set<File> _files = new HashSet<>();

	@Reference
	private DestinationFactory _destinationFactory;

	private ServiceRegistration<Destination> _serviceRegistration;

}