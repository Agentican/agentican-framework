# Agentican Examples

Forty self-contained programs that demonstrate Agentican across real-world
domains and complexity levels. Each example picks a concrete business
scenario, shows the minimum framework surface needed to solve it, and runs
end-to-end from a single `main()`.

## Layout

```
examples/src/main/java/ai/agentican/framework/examples/
├── notools/
│   ├── agents/              ← single-agent tasks (agentican.task(...))
│   │   └── hitl/            ← single-agent + human-in-the-loop
│   └── workflows/           ← multi-step workflows (agentican.workflow(...))
│       └── hitl/            ← multi-step + HITL on an intermediate step
└── withtools/               ← workflows that call external tools via Composio
```

The layout is a progressive-disclosure path:

- **`notools/` vs `withtools/`** — `notools` examples need only an LLM key.
  No external credentials, no side effects, safe to run locally. `withtools`
  examples integrate real services (Gmail, Slack, GitHub, etc.) through
  Composio and need both API keys plus connected accounts.
- **`agents/` vs `workflows/`** — An *agent task* is a single LLM call with
  typed input and output — the simplest surface. A *workflow task* is a
  multi-step plan (sequential, branch, loop) where steps pass output to each
  other. Workflows show orchestration.
- **`hitl/` subdirectories** — Human-in-the-loop examples are isolated
  because they require interactive stdin. They wire a `CliHitlNotifier` onto
  `Agentican.builder().hitlManager(...)` and either prompt for step-output
  approval or answer an in-agent `ASK_QUESTION`.

## Running

Every example reads two YAML files from the classpath:

- **`engine.yaml`** — shared across every example. Just declares the LLM API
  key. Wired via `.configuration().yaml().path(engine()).end()`.
- **`<example>.yaml`** — per-example catalog. Declares the agent(s), skills,
  and (for workflow examples) the `WorkflowDefinition` itself. Wired via
  `.registry().yaml().path(config()).end()`.

This mirrors the engine/catalog separation in the framework: `EngineConfig`
is what boots the runtime, `CatalogConfig` is what seeds the registries.

Required env vars:

- `ANTHROPIC_API_KEY` — required by every example.
- `COMPOSIO_API_KEY`, `COMPOSIO_USER_ID` — required by `withtools/` examples
  only, plus the relevant integrations connected in your Composio account.

A `.env` file at the repo root (or any parent) is picked up automatically.

Run from the IDE (each file has a `main()`) or from Maven:

```bash
mvn -pl examples exec:java \
    -Dexec.mainClass=ai.agentican.framework.examples.notools.agents.AlertReview
```

## HITL at a glance

Agentican supports three checkpoint types, all demonstrated in the `hitl/`
subdirectories:

- **`STEP_OUTPUT`** — fires after a step with `hitl: true` completes. The
  human approves (step becomes `COMPLETED`) or rejects with feedback (the
  framework re-runs the step with the feedback appended, up to
  `maxStepRetries`). Each retry creates its own approval checkpoint.
- **`QUESTION`** — fires when an agent invokes the `ASK_QUESTION` tool
  mid-execution. The human answers, the agent resumes with the answer, and
  eventually produces a step output.
- **`TOOL_CALL`** — fires when a tool marked as HITL-required is about to be
  invoked. Approve to let the tool run; reject to surface a rejection to
  the agent.

`CliHitlNotifier` is a reference `HitlNotifier` that reads and writes stdin.
Swap it for any notifier — Slack, a web UI, a test harness — and framework
behavior is identical.

## Catalog

Examples are grouped by business domain. Each entry shows: form (agent or
workflow), whether it uses external tools, and how HITL is used if at all.

### Customer Success

- `CallDebrief` — distill a customer call into asks, commitments, risks (agent)
- `ChurnRisk` — score churn risk, branch into an intervention plan by tier (workflow)
- `RefundApproval` — draft refund decision + customer reply (agent, HITL approve)
- `CustomerOnboarding` — new-customer onboarding with calendar, Slack and HubSpot (workflow, tools)

### HR / Recruiting

- `ResumeReview` — critique a resume against a role (agent)
- `InterviewLoopDesign` — design an interview kit with tiered questions (workflow)
- `PromotionPackage` — draft promotion packet + team announcement (agent, HITL approve)
- `CandidateScreening` — screen candidates and draft outreach email (workflow, tools)

### Engineering / SRE

- `AlertReview` — critique an alert for signal quality and runbook (agent)
- `CommitMessage` — turn a diff into a conventional commit (agent)
- `Postmortem` — draft a blameless postmortem from incident notes (workflow)
- `PRReview` — summarize a PR and flag risks (workflow)
- `IncidentPostmortem` — customer-facing incident email (agent, HITL approve)
- `IncidentResponse` — triage an alert into runbook + pages via Slack/Linear/GitHub (workflow, tools)

### Marketing

- `BrandTaglines` — generate tagline options from a brief (agent)
- `MessageVariants` — produce campaign message variants (workflow)
- `LandingCopy` — hero copy for a landing page (agent, HITL asks for audience)
- `ContentPipeline` — draft → edit → publish through Notion (workflow, tools)

### Finance

- `BudgetVarianceAnalysis` — explain a variance with a concrete next action (agent)
- `ExpenseAudit` — audit an expense report batch for policy violations (workflow)
- `InvoiceProcessing` — parse and route an invoice via Gmail/Slack (workflow, tools)

### Sales

- `DiscoveryCallPrep` — calibrate discovery questions to a prospect (agent)
- `ObjectionHandler` — draft objection responses grounded in the script (workflow)
- `DealDiscount` — approve a deal discount + rep talk track (agent, HITL approve)
- `LeadQualification` — qualify a lead with HubSpot + Gmail research (workflow, tools)

### Productivity

- `MeetingMinutes` — structure raw meeting notes into decisions and actions (agent)
- `EmailTriage` — loop over an inbox, categorize each, aggregate summary (workflow)
- `MeetingPrepBrief` — pre-meeting brief from Google Calendar (workflow, tools)
- `DailyStandupDigest` — digest yesterday's work + today's plan from GitHub/Linear/Slack (workflow, tools)

### Leadership / Planning

- `OkrDraft` — draft team OKRs (agent, HITL asks for company priorities)

### Security

- `SecurityAdvisory` — customer CVE advisory (agent, HITL asks coordination status)

### Data / Analytics

- `DashboardScope` — scope a new dashboard (agent, HITL asks what decisions it supports)

### Operations / IT

- `DataMigration` — analyze → **plan (approved)** → runbook (workflow, HITL approve)

### Product Management

- `FeatureSpec` — clarify → **spec (approved)** → engineering tasks (workflow, HITL approve)

### Legal / Compliance

- `DpaReview` — parse → **assess risks (approved)** → redline positions (workflow, HITL approve)

### Procurement

- `VendorSelection` — normalize → **rank (approved)** → selection memo (workflow, HITL approve)

### Design / UX

- `ResearchSynthesis` — cluster → **frame (asks decision focus)** → brief (workflow, HITL question)

### Risk / Fraud

- `FraudTriage` — score → **classify (asks current thresholds)** → actions (workflow, HITL question)

### Content / Editorial

- `ArticlePipeline` — outline → **draft (asks audience/tone)** → edit (workflow, HITL question)

### Community / OSS

- `ContributorRecognition` — analyze → **categorize (asks tier criteria)** → messages (workflow, HITL question)

### Generic

- `QuickTask` — planner invents agents on the fly from a task description (workflow)

## Integrations used by `withtools/`

| Tool | Examples |
|---|---|
| Google Calendar | MeetingPrepBrief, CustomerOnboarding |
| GitHub | DailyStandupDigest, IncidentResponse |
| Linear | DailyStandupDigest, IncidentResponse |
| Slack | DailyStandupDigest, IncidentResponse, InvoiceProcessing, CustomerOnboarding |
| Notion | ContentPipeline |
| HubSpot | LeadQualification, CustomerOnboarding |
| Gmail | LeadQualification, CandidateScreening, InvoiceProcessing, CustomerOnboarding |
