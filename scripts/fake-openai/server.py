#!/usr/bin/env python3
"""OpenAI-compatible chat stub for the local trip-agent demo.

The first completion in a turn returns tool_calls for searchActivities and
checkAvailability. The follow-up, which carries those tool results, returns
the itinerary JSON the agent parses. No network and no provider key.

Spring's JDK client speaks HTTP/1.1. It may send Expect: 100-continue and a
chunked body. Both are handled here; otherwise the agent records a 502.
"""

from __future__ import annotations

import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "127.0.0.1"
PORT = 8099
TOOL_ROLE = re.compile(r'"role"\s*:\s*"tool"')

DEFAULT_ITINERARY = {
    "answer": (
        "Three family days in Lisbon: the aquarium if the weather turns, "
        "Belém Tower with skip-the-line entry, and a pastel workshop."
    ),
    "participants": 2,
    "itinerary": [
        {
            "day": 1,
            "date": "2026-11-21",
            "activities": [
                {
                    "activityId": "lis-oceanario",
                    "rationale": "Rain-proof and built for kids.",
                }
            ],
        },
        {
            "day": 2,
            "date": "2026-11-22",
            "activities": [
                {
                    "activityId": "lis-belem-tower",
                    "rationale": "Skip-the-line entry before the crowds.",
                }
            ],
        },
        {
            "day": 3,
            "date": "2026-11-23",
            "activities": [
                {
                    "activityId": "lis-pastel-workshop",
                    "rationale": "A hands-on food stop that still fits a mid budget.",
                }
            ],
        },
    ],
    "crossSellIds": ["lis-tram-28"],
    "objectionNotes": ["The aquarium stays open if it rains, and the tower ticket skips the line."],
}

INDOOR_ITINERARY = {
    "answer": (
        "Day 2 is now the National Tile Museum, which is indoors if it rains. "
        "The aquarium and the pastel workshop stay on the Lisbon plan."
    ),
    "participants": 2,
    "itinerary": [
        {
            "day": 1,
            "date": "2026-11-21",
            "activities": [
                {
                    "activityId": "lis-oceanario",
                    "rationale": "Still the kid-friendly indoor start.",
                }
            ],
        },
        {
            "day": 2,
            "date": "2026-11-22",
            "activities": [
                {
                    "activityId": "lis-tile-museum",
                    "rationale": "Swap the outdoor tower for a covered museum if it rains.",
                }
            ],
        },
        {
            "day": 3,
            "date": "2026-11-23",
            "activities": [
                {
                    "activityId": "lis-pastel-workshop",
                    "rationale": "The workshop is indoors and still on the mid budget.",
                }
            ],
        },
    ],
    "crossSellIds": ["lis-oceanario"],
    "objectionNotes": ["Day 2 is indoors, so rain does not cancel it."],
}


class ChatHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_POST(self) -> None:  # noqa: N802
        if self.path.split("?", 1)[0] != "/v1/chat/completions":
            self._send(404, {"error": {"message": "Not found", "type": "invalid_request_error"}})
            return
        raw = self._read_body()
        try:
            payload = json.loads(raw.decode("utf-8"))
        except json.JSONDecodeError:
            self._send(400, {"error": {"message": "Request body is not JSON", "type": "invalid_request_error"}})
            return
        text = raw.decode("utf-8", errors="replace")
        user_text = self._latest_user_text(payload)
        model = payload.get("model") or "gpt-4o-mini"
        if TOOL_ROLE.search(text):
            body = self._completion(model, json.dumps(self._itinerary(user_text)), tool_calls=None)
        else:
            body = self._completion(model, "", tool_calls=self._tool_calls(user_text))
        self._send(200, body)

    def _latest_user_text(self, payload: dict) -> str:
        # Tool results echo catalog copy such as "indoors". Only the traveler's
        # latest message decides which itinerary to return.
        for message in reversed(payload.get("messages") or []):
            if message.get("role") != "user":
                continue
            content = message.get("content")
            if isinstance(content, str):
                return content
            if isinstance(content, list):
                parts = []
                for part in content:
                    if isinstance(part, dict) and isinstance(part.get("text"), str):
                        parts.append(part["text"])
                return " ".join(parts)
        return ""

    def _wants_indoor(self, user_text: str) -> bool:
        lowered = user_text.lower()
        return "indoor" in lowered or "rains" in lowered or "if it rain" in lowered

    def _itinerary(self, user_text: str) -> dict:
        if self._wants_indoor(user_text):
            return INDOOR_ITINERARY
        return DEFAULT_ITINERARY

    def _tool_calls(self, user_text: str) -> list[dict]:
        indoor = self._wants_indoor(user_text)
        availability = {
            "activityId": "lis-tile-museum" if indoor else "lis-oceanario",
            "date": "2026-11-22" if indoor else "2026-11-21",
            "participants": 2,
        }
        return [
            {
                "id": "call_search",
                "type": "function",
                "function": {
                    "name": "searchActivities",
                    "arguments": json.dumps(
                        {
                            "city": "Lisbon",
                            "category": "family",
                            "maxPrice": 80,
                            "date": "2026-11-21",
                        }
                    ),
                },
            },
            {
                "id": "call_availability",
                "type": "function",
                "function": {
                    "name": "checkAvailability",
                    "arguments": json.dumps(availability),
                },
            },
        ]

    def _completion(self, model: str, content: str, tool_calls: list[dict] | None) -> dict:
        message: dict = {"role": "assistant", "content": content}
        if tool_calls:
            message["tool_calls"] = tool_calls
        return {
            "id": "chatcmpl-stub",
            "object": "chat.completion",
            "created": 1_763_385_600,
            "model": model,
            "choices": [
                {
                    "index": 0,
                    "finish_reason": "tool_calls" if tool_calls else "stop",
                    "message": message,
                }
            ],
            "usage": {"prompt_tokens": 24, "completion_tokens": 16, "total_tokens": 40},
        }

    def _read_body(self) -> bytes:
        # parse_request already answered Expect: 100-continue when the
        # protocol is HTTP/1.1. Read the body only after that reply.
        transfer = self.headers.get("Transfer-Encoding", "")
        if "chunked" in transfer.lower():
            return self._read_chunked()
        length = int(self.headers.get("Content-Length", "0") or "0")
        if length < 0:
            return b""
        return self.rfile.read(length)

    def _read_chunked(self) -> bytes:
        chunks: list[bytes] = []
        while True:
            line = self.rfile.readline()
            if not line:
                break
            size_token = line.strip().split(b";", 1)[0]
            if not size_token:
                continue
            size = int(size_token, 16)
            if size == 0:
                while True:
                    trailer = self.rfile.readline()
                    if trailer in (b"\r\n", b"\n", b""):
                        break
                break
            data = self.rfile.read(size)
            chunks.append(data)
            self.rfile.read(2)
        return b"".join(chunks)

    def _send(self, status: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt: str, *args) -> None:
        print(f"[fake-openai] {self.address_string()} {fmt % args}", flush=True)


def main() -> None:
    server = ThreadingHTTPServer((HOST, PORT), ChatHandler)
    print(f"fake openai listening on http://{HOST}:{PORT}/v1/chat/completions", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
