/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {Gap, GapInsightsContext} from './types';

export const GAP_INSIGHTS_EVENT = 'cmp:aiAssistant:gapInsights';

/**
 * Follow-up-action seams fired by each gap row. Story 2 (LPD-94219, find
 * matching assets in CMS) and Story 3 (LPD-94221, generate content for gaps)
 * will listen for these. They are out of scope for this story - the rows fire
 * them, but nothing consumes them yet.
 */
export const GAP_INSIGHTS_FIND_ASSETS_EVENT =
	'cmp:aiAssistant:gapInsights:findAssets';

export const GAP_INSIGHTS_GENERATE_CONTENT_EVENT =
	'cmp:aiAssistant:gapInsights:generateContent';

export type GapInsightsEventPayload = GapInsightsContext;

export interface GapInsightsActionEventPayload {
	gap: Gap;
}
