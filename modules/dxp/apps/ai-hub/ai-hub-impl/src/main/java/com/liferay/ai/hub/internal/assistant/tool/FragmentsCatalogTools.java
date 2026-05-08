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
import dev.langchain4j.data.message.AiMessage;
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
public class FragmentsCatalogTools {

	public FragmentsCatalogTools(long companyId) {
		_companyId = companyId;
	}

	@Tool(
		"Build a descriptive fragment catalog from raw fragment data using an LLM"
	)
	public String generateFragmentsCatalog(
		@P("Site external reference code") String siteExternalReferenceCode,
		@P("Raw fragments JSON") String fragmentsJSON) {

		String cachedCatalog = _catalogCache.get(siteExternalReferenceCode);

		if (cachedCatalog != null) {
			if (_log.isInfoEnabled()) {
				_log.info(
					"Returning cached fragment catalog for site " +
						siteExternalReferenceCode);
			}

			return cachedCatalog;
		}

		try {
			String generatedCatalog = _callLLM(fragmentsJSON);

			if (Validator.isNotNull(generatedCatalog)) {
				_catalogCache.put(siteExternalReferenceCode, generatedCatalog);

				if (_log.isInfoEnabled()) {
					_log.info(
						"Generated and cached fragment catalog for site " +
							siteExternalReferenceCode);
				}
			}

			return generatedCatalog;
		}
		catch (Exception exception) {
			return ReflectionUtil.throwException(exception);
		}
	}

	private String _callLLM(String fragmentsJSON) throws Exception {
		VertexAiGeminiStreamingChatModel vertexAiGeminiStreamingChatModel =
			VertexAiGeminiUtil.createVertexAiGeminiStreamingChatModel(
				_companyId);

		try {
			CatalogAssistant catalogAssistant = AiServices.builder(
				CatalogAssistant.class
			).streamingChatModel(
				vertexAiGeminiStreamingChatModel
			).systemMessageProvider(
				memoryId -> _FRAGMENTS_CATALOG_PROMPT
			).build();

			CompletableFuture<String> completableFuture =
				new CompletableFuture<>();

			TokenStream tokenStream = catalogAssistant.generateFragmentsCatalog(
				fragmentsJSON);

			tokenStream.onCompleteResponse(
				(ChatResponse response) -> {
					AiMessage aiMessage = response.aiMessage();

					completableFuture.complete(aiMessage.text());
				}
			).onError(
				completableFuture::completeExceptionally
			).start();

			return completableFuture.get();
		}
		finally {
			vertexAiGeminiStreamingChatModel.close();
		}
	}

	private static final String _FRAGMENTS_CATALOG_PROMPT = StringUtil.read(
		FragmentsCatalogTools.class, "dependencies/FragmentsCatalogPrompt.md");

	private static final Log _log = LogFactoryUtil.getLog(
		FragmentsCatalogTools.class);

	private static final Map<String, String> _catalogCache =
		new ConcurrentHashMap<>();

	private final long _companyId;

	private interface CatalogAssistant {

		public TokenStream generateFragmentsCatalog(
			@UserMessage String fragmentsJSON);

	}

}