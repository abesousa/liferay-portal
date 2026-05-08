/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.assistant.tool;

import com.liferay.ai.hub.internal.model.VertexAiGeminiUtil;
import com.liferay.petra.reflect.ReflectionUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Mahmoud Tayem
 */
public class CatalogBuilderTools {

	public CatalogBuilderTools(long companyId) {
		_companyId = companyId;
	}

	@Tool(
		"Build a descriptive fragment catalog from raw fragment data using an LLM"
	)
	public String buildCatalog(
		@P("Site external reference code") String siteExternalReferenceCode,
		@P("Raw fragments JSON") String rawFragments) {

		String cached = _catalogCache.get(siteExternalReferenceCode);

		if (cached != null) {
			if (_log.isInfoEnabled()) {
				_log.info(
					"Returning cached fragment catalog for site " +
						siteExternalReferenceCode);
			}

			return cached;
		}

		try {
			String catalog = _callLLM(rawFragments);

			if (Validator.isNotNull(catalog)) {
				_catalogCache.put(siteExternalReferenceCode, catalog);

				if (_log.isInfoEnabled()) {
					_log.info(
						"Generated and cached fragment catalog for site " +
							siteExternalReferenceCode);
				}
			}

			return catalog;
		}
		catch (Exception exception) {
			return ReflectionUtil.throwException(exception);
		}
	}

	private String _callLLM(String rawFragments) throws Exception {
		VertexAiGeminiStreamingChatModel model =
			VertexAiGeminiUtil.createVertexAiGeminiStreamingChatModel(
				_companyId);

		try {
			CatalogAssistant assistant = AiServices.builder(
				CatalogAssistant.class
			).streamingChatModel(
				model
			).systemMessageProvider(
				memoryId -> _CATALOG_BUILDER_PROMPT
			).build();

			CompletableFuture<String> future = new CompletableFuture<>();

			TokenStream tokenStream = assistant.buildCatalog(rawFragments);

			tokenStream.onCompleteResponse(
				(ChatResponse response) -> future.complete(
					response.aiMessage(
					).text())
			).onError(
				future::completeExceptionally
			).start();

			return future.get();
		}
		finally {
			model.close();
		}
	}

	private static final String _CATALOG_BUILDER_PROMPT = StringUtil.read(
		CatalogBuilderTools.class, "dependencies/CatalogBuilderTools.md");

	private static final Log _log = LogFactoryUtil.getLog(
		CatalogBuilderTools.class);

	private static final Map<String, String> _catalogCache =
		new ConcurrentHashMap<>();

	private final long _companyId;

	private interface CatalogAssistant {

		public TokenStream buildCatalog(@UserMessage String rawFragments);

	}

}