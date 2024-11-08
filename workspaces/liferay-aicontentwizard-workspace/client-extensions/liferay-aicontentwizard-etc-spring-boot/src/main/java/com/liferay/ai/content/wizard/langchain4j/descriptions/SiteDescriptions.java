/**
 * SPDX-FileCopyrightText: (c) 2024 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.content.wizard.langchain4j.descriptions;

import dev.langchain4j.model.output.structured.Description;

/**
 * @author Keven Leone
 * @author Brian Wing Shun Chan
 */
public class SiteDescriptions {

	@Description(
		"Site ERC, if not specified by the user is auto generated UUID"
	)
	public String externalReferenceCode;

	@Description(
		"Membership type, value is lower case, default option is 'OPEN'"
	)
	public MembershipType membershipType;

	@Description("Site Name")
	public String name;

	@Description("Site Template Key, default is BLANK")
	public TemplateKey templateKey;

	public enum MembershipType {

		@Description(
			"Users can join and leave whenever they want. The site is visible to all users in the My Sites tab"
		)
		Open,
		@Description(
			"The site appears in the My Sites application, but users must request membership to join"
		)
		Private,
		@Description(
			"A site administrator must explicitly add users to the site. Private membership sites don’t appear in the My Sites app"
		)
		Restricted

	}

	public enum TemplateKey {

		BLANK(""), MASTERCLASS("com.liferay.site.initializer.masterclass"),
		MINIUM("minium-initializer"), MINIUM_FULL("minium-full-initializer"),
		RAYLIFE_AP("com.liferay.site.initializer.raylife.ap"),
		RAYLIFE_D2C("com.liferay.site.initializer.raylife.d2c"),
		SPEEDWELL("speedwell-initializer"),
		TEAM_EXTRANET("com.liferay.site.initializer.team.extranet"),
		WELCOME("com.liferay.site.initializer.welcome");

		public String toString() {
			return _string;
		}

		private TemplateKey(String string) {
			_string = string;
		}

		private final String _string;

	}

}