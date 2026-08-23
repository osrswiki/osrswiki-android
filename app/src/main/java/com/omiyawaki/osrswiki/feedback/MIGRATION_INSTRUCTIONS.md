# Secure Feedback via Cloudflare Worker

## Overview
In-app feedback submits to a Cloudflare Worker (`osrswiki-feedback`) that creates
GitHub issues server-side. The GitHub token never ships in the app.

## Client wiring

- Retrofit client: `FeedbackWorkerRetrofitClient.kt`
- Base URL: `https://osrswiki-feedback.omiyawaki.workers.dev/`
- Endpoint: `POST createGithubIssue`
- Repository: `SecureFeedbackRepository` (hosted by `FeedbackFragmentSecure`)

Request JSON shape: `{ title, body, labels?, platform?, appVersion?, distribution? }`.
Android sends `platform: "android"` and `appVersion`. `distribution` is reserved
for Task 4 flavor work (Play vs F-Droid).

Worker routing: `platform=ios` → `osrswiki/osrswiki-ios`; otherwise →
`osrswiki/osrswiki-android`.

Worker source/deploy: `tools/feedback-worker/` (wrangler).

## Required host

`FeedbackActivity.kt` must host the secure fragment:

```kotlin
FeedbackFragmentSecure.newInstance()
```

Do not restore direct-GitHub clients or embed a GitHub token in the app.

## Testing

1. Submit a test bug report
2. Submit a test feature request
3. Verify issues appear under the routed repo on GitHub

## Benefits

- Secure: GitHub token never exposed in app code
- Reliable: Server-side error handling and rate limiting
- Maintainable: Rotate token without an app release
