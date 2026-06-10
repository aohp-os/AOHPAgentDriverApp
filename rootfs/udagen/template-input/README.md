# UDAGen input bundle (template)

This directory is the default `--input-dir` for `python3 -m udagen run` on AOHP devices.

Agents can either:

1. **Idea-only** — leave this template as-is; UDAGen auto-generates `mock_docs/` from `--idea`.
2. **Structured input** — copy this tree into a per-job input dir and edit files before `uda.generate`.

## Supported file types

`.md`, `.txt`, `.json`, `.yaml`, `.yml`, `.csv`, `.tsv`

## Recommended layout

```
input/
  README.md                 # optional notes for the pipeline
  requirements/
    idea.md                 # natural-language product brief
  app/
    manifest.json           # launcher metadata (name, theme_color, icon path)
  mock_docs/                # optional API contracts (skip if idea-only)
    README.md
    openapi.yaml
    assumptions.json
    schemas/
      Entity.json
```

## Per-job staging on device

Host path (bind-mounted): `/data/aohp/shared/uda/<jobId>/input/`

Container path (UDA sandbox): `/opt/udagen/workspace/<jobId>/input/`

Use `aohp uda input init` and `aohp uda input write` before `aohp uda generate -j <jobId>`.
