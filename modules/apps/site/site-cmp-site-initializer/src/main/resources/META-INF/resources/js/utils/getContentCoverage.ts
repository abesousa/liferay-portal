/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {fetch} from 'frontend-js-web';

interface TaxonomyTerm {
	externalReferenceCode: string | null;
	id: string;
	name: string;
	uncategorized?: boolean;
}

interface MatrixCell {
	funnelStageId: string;
	personaId: string;
	totalCount: number;
}

export interface ContentCoverage {
	cells: MatrixCell[];
	funnelStages: TaxonomyTerm[];
	personas: TaxonomyTerm[];
	totalAssetCount: number;
}

// Placeholder used until the /content-coverage endpoint (LPD-96935, PR #28)
// ships in this branch. The dimensions are the fixed CMP system vocabularies
// (L_CMP_PERSONAS / L_CMP_FUNNEL_STAGE); a few cells are filled so the agent
// returns a realistic mix of covered cells and gaps.

const MOCK_COVERAGE: ContentCoverage = {
	cells: [
		{funnelStageId: 'awareness', personaId: 'champion', totalCount: 4},
		{funnelStageId: 'consideration', personaId: 'champion', totalCount: 2},
		{funnelStageId: 'awareness', personaId: 'end-user', totalCount: 6},
		{funnelStageId: 'consideration', personaId: 'end-user', totalCount: 3},
		{funnelStageId: 'decision', personaId: 'end-user', totalCount: 1},
		{
			funnelStageId: 'consideration',
			personaId: 'decision-maker',
			totalCount: 2,
		},
		{funnelStageId: 'decision', personaId: 'decision-maker', totalCount: 5},
	],
	funnelStages: [
		{externalReferenceCode: null, id: 'awareness', name: 'Awareness'},
		{
			externalReferenceCode: null,
			id: 'consideration',
			name: 'Consideration',
		},
		{externalReferenceCode: null, id: 'decision', name: 'Decision'},
		{externalReferenceCode: null, id: 'retention', name: 'Retention'},
	],
	personas: [
		{externalReferenceCode: null, id: 'champion', name: 'Champion'},
		{
			externalReferenceCode: null,
			id: 'decision-maker',
			name: 'Decision Maker',
		},
		{externalReferenceCode: null, id: 'end-user', name: 'End User'},
		{
			externalReferenceCode: null,
			id: 'technical-evaluator',
			name: 'Technical Evaluator',
		},
	],
	totalAssetCount: 23,
};

export async function getContentCoverage({
	cmsGroupId,
	projectId,
}: {
	cmsGroupId: number | string;
	projectId: number | string;
}): Promise<ContentCoverage> {
	try {
		const response = await fetch(
			`/o/headless-cmp/v1.0/projects/${projectId}/content-coverage?cmsGroupId=${cmsGroupId}`
		);

		if (response.ok) {
			const data = await response.json();

			return {
				cells: (data.matrixCells ?? []).map((cell: MatrixCell) => ({
					funnelStageId: cell.funnelStageId,
					personaId: cell.personaId,
					totalCount: cell.totalCount,
				})),
				funnelStages: data.funnelStages ?? [],
				personas: data.personas ?? [],
				totalAssetCount: data.totalAssetCount ?? 0,
			};
		}
	}
	catch (error) {
		console.warn((error as Error).message);
	}

	console.warn(
		'[GapInsights] The content-coverage endpoint is unavailable; using ' +
			'placeholder coverage data.'
	);

	return MOCK_COVERAGE;
}
