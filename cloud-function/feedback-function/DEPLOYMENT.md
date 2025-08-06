# Cloud Function Deployment Guide

This document outlines the steps to deploy the serverless function that securely forwards feedback from the Android app to a GitHub repository as an issue.

## 1. GitHub Preparation

Before deploying, you must create a GitHub Personal Access Token (PAT) and store it securely.

### Generate a GitHub PAT (Classic):

1. Navigate to your GitHub Developer settings -> Personal access tokens -> Tokens (classic).
2. Click Generate new token (classic).
3. Give it a descriptive name (e.g., osrs-wiki-app-feedback).
4. Set the desired Expiration.
5. Under Select scopes, check the single box for `public_repo`. This is the only permission required.
6. Generate the token and immediately copy the `ghp_...` string.

### Store the PAT in Google Secret Manager:

1. In the Google Cloud Console for your project, navigate to Security -> Secret Manager.
2. Enable the API if prompted.
3. Click Create Secret, name it `github-pat-android`, and paste the PAT as the secret value.

## 2. Google Cloud SDK Setup

### Install the gcloud CLI:

Follow the official instructions: https://cloud.google.com/sdk/docs/install

**Note for WSL users:** It is highly recommended to use the tarball archive method instead of apt to avoid potential system compatibility issues.

### Authenticate the CLI:

Run both authentication commands to log in for user access and application access.

```bash
gcloud auth login
gcloud auth application-default login
```

### Set Your Project Configuration:

Find your Project ID (e.g., osrs-459713) by running `gcloud projects list`.

Set it as the default for all subsequent commands. Replace YOUR_PROJECT_ID below.

```bash
gcloud config set project YOUR_PROJECT_ID
```

## 3. First-Time Project Setup (IAM & APIs)

If this is the first function being deployed in the project, you must enable the required APIs and grant permissions.

### Enable Required APIs:
The gcloud CLI will prompt you to enable these, but you can also do it manually.

- Cloud Functions API
- Cloud Build API  
- Secret Manager API

### Grant IAM Permissions:
Run the following commands to give the necessary service accounts the roles they need to operate. Replace YOUR_PROJECT_ID and YOUR_PROJECT_NUMBER accordingly.

```bash
# Allows the function to read the secret from Secret Manager
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:YOUR_PROJECT_ID@appspot.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"

# Allows the Cloud Build service to build and deploy the function
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:YOUR_PROJECT_NUMBER@cloudbuild.gserviceaccount.com" \
  --role="roles/cloudbuild.serviceAgent"
```

## 4. Deploy the Function

Navigate to the directory containing index.js and package.json and run the deployment command.

```bash
# Deploys the function, making it public and linking it to the secret
gcloud functions deploy createGithubIssue \
  --runtime nodejs20 \
  --trigger-http \
  --allow-unauthenticated \
  --set-secrets "GITHUB_PAT=github-pat-android:latest" \
  --region us-central1
```

**Note:** Since the project is set in the config, you no longer need the --project flag in every command.

## 5. Testing and Monitoring

### Test with curl:
After deployment, find the httpsTrigger.url in the output and test it.

```bash
curl -X POST YOUR_FUNCTION_URL \
  -H "Content-Type: application/json" \
  -d '{"title": "Test Issue", "body": "This is a test from the Cloud Function"}'
```

A successful response is `{"message":"Issue created successfully."}`.

### View Logs:
To debug, view the function's logs.

```bash
gcloud functions logs read createGithubIssue --region us-central1 --limit 50
```

## 6. Troubleshooting

**500 Internal Server Error with 401 Bad credentials in logs:** This means your GitHub token is incorrect. The most likely cause is a copy-paste error. Delete the token on GitHub, generate a new one, and add it as a new version to your secret in Secret Manager. Redeploying the function is not required.