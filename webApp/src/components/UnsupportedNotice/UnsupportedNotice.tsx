export function UnsupportedNotice() {
    return (
        <div style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: '100vh',
            gap: '12px',
            fontFamily: 'system-ui, sans-serif',
            textAlign: 'center',
            padding: '24px',
        }}>
            <h1>LogTrain</h1>
            <p>
                LogTrain on the web is coming soon. Local workout data is not
                supported in the browser yet — please use the iOS, Android, or
                desktop app.
            </p>
        </div>
    );
}
