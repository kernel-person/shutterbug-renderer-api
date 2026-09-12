# Attachments

RGBA8 color is always present. Request optional `DEPTH`, `NORMAL`, `MATERIAL_ID`, or `OBJECT_ID` only when `supportedAttachments()` advertises it. An unrequested accessor returns `null`; result arrays are caller-owned copies. See the [complete sample plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.0/sample-plugin).
