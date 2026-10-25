#!/usr/bin/env python3
"""Build the offline handbook from README.md and docs/architecture.md.

Run from any directory:
    python -m venv .venv-docs
    .venv-docs/bin/pip install -r docs/tools/requirements.txt
    .venv-docs/bin/python docs/tools/build_handbook.py

WeasyPrint also needs its platform libraries (notably Pango). See its installation
guide: https://doc.courtbouillon.org/weasyprint/stable/first_steps.html
Rendering never downloads remote resources. Only repository image files are read.
"""

from __future__ import annotations

import argparse
from collections import Counter
from html import escape
from pathlib import Path
import re
from urllib.parse import quote, unquote, urlsplit

from markdown_it import MarkdownIt
from weasyprint import HTML, default_url_fetcher


ROOT = Path(__file__).resolve().parents[2]
SOURCES = [(ROOT / "README.md", "guide"), (ROOT / "docs/architecture.md", "design")]
DEFAULT_REPOSITORY_URL = "https://github.com/ideki4444/JAVA_GIU_SWING_DATABASE_PT/blob/main"

CSS = """
@page {
  size: A4;
  margin: 18mm 16mm 18mm;
  @top-left { content: "AQUATRACK  /  LEARNING HANDBOOK"; font: 7pt sans-serif; color: #48707e; }
  @bottom-left { content: "Setup • Operations • Architecture"; font: 7pt sans-serif; color: #48707e; }
  @bottom-right { content: counter(page); font: 8pt sans-serif; color: #12364b; }
}
@page cover { margin: 22mm; @top-left { content: none; } @bottom-left { content: none; } @bottom-right { content: none; } }
* { box-sizing: border-box; }
html { font: 9.7pt / 1.48 "DejaVu Sans", sans-serif; color: #183747; }
body { margin: 0; }
a { color: #067e83; text-decoration: none; overflow-wrap: anywhere; }
strong { color: #143747; }
p { margin: 0 0 3mm; orphans: 3; widows: 3; }
h1, h2, h3, h4 { color: #10394f; line-height: 1.2; break-after: avoid; }
h1 { font-size: 24pt; margin: 0 0 8mm; bookmark-level: 1; }
h2 { font-size: 16pt; margin: 8mm 0 4mm; padding-bottom: 2mm; border-bottom: 1pt solid #b9dbdc; bookmark-level: 2; }
h3 { font-size: 12pt; margin: 6mm 0 3mm; bookmark-level: 3; }
h4 { font-size: 10.4pt; margin: 5mm 0 2mm; bookmark-level: 4; }
ul, ol { padding-left: 5.5mm; margin: 1mm 0 4mm; }
li { margin: 0 0 1.5mm; }
li > p { margin-bottom: 1.5mm; }
table { border-collapse: collapse; width: 100%; margin: 4mm 0 5mm; font-size: 8.2pt; line-height: 1.4; }
thead { display: table-header-group; }
th { background: #12394e; color: white; font-weight: 700; text-align: left; }
th strong, th a { color: white; }
th, td { padding: 2.1mm 2.3mm; border: 0.5pt solid #c5dbdf; vertical-align: top; overflow-wrap: anywhere; }
th:first-child, td:first-child { min-width: 28mm; }
tr { break-inside: avoid; }
tbody tr:nth-child(even) { background: #f0f7f7; }
code { font-family: "DejaVu Sans Mono", monospace; font-size: 0.85em; overflow-wrap: anywhere; }
:not(pre) > code { color: #11656c; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; font: 7.6pt / 1.45 "DejaVu Sans Mono", monospace;
      background: #eef5f6; border-left: 2.5pt solid #0c8b8a; padding: 3mm; margin: 3mm 0 4mm; }
pre code { font-size: inherit; color: #193a4b; }
blockquote { border-left: 3pt solid #dba72e; margin: 4mm 0; padding: 2mm 4mm; background: #fff8e5; }
figure { margin: 3mm 0 4mm; break-inside: avoid; }
img { display: block; width: 100%; max-height: 200mm; object-fit: contain; }
hr { border: 0; border-top: 1pt solid #bfd7dc; margin: 6mm 0; }
.cover { page: cover; height: 246mm; position: relative; break-after: page; }
.eyebrow { padding-top: 12mm; text-transform: uppercase; letter-spacing: 2pt; color: #087c81; font-size: 9pt; font-weight: bold; }
.cover h1 { font-size: 51pt; margin: 20mm 0 3mm; letter-spacing: -2pt; bookmark-level: none; }
.subtitle { font-size: 23pt; line-height: 1.25; color: #087b81; margin-bottom: 14mm; }
.cover-intro { font-size: 12pt; line-height: 1.65; max-width: 145mm; }
.cover-band { margin-top: 16mm; border-top: 3pt solid #e6b139; padding-top: 6mm; }
.cover-band p { font-size: 10pt; margin-bottom: 4mm; }
.cover-footer { position: absolute; bottom: 4mm; font-size: 8.5pt; line-height: 1.7; color: #517381; }
.toc { break-after: page; }
.toc h1 { bookmark-level: none; }
.toc ul { list-style: none; padding: 0; }
.toc li { margin: 0; }
.toc a { display: block; padding: 0.55mm 0; color: #183747; }
.toc a::after { content: leader(".") target-counter(attr(href), page); }
.toc .level-1 { font-weight: bold; font-size: 11pt; margin: 4mm 0 1mm; }
.toc .level-2 { font-size: 9pt; padding-left: 4mm; }
.chapter { break-before: page; }
.chapter > h1:first-child { margin-top: 0; }
.source-note { font-size: 8.5pt; color: #4b6e7c; }
"""


def slug(text: str) -> str:
    """Match GitHub's ordinary heading anchors, including Unicode letters."""
    text = text.strip().lower()
    text = re.sub(r"[^\w\-\s]", "", text)
    return re.sub(r"\s", "-", text)


def plain_inline(children) -> str:
    return "".join(t.content for t in (children or []) if t.type in {"text", "code_inline", "image"})


def local_fetcher(url, *args, **kwargs):
    parsed = urlsplit(url)
    if parsed.scheme != "file":
        raise ValueError(f"Remote resource loading is disabled: {url}")
    path = Path(unquote(parsed.path)).resolve()
    if not path.is_relative_to(ROOT):
        raise ValueError(f"Resource outside repository: {path}")
    return default_url_fetcher(url, *args, **kwargs)


def build(output: Path, repository_url: str) -> None:
    md = MarkdownIt("commonmark", {"html": True}).enable("table").enable("strikethrough")
    parsed_docs = []
    headings = []
    first_ids = {}
    image_paths = set()
    linked_paths = set()
    anchor_ids = set()

    for path, prefix in SOURCES:
        if not path.is_file():
            raise SystemExit(f"Missing documentation source: {path.relative_to(ROOT)}")
        tokens = md.parse(path.read_text(encoding="utf-8"))
        seen = Counter()
        for index, token in enumerate(tokens):
            if token.type != "heading_open":
                continue
            title = plain_inline(tokens[index + 1].children)
            base = slug(title)
            suffix = f"-{seen[base]}" if seen[base] else ""
            seen[base] += 1
            anchor = f"{prefix}-{base}{suffix}"
            token.attrSet("id", anchor)
            anchor_ids.add(anchor)
            first_ids.setdefault(path, anchor)
            if int(token.tag[1:]) <= 2:
                headings.append((int(token.tag[1:]), title, anchor))
        parsed_docs.append((path, prefix, tokens))

    prefixes = {path: prefix for path, prefix in SOURCES}

    def rewrite_link(href: str, source: Path, prefix: str) -> str:
        parsed = urlsplit(href)
        if parsed.scheme or href.startswith("//"):
            return href
        if not parsed.path:
            return f"#{prefix}-{parsed.fragment}" if parsed.fragment else href
        target = (source.parent / unquote(parsed.path)).resolve()
        if not target.is_relative_to(ROOT):
            raise ValueError(f"Link leaves repository: {href} in {source.name}")
        if not target.exists() and target != ROOT / "docs/downloads/AquaTrack-Handbook.pdf":
            raise ValueError(f"Broken local link: {href} in {source.relative_to(ROOT)}")
        linked_paths.add(target)
        if target in prefixes:
            return f"#{prefixes[target]}-{parsed.fragment}" if parsed.fragment else f"#{first_ids[target]}"
        relative = target.relative_to(ROOT).as_posix()
        result = repository_url.rstrip("/") + "/" + quote(relative, safe="/")
        if parsed.fragment:
            result += "#" + parsed.fragment
        return result

    chapters = []
    for path, prefix, tokens in parsed_docs:
        for token in tokens:
            for child in token.children or []:
                if child.type == "link_open":
                    child.attrSet("href", rewrite_link(child.attrGet("href"), path, prefix))
                if child.type == "image":
                    src = child.attrGet("src")
                    if urlsplit(src).scheme or src.startswith("//"):
                        raise ValueError(f"Only local images may be rendered: {src}")
                    image = (path.parent / unquote(src)).resolve()
                    if not image.is_relative_to(ROOT) or not image.is_file():
                        raise ValueError(f"Missing or disallowed image: {src}")
                    image_paths.add(image)
                    child.attrSet("src", image.as_uri())
        html = md.renderer.render(tokens, md.options, {})
        html = re.sub(r"<p>(<img\b[^>]*>)</p>", r"<figure>\1</figure>", html)
        for anchor in re.findall(r'href="#([^\"]+)"', html):
            if anchor not in anchor_ids:
                raise ValueError(f"Broken heading link: #{anchor} in {path.relative_to(ROOT)}")
        chapters.append(f'<section class="chapter">{html}</section>')

    toc = "".join(
        f'<li class="level-{level}"><a href="#{anchor}">{escape(title)}</a></li>'
        for level, title, anchor in headings
    )
    markup = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<title>AquaTrack — Complete User and Developer Handbook</title>
<meta name="author" content="AquaTrack project documentation">
<meta name="description" content="AquaTrack installation, daily operations, infographics, and source-based design explanation.">
<style>{CSS}</style></head><body>
<section class="cover">
  <div class="eyebrow">Java Swing · JDBC · MySQL</div>
  <h1>AquaTrack</h1>
  <div class="subtitle">Use the system.<br>Understand the code.</div>
  <p class="cover-intro">A complete learning handbook for the water-refilling station application:
  from first installation to daily sales, deliveries, stock, customer balances, and the design behind each operation.</p>
  <div class="cover-band">
    <p><strong>OPERATE</strong> &nbsp; Install, sign in, configure, and complete everyday work.</p>
    <p><strong>UNDERSTAND</strong> &nbsp; Follow the infographics, formulas, data model, and transaction flow.</p>
    <p><strong>MAINTAIN</strong> &nbsp; Read the source map, tradeoffs, limits, and extension guidance.</p>
  </div>
  <div class="cover-footer">Offline edition · Searchable text · Clickable contents and source links<br>
  Generated from the repository README and architecture guide.<br>
  Repository links require an internet connection; all handbook diagrams are embedded.</div>
</section>
<nav class="toc"><h1>Inside this handbook</h1>
<p class="source-note">Use these links or your PDF reader's bookmarks to jump to a topic.</p>
<ul>{toc}</ul></nav>
{''.join(chapters)}
</body></html>"""
    output.parent.mkdir(parents=True, exist_ok=True)
    document = HTML(string=markup, base_url=ROOT.as_uri() + "/", url_fetcher=local_fetcher).render()
    document.write_pdf(output)
    print(f"Built {output.relative_to(ROOT) if output.is_relative_to(ROOT) else output}")
    print(f"{len(document.pages)} pages; {len(headings)} contents entries; "
          f"{len(image_paths)} unique local images; {len(linked_paths)} checked local link targets; "
          f"{output.stat().st_size:,} bytes")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "docs/downloads/AquaTrack-Handbook.pdf")
    parser.add_argument("--repository-url", default=DEFAULT_REPOSITORY_URL,
                        help="GitHub blob base URL for source hyperlinks (defaults to main).")
    args = parser.parse_args()
    build(args.output.resolve(), args.repository_url)


if __name__ == "__main__":
    main()
