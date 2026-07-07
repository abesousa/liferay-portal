/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {EventSource} from 'eventsource';
import {useCallback, useEffect, useRef, useState} from 'react';

import {
	createGapInsightsEventSource,
	postGapInsightsAgentInstance,
} from './api';
import {
	GAP_INSIGHTS_AGENT,
	GapAnalysisResult,
	GapInsightsContext,
	GapInsightsStatus,
} from './types';
import {parseGapAnalysis} from './utils/parseGapAnalysis';

function toRequestContext(
	context: GapInsightsContext
): Record<string, unknown> {
	const focusScope = context.focusScope ?? 'full-matrix';

	return {
		contentCoverage: JSON.stringify(context.contentCoverage ?? {}),
		focusScope:
			typeof focusScope === 'string'
				? focusScope
				: JSON.stringify(focusScope),
		projectContext: JSON.stringify(context.projectContext ?? {}),
	};
}

export default function useGapInsightsAgent() {
	const [error, setError] = useState<string>();
	const [result, setResult] = useState<GapAnalysisResult | null>(null);
	const [status, setStatus] = useState<GapInsightsStatus>('idle');

	const connectingRef = useRef<boolean>(false);
	const eventSourceRef = useRef<EventSource | null>(null);
	const lastContextRef = useRef<GapInsightsContext | null>(null);
	const mountedRef = useRef<boolean>(true);
	const pendingRef = useRef<boolean>(false);
	const sseEventSinkKeyRef = useRef<string | null>(null);

	const closeEventSource = useCallback(() => {
		eventSourceRef.current?.close();
		eventSourceRef.current = null;
		sseEventSinkKeyRef.current = null;
	}, []);

	const invoke = useCallback(
		async (context: GapInsightsContext) => {
			try {
				await postGapInsightsAgentInstance({
					context: toRequestContext(context),
					sseEventSinkKey: sseEventSinkKeyRef.current as string,
				});
			}
			catch {
				setError(Liferay.Language.get('an-unexpected-error-occurred'));
				setStatus('error');

				closeEventSource();
			}
		},
		[closeEventSource]
	);

	const connect = useCallback(() => {
		if (eventSourceRef.current || connectingRef.current) {
			return;
		}

		connectingRef.current = true;

		createGapInsightsEventSource()
			.then((eventSource) => {
				connectingRef.current = false;

				if (!mountedRef.current) {
					eventSource?.close();

					return;
				}

				if (!eventSource) {
					pendingRef.current = false;

					setStatus('idle');

					return;
				}

				eventSourceRef.current = eventSource;

				eventSource.addEventListener('Subscribe', (event) => {
					sseEventSinkKeyRef.current = event.data;

					if (pendingRef.current && lastContextRef.current) {
						pendingRef.current = false;

						invoke(lastContextRef.current);
					}
				});

				eventSource.addEventListener(GAP_INSIGHTS_AGENT, (event) => {
					try {
						const dataJSON = JSON.parse(event.data);

						const parsed = parseGapAnalysis(dataJSON.data ?? '');

						setResult(parsed);
						setStatus(parsed.gaps.length ? 'ready' : 'empty');
					}
					catch {
						setError(
							Liferay.Language.get('an-unexpected-error-occurred')
						);
						setStatus('error');
					}

					closeEventSource();
				});

				eventSource.addEventListener(
					'Agent Invocation Failed',
					(event) => {
						let text = '';

						try {
							text = JSON.parse(event.data).data;
						}
						catch {
							text = '';
						}

						setError(
							text ||
								Liferay.Language.get(
									'an-unexpected-error-occurred'
								)
						);
						setStatus('error');

						closeEventSource();
					}
				);
			})
			.catch(() => {
				connectingRef.current = false;
				pendingRef.current = false;

				if (!mountedRef.current) {
					return;
				}

				setError(Liferay.Language.get('an-unexpected-error-occurred'));
				setStatus('error');
			});
	}, [closeEventSource, invoke]);

	const run = useCallback(
		(context: GapInsightsContext) => {
			lastContextRef.current = context;

			setError(undefined);
			setResult(null);
			setStatus('loading');

			if (sseEventSinkKeyRef.current) {
				invoke(context);
			}
			else {
				pendingRef.current = true;

				connect();
			}
		},
		[connect, invoke]
	);

	const regenerate = useCallback(() => {
		if (lastContextRef.current) {
			run(lastContextRef.current);
		}
	}, [run]);

	const reset = useCallback(() => {
		setError(undefined);
		setResult(null);
		setStatus('idle');
	}, []);

	useEffect(() => {
		mountedRef.current = true;

		return () => {
			mountedRef.current = false;
		};
	}, []);

	useEffect(() => {
		return () => {
			pendingRef.current = false;

			closeEventSource();
		};
	}, [closeEventSource]);

	return {error, regenerate, reset, result, run, status};
}
