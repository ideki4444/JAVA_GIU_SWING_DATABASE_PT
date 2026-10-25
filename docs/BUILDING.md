# Rebuild the AquaTrack learning materials

The root [README](../README.md) and [architecture chapter](architecture.md) are the text sources for the downloadable [PDF handbook](downloads/AquaTrack-Handbook.pdf). The five diagrams are editable SVGs generated from Python code. Rebuild the assets when their source material changes so the repository and offline edition agree.

## Requirements

- Python 3.10 or later, with `venv` and `pip`.
- The pinned Python packages in [tools/requirements.txt](tools/requirements.txt): Markdown-it-py and WeasyPrint.
- WeasyPrint's platform libraries, including Pango and Fontconfig, plus a readable sans-serif and monospace font. DejaVu Sans and DejaVu Sans Mono were used for the checked-in edition.
- No running database, Java compiler, browser, or remote rendering service is needed to build the documentation. Python dependencies are separate from the Java application's dependencies.

Follow [WeasyPrint's platform installation instructions](https://doc.courtbouillon.org/weasyprint/stable/first_steps.html) for the native libraries on Windows, macOS, or Linux. Installing the Python package alone is insufficient when Pango is missing. On Windows, using WSL with the Linux instructions is another option. Do not disable TLS verification to install dependencies.

## Generate the SVGs

From the repository root:

```sh
python3 docs/tools/build_infographics.py
```

On Windows, use `python` or `py -3` if `python3` is unavailable. This script uses only Python's standard library. Its source contains the diagram text, positions, colors, and SVG accessibility descriptions. Output goes to `docs/images/`; repeated runs with unchanged source produce identical SVG bytes.

## Generate the PDF

Linux/macOS:

```sh
python3 -m venv .venv-docs
.venv-docs/bin/python -m pip install -r docs/tools/requirements.txt
.venv-docs/bin/python docs/tools/build_handbook.py
```

Windows PowerShell, after installing the required platform libraries:

```powershell
py -3 -m venv .venv-docs
.venv-docs\Scripts\python.exe -m pip install -r docs/tools/requirements.txt
.venv-docs\Scripts\python.exe docs/tools/build_handbook.py
```

The output is `docs/downloads/AquaTrack-Handbook.pdf`. It contains a cover, clickable page-numbered contents, PDF bookmarks, the complete README, the architecture chapter, and all five embedded vector diagrams. Text is searchable/selectable. PDF readers vary in their support for links and bookmarks.

The exporter checks local Markdown link targets and internal heading anchors before rendering. Source-file links point to GitHub `main` by default; links between the included chapters become internal PDF jumps. Rendering loads image resources only from the repository and does not download remote images. An internet connection is needed later only if a reader chooses a GitHub or external reference link.

To make a preview whose code links point to a branch before it is merged:

```sh
.venv-docs/bin/python docs/tools/build_handbook.py \
  --repository-url https://github.com/ideki4444/JAVA_GIU_SWING_DATABASE_PT/blob/codex/aquatrack-learning-handbook \
  --output /tmp/AquaTrack-preview.pdf
```

Use a suitable writable output path on your platform. The committed edition uses `main` so its references follow the repository after merging. The build recreates the document from its sources, but byte-identical PDFs are not promised across operating systems, fonts, or renderer versions.

## Review the generated edition

1. Open the PDF and inspect its cover, contents, all five diagrams, code blocks, and dense tables. Confirm there are no clipped labels or missing glyphs.
2. Follow the contents links and the README/architecture cross-links. Open a source-code link with network access.
3. Search for a phrase from the final architecture section to confirm the chapter is included in full.
4. Check the README images on GitHub and open each SVG at full size. The surrounding prose and alt text provide a text explanation of each graphic.
5. Run `git diff --check` and commit the Markdown, scripts, SVGs, and PDF together. Keep `.venv-docs/` out of Git; it is ignored.

If Fontconfig reports a non-writable cache in a restricted environment, provide a writable cache directory for that build, for example `XDG_CACHE_HOME=/tmp/aquatrack-fontcache` on Linux. This is a documentation-build setting, not an AquaTrack runtime setting.

## Verification scope of this edition

The restored Java source compiled with a Java 17 release target, and the existing headless `LoginValidationTest` passed on Java 21. Documentation was reviewed against the source and fresh-install SQL. The SVGs were checked for valid XML and consistent regeneration, and the PDF was checked for layout, embedded visuals, text, bookmarks, and local links. A live MySQL installation, visible Swing session, physical printer, and end-to-end business transactions were not part of that verification.
