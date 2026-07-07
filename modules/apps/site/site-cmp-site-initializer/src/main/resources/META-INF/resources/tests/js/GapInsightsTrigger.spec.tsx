/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import '@testing-library/jest-dom';
import {GAP_INSIGHTS_EVENT} from '@liferay/ai-hub-cell-js-components-web';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import GapInsightsTrigger from '../../js/components/project/GapInsightsTrigger';

beforeEach(() => {
	Liferay.FeatureFlags['LPD-62272'] = true;

	global.fetch = jest.fn().mockResolvedValue({
		json: () => Promise.resolve({}),
		ok: true,
	} as Response);
});

afterEach(() => {
	Liferay.FeatureFlags['LPD-62272'] = false;
	jest.restoreAllMocks();
});

describe('GapInsightsTrigger', () => {
	it('renders nothing when the AI Hub feature flag is off', () => {
		Liferay.FeatureFlags['LPD-62272'] = false;

		const {container} = render(
			<GapInsightsTrigger cmsGroupId={20121} projectId={39398} />
		);

		expect(container).toBeEmptyDOMElement();
	});

	it('renders the Get Gap Insights button when the flag is on', () => {
		render(<GapInsightsTrigger cmsGroupId={20121} projectId={39398} />);

		expect(screen.getByText('get-gap-insights')).toBeInTheDocument();
	});

	it('fires the gap insights event with the project context on click', async () => {
		const fireSpy = jest.spyOn(Liferay, 'fire');

		render(
			<GapInsightsTrigger
				cmsGroupId={20121}
				projectDescription="Content for the Q3 launch"
				projectGoals="Drive awareness"
				projectId={39398}
				projectName="Q3 Launch"
			/>
		);

		await userEvent.click(screen.getByText('get-gap-insights'));

		await waitFor(() =>
			expect(fireSpy).toHaveBeenCalledWith(
				GAP_INSIGHTS_EVENT,
				expect.objectContaining({
					focusScope: 'full-matrix',
					projectContext: {
						description: 'Content for the Q3 launch',
						goals: 'Drive awareness',
						name: 'Q3 Launch',
					},
				})
			)
		);
	});
});
