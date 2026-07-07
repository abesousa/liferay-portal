/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import '@testing-library/jest-dom';
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import GapInsightsResults from '../../../../src/main/resources/META-INF/resources/js/GapInsights/components/GapInsightsResults';

const GAP = {
	currentCount: 0,
	funnelStageId: 'f1',
	funnelStageName: 'Awareness',
	personaId: 'p1',
	personaName: 'Decision Makers',
	reason: 'No awareness content.',
	severity: 'high' as const,
};

const RESULT = {
	gaps: [GAP],
	summary: {funnelStageCount: 4, gapCount: 1, personaCount: 4},
};

function renderResults(props = {}) {
	return render(
		<GapInsightsResults
			onDismiss={jest.fn()}
			onFindAssets={jest.fn()}
			onGenerateContent={jest.fn()}
			onRegenerate={jest.fn()}
			result={RESULT}
			status="ready"
			{...props}
		/>
	);
}

describe('GapInsightsResults', () => {
	it('renders nothing while idle', () => {
		const {container} = renderResults({result: null, status: 'idle'});

		expect(container).toBeEmptyDOMElement();
	});

	it('shows the empty-state message when there are no gaps', () => {
		renderResults({
			result: {
				gaps: [],
				summary: {funnelStageCount: 0, gapCount: 0, personaCount: 0},
			},
			status: 'empty',
		});

		expect(
			screen.getByText('no-content-gaps-were-found-for-this-selection')
		).toBeInTheDocument();
	});

	it('renders the summary and each gap with its dimensions, severity, and reason', () => {
		renderResults();

		expect(
			screen.getByText(
				'x-personas-by-x-funnel-stages-analyzed-x-gaps-found'
			)
		).toBeInTheDocument();
		expect(
			screen.getByText('Decision Makers × Awareness')
		).toBeInTheDocument();
		expect(screen.getByText('No awareness content.')).toBeInTheDocument();
		expect(screen.getByText('high')).toBeInTheDocument();
	});

	it('fires the follow-up callbacks with the gap when its actions are clicked', async () => {
		const onDismiss = jest.fn();
		const onFindAssets = jest.fn();
		const onGenerateContent = jest.fn();

		renderResults({onDismiss, onFindAssets, onGenerateContent});

		await userEvent.click(screen.getByText('find-matching-assets'));
		await userEvent.click(screen.getByText('generate-content'));
		await userEvent.click(screen.getByText('dismiss'));

		expect(onFindAssets).toHaveBeenCalledWith(GAP);
		expect(onGenerateContent).toHaveBeenCalledWith(GAP);
		expect(onDismiss).toHaveBeenCalledWith(GAP);
	});

	it('regenerates when the regenerate button is clicked', async () => {
		const onRegenerate = jest.fn();

		renderResults({onRegenerate});

		await userEvent.click(screen.getByText('regenerate'));

		expect(onRegenerate).toHaveBeenCalledTimes(1);
	});
});
