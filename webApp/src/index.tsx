import React from 'react';
import ReactDOM from 'react-dom/client';
import {AppAuth} from 'sharedLogic';
import {AuthGate} from './components/AuthGate/AuthGate.tsx';

if (window.location.pathname === '/auth/callback') {
    AppAuth.getInstance().loginController.completeLogin(window.location.href);
    window.history.replaceState({}, '', '/');
}

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('Failed to find the root element');

ReactDOM.createRoot(rootElement).render(
    <React.StrictMode>
        <AuthGate/>
    </React.StrictMode>
);