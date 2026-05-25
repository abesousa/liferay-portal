/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import ClayIcon from '@clayui/icon';
import ClayLabel from '@clayui/label';
import {getFDSInternalRenderer} from '@liferay/frontend-data-set-web';
import React from 'react';

import type {IItemsActions} from '@liferay/frontend-data-set-web';

const ActionLinkRenderer = getFDSInternalRenderer('actionLink')?.component;

interface IProps {
	actions: IItemsActions[];
	itemData: unknown;
	itemId: unknown;
	value: unknown;
}

const FDSItemTitle = ({actions, itemData, itemId, value}: IProps) => {
	const title = ActionLinkRenderer ? (
		<ActionLinkRenderer
			actions={actions}
			itemData={itemData}
			itemId={itemId}
			options={{actionId: 'view'}}
			value={value}
		/>
	) : (
		<>{value}</>
	);

	const system =
		(itemData as {system?: boolean} | null | undefined)?.system === true;

	if (!system) {
		return title;
	}

	return (
		<span className="align-items-center d-inline-flex">
			<ClayIcon className="mr-2 text-muted" symbol="lock" />

			{title}

			<ClayLabel className="ml-2" displayType="info">
				{Liferay.Language.get('system')}
			</ClayLabel>
		</span>
	);
};

export default FDSItemTitle;
