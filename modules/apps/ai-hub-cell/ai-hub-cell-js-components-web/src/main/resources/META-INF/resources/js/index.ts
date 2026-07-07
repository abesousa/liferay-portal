/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

export {default as AIAssistantChat} from './AIAssistantChat/AIAssistantChat';
export type {ChatContext} from './AIAssistantChat/api';
export {default as CategorizationSuggestions} from './Categorization/components/CategorizationSuggestions';
export {CATEGORIZE_EVENT, COMMIT_EVENT} from './Categorization/events';
export type {
	CategorizeEventPayload,
	CommitEventPayload,
} from './Categorization/events';
export {getCandidateCategories} from './Categorization/services/getCandidateCategories';
export {getExistingTags} from './Categorization/services/getExistingTags';
export {ECategorizationAgent} from './Categorization/types';
export type {
	CandidateCategory,
	CategorizationContext,
	CategorizationStatus,
	Suggestion,
} from './Categorization/types';
export {default as useCategorizationAgent} from './Categorization/useCategorizationAgent';
export {default as GapInsightsResults} from './GapInsights/components/GapInsightsResults';
export {
	GAP_INSIGHTS_EVENT,
	GAP_INSIGHTS_FIND_ASSETS_EVENT,
	GAP_INSIGHTS_GENERATE_CONTENT_EVENT,
} from './GapInsights/events';
export type {
	GapInsightsActionEventPayload,
	GapInsightsEventPayload,
} from './GapInsights/events';
export {GAP_INSIGHTS_AGENT} from './GapInsights/types';
export type {
	Gap,
	GapAnalysisResult,
	GapInsightsContext,
	GapInsightsStatus,
	GapInsightsSummary,
	GapSeverity,
} from './GapInsights/types';
export {default as useGapInsightsAgent} from './GapInsights/useGapInsightsAgent';
export {default as FeedbackActionsRow} from './ReportFeedback/FeedbackActionsRow';
export {default as ReportFeedbackModal} from './ReportFeedback/ReportFeedbackModal';
export type {
	ReportFeedbackPayload,
	ReportFeedbackReason,
	ReportFeedbackSurface,
} from './ReportFeedback/api';
export {default as submitPositiveReportFeedback} from './ReportFeedback/submitPositiveReportFeedback';
