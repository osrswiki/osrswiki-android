## Git Workflow
- Use `git status` to specifically pick out files to add and stage for a commit.
- For any changes, you must commit directly to the `main` branch.
- Do not create new feature or fix branches.
- After committing, push the changes directly to `origin main`.

<hr>

## Gradle Build Workflow
- To build the project from the macOS terminal, use the Gradle Wrapper script.
- The base command is: `./gradlew <task>`

### Example Tasks
- **Build the project:**
  `./gradlew build`
- **Build a debug APK:**
  `./gradlew assembleDebug`
- **Build and install on a connected device/emulator:**
  `./gradlew installDebug`

<hr>

## adb Workflow
- To run `adb` for tasks like viewing logcat, execute it directly from the terminal.
- For this to work, ensure the Android SDK `platform-tools` directory is included in your shell's `PATH` variable.
- **Example for viewing specific logs:**
  `adb logcat -d | grep -i "YourLogTag"`