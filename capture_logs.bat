@echo off
echo Starting log capture for ViewHideHandler...
echo.
echo INSTRUCTIONS:
echo 1. Open the app and navigate to an article page
echo 2. Scroll down to hide the toolbar
echo 3. Hold your finger still on the screen
echo 4. Watch for oscillation and let it run for a few cycles
echo 5. Press Ctrl+C to stop log capture
echo.
echo Logs will be saved to: toolbar_oscillation_logs.txt
echo.
adb logcat -c
adb logcat ViewHideHandler:D *:S > toolbar_oscillation_logs.txt