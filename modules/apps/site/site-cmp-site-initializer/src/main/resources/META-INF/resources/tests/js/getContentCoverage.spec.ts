/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {fetch} from 'frontend-js-web';

import {getContentCoverage} from '../../js/utils/getContentCoverage';

const mockFetch = fetch as jest.Mock;

describe('getContentCoverage', () => {
	beforeEach(() => {
		mockFetch.mockReset();
	});

	it('maps the content-coverage endpoint response into the view model', async () => {
		mockFetch.mockResolvedValueOnce({
			json: async () => ({
				funnelStages: [{id: 'f1', name: 'Awareness'}],
				matrixCells: [
					{funnelStageId: 'f1', personaId: 'p1', totalCount: 5},
				],
				personas: [{id: 'p1', name: 'Champion'}],
				totalAssetCount: 5,
			}),
			ok: true,
		});

		const coverage = await getContentCoverage({
			cmsGroupId: 20121,
			projectId: 39398,
		});

		expect(coverage.cells).toEqual([
			{funnelStageId: 'f1', personaId: 'p1', totalCount: 5},
		]);
		expect(coverage.personas).toEqual([{id: 'p1', name: 'Champion'}]);
		expect(coverage.funnelStages).toEqual([{id: 'f1', name: 'Awareness'}]);
		expect(coverage.totalAssetCount).toBe(5);
	});

	it('requests the coverage endpoint scoped to the project and CMS group', async () => {
		mockFetch.mockResolvedValueOnce({json: async () => ({}), ok: true});

		await getContentCoverage({cmsGroupId: 20121, projectId: 39398});

		expect(mockFetch).toHaveBeenCalledWith(
			'/o/headless-cmp/v1.0/projects/39398/content-coverage?cmsGroupId=20121'
		);
	});

	it('falls back to placeholder coverage when the endpoint is unavailable', async () => {
		mockFetch.mockResolvedValueOnce({json: async () => ({}), ok: false});

		const coverage = await getContentCoverage({
			cmsGroupId: 20121,
			projectId: 39398,
		});

		expect(coverage.totalAssetCount).toBe(23);
		expect(coverage.personas).toHaveLength(4);
		expect(coverage.funnelStages).toHaveLength(4);
	});

	it('falls back to placeholder coverage when the request throws', async () => {
		mockFetch.mockRejectedValueOnce(new Error('network down'));

		const coverage = await getContentCoverage({
			cmsGroupId: 20121,
			projectId: 39398,
		});

		expect(coverage.totalAssetCount).toBe(23);
	});
});
