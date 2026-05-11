/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.memory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Gabriel Albuquerque
 */
public class SessionVariablesUtil {

	public static String getVariable(String sseEventSinkKey, String name) {
		Map<String, String> variables = _variables.get(sseEventSinkKey);

		if (variables == null) {
			return null;
		}

		return variables.get(name);
	}

	public static void putVariable(
		String sseEventSinkKey, String name, String value) {

		Map<String, String> variables = _variables.computeIfAbsent(
			sseEventSinkKey, key -> new ConcurrentHashMap<>());

		variables.put(name, value);
	}

	private SessionVariablesUtil() {
	}

	private static final Map<String, Map<String, String>> _variables =
		new ConcurrentHashMap<>();

}