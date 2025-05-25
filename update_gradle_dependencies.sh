#!/bin/bash -l
# Script to ensure KSP and Room dependencies are in app/build.gradle.kts

PROJECT_ROOT="/mnt/c/Users/Osamu/AndroidStudioProjects/OSRSWiki"
GRADLE_FILE_PATH="${PROJECT_ROOT}/app/build.gradle.kts"
TEMP_GRADLE_FILE=$(mktemp)

# --- Configuration: KSP and Room versions ---
# Please ensure KSP_VERSION is compatible with your project's Kotlin version.
# For Kotlin 1.9.23, KSP 1.9.23-1.0.19 is commonly used.
# Room 2.6.1 is a recent stable version as of early 2024.
KSP_VERSION="1.9.23-1.0.19"
ROOM_VERSION="2.6.1"

# --- Lines to ensure in the Gradle file ---
# For plugins block:
KSP_PLUGIN_DECLARATION="    id(\"com.google.devtools.ksp\") version \"${KSP_VERSION}\""
KSP_PLUGIN_PATTERN="id(\"com.google.devtools.ksp\")" # Grep pattern to check for KSP plugin

# For dependencies block:
ROOM_VERSION_VAR_DECLARATION="    val room_version = \"${ROOM_VERSION}\""
ROOM_VERSION_VAR_PATTERN="val room_version[ \t]*=" # Grep pattern for Room version variable

ROOM_RUNTIME_DECLARATION="    implementation(\"androidx.room:room-runtime:\$room_version\")" # Note: \$room_version is literal for Gradle
ROOM_RUNTIME_PATTERN="androidx.room:room-runtime" # Grep pattern

ROOM_KTX_DECLARATION="    implementation(\"androidx.room:room-ktx:\$room_version\")"
ROOM_KTX_PATTERN="androidx.room:room-ktx" # Grep pattern

ROOM_COMPILER_DECLARATION="    ksp(\"androidx.room:room-compiler:\$room_version\")"
ROOM_COMPILER_ARTIFACT_PATTERN="androidx.room:room-compiler" # Grep pattern for the artifact
KSP_COMPILER_INVOCATION_PATTERN="ksp[ \t(]*['\"]androidx.room:room-compiler:.*['\"]\)*" # Grep regex for ksp("androidx.room:room-compiler...")

# --- Helper function to add a line if a pattern is missing ---
# Arguments: 1:LineToAdd, 2:PatternToDetectExistence, 3:AnchorPatternForInsertion, 4:FileToModify, 5:IsPatternToDetectRegex (true/false)
add_line_if_missing() {
    local line_to_add="$1"
    local pattern_to_detect="$2"
    local anchor_pattern="$3"
    local file_to_modify="$4"
    local is_detection_regex="${5:-false}"
    local success=0

    local grep_detect_cmd="grep -q"
    if [ "$is_detection_regex" = true ]; then
        grep_detect_cmd="grep -qE"
    fi

    if ! $grep_detect_cmd "$pattern_to_detect" "$file_to_modify"; then
        echo "INFO: Pattern '$pattern_to_detect' not found. Adding line: $line_to_add"
        # Insert after the anchor pattern
        awk -v line="$line_to_add" -v anchor="$anchor_pattern" '
        $0 ~ anchor {
            print $0; # Print the anchor line
            print line; # Print the new line after the anchor
            inserted=1; # Flag that we inserted
            next;
        }
        { print $0; } # Print all other lines
        END { if (inserted != 1) { print "AWK_ERROR: Anchor pattern \"" anchor "\" not found for line: \"" line "\""; exit 1} }
        ' "$file_to_modify" > "${file_to_modify}.tmp"

        if [ $? -ne 0 ]; then
            echo "ERROR: awk command failed for line: $line_to_add"
            rm -f "${file_to_modify}.tmp" # Clean up temp file from awk
            return 1
        fi
        mv "${file_to_modify}.tmp" "$file_to_modify"

        # Verify that the line (or its pattern) is now present
        if ! $grep_detect_cmd "$pattern_to_detect" "$file_to_modify"; then
            echo "ERROR: Failed to add and verify line for pattern '$pattern_to_detect'."
            return 1
        else
            echo "INFO: Successfully added and verified line for pattern '$pattern_to_detect'."
        fi
    else
        echo "INFO: Pattern '$pattern_to_detect' already present. No changes made for this line."
    fi
    return 0
}

# --- Main script execution ---
echo "Starting update of $GRADLE_FILE_PATH..."

# Safety check: Ensure Gradle file exists
if [ ! -f "$GRADLE_FILE_PATH" ]; then
    echo "ERROR: Gradle file not found at $GRADLE_FILE_PATH. Please check the path."
    exit 1
fi
cp "$GRADLE_FILE_PATH" "$TEMP_GRADLE_FILE"
echo "INFO: Copied $GRADLE_FILE_PATH to $TEMP_GRADLE_FILE for safe modification."

# 1. Ensure KSP plugin in 'plugins {...}' block
echo "--- Checking KSP plugin ---"
add_line_if_missing "$KSP_PLUGIN_DECLARATION" "$KSP_PLUGIN_PATTERN" '^[ \t]*plugins[ \t]*\{' "$TEMP_GRADLE_FILE"
if [ $? -ne 0 ]; then echo "ERROR: Failed to process KSP plugin."; rm "$TEMP_GRADLE_FILE"; exit 1; fi

# 2. Ensure Room dependencies in 'dependencies {...}' block
echo "--- Checking Room dependencies ---"
# Add Room version variable
add_line_if_missing "$ROOM_VERSION_VAR_DECLARATION" "$ROOM_VERSION_VAR_PATTERN" '^[ \t]*dependencies[ \t]*\{' "$TEMP_GRADLE_FILE" true
if [ $? -ne 0 ]; then echo "ERROR: Failed to process Room version variable."; rm "$TEMP_GRADLE_FILE"; exit 1; fi

# Add Room runtime (anchors to room_version variable line)
add_line_if_missing "$ROOM_RUNTIME_DECLARATION" "$ROOM_RUNTIME_PATTERN" "$ROOM_VERSION_VAR_PATTERN" "$TEMP_GRADLE_FILE" true
if [ $? -ne 0 ]; then echo "ERROR: Failed to process Room runtime."; rm "$TEMP_GRADLE_FILE"; exit 1; fi

# Add Room KTX (anchors to room-runtime line)
add_line_if_missing "$ROOM_KTX_DECLARATION" "$ROOM_KTX_PATTERN" "$ROOM_RUNTIME_PATTERN" "$TEMP_GRADLE_FILE"
if [ $? -ne 0 ]; then echo "ERROR: Failed to process Room KTX."; rm "$TEMP_GRADLE_FILE"; exit 1; fi

# Add Room compiler KSP (anchors to room-ktx line)
add_line_if_missing "$ROOM_COMPILER_DECLARATION" "$KSP_COMPILER_INVOCATION_PATTERN" "$ROOM_KTX_PATTERN" "$TEMP_GRADLE_FILE" true
if [ $? -ne 0 ]; then echo "ERROR: Failed to process Room KSP compiler."; rm "$TEMP_GRADLE_FILE"; exit 1; fi

# --- Final Verification ---
echo "--- Final verification of $TEMP_GRADLE_FILE ---"
ALL_VERIFIED=true
grep -q "$KSP_PLUGIN_PATTERN" "$TEMP_GRADLE_FILE" || { echo "FINAL CHECK FAILED: KSP plugin pattern '$KSP_PLUGIN_PATTERN' not found."; ALL_VERIFIED=false; }
grep -qE "$ROOM_VERSION_VAR_PATTERN" "$TEMP_GRADLE_FILE" || { echo "FINAL CHECK FAILED: Room version variable pattern '$ROOM_VERSION_VAR_PATTERN' not found."; ALL_VERIFIED=false; }
grep -q "$ROOM_RUNTIME_PATTERN" "$TEMP_GRADLE_FILE" || { echo "FINAL CHECK FAILED: Room runtime pattern '$ROOM_RUNTIME_PATTERN' not found."; ALL_VERIFIED=false; }
grep -q "$ROOM_KTX_PATTERN" "$TEMP_GRADLE_FILE" || { echo "FINAL CHECK FAILED: Room KTX pattern '$ROOM_KTX_PATTERN' not found."; ALL_VERIFIED=false; }
grep -qE "$KSP_COMPILER_INVOCATION_PATTERN" "$TEMP_GRADLE_FILE" || { echo "FINAL CHECK FAILED: KSP compiler invocation pattern '$KSP_COMPILER_INVOCATION_PATTERN' not found."; ALL_VERIFIED=false; }

if [ "$ALL_VERIFIED" = true ]; then
    echo "INFO: All required dependencies and plugins are present in the temporary file."
    # Optional: Create a backup of the original file before overwriting
    # local backup_file="${GRADLE_FILE_PATH}.$(date +%Y%m%d-%H%M%S).bak"
    # echo "INFO: Creating backup: cp "$GRADLE_FILE_PATH" "$backup_file""
    # cp "$GRADLE_FILE_PATH" "$backup_file"
    
    mv "$TEMP_GRADLE_FILE" "$GRADLE_FILE_PATH"
    echo "SUCCESS: $GRADLE_FILE_PATH has been updated."
    echo "IMPORTANT ACTION: Please carefully review the changes made to $GRADLE_FILE_PATH."
    echo "After reviewing, sync your project with Gradle files in Android Studio."
    echo ""
    echo "ADDITIONAL NOTE: If not already done, ensure the KSP plugin is declared in your project-level (root) build.gradle.kts:"
    echo "plugins {"
    echo "    id(\"com.google.devtools.ksp\") version \"${KSP_VERSION}\" apply false"
    echo "}"
else
    echo "ERROR: Final verification failed. Original file $GRADLE_FILE_PATH was NOT modified."
    echo "Temporary file $TEMP_GRADLE_FILE has been removed."
    rm "$TEMP_GRADLE_FILE"
    exit 1
fi

exit 0
