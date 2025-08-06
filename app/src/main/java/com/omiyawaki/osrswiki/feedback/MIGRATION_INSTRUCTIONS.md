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

### 3. Switch to Secure Implementation

In `FeedbackActivity.kt`, update the fragment:

```kotlin
// Change from:
FeedbackFragment.newInstance()

// To:
FeedbackFragmentSecure.newInstance()
```

### 4. Clean Up (Optional)

Once verified working, you can remove:
- `FeedbackRepository.kt` (uses direct GitHub API)
- `FeedbackFragment.kt` (old implementation)
- `GitHubApiService.kt`
- `GitHubRetrofitClient.kt`
- GitHub API models in `network/model/github/`

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

If needed, switch back to `FeedbackFragment` in `FeedbackActivity.kt`.