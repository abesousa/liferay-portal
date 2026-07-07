/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {act, renderHook} from '@testing-library/react';

import {
	createGapInsightsEventSource,
	postGapInsightsAgentInstance,
} from '../../../src/main/resources/META-INF/resources/js/GapInsights/api';
import {GAP_INSIGHTS_AGENT} from '../../../src/main/resources/META-INF/resources/js/GapInsights/types';
import useGapInsightsAgent from '../../../src/main/resources/META-INF/resources/js/GapInsights/useGapInsightsAgent';

jest.mock('../../../src/main/resources/META-INF/resources/js/GapInsights/api');

const mockCreateEventSource =
	createGapInsightsEventSource as jest.MockedFunction<
		typeof createGapInsightsEventSource
	>;
const mockPostAgentInstance =
	postGapInsightsAgentInstance as jest.MockedFunction<
		typeof postGapInsightsAgentInstance
	>;

const CONTEXT = {
	contentCoverage: {cells: [], totalAssetCount: 0},
	focusScope: 'full-matrix',
	projectContext: {description: 'd', goals: 'g', name: 'Q3 Launch'},
};

const REQUEST_CONTEXT = {
	contentCoverage: JSON.stringify(CONTEXT.contentCoverage),
	focusScope: 'full-matrix',
	projectContext: JSON.stringify(CONTEXT.projectContext),
};

function agentResult(gaps: unknown[]) {
	return JSON.stringify({
		data: JSON.stringify({
			gaps,
			summary: {
				funnelStageCount: 4,
				gapCount: gaps.length,
				personaCount: 4,
			},
		}),
		nodeName: 'contentGapAnalysis',
	});
}

const GAP = {
	currentCount: 0,
	funnelStageName: 'Awareness',
	personaName: 'Decision Makers',
	reason: 'No awareness content.',
	severity: 'high',
};

function createFakeEventSource() {
	const listeners: Record<string, (event: {data: string}) => void> = {};

	return {
		addEventListener: jest.fn(
			(type: string, handler: (event: {data: string}) => void) => {
				listeners[type] = handler;
			}
		),
		close: jest.fn(),
		emit(type: string, data: string) {
			listeners[type]?.({data});
		},
	};
}

describe('useGapInsightsAgent', () => {
	beforeEach(() => {
		jest.clearAllMocks();
		mockPostAgentInstance.mockResolvedValue(undefined);
	});

	it('subscribes, posts the stringified context, and parses the gap analysis', async () => {
		const fakeEventSource = createFakeEventSource();

		mockCreateEventSource.mockResolvedValue(fakeEventSource as never);

		const {result} = renderHook(() => useGapInsightsAgent());

		await act(async () => {
			result.current.run(CONTEXT);
		});

		expect(result.current.status).toBe('loading');

		await act(async () => {
			fakeEventSource.emit('Subscribe', 'sink-1');
		});

		expect(mockPostAgentInstance).toHaveBeenCalledWith({
			context: REQUEST_CONTEXT,
			sseEventSinkKey: 'sink-1',
		});

		await act(async () => {
			fakeEventSource.emit(GAP_INSIGHTS_AGENT, agentResult([GAP]));
		});

		expect(result.current.status).toBe('ready');
		expect(result.current.result?.gaps).toHaveLength(1);
		expect(result.current.result?.summary.gapCount).toBe(1);
	});

	it('reports an empty status when the agent finds no gaps', async () => {
		const fakeEventSource = createFakeEventSource();

		mockCreateEventSource.mockResolvedValue(fakeEventSource as never);

		const {result} = renderHook(() => useGapInsightsAgent());

		await act(async () => {
			result.current.run(CONTEXT);
		});

		await act(async () => {
			fakeEventSource.emit('Subscribe', 'sink-1');
		});

		await act(async () => {
			fakeEventSource.emit(GAP_INSIGHTS_AGENT, agentResult([]));
		});

		expect(result.current.status).toBe('empty');
	});

	it('defers the agent invocation until the subscribe event arrives', async () => {
		const fakeEventSource = createFakeEventSource();

		mockCreateEventSource.mockResolvedValue(fakeEventSource as never);

		const {result} = renderHook(() => useGapInsightsAgent());

		await act(async () => {
			result.current.run(CONTEXT);
		});

		expect(mockPostAgentInstance).not.toHaveBeenCalled();

		await act(async () => {
			fakeEventSource.emit('Subscribe', 'sink-2');
		});

		expect(mockPostAgentInstance).toHaveBeenCalledWith({
			context: REQUEST_CONTEXT,
			sseEventSinkKey: 'sink-2',
		});
	});

	it('surfaces the failure text when the agent invocation fails', async () => {
		const fakeEventSource = createFakeEventSource();

		mockCreateEventSource.mockResolvedValue(fakeEventSource as never);

		const {result} = renderHook(() => useGapInsightsAgent());

		await act(async () => {
			result.current.run(CONTEXT);
		});

		await act(async () => {
			fakeEventSource.emit('Subscribe', 'sink-1');
		});

		await act(async () => {
			fakeEventSource.emit(
				'Agent Invocation Failed',
				JSON.stringify({data: 'model armor blocked the request'})
			);
		});

		expect(result.current.status).toBe('error');
		expect(result.current.error).toBe('model armor blocked the request');
	});

	it('regenerate re-posts the last context', async () => {
		const fakeEventSource = createFakeEventSource();

		mockCreateEventSource.mockResolvedValue(fakeEventSource as never);

		const {result} = renderHook(() => useGapInsightsAgent());

		await act(async () => {
			result.current.run(CONTEXT);
		});

		await act(async () => {
			fakeEventSource.emit('Subscribe', 'sink-1');
		});

		await act(async () => {
			result.current.regenerate();
		});

		expect(mockPostAgentInstance).toHaveBeenCalledTimes(2);
	});
});
