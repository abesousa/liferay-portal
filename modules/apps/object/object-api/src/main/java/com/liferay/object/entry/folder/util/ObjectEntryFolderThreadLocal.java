/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.object.entry.folder.util;

import com.liferay.petra.lang.CentralizedThreadLocal;
import com.liferay.petra.lang.SafeCloseable;

/**
 * @author Adolfo Pérez
 */
public class ObjectEntryFolderThreadLocal {

	public static boolean isForceDeleteSystemObjectEntryFolder() {
		return _forceDeleteSystemObjectEntryFolder.get();
	}

	public static SafeCloseable
		setForceDeleteSystemObjectEntryFolderWithSafeCloseable(
			boolean forceDeleteSystemObjectEntryFolder) {

		return _forceDeleteSystemObjectEntryFolder.setWithSafeCloseable(
			forceDeleteSystemObjectEntryFolder);
	}

	private static final CentralizedThreadLocal<Boolean>
		_forceDeleteSystemObjectEntryFolder = new CentralizedThreadLocal<>(
			ObjectEntryFolderThreadLocal.class +
				"._forceDeleteSystemObjectEntryFolder",
			() -> false);

}