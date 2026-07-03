import {useEffect, useState} from 'react';
import {AppAuth} from 'sharedLogic';

export function AuthGate() {
    const loginController = AppAuth.getInstance().loginController;
    const [isSignedIn, setIsSignedIn] = useState<boolean>(loginController.isSignedIn());

    useEffect(() => {
        const listener = () => setIsSignedIn(loginController.isSignedIn());
        loginController.addListener(listener);
        return () => loginController.removeListener(listener);
    }, [loginController]);

    if (isSignedIn) {
        return (
            <div>
                <p>Signed in</p>
                <button onClick={() => loginController.signOut()}>Sign out</button>
            </div>
        );
    }

    return (
        <button onClick={() => loginController.startLogin()}>
            Sign in with Google
        </button>
    );
}
