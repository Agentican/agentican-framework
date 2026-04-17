# Agentican Showcase Examples

Real-world workflows spanning domains and complexity levels. Each example demonstrates a different combination of Agentican features.

## Examples (simple → complex)

| # | Example | Domain | Complexity | Agents | Features |
|---|---|---|---|---|---|
| 1 | [QuickTask](QuickTask.java) | Any | ★☆☆☆☆ | 0 (Planner creates them) | PlannerAgent, zero-config |
| 2 | [MeetingPrepBrief](MeetingPrepBrief.java) | Productivity | ★★☆☆☆ | 1 | Single tool (Google Calendar) |
| 3 | [DailyStandupDigest](DailyStandupDigest.java) | Engineering | ★★☆☆☆ | 1 | Multiple tools (GitHub, Linear, Slack) |
| 4 | [ContentPipeline](ContentPipeline.java) | Marketing | ★★★☆☆ | 3 | Sequential chain, skills, HITL gate, Notion |
| 5 | [LeadQualification](LeadQualification.java) | Sales | ★★★☆☆ | 3 | HITL approval, CRM (HubSpot), Gmail |
| 6 | [IncidentResponse](IncidentResponse.java) | Operations | ★★★★☆ | 2 | Branch routing, parallel notifications, Slack + Linear + GitHub |
| 7 | [CandidateScreening](CandidateScreening.java) | HR | ★★★★☆ | 2 | Loop step (per-candidate), HITL review, Gmail |
| 8 | [InvoiceProcessing](InvoiceProcessing.java) | Finance | ★★★★☆ | 3 | Branch (auto vs HITL), three-path routing, Gmail + Slack |
| 9 | [CustomerOnboarding](CustomerOnboarding.java) | Customer Success | ★★★★★ | 5 | Parallel, branch, HITL, knowledge, skills, HubSpot + Gmail + Calendar + Slack |

## Feature coverage matrix

| Feature | Quick Task | Meeting | Standup | Content | Lead | Incident | Candidate | Invoice | Onboarding |
|---|---|---|---|---|---|---|---|---|---|
| Planner (auto) | ✅ | | | | | | | | |
| Single agent | | ✅ | ✅ | | | | | | |
| Multi-agent | | | | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Sequential deps | | | | ✅ | ✅ | | | ✅ | ✅ |
| Parallel steps | | | | | | ✅ | | | ✅ |
| Loop step | | | | | | | ✅ | | |
| Branch step | | | | | | ✅ | | ✅ | ✅ |
| HITL (step) | | | | ✅ | ✅ | | ✅ | ✅ | ✅ |
| Skills | | | | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Knowledge | | | | | | | | | ✅ |
| 1 tool | | ✅ | | | | | | | |
| Multi-tool | | | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

## Tools used (via Composio)

| Tool | Examples that use it |
|---|---|
| Google Calendar | MeetingPrepBrief, CustomerOnboarding |
| GitHub | DailyStandupDigest, IncidentResponse |
| Linear | DailyStandupDigest, IncidentResponse |
| Slack | DailyStandupDigest, IncidentResponse, InvoiceProcessing, CustomerOnboarding |
| Notion | ContentPipeline |
| HubSpot | LeadQualification, CustomerOnboarding |
| Gmail | LeadQualification, CandidateScreening, InvoiceProcessing, CustomerOnboarding |

## Running

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export COMPOSIO_API_KEY=...
```

QuickTask requires only the LLM key. All others also need the Composio key with the relevant integrations connected in your Composio account.
