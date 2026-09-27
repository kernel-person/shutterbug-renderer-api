# Results, attachments, timings, and visibility

RGBA8 color is always present. Request optional `DEPTH`, `NORMAL`, `MATERIAL_ID`, or `OBJECT_ID` only when `supportedAttachments()` advertises it. An unrequested accessor returns `null`; result arrays and buffers are caller-owned defensive copies.

`RenderTimings` contains measured nanosecond phases. `UNAVAILABLE` means a phase did not occur inside the provider measurement boundary; zero is a real measurement. Use timings for diagnostics and comparison, not billing or guaranteed performance. `VisibilityStatistics` provides immutable semantic counts and pixel coverage for the completed frame; it is evidence from that render, not a world query.

```java
--8<-- "fixtures/ResultInspectionExample.java"
```
