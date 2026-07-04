import React from 'react';
import ReactDOM from 'react-dom/client';
import {UnsupportedNotice} from './components/UnsupportedNotice/UnsupportedNotice.tsx';

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('Failed to find the root element');

ReactDOM.createRoot(rootElement).render(
    <React.StrictMode>
        <UnsupportedNotice/>
    </React.StrictMode>
);
