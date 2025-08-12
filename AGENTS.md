## Git Workflow
- Use git status to specifically pick out files to add and stage for a commit.
- For any changes, you must commit directly to the `main` branch.
- Do not create new feature or fix branches.
- After committing, push the changes directly to `origin main`.

## Gradle Build Workflow
- To build the project from the terminal, use the Gradle Wrapper script.
- The base command is: `./gradlew <task>`

### Example Tasks
- **Build a debug APK:**
  `./gradlew assembleDebug`
- **Build and install on a connected device/emulator:**
  `./gradlew uninstallDebug installDebug`

## adb Workflow
- To run `adb` for tasks like viewing logcat, execute it directly from the terminal.
- For example: `adb logcat -d | grep -i ResponsiveVideos`