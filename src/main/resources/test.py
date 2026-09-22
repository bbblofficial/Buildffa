import os
import sys
import json
import requests

# --- CONFIGURATION ---
BASE_URL = "https://notrack.ai"
SESSION_COOKIE_ID = os.getenv("NOTRACK_SESSION_ID", "559th44P_3uItX")

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                  "(KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36",
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "en-US,en;q=0.9",
    "Origin": BASE_URL,
    "Referer": f"{BASE_URL}/chat",
    "Content-Type": "application/json",
    "Sec-Fetch-Dest": "empty",
    "Sec-Fetch-Mode": "cors",
    "Sec-Fetch-Site": "same-origin",
    "priority": "u=1, i",
}

COOKIES = {
    "si_usr_id":  "557WGcrl_1CKVuu",
    "uid":        "0027dec1-80ed-4079-ab31-be853b1d041d",
    "nt_src":     "chatlink",
    "si_ses_id":  SESSION_COOKIE_ID,
    "nt_api_ref": "1",
}


def build_session() -> requests.Session:
    s = requests.Session()
    s.headers.update(HEADERS)
    s.cookies.update(COOKIES)
    return s


def base_payload(prompt: str, chat_id):
    """Exactly the object you captured from DevTools."""
    return {
        "user_input":  prompt,
        "mode":        "usual",
        "model":       "C",
        "persona":     "creative",
        "max_turns":   6,
        "chat_id":     chat_id,
        "edit":        False,
        "edit_mid":    None,
        "regenerate":  False,
        "via":         "typed",
        "attachments": [],
    }


# Candidate bodies — all based on your real capture, only differing in the
# bits the friendly view might have hidden (stream flag, extra id, etc.).
def candidate_payloads(prompt: str, chat_id):
    base = base_payload(prompt, chat_id)
    yield "exact-capture", base
    yield "with-stream",  {**base, "stream": True}
    yield "no-max-turns", {k: v for k, v in base.items() if k != "max_turns"}
    yield "no-mode",      {k: v for k, v in base.items() if k != "mode"}
    yield "minimal",      {"user_input": prompt, "chat_id": chat_id}
    yield "nested",       {"data": base}


def debug_post(prompt: str, chat_id):
    """Send each candidate and show status + first bytes of the response."""
    s = build_session()
    url = f"{BASE_URL}/api/chat"
    print(f"[*] POST {url}")
    print(f"[*] Headers: {json.dumps(dict(s.headers), indent=2)}")
    print(f"[*] Cookies: {s.cookies.get_dict()}\n")

    for name, body in candidate_payloads(prompt, chat_id):
        print(f"--- candidate: {name} ---")
        print("BODY:", json.dumps(body))
        try:
            r = s.post(url, json=body, timeout=30, stream=True)
        except Exception as e:
            print("ERR:", e, "\n")
            continue
        ct = r.headers.get("content-type", "")
        peek = next(r.iter_content(chunk_size=400), b"")[:200]
        print(f"STATUS: {r.status_code}  CT: {ct}")
        print(f"BODY:   {peek!r}\n")
        r.close()


def chat(prompt: str, chat_id):
    """Send once with the exact capture, stream the reply if SSE."""
    s = build_session()
    url = f"{BASE_URL}/api/chat"
    body = base_payload(prompt, chat_id)

    r = s.post(url, json=body, timeout=120, stream=True)

    if r.status_code != 200:
        print(f"HTTP {r.status_code}: {r.text[:500]}")
        print("\nHint: run `python test.py debug \"<prompt>\"` to try variants.")
        return

    ct = r.headers.get("content-type", "")

    if "event-stream" in ct:
        for line in r.iter_lines(decode_unicode=True):
            if not line:
                continue
            if line.startswith("data:"):
                data = line[5:].strip()
                if data in ("[DONE]", ""):
                    break
                try:
                    obj = json.loads(data)
                except json.JSONDecodeError:
                    print(data, end="", flush=True)
                    continue
                for k in ("delta", "content", "token", "text", "response", "message"):
                    v = obj.get(k)
                    if isinstance(v, str):
                        print(v, end="", flush=True)
                        break
        print()
        return

    # Non-streamed JSON
    try:
        data = r.json()
    except json.JSONDecodeError:
        print("RAW:", r.text[:2000])
        return
    print(json.dumps(data, indent=2)[:3000])


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage:")
        print("  python test.py chat  '<prompt>'")
        print("  python test.py chat  <chat_id> '<prompt>'")
        print("  python test.py debug '<prompt>'")
        sys.exit(1)

    cmd = sys.argv[1]
    rest = sys.argv[2:]

    # Optional chat_id as first arg
    if rest and len(rest[0]) == 36 and rest[0].count("-") == 4:
        chat_id, prompt_parts = rest[0], rest[1:]
    else:
        chat_id, prompt_parts = None, rest

    prompt = " ".join(prompt_parts) or "hi"

    if cmd == "chat":
        chat(prompt, chat_id)
    elif cmd == "debug":
        debug_post(prompt, chat_id)
    else:
        print("Unknown command:", cmd)