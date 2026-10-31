# Third-party notices for UI2

UI2 plans to use these mature libraries. Keep their LICENSE/NOTICE files in the release and list
them in the app's third-party notices / About surface.

| Library | Planned version | License | Notes |
|---|---:|---|---|
| FlatLaf | 3.7.2 | Apache-2.0 | keep LICENSE and NOTICE |
| flatlaf-extras | 3.7.2 | Apache-2.0 | only if SVG extras are used |
| flatlaf-swingx | 3.7.2 | Apache-2.0 | SwingX 1.6.4 compatibility |
| MigLayout | 11.5.2 | BSD-style | keep license text |
| SwingX | 1.6.4 (existing) | existing project license | not newly vendored |
| JNA (optional) | 5.19.x | Apache-2.0 OR LGPL-2.1+ | choose Apache-2.0 for distribution |

Rules:

- do not copy source or assets from IntelliJ, Lichess, VS Code, or other products;
- use their interaction/design patterns, not their bundled files;
- board-theme assets keep their own notices;
- update this file when an actual dependency is added.
