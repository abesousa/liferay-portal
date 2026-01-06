/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.site.cms.site.initializer.internal.display.context;

import com.liferay.depot.service.DepotEntryLocalService;
import com.liferay.document.library.configuration.DLConfiguration;
import com.liferay.frontend.taglib.clay.servlet.taglib.util.CreationMenu;
import com.liferay.frontend.taglib.clay.servlet.taglib.util.DropdownItem;
import com.liferay.frontend.taglib.clay.servlet.taglib.util.DropdownItemBuilder;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntryFolder;
import com.liferay.object.service.ObjectDefinitionService;
import com.liferay.object.service.ObjectDefinitionSettingLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.model.GroupConstants;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.translation.exporter.TranslationInfoItemFieldValuesExporterRegistry;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Alberto Sousa
 */
public class ViewTeamsDisplayContext extends BaseSectionDisplayContext{

	public ViewTeamsDisplayContext(
		DepotEntryLocalService depotEntryLocalService,
		DLConfiguration dlConfiguration, GroupLocalService groupLocalService,
		HttpServletRequest httpServletRequest, Language language,
		ObjectDefinitionService objectDefinitionService,
		ObjectDefinitionSettingLocalService objectDefinitionSettingLocalService,
		ModelResourcePermission<ObjectEntryFolder>
			objectEntryFolderModelResourcePermission,
		Portal portal,
		TranslationInfoItemFieldValuesExporterRegistry
			translationInfoItemFieldValuesExporterRegistry) {

		super(
			depotEntryLocalService, dlConfiguration, groupLocalService,
			httpServletRequest, language, objectDefinitionService,
			objectDefinitionSettingLocalService,
			objectEntryFolderModelResourcePermission, portal,
			translationInfoItemFieldValuesExporterRegistry);

		try {
			ObjectDefinition objectDefinition =
				objectDefinitionService.getObjectDefinitionByExternalReferenceCode(
					"L_CMS_EXERCISE_TEAM", themeDisplay.getCompanyId());
			_objectDefinitionId = objectDefinition.getObjectDefinitionId();
		} catch (PortalException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Map<String, Object> getEmptyState() {
		return HashMapBuilder.<String, Object>put(
			"description",
			language.get(
				httpServletRequest,
				"click-new-to-create-your-first-piece-of-content")
		).put(
			"image", "/states/cms_empty_state_content.svg"
		).put(
			"title", language.get(httpServletRequest, "no-content-yet")
		).build();
	}


	@Override
	protected String getCMSSectionFilterString() {
		return appendStatus("cmsRoot eq true and objectDefinitionId eq " + _objectDefinitionId);
	}

	@Override
	public CreationMenu getCreationMenu(){
		return null;
	}

	@Override
	public List<DropdownItem> getCreationMenuDropdownItems() {
		return new ArrayList<>(
			List.of(
				DropdownItemBuilder.putData(
					"objectDefinitionId",
					String.valueOf(_objectDefinitionId)
				).putData(
					"action", "createAsset"
				).setHref(
					StringBundler.concat(
						themeDisplay.getPortalURL(),
						themeDisplay.getPathMain(),
						GroupConstants.CMS_FRIENDLY_URL,
						"/add_project?objectDefinitionId=",
						_objectDefinitionId,
						"&objectEntryFolderExternalReferenceCode=", "",
						"&plid=", themeDisplay.getPlid(), "&redirect=",
						themeDisplay.getURLCurrent())
				).setIcon(
					"forms"
				).setLabel(
					LanguageUtil.get(httpServletRequest, "project")
				).build()));
	}

	private long _objectDefinitionId = 0;
}
