/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.tools;

import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author Mahmoud Tayem
 */
public class IRToPageSpecTools {

	public IRToPageSpecTools(String fullFragmentsCatalog) {
		_customEditableTypes = _parseCustomEditableTypes(fullFragmentsCatalog);
		_customFragmentSources = _parseCustomFragmentSources(
			fullFragmentsCatalog);
	}

	@Tool(
		"Convert an IR JSON object into a Liferay ContentPageSpecification JSON"
	)
	public String convertToPageSpec(
		@P("IR JSON object") String irJSON,
		@P("Draft page specification ERC") String draftERC,
		@P("Draft page experience ERC") String experienceERC,
		@P("Locale code, e.g. en-US") String locale) {

		if ((locale == null) || locale.isEmpty()) {
			locale = "en-US";
		}

		try {
			JSONObject irJSONObject = JSONFactoryUtil.createJSONObject(
				_stripMarkdownFences(irJSON));

			JSONArray irElementsJSONArray = irJSONObject.getJSONArray(
				"elements");

			if (irElementsJSONArray == null) {
				irElementsJSONArray = JSONFactoryUtil.createJSONArray();
			}

			JSONArray pageElementsJSONArray = JSONFactoryUtil.createJSONArray();

			for (int i = 0; i < irElementsJSONArray.length(); i++) {
				JSONObject elementJSONObject = _convertElement(
					irElementsJSONArray.getJSONObject(i), locale, null, i);

				if (elementJSONObject != null) {
					pageElementsJSONArray.put(elementJSONObject);
				}
			}

			JSONArray pageExperiencesJSONArray = JSONUtil.put(
				JSONUtil.put(
					"externalReferenceCode", experienceERC
				).put(
					"key", "DEFAULT"
				).put(
					"name_i18n", _i18n(locale, "Default")
				).put(
					"pageElements", pageElementsJSONArray
				).put(
					"priority", 0
				));

			return JSONUtil.put(
				"customFields", JSONFactoryUtil.createJSONArray()
			).put(
				"externalReferenceCode", draftERC
			).put(
				"pageExperiences", pageExperiencesJSONArray
			).put(
				"status", "Draft"
			).put(
				"type", "ContentPageSpecification"
			).toString();
		}
		catch (Exception exception) {
			if (_log.isWarnEnabled()) {
				_log.warn(exception);
			}

			return "Error converting IR to page spec: " +
				exception.getMessage();
		}
	}

	private void _applySpacing(
			String defaultValue, String prefix, JSONObject sourceJSONObject,
			JSONObject targetJSONObject)
		throws Exception {

		JSONObject spacingJSONObject = sourceJSONObject.getJSONObject(prefix);

		String[] sides = {"Top", "Bottom", "Left", "Right"};

		for (String side : sides) {
			String value = null;

			if (spacingJSONObject != null) {
				value = spacingJSONObject.getString(
					StringUtil.toLowerCase(side));
			}

			if ((value == null) || value.isEmpty()) {
				value = defaultValue;
			}

			if (value != null) {
				targetJSONObject.put(prefix + side, value);
			}
		}
	}

	private void _applyStyleProperties(
		JSONObject sourceJSONObject, JSONObject styleJSONObject) {

		for (String colorKey : _colorStyleKeys) {
			String value = sourceJSONObject.getString(colorKey);

			if (Validator.isNotNull(value)) {
				styleJSONObject.put(colorKey, _normalizeColor(value));
			}
		}

		for (String key : _stringStyleKeys) {
			String value = sourceJSONObject.getString(key);

			if (Validator.isNotNull(value)) {
				styleJSONObject.put(key, value);
			}
		}

		if (sourceJSONObject.has("hidden")) {
			styleJSONObject.put(
				"hidden", sourceJSONObject.getBoolean("hidden"));
		}
	}

	private JSONObject _buildContainerLayout(JSONObject irJSONObject)
		throws Exception {

		JSONObject layoutJSONObject = JSONUtil.put(
			"align", "Center"
		).put(
			"contentDisplay", "Block"
		).put(
			"justify", "Center"
		).put(
			"widthType", "Fluid"
		);

		String contentDisplay = irJSONObject.getString("contentDisplay");

		if (Objects.equals(contentDisplay, "flex-row")) {
			layoutJSONObject.put("contentDisplay", "FlexRow");
		}
		else if (Objects.equals(contentDisplay, "flex-column")) {
			layoutJSONObject.put("contentDisplay", "FlexColumn");
		}

		String widthType = irJSONObject.getString("widthType");

		if (Objects.equals(widthType, "fixed")) {
			layoutJSONObject.put("widthType", "Fixed");
		}

		return layoutJSONObject;
	}

	private JSONObject _buildEditable(
			String id, String locale, Object raw, String type)
		throws Exception {

		if (Objects.equals(type, "text")) {
			return _buildTextEditable(id, locale, _toString(raw));
		}

		if (Objects.equals(type, "richText")) {
			return _buildRichTextEditable(id, locale, _toString(raw));
		}

		if (Objects.equals(type, "image")) {
			return _buildImageEditable(id, locale, raw);
		}

		if (Objects.equals(type, "link")) {
			return _buildLinkEditable(id, locale, raw);
		}

		return null;
	}

	private JSONArray _buildEditableElements(
			JSONObject contentJSONObject, String fragmentKey, String locale)
		throws Exception {

		JSONArray elementsJSONArray = JSONFactoryUtil.createJSONArray();

		Map<String, String> editableTypes = _editableTypes.get(fragmentKey);

		if (editableTypes == null) {
			editableTypes = _customEditableTypes.get(fragmentKey);
		}

		Iterator<String> iterator = contentJSONObject.keys();

		while (iterator.hasNext()) {
			String id = iterator.next();

			Object raw = contentJSONObject.get(id);

			String editableType = "text";

			if (editableTypes != null) {
				String mapped = editableTypes.get(id);

				if (mapped != null) {
					editableType = mapped;
				}
			}

			JSONObject editableJSONObject = _buildEditable(
				id, locale, raw, editableType);

			if (editableJSONObject != null) {
				elementsJSONArray.put(editableJSONObject);
			}
		}

		return elementsJSONArray;
	}

	private JSONObject _buildImageEditable(String id, String locale, Object raw)
		throws Exception {

		String url;

		if (raw instanceof JSONObject) {
			JSONObject rawJSONObject = (JSONObject)raw;

			url = rawJSONObject.getString("url");
		}
		else {
			url = String.valueOf(raw);
		}

		return JSONUtil.put(
			"fragmentEditableElementValue",
			JSONUtil.put(
				"fragmentImage",
				JSONUtil.put(
					"fragmentImageValue",
					JSONUtil.put(
						"type", "Direct"
					).put(
						"value_i18n",
						_i18nObject(
							locale,
							JSONUtil.put(
								"type", "URL"
							).put(
								"url", url
							))
					))
			).put(
				"type", "Image"
			)
		).put(
			"id", id
		);
	}

	private JSONObject _buildLinkEditable(String id, String locale, Object raw)
		throws Exception {

		String text = "Learn more";
		String url = "#";
		String target = "Self";

		if (raw instanceof JSONObject) {
			JSONObject linkJSONObject = (JSONObject)raw;

			text = linkJSONObject.getString("text", "Learn more");
			url = linkJSONObject.getString("url", "#");

			String rawTarget = linkJSONObject.getString("target");

			if ((rawTarget != null) && !rawTarget.isEmpty()) {
				target = _normalizeLinkTarget(rawTarget);
			}
		}
		else if (raw instanceof String) {
			url = (String)raw;
		}

		return JSONUtil.put(
			"fragmentEditableElementValue",
			JSONUtil.put(
				"fragmentLinkTextValue",
				JSONUtil.put(
					"fragmentEditableElementValueFragmentLink",
					JSONUtil.put(
						"fragmentLink",
						JSONUtil.put(
							"target", target
						).put(
							"value",
							JSONUtil.put(
								"type", "FragmentInlineValue"
							).put(
								"value_i18n", _i18n(locale, url)
							)
						))
				).put(
					"textFragmentValue",
					JSONUtil.put(
						"fragmentInlineValue",
						JSONUtil.put("value_i18n", _i18n(locale, text))
					).put(
						"type", "Inline"
					)
				)
			).put(
				"type", "Text"
			)
		).put(
			"id", id
		);
	}

	private JSONObject _buildRichTextEditable(
			String id, String locale, String value)
		throws Exception {

		return JSONUtil.put(
			"fragmentEditableElementValue",
			JSONUtil.put(
				"htmlFragmentValue",
				JSONUtil.put(
					"fragmentInlineValue",
					JSONUtil.put("value_i18n", _i18n(locale, value))
				).put(
					"type", "Inline"
				)
			).put(
				"type", "RichText"
			)
		).put(
			"id", id
		);
	}

	private JSONObject _buildTextEditable(
			String id, String locale, String value)
		throws Exception {

		return JSONUtil.put(
			"fragmentEditableElementValue",
			JSONUtil.put(
				"fragmentLinkTextValue",
				JSONUtil.put(
					"textFragmentValue",
					JSONUtil.put(
						"fragmentInlineValue",
						JSONUtil.put("value_i18n", _i18n(locale, value))
					).put(
						"type", "Inline"
					))
			).put(
				"type", "Text"
			)
		).put(
			"id", id
		);
	}

	private JSONObject _buildViewportStyle(JSONObject irJSONObject)
		throws Exception {

		JSONObject styleJSONObject = JSONFactoryUtil.createJSONObject();

		_applySpacing("5", "padding", irJSONObject, styleJSONObject);
		_applySpacing(null, "margin", irJSONObject, styleJSONObject);
		_applyStyleProperties(irJSONObject, styleJSONObject);

		JSONObject irStyleJSONObject = irJSONObject.getJSONObject("style");

		if (irStyleJSONObject != null) {
			_applySpacing(null, "padding", irStyleJSONObject, styleJSONObject);
			_applySpacing(null, "margin", irStyleJSONObject, styleJSONObject);
			_applyStyleProperties(irStyleJSONObject, styleJSONObject);
		}

		return styleJSONObject;
	}

	private JSONArray _convertChildren(
			JSONArray childrenJSONArray, String locale, String parentERC)
		throws Exception {

		JSONArray resultJSONArray = JSONFactoryUtil.createJSONArray();

		if (childrenJSONArray == null) {
			return resultJSONArray;
		}

		for (int i = 0; i < childrenJSONArray.length(); i++) {
			JSONObject convertedJSONObject = _convertElement(
				childrenJSONArray.getJSONObject(i), locale, parentERC, i);

			if (convertedJSONObject != null) {
				resultJSONArray.put(convertedJSONObject);
			}
		}

		return resultJSONArray;
	}

	private JSONObject _convertContainer(
			JSONObject irJSONObject, String locale, String parentERC,
			int position)
		throws Exception {

		String erc = irJSONObject.getString("erc");

		JSONObject definitionJSONObject = JSONUtil.put(
			"fragmentViewports",
			JSONUtil.put(
				JSONUtil.put(
					"fragmentViewportStyle", _buildViewportStyle(irJSONObject)
				).put(
					"id", "Desktop"
				))
		).put(
			"layout", _buildContainerLayout(irJSONObject)
		).put(
			"type", "Container"
		);

		JSONArray childrenJSONArray = _convertChildren(
			irJSONObject.getJSONArray("children"), locale, erc);

		JSONObject nodeJSONObject = JSONUtil.put(
			"externalReferenceCode", erc
		).put(
			"pageElementDefinition", definitionJSONObject
		).put(
			"pageElements", childrenJSONArray
		).put(
			"position", position
		);

		if (parentERC != null) {
			nodeJSONObject.put("parentExternalReferenceCode", parentERC);
		}

		return nodeJSONObject;
	}

	private JSONObject _convertElement(
			JSONObject irJSONObject, String locale, String parentERC,
			int position)
		throws Exception {

		String type = irJSONObject.getString("type");

		if (Objects.equals(type, "container")) {
			return _convertContainer(irJSONObject, locale, parentERC, position);
		}

		if (Objects.equals(type, "grid")) {
			return _convertGrid(irJSONObject, locale, parentERC, position);
		}

		if (Objects.equals(type, "fragment")) {
			return _convertFragment(irJSONObject, locale, parentERC, position);
		}

		return null;
	}

	private JSONObject _convertFragment(
			JSONObject irJSONObject, String locale, String parentERC,
			int position)
		throws Exception {

		String erc = irJSONObject.getString("erc");

		JSONObject fragmentReferenceJSONObject =
			JSONFactoryUtil.createJSONObject();

		String source = irJSONObject.getString("source");

		if (Objects.equals(source, "ootb")) {
			String key = irJSONObject.getString("key");

			String fragmentKey = _ootbNameToKey.get(key);

			if (fragmentKey == null) {
				fragmentKey = key;
			}

			fragmentReferenceJSONObject.put(
				"defaultFragmentKey", fragmentKey
			).put(
				"fragmentReferenceType", "DefaultFragmentReference"
			);
		}
		else if (Objects.equals(source, "custom")) {
			fragmentReferenceJSONObject.put(
				"externalReferenceCode", irJSONObject.getString("key")
			).put(
				"fragmentReferenceType", "FragmentItemExternalReference"
			);
		}

		JSONObject instanceJSONObject = JSONUtil.put(
			"fragmentInstanceExternalReferenceCode", erc + "-inst"
		).put(
			"fragmentReference", fragmentReferenceJSONObject
		).put(
			"indexed", true
		);

		JSONObject contentJSONObject = irJSONObject.getJSONObject("content");

		if (contentJSONObject != null) {
			String fragmentKey = null;

			if (Objects.equals(source, "ootb")) {
				String key = irJSONObject.getString("key");

				fragmentKey = _ootbNameToKey.get(key);

				if (fragmentKey == null) {
					fragmentKey = key;
				}
			}
			else if (Objects.equals(source, "custom")) {
				fragmentKey = irJSONObject.getString("key");
			}

			JSONArray editableElementsJSONArray = _buildEditableElements(
				contentJSONObject, fragmentKey, locale);

			if (editableElementsJSONArray.length() > 0) {
				instanceJSONObject.put(
					"fragmentEditableElements", editableElementsJSONArray);
			}
		}

		JSONObject irStyleJSONObject = irJSONObject.getJSONObject("style");

		if (irStyleJSONObject != null) {
			JSONArray fragmentViewportsJSONArray = JSONUtil.put(
				JSONUtil.put(
					"fragmentViewportStyle", _buildViewportStyle(irJSONObject)
				).put(
					"id", "Desktop"
				));

			instanceJSONObject.put(
				"fragmentViewports", fragmentViewportsJSONArray);
		}

		if (Objects.equals(source, "custom")) {
			String fragmentKey = irJSONObject.getString("key");

			JSONObject fragmentSourcesJSONObject = _customFragmentSources.get(
				fragmentKey);

			if (fragmentSourcesJSONObject != null) {
				String configuration = fragmentSourcesJSONObject.getString(
					"configuration");

				if (Validator.isNotNull(configuration)) {
					instanceJSONObject.put("configuration", configuration);
				}

				String css = fragmentSourcesJSONObject.getString("css");

				if (Validator.isNotNull(css)) {
					instanceJSONObject.put("css", css);
				}

				String html = fragmentSourcesJSONObject.getString("html");

				if (Validator.isNotNull(html)) {
					instanceJSONObject.put("html", html);
				}

				String js = fragmentSourcesJSONObject.getString("js");

				if (Validator.isNotNull(js)) {
					instanceJSONObject.put("js", js);
				}
			}
		}

		JSONObject nodeJSONObject = JSONUtil.put(
			"externalReferenceCode", erc
		).put(
			"pageElementDefinition",
			JSONUtil.put(
				"fragmentInstance", instanceJSONObject
			).put(
				"type", "BasicFragment"
			)
		).put(
			"pageElements", JSONFactoryUtil.createJSONArray()
		).put(
			"position", position
		);

		if (parentERC != null) {
			nodeJSONObject.put("parentExternalReferenceCode", parentERC);
		}

		return nodeJSONObject;
	}

	private JSONObject _convertGrid(
			JSONObject irJSONObject, String locale, String parentERC,
			int position)
		throws Exception {

		String erc = irJSONObject.getString("erc");

		JSONArray columnsJSONArray = irJSONObject.getJSONArray("columns");

		if (columnsJSONArray == null) {
			columnsJSONArray = JSONFactoryUtil.createJSONArray();
		}

		int columnCount = columnsJSONArray.length();

		boolean gutters = irJSONObject.getBoolean("gutters", true);

		JSONObject modulesPerRowJSONObject = irJSONObject.getJSONObject(
			"modulesPerRow");

		int desktopPerRow = columnCount;
		int tabletPerRow = 0;
		int mobilePerRow = 0;

		if (modulesPerRowJSONObject != null) {
			desktopPerRow = modulesPerRowJSONObject.getInt(
				"desktop", columnCount);
			tabletPerRow = modulesPerRowJSONObject.getInt("tablet", 0);
			mobilePerRow = modulesPerRowJSONObject.getInt("mobile", 0);
		}

		JSONArray gridViewportsJSONArray = JSONUtil.put(
			JSONUtil.put(
				"gridViewportDefinition",
				JSONUtil.put(
					"modulesPerRow", desktopPerRow
				).put(
					"verticalAlignment", "Top"
				)
			).put(
				"id", "Desktop"
			));

		if (tabletPerRow > 0) {
			gridViewportsJSONArray.put(
				JSONUtil.put(
					"gridViewportDefinition",
					JSONUtil.put(
						"modulesPerRow", tabletPerRow
					).put(
						"verticalAlignment", "Top"
					)
				).put(
					"id", "Tablet"
				));
		}

		if (mobilePerRow > 0) {
			gridViewportsJSONArray.put(
				JSONUtil.put(
					"gridViewportDefinition",
					JSONUtil.put(
						"modulesPerRow", mobilePerRow
					).put(
						"verticalAlignment", "Top"
					)
				).put(
					"id", "PortraitMobile"
				));
		}

		JSONObject definitionJSONObject = JSONUtil.put(
			"gridViewports", gridViewportsJSONArray
		).put(
			"gutters", gutters
		).put(
			"numberOfModules", columnCount
		).put(
			"type", "Grid"
		);

		JSONArray moduleElementsJSONArray = JSONFactoryUtil.createJSONArray();

		for (int i = 0; i < columnCount; i++) {
			JSONObject colJSONObject = columnsJSONArray.getJSONObject(i);

			moduleElementsJSONArray.put(
				_convertModule(colJSONObject, columnCount, erc, locale, i));
		}

		JSONObject nodeJSONObject = JSONUtil.put(
			"externalReferenceCode", erc
		).put(
			"pageElementDefinition", definitionJSONObject
		).put(
			"pageElements", moduleElementsJSONArray
		).put(
			"position", position
		);

		if (parentERC != null) {
			nodeJSONObject.put("parentExternalReferenceCode", parentERC);
		}

		return nodeJSONObject;
	}

	private JSONObject _convertModule(
			JSONObject colJSONObject, int columnCount, String gridERC,
			String locale, int position)
		throws Exception {

		int defaultSize = 6;

		if (columnCount > 0) {
			defaultSize = 12 / columnCount;
		}

		int size = colJSONObject.getInt("size", defaultSize);

		String colERC = StringBundler.concat(
			"mod-", gridERC, "-", position + 1);

		JSONArray moduleViewportsJSONArray = JSONUtil.put(
			JSONUtil.put(
				"id", "Desktop"
			).put(
				"moduleViewportDefinition", JSONUtil.put("size", size)
			));

		JSONObject definitionJSONObject = JSONUtil.put(
			"moduleViewports", moduleViewportsJSONArray
		).put(
			"type", "Module"
		);

		JSONArray childrenJSONArray = _convertChildren(
			colJSONObject.getJSONArray("children"), locale, colERC);

		return JSONUtil.put(
			"externalReferenceCode", colERC
		).put(
			"pageElementDefinition", definitionJSONObject
		).put(
			"pageElements", childrenJSONArray
		).put(
			"parentExternalReferenceCode", gridERC
		).put(
			"position", position
		);
	}

	private JSONObject _i18n(String locale, String value) throws Exception {
		return JSONUtil.put(locale, value);
	}

	private JSONObject _i18nObject(String locale, JSONObject valueJSONObject)
		throws Exception {

		return JSONUtil.put(locale, valueJSONObject);
	}

	private String _normalizeColor(String color) {
		if ((color != null) && !color.endsWith("Color")) {
			return color + "Color";
		}

		return color;
	}

	private String _normalizeLinkTarget(String target) {
		if (target == null) {
			return "Self";
		}

		String cleaned = StringUtil.toLowerCase(target.replaceFirst("^_", ""));

		if (Objects.equals(cleaned, "blank")) {
			return "Blank";
		}

		if (Objects.equals(cleaned, "parent")) {
			return "Parent";
		}

		if (Objects.equals(cleaned, "top")) {
			return "Top";
		}

		return "Self";
	}

	private Map<String, Map<String, String>> _parseCustomEditableTypes(
		String fullFragmentsCatalog) {

		Map<String, Map<String, String>> result = new HashMap<>();

		if (Validator.isNull(fullFragmentsCatalog)) {
			return result;
		}

		try {
			JSONArray catalogJSONArray = JSONFactoryUtil.createJSONArray(
				fullFragmentsCatalog);

			for (int i = 0; i < catalogJSONArray.length(); i++) {
				JSONObject fragmentJSONObject = catalogJSONArray.getJSONObject(
					i);

				JSONArray editablesJSONArray = fragmentJSONObject.getJSONArray(
					"editables");

				if ((editablesJSONArray == null) ||
					(editablesJSONArray.length() == 0)) {

					continue;
				}

				String erc = fragmentJSONObject.getString(
					"externalReferenceCode");

				Map<String, String> editableMap = new HashMap<>();

				for (int j = 0; j < editablesJSONArray.length(); j++) {
					JSONObject editableJSONObject =
						editablesJSONArray.getJSONObject(j);

					editableMap.put(
						editableJSONObject.getString("id"),
						editableJSONObject.getString("type"));
				}

				result.put(erc, editableMap);
			}
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}
		}

		return result;
	}

	private Map<String, JSONObject> _parseCustomFragmentSources(
		String fullFragmentsCatalog) {

		Map<String, JSONObject> result = new HashMap<>();

		if (Validator.isNull(fullFragmentsCatalog)) {
			return result;
		}

		try {
			JSONArray catalogJSONArray = JSONFactoryUtil.createJSONArray(
				fullFragmentsCatalog);

			for (int i = 0; i < catalogJSONArray.length(); i++) {
				JSONObject fragmentJSONObject = catalogJSONArray.getJSONObject(
					i);

				result.put(
					fragmentJSONObject.getString("externalReferenceCode"),
					JSONUtil.put(
						"configuration",
						fragmentJSONObject.getString("configuration")
					).put(
						"css", fragmentJSONObject.getString("css")
					).put(
						"html", fragmentJSONObject.getString("html")
					).put(
						"js", fragmentJSONObject.getString("js")
					));
			}
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(exception);
			}
		}

		return result;
	}

	private String _stripMarkdownFences(String input) {
		if (input == null) {
			return input;
		}

		input = input.trim();

		if (input.startsWith("```")) {
			int index = input.indexOf('\n');

			if (index > 0) {
				input = input.substring(index + 1);
			}
			else {
				input = input.substring(3);
			}
		}

		if (input.endsWith("```")) {
			input = input.substring(0, input.length() - 3);
		}

		return input.trim();
	}

	private String _toString(Object value) {
		if (value instanceof String) {
			return (String)value;
		}

		if (value instanceof JSONObject) {
			JSONObject jsonObject = (JSONObject)value;

			if (jsonObject.has("text")) {
				return jsonObject.getString("text");
			}

			if (jsonObject.has("value")) {
				return jsonObject.getString("value");
			}

			if (jsonObject.has("content")) {
				return jsonObject.getString("content");
			}
		}

		return String.valueOf(value);
	}

	private static final Log _log = LogFactoryUtil.getLog(
		IRToPageSpecTools.class);

	private static final Set<String> _colorStyleKeys = Set.of(
		"backgroundColor", "borderColor", "textColor");
	private static final Map<String, Map<String, String>> _editableTypes =
		HashMapBuilder.<String, Map<String, String>>put(
			"BASIC_COMPONENT-button",
			Map.of("element-text", "text", "element-link", "link")
		).put(
			"BASIC_COMPONENT-card",
			Map.of(
				"01-img", "image", "02-title", "richText", "03-content",
				"richText", "04-link", "link")
		).put(
			"BASIC_COMPONENT-heading", Map.of("element-text", "text")
		).put(
			"BASIC_COMPONENT-image", Map.of("image-square", "image")
		).put(
			"BASIC_COMPONENT-paragraph", Map.of("element-text", "richText")
		).put(
			"BASIC_COMPONENT-video", Map.of("element-video", "link")
		).build();
	private static final Map<String, String> _ootbNameToKey =
		HashMapBuilder.put(
			"button", "BASIC_COMPONENT-button"
		).put(
			"card", "BASIC_COMPONENT-card"
		).put(
			"heading", "BASIC_COMPONENT-heading"
		).put(
			"image", "BASIC_COMPONENT-image"
		).put(
			"paragraph", "BASIC_COMPONENT-paragraph"
		).put(
			"separator", "BASIC_COMPONENT-separator"
		).put(
			"spacer", "BASIC_COMPONENT-spacer"
		).put(
			"video", "BASIC_COMPONENT-video"
		).build();
	private static final Set<String> _stringStyleKeys = Set.of(
		"borderRadius", "borderWidth", "fontFamily", "fontSize", "fontWeight",
		"height", "maxHeight", "maxWidth", "minHeight", "minWidth", "opacity",
		"overflow", "shadow", "textAlign", "width");

	private final Map<String, Map<String, String>> _customEditableTypes;
	private final Map<String, JSONObject> _customFragmentSources;

}