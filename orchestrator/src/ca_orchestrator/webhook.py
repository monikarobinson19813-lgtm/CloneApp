from __future__ import annotations

import hashlib
import hmac
import json
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Callable

from .ci_feedback import CiFeedbackProcessor


def valid_signature(secret: str, body: bytes, signature: str | None) -> bool:
    if not signature or not signature.startswith("sha256="):
        return False
    expected = "sha256=" + hmac.new(
        secret.encode("utf-8"),
        body,
        hashlib.sha256,
    ).hexdigest()
    return hmac.compare_digest(expected, signature)


def build_handler(
    processor: CiFeedbackProcessor,
    secret: str,
    on_event: Callable[[dict], None] | None = None,
):
    class GitHubWebhookHandler(BaseHTTPRequestHandler):
        server_version = "CloneAppWebhook/0.1"

        def log_message(self, format: str, *args) -> None:
            return

        def _json_response(self, status: int, payload: dict) -> None:
            body = json.dumps(payload, sort_keys=True).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self) -> None:
            if self.path != "/github/webhook":
                self._json_response(404, {"error": "not_found"})
                return

            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                self._json_response(400, {"error": "invalid_content_length"})
                return

            if length <= 0 or length > 2_000_000:
                self._json_response(413, {"error": "invalid_payload_size"})
                return

            raw_body = self.rfile.read(length)
            signature = self.headers.get("X-Hub-Signature-256")
            if not valid_signature(secret, raw_body, signature):
                self._json_response(401, {"error": "invalid_signature"})
                return

            delivery_id = self.headers.get("X-GitHub-Delivery")
            event_type = self.headers.get("X-GitHub-Event")
            if not delivery_id or not event_type:
                self._json_response(400, {"error": "missing_github_headers"})
                return

            try:
                payload = json.loads(raw_body.decode("utf-8"))
                if not isinstance(payload, dict):
                    raise ValueError("JSON payload must be an object")
                result = processor.handle(
                    delivery_id=delivery_id,
                    event_type=event_type,
                    payload=payload,
                    raw_body=raw_body,
                )
            except (KeyError, TypeError, ValueError) as exc:
                self._json_response(400, {"error": str(exc)})
                return
            except Exception as exc:
                self._json_response(500, {"error": str(exc)})
                return

            if on_event is not None and result.get("state") == "ROUTED":
                on_event(result)
            self._json_response(202, result)

    return GitHubWebhookHandler


def create_webhook_server(
    processor: CiFeedbackProcessor,
    secret: str,
    host: str,
    port: int,
    on_event: Callable[[dict], None] | None = None,
) -> HTTPServer:
    if not secret:
        raise ValueError("GitHub webhook secret is required")
    return HTTPServer(
        (host, port),
        build_handler(processor, secret, on_event=on_event),
    )
