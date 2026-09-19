import unittest
from unittest.mock import patch

from ca_orchestrator.github_client import GitHubClient


class GitHubClientTests(unittest.TestCase):
    def test_rerun_failed_jobs_posts_to_actions_endpoint(self):
        client = GitHubClient("owner/repo", "token")
        with patch.object(client, "_request", return_value=b"") as request:
            client.rerun_failed_jobs(12345)

        request.assert_called_once_with(
            "/repos/owner/repo/actions/runs/12345/rerun-failed-jobs",
            method="POST",
        )


if __name__ == "__main__":
    unittest.main()
