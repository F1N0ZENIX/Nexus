# OpenCode Android port status

`OpenCodePortClient` uses the fork's HTTP Basic Auth identity: `opencode`.
It is intentionally not connected to the Nexus UI or provider selector yet.

Only request authentication and transport are covered. Response-to-request
selection and pagination/cursor semantics have **not** been validated against
an OpenCode server, so no response selection claim is made and no UI path is
enabled. Wire-contract validation is in `OpenCodePortClientTest`: a real local
HTTP request is captured and its literal Authorization header is checked.
