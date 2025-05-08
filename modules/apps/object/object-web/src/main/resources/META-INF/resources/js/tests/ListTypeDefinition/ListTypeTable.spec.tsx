/**
 * SPDX-FileCopyrightText: (c) 2025 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import '@testing-library/jest-dom/extend-expect';

import {getDataSetProps} from '../../components/ListTypeDefinition/ListTypeTable';

describe('getDataSetProps', () => {
	beforeAll(() => {
		global.Liferay = {
			...global.Liferay,
			FeatureFlags: {
				...global.Liferay?.FeatureFlags,
				'LPD-24055': true,
			},
		};
	});

	it('displays the add entry button when a system picklist is selected', async () => {
		const result = getDataSetProps(
			() => {},
			-1,
			false,
			() => {},
			{system: true}
		);

		const primaryItemsString = result?.creationMenu?.primaryItems + '';

		const expectedString =
			[
				{
					href: 'handleAddItems',
					label: 'add-item',
					target: 'event',
					type: 'item',
				},
			] + '';

		expect(primaryItemsString).toBe(expectedString);
	});
});
