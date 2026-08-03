# GomsBook AI Agent Architecture

## Overview

GomsBook AI Agent is an AI-driven orchestration framework designed to automate EPUB3 authoring, validation, accessibility verification, and publishing workflows.

Instead of executing a single LLM request, the agent analyzes the user's intent, plans the workflow, selects appropriate tools, validates the output, and returns reliable results.

---

# High-Level Architecture

```text
                   User
                     │
                     ▼
            GomsBookEditor UI
                     │
                     ▼
            AI Agent Orchestrator
                     │
     ┌───────────────┼────────────────┐
     ▼               ▼                ▼
 Planner        Context Manager   Prompt Manager
     │
     ▼
 Tool Router
     │
 ┌───┼──────────────┬───────────────┬───────────────┐
 ▼   ▼              ▼               ▼               ▼
XHTML Tool    EPUB Tool    Accessibility Tool   Metadata Tool
     │
     ▼
 Validator
     │
     ▼
 Result Formatter
     │
     ▼
 GomsBookEditor
```

---

# Agent Workflow

```text
User Request

↓

Intent Analysis

↓

Task Planning

↓

Tool Selection

↓

Prompt Construction

↓

LLM Execution

↓

Validation

↓

Result Formatting

↓

Apply to GomsBookEditor
```

---

# Core Components

## 1. AI Agent Orchestrator

The central controller of the system.

Responsibilities

- Receive user requests
- Coordinate workflow
- Execute tools
- Handle retries
- Aggregate results

---

## 2. Planner

The planner determines what the user wants to accomplish.

Example

User

> Generate XHTML for Chapter 3.

Planner

```text
Task
Generate XHTML

↓

Need
Formatting Rules

↓

Need
Accessibility Rules

↓

Need
Validation
```

---

## 3. Prompt Manager

Constructs optimized prompts for the selected LLM.

Responsibilities

- Prompt Templates
- Context Injection
- Few-shot Examples
- Output Formatting

---

## 4. Context Manager

Provides project-specific information.

Examples

- Book metadata
- EPUB specification
- XHTML rules
- CSS rules
- Accessibility guidelines

---

## 5. Tool Router

The Tool Router selects the appropriate tool according to the user's request.

Examples

| User Request | Selected Tool |
|--------------|---------------|
| Create XHTML | XHTML Tool |
| Validate EPUB | EPUB Tool |
| Fix CSS | CSS Tool |
| Check Accessibility | Accessibility Tool |
| Generate Metadata | Metadata Tool |

---

# Tool Layer

## XHTML Tool

Generate XHTML documents compatible with EPUB3.

Outputs

- XHTML
- Heading hierarchy
- Paragraph IDs
- Image tags
- Accessibility attributes

---

## EPUB Validation Tool

Checks

- OPF
- NAV
- Manifest
- Spine
- Broken links
- Missing resources

---

## Accessibility Tool

Checks

- lang
- role
- aria-label
- aria-labelledby
- image alt
- heading hierarchy

---

## CSS Analysis Tool

Detects

- Overflow
- Broken layout
- Missing background
- Unsupported CSS

---

## Metadata Tool

Generate

- OPF Metadata
- Description
- Keywords
- Author
- ISBN
- Publisher

---

# Validation Layer

Every generated result is validated before being returned.

```text
LLM Output

↓

Validator

↓

Success ?

↓

Yes → Return

No

↓

Retry

↓

Return
```

---

# LLM Layer

The architecture supports multiple LLM providers.

```text
            LLM Adapter
          ┌──────┴─────────┐
          ▼                ▼
   Local LLM         Cloud LLM
```

Supported Models

- GPT
- Gemini
- Claude
- Ollama
- LM Studio

---

# RAG Layer

Knowledge sources

- EPUB3 Specification
- GomsBook Rules
- Accessibility Guide
- CSS Reference
- Internal Templates

```text
Knowledge Base

↓

Embedding

↓

Vector Search

↓

Context

↓

LLM
```

---

# Error Handling

The Agent retries when

- Invalid XHTML
- Missing resources
- Invalid EPUB structure
- Tool execution failure

---

# Security

The architecture is designed to support local execution.

Benefits

- No source leakage
- Offline editing
- Faster response
- User privacy

---

# Future Architecture

```text
                   Planner
                      │
      ┌───────────────┼────────────────┐
      ▼               ▼                ▼
 Document Agent  Validation Agent  Publishing Agent
      │               │                │
      └───────────────┼────────────────┘
                      ▼
                Coordinator Agent
                      │
                GomsBookEditor
```

This architecture will evolve toward a Multi-Agent system, where specialized agents collaborate to perform complex publishing workflows.

---

# Design Principles

- Modular
- Tool-driven
- Human-in-the-loop
- LLM Independent
- Local-first
- Extensible
- Reliable
- Explainable

---

# Repository

```
src/
 ├── agent/
 ├── planner/
 ├── tool/
 ├── validation/
 ├── llm/
 ├── rag/
 └── prompt/

docs/
 ├── architecture.md
 ├── agent-workflow.md
 ├── tool-design.md
 └── roadmap.md
```

---

# Next Steps

- Implement Planner
- Implement Tool Router
- Build XHTML Tool
- Integrate EPUBCheck
- Build Accessibility Checker
- Integrate Local LLM
- Implement RAG
- Build Multi-Agent Workflow