/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import React, {useEffect, useState} from 'react';

import GapInsightsResults from '../../GapInsights/components/GapInsightsResults';
import {
	GAP_INSIGHTS_FIND_ASSETS_EVENT,
	GAP_INSIGHTS_GENERATE_CONTENT_EVENT,
	GapInsightsEventPayload,
} from '../../GapInsights/events';
import {Gap} from '../../GapInsights/types';
import useGapInsightsAgent from '../../GapInsights/useGapInsightsAgent';

function getKey(gap: Gap): string {
	return `${gap.personaId}:${gap.funnelStageId}`;
}

export default function GapInsightsMessageBalloon({
	contentCoverage,
	focusScope,
	projectContext,
}: GapInsightsEventPayload) {
	const [dismissed, setDismissed] = useState<string[]>([]);

	const {regenerate, result, run, status} = useGapInsightsAgent();

	useEffect(() => {
		run({contentCoverage, focusScope, projectContext});
	}, [contentCoverage, focusScope, projectContext, run]);

	const visibleResult = result
		? {
				...result,
				gaps: result.gaps.filter(
					(gap) => !dismissed.includes(getKey(gap))
				),
			}
		: result;

	return (
		<div className="ai-assistant-chat__ai-assistant-message-balloon mb-2 p-2 rounded">
			<GapInsightsResults
				onDismiss={(gap) =>
					setDismissed((previousDismissed) => [
						...previousDismissed,
						getKey(gap),
					])
				}
				onFindAssets={(gap) =>
					Liferay.fire(GAP_INSIGHTS_FIND_ASSETS_EVENT, {gap})
				}
				onGenerateContent={(gap) =>
					Liferay.fire(GAP_INSIGHTS_GENERATE_CONTENT_EVENT, {gap})
				}
				onRegenerate={() => {
					setDismissed([]);

					regenerate();
				}}
				result={visibleResult}
				status={status === 'idle' ? 'loading' : status}
			/>
		</div>
	);
}
