# Migrating to Secure Cloud Function Feedback

## Overview
The feedback system has been updated to use a secure Google Cloud Function instead of embedding GitHub tokens in the app. This prevents security vulnerabilities from token exposure.

## Migration Steps

### 1. Deploy the Cloud Function

From the `cloud-function/feedback-function` directory:

```bash
# Install dependencies
npm install

# Deploy to Google Cloud
gcloud functions deploy createGithubIssue \
  --runtime nodejs20 \
  --trigger-http \
  --allow-unauthenticated \
  --set-secrets "GITHUB_PAT=projects/329675289789/secrets/github-pat-android:latest" \
  --region us-central1 \
  --project 329675289789
```

### 2. Update the Cloud Function URL

After deployment, update the URL in `CloudFunctionRetrofitClient.kt`:

```kotlin
// Replace this line with your actual Cloud Function URL
private const val CLOUD_FUNCTION_URL = "https://us-central1-329675289789.cloudfunctions.net/"
```

### 3. Secure Implementation Is Required

`FeedbackActivity.kt` must host the secure fragment:

```kotlin
FeedbackFragmentSecure.newInstance()
```

The old direct-GitHub fragment, repository, Retrofit client, API service, and
GitHub issue models have been removed from production sources. Do not restore
them; feedback submissions must go through the Cloud Function boundary.

## Testing

1. Submit a test bug report
2. Submit a test feature request
3. Verify issues appear in: https://github.com/omiyawaki/osrswiki-android/issues

## Benefits

✅ **Secure**: GitHub token never exposed in app code
✅ **Reliable**: Server-side error handling
✅ **Maintainable**: Easy to update token without app release
✅ **Scalable**: Can add rate limiting, validation, etc.

## Rollback

Rollback should disable in-app submission or point the Cloud Function client at
a known-good deployment. Do not roll back to direct GitHub API submission in the
Android app.
