/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import ClayButton from '@clayui/button';
import ClayIcon from '@clayui/icon';
import ClayLoadingIndicator from '@clayui/loading-indicator';
import {sub} from 'frontend-js-web';
import React from 'react';

import {Gap, GapAnalysisResult, GapInsightsStatus, GapSeverity} from '../types';

import '../gap-insights.scss';

const SEVERITY_LABELS: Record<GapSeverity, () => string> = {
	high: () => Liferay.Language.get('high'),
	low: () => Liferay.Language.get('low'),
	medium: () => Liferay.Language.get('medium'),
};

interface GapInsightsResultsProps {
	onDismiss: (gap: Gap) => void;
	onFindAssets: (gap: Gap) => void;
	onGenerateContent: (gap: Gap) => void;
	onRegenerate: () => void;
	result: GapAnalysisResult | null;
	status: GapInsightsStatus;
}

export default function GapInsightsResults({
	onDismiss,
	onFindAssets,
	onGenerateContent,
	onRegenerate,
	result,
	status,
}: GapInsightsResultsProps) {
	if (status === 'idle') {
		return null;
	}

	if (status === 'loading') {
		return (
			<div className="align-items-center d-flex gap-insights">
				<ClayLoadingIndicator className="mr-2" />

				<span className="font-weight-semi-bold text-secondary">
					{Liferay.Language.get('analyzing-content-coverage')}
				</span>
			</div>
		);
	}

	if (status === 'error') {
		return (
			<span className="gap-insights text-danger">
				{Liferay.Language.get('an-unexpected-error-occurred')}
			</span>
		);
	}

	if (status === 'empty' || !result?.gaps.length) {
		return (
			<span className="gap-insights">
				{Liferay.Language.get(
					'no-content-gaps-were-found-for-this-selection'
				)}
			</span>
		);
	}

	const {gaps, summary} = result;

	return (
		<div className="gap-insights">
			<p className="font-weight-semi-bold">
				{sub(
					Liferay.Language.get(
						'x-personas-by-x-funnel-stages-analyzed-x-gaps-found'
					),
					`${summary.personaCount}`,
					`${summary.funnelStageCount}`,
					`${summary.gapCount}`
				)}
			</p>

			<ul className="gap-insights__gaps list-unstyled">
				{gaps.map((gap) => (
					<li
						className="gap-insights__gap"
						key={`${gap.personaId}:${gap.funnelStageId}`}
					>
						<div className="align-items-center d-flex gap-insights__gap-header justify-content-between">
							<span>
								<span className="font-weight-semi-bold">
									{`${gap.personaName} × ${gap.funnelStageName}`}
								</span>

								<span className="gap-insights__gap-count text-secondary">
									{sub(
										Liferay.Language.get('x-assets'),
										`${gap.currentCount}`
									)}
								</span>
							</span>

							<span
								className={`gap-insights__severity gap-insights__severity--${gap.severity}`}
							>
								{SEVERITY_LABELS[gap.severity]()}
							</span>
						</div>

						<p className="gap-insights__gap-reason text-secondary">
							{gap.reason}
						</p>

						<div className="gap-insights__gap-actions">
							<ClayButton
								displayType="unstyled"
								onClick={() => onFindAssets(gap)}
								small
							>
								{Liferay.Language.get('find-matching-assets')}
							</ClayButton>

							<ClayButton
								displayType="unstyled"
								onClick={() => onGenerateContent(gap)}
								small
							>
								{Liferay.Language.get('generate-content')}
							</ClayButton>

							<ClayButton
								displayType="unstyled"
								onClick={() => onDismiss(gap)}
								small
							>
								{Liferay.Language.get('dismiss')}
							</ClayButton>
						</div>
					</li>
				))}
			</ul>

			<div className="d-flex justify-content-end">
				<ClayButton displayType="secondary" onClick={onRegenerate}>
					<ClayIcon
						className="mr-2"
						spritemap={Liferay.Icons.spritemap}
						symbol="reload"
					/>

					{Liferay.Language.get('regenerate')}
				</ClayButton>
			</div>
		</div>
	);
}
