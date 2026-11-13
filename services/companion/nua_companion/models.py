from __future__ import annotations
import json
import os
import urllib.request
from dataclasses import dataclass
from typing import Any

@dataclass(frozen=True)
class ModelEndpoint:
    id: str
    provider: str
    base_url: str
    model: str

def configured_models() -> list[ModelEndpoint]:
    models: list[ModelEndpoint] = []
    ollama = os.getenv("NUA_OLLAMA_URL")
    if ollama:
        models.append(ModelEndpoint("ollama", "ollama", ollama.rstrip("/"), os.getenv("NUA_OLLAMA_MODEL", "llama3.2")))
    openai = os.getenv("NUA_LOCAL_OPENAI_URL")
    if openai:
        models.append(ModelEndpoint("local-openai", "openai-compatible", openai.rstrip("/"), os.getenv("NUA_LOCAL_OPENAI_MODEL", "local-model")))
    return models

def complete(endpoint: ModelEndpoint, prompt: str, timeout: float = 60.0) -> str:
    if endpoint.provider == "ollama":
        body = json.dumps({"model": endpoint.model, "prompt": prompt, "stream": False}).encode()
        req = urllib.request.Request(endpoint.base_url + "/api/generate", data=body, headers={"content-type": "application/json"})
        with urllib.request.urlopen(req, timeout=timeout) as response:
            data = json.load(response)
        return str(data.get("response", ""))

    body = json.dumps({
        "model": endpoint.model,
        "messages": [{"role": "user", "content": prompt}],
        "stream": False,
    }).encode()
    req = urllib.request.Request(endpoint.base_url + "/v1/chat/completions", data=body, headers={"content-type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as response:
        data: dict[str, Any] = json.load(response)
    return str(data["choices"][0]["message"]["content"])
