#!/bin/bash
# Extract applicationId from build.gradle.kts
grep "applicationId = " app/build.gradle.kts | sed 's/.*applicationId = "\(.*\)".*/\1/'