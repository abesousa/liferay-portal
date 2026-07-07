/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import ClayButton from '@clayui/button';
import ClayIcon from '@clayui/icon';
import {
	AIAssistantChat,
	GAP_INSIGHTS_EVENT,
} from '@liferay/ai-hub-cell-js-components-web';
import React, {useState} from 'react';

import {getContentCoverage} from '../../utils/getContentCoverage';

import './GapInsightsTrigger.scss';

interface GapInsightsTriggerProps {
	cmsGroupId: number | string;
	projectDescription?: string;
	projectGoals?: string;
	projectId: number | string;
	projectName?: string;
}

export default function GapInsightsTrigger({
	cmsGroupId,
	projectDescription,
	projectGoals,
	projectId,
	projectName,
}: GapInsightsTriggerProps) {
	const [loading, setLoading] = useState(false);

	// The AI Assistant (and the whole GAP Insights flow) is gated behind the
	// AI Hub feature flag, matching how CMS mounts it.

	if (!Liferay.FeatureFlags?.['LPD-62272']) {
		return null;
	}

	const handleClick = async () => {
		setLoading(true);

		try {
			const contentCoverage = await getContentCoverage({
				cmsGroupId,
				projectId,
			});

			Liferay.fire(GAP_INSIGHTS_EVENT, {
				contentCoverage,
				focusScope: 'full-matrix',
				projectContext: {
					description: projectDescription ?? '',
					goals: projectGoals ?? '',
					name: projectName ?? '',
				},
			});
		}
		finally {
			setLoading(false);
		}
	};

	return (
		<div className="align-items-center d-flex justify-content-end lfr-cmp__gap-insights-trigger">
			<ClayButton
				borderless
				disabled={loading}
				displayType="secondary"
				onClick={handleClick}
			>
				<ClayIcon
					className="mr-2"
					spritemap={Liferay.Icons.spritemap}
					symbol="stars"
				/>

				{Liferay.Language.get('get-gap-insights')}
			</ClayButton>

			<AIAssistantChat
				getContext={() => ({})}
				instructionDefinitionScope="everywhere"
			/>
		</div>
	);
}
