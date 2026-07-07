/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {parseGapAnalysis} from '../../../../src/main/resources/META-INF/resources/js/GapInsights/utils/parseGapAnalysis';

const EMPTY = {
	gaps: [],
	summary: {funnelStageCount: 0, gapCount: 0, personaCount: 0},
};

describe('parseGapAnalysis', () => {
	it('parses plain JSON into gaps and summary', () => {
		const result = parseGapAnalysis(
			JSON.stringify({
				gaps: [
					{
						currentCount: 0,
						funnelStageId: 'f1',
						funnelStageName: 'Awareness',
						personaId: 'p1',
						personaName: 'Decision Makers',
						reason: 'No awareness content.',
						severity: 'high',
					},
				],
				summary: {funnelStageCount: 4, gapCount: 1, personaCount: 4},
			})
		);

		expect(result.summary).toEqual({
			funnelStageCount: 4,
			gapCount: 1,
			personaCount: 4,
		});
		expect(result.gaps).toHaveLength(1);
		expect(result.gaps[0]).toMatchObject({
			currentCount: 0,
			funnelStageName: 'Awareness',
			personaName: 'Decision Makers',
			reason: 'No awareness content.',
			severity: 'high',
		});
	});

	it('strips the ```json fences gemini-2.5-flash adds before parsing', () => {
		const result = parseGapAnalysis(
			'```json\n{"gaps":[],"summary":{"gapCount":0}}\n```'
		);

		expect(result.summary.gapCount).toBe(0);
	});

	it('strips bare ``` fences before parsing', () => {
		const result = parseGapAnalysis(
			'```\n{"gaps":[{"personaName":"P","funnelStageName":"F"}]}\n```'
		);

		expect(result.gaps).toHaveLength(1);
	});

	it('drops gaps that are missing a persona or funnel stage name', () => {
		const result = parseGapAnalysis(
			JSON.stringify({
				gaps: [
					{funnelStageName: 'F', personaName: 'P'},
					{funnelStageName: 'F', personaName: ''},
					{funnelStageName: 'F'},
					{personaName: 'P'},
				],
			})
		);

		expect(result.gaps).toHaveLength(1);
	});

	it('defaults an unrecognized severity to medium', () => {
		const result = parseGapAnalysis(
			JSON.stringify({
				gaps: [
					{
						funnelStageName: 'F',
						personaName: 'P',
						severity: 'catastrophic',
					},
				],
			})
		);

		expect(result.gaps[0].severity).toBe('medium');
	});

	it('coerces a non-numeric current count to zero', () => {
		const result = parseGapAnalysis(
			JSON.stringify({
				gaps: [
					{
						currentCount: 'lots',
						funnelStageName: 'F',
						personaName: 'P',
					},
				],
			})
		);

		expect(result.gaps[0].currentCount).toBe(0);
	});

	it('falls back gapCount to the gap total when the summary omits it', () => {
		const result = parseGapAnalysis(
			JSON.stringify({
				gaps: [
					{funnelStageName: 'F', personaName: 'P'},
					{funnelStageName: 'G', personaName: 'Q'},
				],
			})
		);

		expect(result.summary.gapCount).toBe(2);
	});

	it('returns an empty result for text that is not JSON', () => {
		expect(parseGapAnalysis('I cannot fulfill this request.')).toEqual(
			EMPTY
		);
	});

	it('returns an empty result for a JSON non-object', () => {
		expect(parseGapAnalysis('null')).toEqual(EMPTY);
	});
});
