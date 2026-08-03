# GomsBook AI Agent Workflow

> End-to-end workflow of the GomsBook AI Agent from user request to validated publishing result.

---

# Overview

GomsBook AI Agent is designed to understand user intent, plan tasks, select the appropriate tools, validate the generated output, and seamlessly integrate the results into GomsBookEditor.

Unlike a traditional chatbot, the agent executes structured workflows to ensure reliable and reproducible publishing outcomes.

---

# Workflow Overview

```text
User Request
      │
      ▼
Intent Analysis
      │
      ▼
Task Planning
      │
      ▼
Context Collection
      │
      ▼
Prompt Construction
      │
      ▼
Tool Selection
      │
      ▼
LLM Execution
      │
      ▼
Validation
      │
      ▼
Result Formatting
      │
      ▼
Apply to GomsBookEditor
```

---

# Detailed Workflow

## Step 1 — User Request

The user submits a natural language instruction.

Examples

- Generate XHTML for Chapter 3
- Validate this EPUB
- Check accessibility issues
- Create book metadata
- Fix CSS layout problems

---

## Step 2 — Intent Analysis

The Planner identifies the user's goal.

Example

User:

> Generate XHTML for Chapter 2.

Intent

```
Task

Generate XHTML

Target

Chapter 2

Need Validation

YES

Need Accessibility

YES
```

Output

```json
{
  "task": "generate_xhtml",
  "target": "chapter02",
  "validation": true,
  "accessibility": true
}
```

---

## Step 3 — Task Planning

The Planner builds an execution plan.

Example

```
Generate XHTML

↓

Validate XHTML

↓

Check Accessibility

↓

Return Result
```

---

## Step 4 — Context Collection

The Context Manager gathers relevant information.

Sources

- EPUB3 Specification
- Project Templates
- Book Metadata
- Accessibility Rules
- XHTML Guidelines
- CSS Rules

---

## Step 5 — Prompt Construction

The Prompt Manager creates an optimized prompt.

Example

```
Generate XHTML.

Requirements

• EPUB3 compatible
• UTF-8
• Heading hierarchy
• aria-labelledby
• lang="ko"
• GomsBook formatting rules
```

---

## Step 6 — Tool Selection

The Tool Router chooses the correct tool.

| User Request | Tool |
|--------------|------|
| Generate XHTML | XHTML Generation Tool |
| Validate EPUB | EPUB Validation Tool |
| Check Accessibility | Accessibility Tool |
| Fix CSS | CSS Analysis Tool |
| Generate Metadata | Metadata Tool |

---

## Step 7 — LLM Execution

The selected LLM processes the request.

Supported Models

- OpenAI GPT
- Google Gemini
- Anthropic Claude
- Ollama
- LM Studio

---

## Step 8 — Validation

Every output is validated before being returned.

Validation includes

- XHTML syntax
- EPUB structure
- Accessibility rules
- Metadata
- Internal formatting rules

Workflow

```
LLM Output

↓

Validator

↓

Success?

↓

YES → Continue

NO

↓

Retry

↓

Return Error
```

---

## Step 9 — Result Formatting

The response is converted into a format suitable for GomsBookEditor.

Examples

- XHTML
- CSS
- Metadata
- Validation Report
- Suggestions

---

## Step 10 — Apply to GomsBookEditor

The validated output is applied to the editor.

Possible Actions

- Insert XHTML
- Replace document
- Update metadata
- Show validation report
- Highlight issues

---

# Workflow Example

## Example

User

> Generate XHTML for Chapter 5.

Execution

```
User

↓

Planner

↓

Collect EPUB Rules

↓

Select XHTML Tool

↓

Generate XHTML

↓

Validate XHTML

↓

Accessibility Check

↓

Return Result

↓

Insert into GomsBookEditor
```

---

# Error Handling

The agent automatically retries when possible.

Retry Conditions

- Invalid XHTML
- Missing metadata
- Broken image links
- Invalid EPUB structure
- LLM formatting errors

If automatic recovery fails, the agent returns an explanation and suggested actions.

---

# Human-in-the-Loop

The AI Agent never modifies publishing content without user confirmation when a change may affect existing work.

Workflow

```
Suggestion

↓

Preview

↓

User Approval

↓

Apply Changes
```

---

# Workflow Principles

The workflow follows these principles.

- Intent-driven
- Tool-based
- Validation-first
- Human-in-the-loop
- LLM-independent
- Modular
- Explainable
- Extensible

---

# Future Workflow

The current single-agent workflow will evolve into a collaborative multi-agent architecture.

```
User
  │
  ▼
Planner Agent
  │
  ├──────────────┬──────────────┬──────────────┐
  ▼              ▼              ▼              ▼
XHTML Agent  Validation Agent  Accessibility Agent  Metadata Agent
  │              │              │              │
  └──────────────┴──────────────┴──────────────┘
                 ▼
         Publishing Agent
                 │
                 ▼
         GomsBookEditor
```

---

# Workflow Benefits

- Reduced manual editing
- Consistent EPUB structure
- Improved accessibility
- Faster publishing workflow
- Lower error rate
- Better user productivity
- Scalable AI architecture