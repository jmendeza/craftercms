/*
 * Copyright (C) 2007-2024 Crafter Software Corporation. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import { registerComponents } from './env/registerComponents';
import { createCodebaseBridge } from './env/codebase-bridge';
import { publishCrafterGlobal } from './env/craftercms';
import { createRoot } from 'react-dom/client';
import React, { StrictMode, useEffect, useState } from 'react';
import CrafterCMSNextBridge from './components/CrafterCMSNextBridge';
import SiteTools from './pages/SiteTools';
import LoadingState from './components/LoadingState';
import ErrorState from './components/ErrorState/ErrorState';
import { fetchUiBootstrap } from './services/environment';

registerComponents();
publishCrafterGlobal();
createCodebaseBridge();

function SiteToolsBootstrap() {
	const [footerHtml, setFooterHtml] = useState<string | null>(null);
	const [error, setError] = useState(false);

	useEffect(() => {
		const subscription = fetchUiBootstrap().subscribe({
			next: (bootstrap) => {
				setFooterHtml(bootstrap.footerHtml ?? '');
			},
			error: () => setError(true)
		});
		return () => subscription.unsubscribe();
	}, []);

	if (error) {
		return (
			<ErrorState
				title="Unable to load application"
				imageUrl="/studio/static-assets/images/warning_state.svg"
				sxs={{
					root: { height: '100%' },
					title: { textAlign: 'center' },
					image: { width: 250, marginBottom: '10px', marginTop: '10px' }
				}}
			/>
		);
	}

	if (footerHtml === null) {
		return <LoadingState sxs={{ root: { height: '100%', margin: 0 } }} />;
	}

	return (
		<CrafterCMSNextBridge>
			<SiteTools footerHtml={footerHtml} />
		</CrafterCMSNextBridge>
	);
}

createRoot(document.getElementById('root')).render(
	<StrictMode>
		<SiteToolsBootstrap />
	</StrictMode>
);
