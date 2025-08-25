#!/bin/bash

# Simple UI click script for Android
if [[ "$1" == "--text" ]]; then
    adb shell uiautomator runtest /system/framework/uiautomator.jar -c android.support.test.uiautomator.UiDevice -e text "$2"
elif [[ "$1" == "--content-desc" ]]; then
    adb shell input tap $(adb shell uiautomator dump /dev/stdout | grep -oP "content-desc=\"$2\"[^>]*bounds=\"\[\K[0-9,]+(?=\]\[)" | head -1 | tr ',' ' ')
else
    echo "Usage: $0 --text \"text\" or --content-desc \"description\""
fi