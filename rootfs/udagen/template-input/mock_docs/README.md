# Mock API & assumptions (optional)

Provide structured contracts when the app needs predictable API shapes or rich personalization.

## Typical files

| File | Purpose |
|------|---------|
| `openapi.yaml` | REST endpoints the frontend will call (mock server serves these) |
| `assumptions.json` | Personas, layout rules, UI actions → API mappings |
| `schemas/*.json` | JSON Schema for core entities |

If this directory is empty, UDAGen can **auto-generate** mock docs from the seed `--idea` during `uda.generate`.

## Minimal OpenAPI sketch

```yaml
openapi: 3.0.3
info:
  title: My App API
  version: 1.0.0
paths:
  /search:
    get:
      summary: Search by keyword
      parameters:
        - name: q
          in: query
          schema: { type: string }
      responses:
        '200':
          description: Results
```

See shipped demos under `aohp-app/UDA/*/mock_docs/` in the AOHP repo for full examples.
