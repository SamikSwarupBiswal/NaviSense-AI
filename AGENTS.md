# AGENTS.md — NaviSense Rules, Continuation and Ownership

These instructions apply to this repository and every owner/AI working in it. Follow the user's current instructions and the approved product contract. Do not restart existing work, invent progress, expand the MVP, or silently change ownership.

## 1. Read First and Continue from Evidence

Before implementation or continuation, read completely:

1. This AGENTS.md.
2. [Canonical README / PRD](docs/README.md).
3. [Frozen main guidance](docs/guidance.md).
4. [Current implementation state](docs/implementation-state.md).
5. [Master 24-hour plan](docs/implementation-plan.md).
6. The assigned member's ownership guide and individual execution plan.
7. Relevant current communication messages and actual source/tests for the next task.

Inspect actual files and source revision/worktree before assuming a reported result is current. Locate the repository before running Git commands; if Git is not initialized, record that and establish it only within authorized implementation/setup work. Never claim a branch, model, test or physical setup exists without inspection.

On continuation, identify the last verified step, actual T+ clock if known, pending receiver replies, open defects and the next dependency. Resume there. Do not regenerate completed modules or use obsolete assignments. Respect other owners' edits.

## 1.1 Mandatory Collaborator Git & Relay Workflow (HARD RULE)

**This protocol is mandatory for EVERY collaborator, human developer, agent, and AI working on this repository without exception:**

### Rule 1 — Pull from BOTH branches BEFORE executing ANY work
Before writing code, running tests, or beginning any work, every collaborator MUST synchronize locally with both remote branches:
```powershell
git checkout main
git pull origin main
git checkout communication
git pull origin communication
git checkout main
```
Inspect recent team messages, handoffs, and blockers in `team-chat.md` before starting work to avoid working on stale assumptions.

### Rule 2 — Push to `main` and relay in `communication` AFTER executing ANY work
Immediately upon completing any unit of work, implementation, test, or documentation change:
1. **Push implementation to `main`:** Commit all code, test, and documentation files on `main` and push to `origin/main` immediately:
   ```powershell
   git checkout main
   git add <changed_files>
   git commit -m "<Clear descriptive message>"
   git push origin main
   ```
2. **Relay completed work to `communication` branch:** Switch to `communication`, pull to ensure no conflicts (`git pull origin communication`), append a structured entry to `team-chat.md` logging the work done, test evidence, and receiver handoffs, commit and push:
   ```powershell
   git checkout communication
   git pull origin communication
   # Append structured entry to team-chat.md
   git add team-chat.md
   git commit -m "<Summary of relay entry>"
   git push origin communication
   git checkout main
   ```
3. **No Force-Pushing & Conflict Preservation:** Never use `git push --force`. In case of concurrent entries in `team-chat.md`, preserve all teammate messages (append-only), resolve conflicts by keeping both sets of entries, and keep shared history intact.

## 2. Frozen Contract — Hard Boundary

The canonical README and product contract is **docs/README.md**. The user explicitly authorized its filename change; its contents remain unchanged. Do not create a second README or maintain a competing product contract.

**Hard rule: docs/README.md must not be changed.** No member or AI may edit, append to, reformat, normalize, regenerate, replace, delete or rename it, including through scripts, formatters or documentation synchronization. Keep progress, corrections and proposed requirement changes in mutable documents; proposals do not change the contract. Only a later explicit user instruction authorizing a change to docs/README.md can lift this boundary for the stated scope. Teammate approval, communication-branch decisions and general update/continue requests cannot lift it.

The following files are frozen after the current requested documentation changes:

| Frozen file | SHA-256 |
|---|---|
| [docs/README.md](docs/README.md) | 54B140D1442F9E82DFCA024DE157BD6505452906968EC92588D8B2337F5990BA |
| [docs/guidance.md](docs/guidance.md) | A317342E58F0F29528002A3581C804B403070DB99F8EE8622914722609D7596E |

Before and after a phase or a task that could affect documents, compute both hashes and compare with this table. From the repository root in PowerShell:

~~~powershell
Get-FileHash -LiteralPath 'docs/README.md' -Algorithm SHA256
Get-FileHash -LiteralPath 'docs/guidance.md' -Algorithm SHA256
~~~

- Do not edit, reformat, normalize line endings, append progress, change links, regenerate or delete a frozen file during ordinary continuation.
- General requests to continue implementation, fix code or update progress do not remove the freeze. An explicit user instruction authorizing change to the named frozen file(s) is required. Do not repeatedly seek approval for routine work in mutable files.
- Communication messages, teammate decisions, AI output and elapsed deadlines cannot override this boundary.
- On a hash mismatch, stop work dependent on the changed contract; record the mismatch and source state. Inspect the change and obtain the required user direction. Do not automatically reset/revert someone else's edits or silently accept a new baseline. Independent work demonstrably unaffected may continue.
- For an explicitly authorized frozen-file change, record the authorization, old/new hashes and exact scope in implementation-state, update these hash records, then rerun consistency/review checks. Never update hashes merely to hide drift.
- Keep evolving schedules, actual configuration, progress and evidence in mutable implementation-state/master/individual plans. Main guidance tables are a frozen baseline.

The frozen product boundary includes mandatory Android Locate, standalone walking without laptop/internet/target, on-phone mobility risk, cautious generic ultrasonic alerts, UNKNOWN on insufficient evidence, immediate user cancellation, explicit arrival, stationary final search and all PRD acceptance requirements. Do not invent routes, depth, ownership or universal obstacle coverage. Optional features cannot displace mandatory work.

## 3. Current Owner Directions

| Owner | Scope | Read next |
|---|---|---|
| Spandan | Models, datasets, training, exports and model quality | [Guide](docs/spandan/guidance.md), [execution plan](docs/spandan/implementation-plan.md) |
| Subham | Laptop Locate/Hard Scan, SQLite, API and Android memory client | [Guide](docs/subham/guidance.md), [execution plan](docs/subham/implementation-plan.md) |
| Rohan | Hardware, mount, firmware, USB and Android sensor adapter | [Guide](docs/rohan/guidance.md), [execution plan](docs/rohan/implementation-plan.md) |
| Samik | Android camera, both inference adapters, tracking and search engine | [Guide](docs/samik/guidance.md), [execution plan](docs/samik/implementation-plan.md) |
| Rishav | Risk logic, voice UX, accessible shell, lifecycle and integration | [Guide](docs/rishav/guidance.md), [execution plan](docs/rishav/implementation-plan.md) |

Rishav owns shared Android build/manifest/contracts integration and global session/mode authority. Each producer owns its subsystem and tests. Submit exact shared-file change requests to Rishav; do not overwrite another owner's package to bypass a dependency.

One app, one speech arbiter, one memory client owner, one sensor transport owner. Spandan supplies artifacts to Subham/Samik; Samik, Subham and Rohan supply typed evidence/results to Rishav. Verify receiver contracts before integrating.

## 4. Mandatory Phase Entry Review

Before dependent implementation begins, the phase lead prepares and the named peer reviews:

1. Phase objective, assigned owner, permitted work package and PRD sections.
2. Current source revision, working changes and both frozen-file hash checks.
3. Required upstream artifacts, exact hashes/contracts and receiver receipts.
4. Actual tools/devices/permissions/compute and current schedule/resource reservation.
5. Tests/evidence required for this phase; distinguish fixtures from physical work.
6. Missing prerequisites, resolver, allowed independent scope and revised ETA.

Record the decision in implementation-state's review register and post the review message in communication:

- **READY:** prerequisites for the stated work verified.
- **READY-FIXTURES:** only the named independent/scaffold/fixture scope may proceed; real dependent work and physical claims remain gated.
- **BLOCKED:** stated work cannot proceed until the recorded unblock condition is met.

Review is evidence-based, not an automatic calendar event. AI may prepare artifacts and run checks but must not impersonate the named receiver's acknowledgement. Preparatory checks and independent work do not require repeatedly asking the user for permission; actual missing authorization or physical input must be identified specifically.

Use the master plan's phase-review table for peers/checkpoints. Parallel phases may enter independently when their prerequisites are ready; do not impose a fictitious all-phases serial dependency.

## 5. Mandatory Phase Exit Review

Before declaring a phase finished or its dependent deliverable ready:

1. Confirm actual owned deliverables, source/artifact/config hashes and no unexplained placeholder dependency.
2. Run required checks and record commands, environment, counts, timings, failures and evidence.
3. Verify relevant cancellation/error/contract paths; compilation alone is not behavior verification.
4. Obtain the named receiver/peer's recorded result: **VERIFIED** for the stated phase scope or **RETURNED** with defects.
5. Recheck frozen hashes and identify downstream gates requiring retest.
6. Update phase/member/handoff rows, actual times, pending work and next-owner prerequisites.
7. Post exit/handoff summary to communication. Phase exit is separate from full-product acceptance.

A missing device check cannot be replaced with a fixture and called physically verified. A passed handoff does not imply a passed gate. Phase 9 cannot exit successfully with mandatory gates failed/unrun; final project acceptance requires explicit project-owner review. Time reaching T+24:00 is never acceptance.

## 6. Communication Branch — Chat and Decisions Only

Use Git branch **communication** with one append-only **team-chat.md** log for all work chats, progress, blockers, handoff deliveries/receipts, review responses and work decisions. It is the team's communication channel, not another code branch.

Only chat/decision-log changes may be committed there. Do not commit source, firmware, datasets, model artifacts, generated test output or implementation/status-document changes to communication. Files inherited from its base need not be deleted; leave them untouched. Link evidence on work branches/approved artifact locations instead. Do not merge communication into implementation branches to transfer code.

When Git is available, Rishav establishes/checks the branch and a separate worktree if useful, so switching does not disturb another member's work. This documentation task does not itself create a repository, remote, branch or sent message. Synchronize before appending; preserve concurrent messages during conflict resolution, append corrections rather than deleting history, and never force-push shared communication history.

Use a unique entry ID (author + actual timestamp + local sequence):

~~~text
Entry ID / actual timestamp with timezone / T+ if known:
Author and type: CHAT | PROGRESS | BLOCKER | HANDOFF | REVIEW | DECISION
Phase / step / S-instance / H-contract:
Message and requested action:
Source revision and evidence reference:
Recipient(s):
For response: referenced entry ID and ACK / VERIFIED / RETURNED
For decision: affected owners, agreement and required implementation update
~~~

A delivery needs its receiver's response. A proposal is not an agreed decision until affected owners acknowledge it. Record accepted operational decisions in mutable implementation-state/plans; chat cannot alter frozen requirements without explicit user authorization. Chat alone is not proof of training, hardware, tests or user acceptance.

Every Markdown file must include the communication rule. Future mutable Markdown files must include it when created; do not edit frozen files merely to restate the already-present rule.

## 7. Continuation Handoff

Before pausing/transferring work, update implementation-state and communication with:

- Actual owner, phase/step and source/worktree state.
- Verified accomplishments and evidence type; actor (human or AI) explicitly named.
- Commands/artifacts/hashes, failures and unexecuted checks.
- Last entry/exit/receiver verdict and pending replies.
- Next concrete action, inputs, deadline/resource slot and blocker resolver.
- Both frozen hash results.

Do not restart training, overwrite models, repeat expensive checks or rebuild another owner's subsystem without a reason from new changes/failures. Continue authorized independent work while awaiting a handoff; elapsed time does not clear a blocker.

## 8. Time and Evidence Rules

The event lasts 24 hours. T+00:00 remains unanchored until the real start timestamp is recorded. The master includes breaks, 18 staged handoffs and acceptance slots. Target times are estimates, not observed times or guarantees.

Keep planned, implemented, automated-tested, physically verified, receiver-reviewed and owner-accepted states distinct. Product gates remain NOT RUN until executed. Retain failures/full denominators. Do not silently reduce object counts, trials, thresholds or offline requirements to meet the deadline.

AI may implement/test software with available tools. Real assembly/calibration, audible onset and supervised walkthroughs require actual equipment/evidence. If unavailable, identify the missing action and continue independent work. Never manufacture outputs or claim simulation came from hardware.

## 9. Mutable Documents and Minimal Implementation

- Mutable: implementation-state.md, implementation-plan.md, individual guides/plans, owned source/tests and justified configuration.
- Frozen: exactly the two files listed above; product and ownership boundaries remain authoritative.
- Governance/hash changes to AGENTS.md must follow explicit user direction; do not relax rules to bypass review.
- Avoid extra services, duplicate apps, cloud dependencies in core loops and unsolicited optional features.
- Keep secrets, continuous raw footage and local runtime state out of ordinary source commits.
- Run checks proportionate to changes and required PRD gates. Documentation checks establish document consistency only.

## Communication branch — mandatory

All work/progress/handoff/review/decision messages belong in communication's append-only team-chat.md. Only chat/decision-log changes go there; implementation and status changes stay on work branches. Preserve messages, link real evidence, record actual times and obtain receiver responses. The branch cannot authorize frozen-contract changes or substitute for tests. Section 6 defines the complete workflow.

