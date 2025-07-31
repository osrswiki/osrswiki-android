const axios = require('axios');

// GitHub repository details
const GITHUB_USERNAME = 'omiyawaki';
const GITHUB_REPO = 'osrswiki-android';

/**
 * A Google Cloud Function that receives feedback via an HTTP POST request
 * and creates an issue in a GitHub repository.
 * The request body should be a JSON object with "title" and "body" fields.
 */
exports.createGithubIssue = async (req, res) => {
  // Set CORS headers to allow requests from any origin.
  // This is necessary for the function to be callable from the web client in the app.
  res.set('Access-Control-Allow-Origin', '*');

  if (req.method === 'OPTIONS') {
    // This is a preflight request for CORS.
    res.set('Access-Control-Allow-Methods', 'POST');
    res.set('Access-Control-Allow-Headers', 'Content-Type');
    res.set('Access-Control-Max-Age', '3600');
    res.status(204).send('');
    return;
  }

  // Ensure the request is a POST request.
  if (req.method !== 'POST') {
    res.status(405).send('Method Not Allowed');
    return;
  }
  
  const { title, body } = req.body;

  // Validate that title and body are present in the request.
  if (!title || !body) {
    res.status(400).send('Bad Request: Missing title or body in request JSON.');
    return;
  }

  // The GitHub PAT is securely accessed as an environment variable.
  // It is populated by the Secret Manager integration during deployment.
  const githubToken = process.env.GITHUB_PAT;
  const githubApiUrl = `https://api.github.com/repos/${GITHUB_USERNAME}/${GITHUB_REPO}/issues`;

  try {
    // Make the authenticated API call to GitHub to create the issue.
    await axios.post(githubApiUrl, {
      title: title,
      body: body
    }, {
      headers: {
        'Authorization': `token ${githubToken}`,
        'Accept': 'application/vnd.github.v3+json',
        'Content-Type': 'application/json'
      }
    });
    res.status(200).send({ message: 'Issue created successfully.' });
  } catch (error) {
    console.error('Error creating GitHub issue:', error.response ? error.response.data : error.message);
    res.status(500).send({ message: 'Internal Server Error: Failed to create GitHub issue.' });
  }
};