#!/usr/bin/env python3
"""Exports the in-app privacy policy and terms (LegalDocuments.kt) to docs/legal/ as Markdown and HTML.

Play Store needs the privacy policy at a public URL; host docs/legal/privacy-policy.html (e.g. GitHub Pages).
Re-run after editing LegalDocuments.kt so the hosted copy always matches the app:
    python3 scripts/export_legal_docs.py
"""
import html
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "app/src/main/java/com/bookflow/app/presentation/screens/legal/LegalDocuments.kt"
OUT = ROOT / "docs/legal"

src = SOURCE.read_text()
info = dict(re.findall(r'const val (\w+) = "([^"]*)"', src))


def kotlin_strings(fragment):
    """Concatenates the Kotlin string literals in an expression like "a" + "b", resolving escapes and templates."""
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', fragment)
    text = "".join(parts)
    text = text.replace('\\n', "\n").replace('\\"', '"').replace("\\$", "$")
    return re.sub(r"\$\{LegalInfo\.(\w+)\}", lambda m: info[m.group(1)], text)


def documents():
    for m in re.finditer(r'(\w+)\(\s*route = "(\w+)",\s*title = "([^"]+)",\s*sections = listOf\((.*?)\n        \)\n    \)', src, re.S):
        sections = []
        for s in re.finditer(r"LegalSection\(\s*(\"(?:[^\"\\]|\\.)*\"),\s*(.*?)\n            \)", m.group(4), re.S):
            sections.append((kotlin_strings(s.group(1)), kotlin_strings(s.group(2))))
        yield m.group(2), m.group(3), sections


OUT.mkdir(parents=True, exist_ok=True)
names = {"privacy": "privacy-policy", "terms": "terms-of-use"}
for route, title, sections in documents():
    name = names.get(route, route)
    md = [f"# {title}", "", f"_{info['PUBLISHER']} · Effective {info['EFFECTIVE_DATE']}_", ""]
    for heading, body in sections:
        md += [f"## {heading}", "", body, ""]
    (OUT / f"{name}.md").write_text("\n".join(md))

    body_html = "".join(
        f"<h2>{html.escape(h)}</h2>" + "".join(f"<p>{html.escape(p).replace(chr(10), '<br>')}</p>" for p in b.split("\n\n"))
        for h, b in sections
    )
    (OUT / f"{name}.html").write_text(f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)} – {html.escape(info['PUBLISHER'])}</title>
<style>
:root {{ color-scheme: light dark; }}
body {{ font: 16px/1.6 -apple-system, system-ui, Roboto, sans-serif; max-width: 720px; margin: 0 auto; padding: 32px 20px; color: #101326; background: #fff; }}
h1 {{ font-size: 28px; margin-bottom: 4px; }} h2 {{ font-size: 18px; margin-top: 28px; }}
.meta {{ color: #666b85; margin-top: 0; }}
@media (prefers-color-scheme: dark) {{ body {{ color: #f1f5f9; background: #0b1120; }} .meta {{ color: #a3b1c6; }} }}
</style></head>
<body><h1>{html.escape(title)}</h1><p class="meta">{html.escape(info['PUBLISHER'])} · Effective {html.escape(info['EFFECTIVE_DATE'])}</p>{body_html}</body></html>
""")
    print(f"wrote docs/legal/{name}.md and .html ({len(sections)} sections)")
