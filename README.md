# GomsBook AI Agent

> AI-powered Agent Framework for EPUB3 Authoring, Accessibility, and Publishing Automation

![Status](https://img.shields.io/badge/status-In%20Progress-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![Platform](https://img.shields.io/badge/Platform-Windows-lightgrey)
![License](https://img.shields.io/badge/License-MIT-green)

---

## Overview

GomsBook AI Agent is an AI-powered agent framework designed to automate EPUB3 authoring and publishing workflows.

The project extends **GomsBookEditor** by integrating AI Agents capable of understanding user requests, selecting appropriate tools, validating results, and assisting with electronic publishing tasks.

---

## Vision

Transform traditional EPUB editing into an intelligent AI-assisted publishing environment.

Instead of manually editing XHTML, CSS, OPF, and metadata, users simply describe what they want, and AI Agents perform the required tasks.

---

## Project Goals

- XHTML generation
- EPUB validation
- Accessibility checking
- CSS layout analysis
- Metadata generation
- Prompt Engineering
- Tool Calling
- Local LLM integration
- RAG-based document retrieval

---

## Architecture

```text
                User Request
                     │
                     ▼
             AI Agent Planner
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
    Prompt Manager         Tool Router
          │                     │
          │         ┌───────────┼─────────────┐
          │         ▼           ▼             ▼
          │    XHTML Tool   EPUB Tool   Accessibility Tool
          │
          ▼
       Local / Cloud LLM
          │
          ▼
     GomsBookEditor
```

---

## Features

### XHTML Generation

Generate EPUB3-compatible XHTML documents.

- Heading structure
- Paragraph generation
- Image insertion
- Accessibility attributes
- GomsBook formatting rules

---

### EPUB Validation

Analyze EPUB packages.

- OPF
- NAV
- Manifest
- Spine
- Resources

---

### Accessibility Checker

Automatically verify

- lang
- aria-label
- aria-labelledby
- role
- heading hierarchy
- image alt text

---

### CSS Analyzer

Detect layout issues.

- PDF export
- Overflow
- Missing background
- Font issues

---

### Local LLM

Support running AI models locally.

- Privacy
- Offline publishing
- Faster response

---

## Technology Stack

| Category | Technology |
|-----------|------------|
| Language | Java |
| Desktop | Eclipse RCP / e4 |
| AI | LLM |
| Agent | Tool Calling |
| Retrieval | RAG |
| Document | EPUB3 |
| Markup | XHTML5 |
| Style | CSS3 |
| Validation | EPUBCheck |

---

## Repository Structure

```
src/
 ├── agent/
 ├── planner/
 ├── tool/
 ├── prompt/
 ├── llm/
 ├── validation/
 └── rag/

docs/

assets/

examples/
```

---

## Roadmap

### Phase 1

- [ ] Prompt Planner
- [ ] Tool Router
- [ ] XHTML Generator

### Phase 2

- [ ] EPUB Validator
- [ ] Accessibility Checker
- [ ] CSS Analyzer

### Phase 3

- [ ] Local LLM
- [ ] RAG
- [ ] Multi-Agent Workflow

---

## Related Project

### GomsBookEditor

Desktop EPUB authoring tool.

GomsBook AI Agent works as the AI engine for GomsBookEditor.

---

## Current Status

🚧 Under Development

This repository is actively being developed.

---

## Author

**Han Junghoon**

Software Developer  
Independent Publisher  
AI Agent & EPUB3 Research
