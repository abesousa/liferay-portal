/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

export const GAP_INSIGHTS_AGENT = 'L_CONTENT_GAP_ANALYSIS';

export type GapInsightsStatus =
	| 'empty'
	| 'error'
	| 'idle'
	| 'loading'
	| 'ready';

export type GapSeverity = 'high' | 'low' | 'medium';

export interface Gap {
	currentCount: number;
	funnelStageId: string;
	funnelStageName: string;
	personaId: string;
	personaName: string;
	reason: string;
	severity: GapSeverity;
}

export interface GapInsightsSummary {
	funnelStageCount: number;
	gapCount: number;
	personaCount: number;
}

export interface GapAnalysisResult {
	gaps: Gap[];
	summary: GapInsightsSummary;
}

/**
 * The project matrix data handed to the agent. Each field maps to a declared
 * input variable of L_CONTENT_GAP_ANALYSIS (contentCoverage, focusScope,
 * projectContext) and is stringified before it is sent.
 */
export interface GapInsightsContext {
	contentCoverage: unknown;
	focusScope?: unknown;
	projectContext?: unknown;
}
