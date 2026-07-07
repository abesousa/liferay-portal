/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {Gap, GapAnalysisResult, GapSeverity} from '../types';

const SEVERITIES: GapSeverity[] = ['high', 'low', 'medium'];

const EMPTY_RESULT: GapAnalysisResult = {
	gaps: [],
	summary: {funnelStageCount: 0, gapCount: 0, personaCount: 0},
};

/**
 * gemini-2.5-flash intermittently wraps its JSON in ```json fences despite the
 * prompt forbidding it, so strip them defensively before parsing.
 */
function stripCodeFences(text: string): string {
	const trimmed = text.trim();

	const match = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/i);

	return match ? match[1].trim() : trimmed;
}

function toSeverity(value: unknown): GapSeverity {
	const severity = String(value).toLowerCase();

	return SEVERITIES.includes(severity as GapSeverity)
		? (severity as GapSeverity)
		: 'medium';
}

function toGap(rawGap: Record<string, unknown>): Gap | null {
	const personaName = String(rawGap.personaName ?? '').trim();
	const funnelStageName = String(rawGap.funnelStageName ?? '').trim();

	if (!personaName || !funnelStageName) {
		return null;
	}

	const currentCount = Number(rawGap.currentCount);

	return {
		currentCount: Number.isFinite(currentCount) ? currentCount : 0,
		funnelStageId: String(rawGap.funnelStageId ?? ''),
		funnelStageName,
		personaId: String(rawGap.personaId ?? ''),
		personaName,
		reason: String(rawGap.reason ?? '').trim(),
		severity: toSeverity(rawGap.severity),
	};
}

export function parseGapAnalysis(data: string): GapAnalysisResult {
	let parsed: {gaps?: unknown; summary?: Record<string, unknown>};

	try {
		parsed = JSON.parse(stripCodeFences(data));
	}
	catch {
		return EMPTY_RESULT;
	}

	if (!parsed || typeof parsed !== 'object') {
		return EMPTY_RESULT;
	}

	const gaps = (Array.isArray(parsed.gaps) ? parsed.gaps : [])
		.map((rawGap) => toGap(rawGap as Record<string, unknown>))
		.filter((gap): gap is Gap => gap !== null);

	const summary = parsed.summary ?? {};

	return {
		gaps,
		summary: {
			funnelStageCount: Number(summary.funnelStageCount) || 0,
			gapCount: Number(summary.gapCount) || gaps.length,
			personaCount: Number(summary.personaCount) || 0,
		},
	};
}
