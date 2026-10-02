# Known pitfalls

> Owner: Architect · Read in: phases 0, 2, 3, 4, 6 · Agent: read-only

Defects that earlier runs of this project produced more than once. Each is a constraint; the specification maps it and phase 6 records evidence for it.

| ID | Constraint | Seen as |
|---|---|---|
| KP-01 | The nginx configuration never passes a client-supplied host (`$host`, `$http_host`) to the backend or into a redirect; it sets a fixed value. | Semgrep finding in three runs |
| KP-02 | Every response carries each security header (SB-10) exactly once. An nginx `location` that sets any header repeats all of them, and nginx does not add headers the backend already sets on `/api`. | missing headers on HTML; duplicate headers on `/api` |
| KP-03 | Text files are LF (`.gitattributes` at the repository root, shipped with the template). Hashes and format checks run on LF content. | broken format checks and manifest hashes |
| KP-04 | Paths under `02_output/` stay short enough for Windows; the root README says to clone into a short path with `git -c core.longpaths=true clone`. | clean checkout failed in two runs |
| KP-05 | If Testcontainers cannot reach the Docker Engine, set the Docker API version in test resources (`docker-java.properties`), as a non-blocking decision. Do not change a pinned version for it. | 25 setup errors in one first test run |
| KP-06 | End-to-end tests start their own fresh stack or servers; they never reuse a server that is already running. | six failures against a stale dev server |
