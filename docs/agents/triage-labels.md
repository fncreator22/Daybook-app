# Triage Labels — Daybook

Using the five **default** canonical labels. Label strings equal their role names.

| Role | Label string | Meaning |
|------|-------------|---------|
| `needs-triage` | `needs-triage` | Filed, not yet evaluated |
| `needs-info` | `needs-info` | Waiting on reporter for more detail |
| `ready-for-agent` | `ready-for-agent` | Fully specified, agent brief attached, ready to implement |
| `ready-for-human` | `ready-for-human` | Needs human (judgment call, external access, design decision) |
| `wontfix` | `wontfix` | Will not be actioned; issue closed |

Category labels (applied in addition to state):

| Category | Label string |
|----------|-------------|
| Bug | `bug` |
| Enhancement | `enhancement` |

Create these labels in GitHub if they don't already exist:

```bash
gh label create needs-triage  --color "ededed" --description "Not yet triaged"
gh label create needs-info     --color "d93f0b" --description "Waiting on reporter"
gh label create ready-for-agent --color "0075ca" --description "Agent brief attached"
gh label create ready-for-human --color "e4e669" --description "Needs human decision"
gh label create wontfix        --color "ffffff" --description "Will not be actioned"
gh label create bug            --color "d73a4a" --description "Something is broken"
gh label create enhancement    --color "a2eeef" --description "New feature or improvement"
```
