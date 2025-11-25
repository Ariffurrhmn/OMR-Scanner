# OMR Reader V2 - Planning Documentation

---

## Table of Contents

1. [Overview](#overview)
2. [Features](#features)
3. [OMR Sheet Structure](#omr-sheet-structure)
4. [UI Design System](#ui-design-system)
5. [UI Navigation](#ui-navigation)
6. [UI Screens](#ui-screens)
7. [UI Components](#ui-components)
8. [Database Schema](#database-schema)
9. [Project Structure](#project-structure)
10. [Implementation Order](#implementation-order)

---

## Overview

**OMR Reader V2** is a desktop application for processing Optical Mark Recognition (OMR) answer sheets.

### Technical Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| UI Framework | JavaFX 21.0.2 |
| Image Processing | OpenCV 4.9.0 (via JavaCV) |
| Database | SQLite |
| Build Tool | Maven |

### Sheet Specification

| Element | Details |
|---------|---------|
| Student ID | 10 digits (OMR bubbles 0-9 per column) |
| Test ID | 4 digits (OMR bubbles 0-9 per column) |
| Questions | 60 multiple-choice |
| Choices | 4 per question (A, B, C, D) |
| Layout | 4 answer blocks × 15 questions each |

---

## Features

### Must Have

| # | Feature | Description |
|---|---------|-------------|
| 1 | Single Sheet Scan | Load and process one OMR sheet |
| 2 | Student ID Recognition | Read 10-digit OMR student ID |
| 3 | Test ID Recognition | Read 4-digit OMR test ID |
| 4 | Answer Recognition | Read 60 multiple-choice answers (A/B/C/D) |
| 5 | Answer Key Management | Create, edit, store, link answer keys to Test IDs |
| 6 | Auto-Grading | Compare answers to key, calculate score |

### Should Have

| # | Feature | Description |
|---|---------|-------------|
| 7 | Batch Processing | Process multiple sheets from a folder |
| 8 | Results History | Store and browse past scan results |
| 9 | Export to CSV | Export results for external use |
| 10 | Manual Correction | Fix recognition errors manually in UI |

---

## OMR Sheet Structure

### Physical Layout

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ L (TL)                                                            L (TR)   │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                                                                     │   │
│   │   STUDENT ID (10 digits)              TEST ID (4 digits)           │   │
│   │   ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┐              ┌─┬─┬─┬─┐                     │   │
│   │   │0│0│0│0│0│0│0│0│0│0│              │0│0│0│0│                     │   │
│   │   │1│1│1│1│1│1│1│1│1│1│              │1│1│1│1│                     │   │
│   │   │2│2│2│2│2│2│2│2│2│2│              │2│2│2│2│                     │   │
│   │   │3│3│3│3│3│3│3│3│3│3│              │3│3│3│3│                     │   │
│   │   │4│4│4│4│4│4│4│4│4│4│              │4│4│4│4│                     │   │
│   │   │5│5│5│5│5│5│5│5│5│5│              │5│5│5│5│                     │   │
│   │   │6│6│6│6│6│6│6│6│6│6│              │6│6│6│6│                     │   │
│   │   │7│7│7│7│7│7│7│7│7│7│              │7│7│7│7│                     │   │
│   │   │8│8│8│8│8│8│8│8│8│8│              │8│8│8│8│                     │   │
│   │   │9│9│9│9│9│9│9│9│9│9│              │9│9│9│9│                     │   │
│   │   └─┴─┴─┴─┴─┴─┴─┴─┴─┴─┘              └─┴─┴─┴─┘                     │   │
│   │                                                                     │   │
│   ├─────────────────────────────────────────────────────────────────────┤   │
│   │ ■ (TL)                    ANSWER SECTION                    ■ (TR) │   │
│   │                                                                     │   │
│   │   ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐                  │   │
│   │   │ Q1-Q15  │ │ Q16-Q30 │ │ Q31-Q45 │ │ Q46-Q60 │                  │   │
│   │   │ A B C D │ │ A B C D │ │ A B C D │ │ A B C D │                  │   │
│   │   │ ○ ○ ○ ○ │ │ ○ ○ ○ ○ │ │ ○ ○ ○ ○ │ │ ○ ○ ○ ○ │                  │   │
│   │   │  ...    │ │  ...    │ │  ...    │ │  ...    │                  │   │
│   │   └─────────┘ └─────────┘ └─────────┘ └─────────┘                  │   │
│   │                                                                     │   │
│   │ ■ (BL)                                                      ■ (BR) │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│ L (BL)                                                            L (BR)   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Fiducial Markers

| Type | Count | Location | Purpose |
|------|-------|----------|---------|
| L-shaped | 4 | Outer sheet corners | Deskewing |
| Rectangular | 4 | Answer section corners | Answer region extraction |

---

## UI Design System

> **Full design specifications in [DESIGN_TOKENS.md](DESIGN_TOKENS.md)**

### Quick Reference

| Element | Value |
|---------|-------|
| **Font Primary** | Inter |
| **Font Mono** | Roboto Mono |
| **Background** | `#FFFFFF` |
| **Text** | `#09090B` |
| **Primary** | `#18181B` |
| **Border** | `#E4E4E7` |
| **Success** | `#16A34A` |
| **Warning** | `#F59E0B` |
| **Error** | `#EF4444` |
| **Radius** | 8px (buttons), 12px (cards) |

### Status Colors

| Status | Color | Icon |
|--------|-------|------|
| Correct | `#16A34A` | ✓ |
| Wrong | `#EF4444` | ✗ |
| Skipped | `#71717A` | – |
| Invalid | `#F59E0B` | ⚠ |
| Processing | `#3B82F6` | ⏳ |

---

## UI Navigation

### Architecture

Simple sidebar navigation - click to switch screens. No complex flows, no keyboard shortcuts.

```
┌──────────────────────────────────────────────────────────┐
│                    APPLICATION WINDOW                    │
│  ┌──────────┬─────────────────────────────────────────┐  │
│  │          │                                         │  │
│  │  SIDEBAR │            CONTENT AREA                 │  │
│  │  (Fixed) │            (Dynamic)                    │  │
│  │          │                                         │  │
│  │  Home    │   Screen loads based on sidebar click:  │  │
│  │  Scan    │                                         │  │
│  │  Batch   │   • Click "Home"  → home.fxml           │  │
│  │  Keys    │   • Click "Scan"  → scan.fxml           │  │
│  │  History │   • Click "Batch" → batch.fxml          │  │
│  │          │   • Click "Keys"  → answer-key.fxml     │  │
│  │          │   • Click "History" → history.fxml      │  │
│  │          │                                         │  │
│  └──────────┴─────────────────────────────────────────┘  │
│  └─────────────────── STATUS BAR ─────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### Navigation Items

| Icon | Label | Screen |
|------|-------|--------|
| 🏠 | Home | Dashboard with quick actions |
| 📄 | Scan | Single sheet processing |
| 📁 | Batch | Multiple sheet processing |
| 🔑 | Keys | Answer key management |
| 📊 | History | Past scan results |

### Screen Flow

```
┌────────────────────────────────────────────────┐
│                                                │
│   SIDEBAR (always visible)                     │
│                                                │
│   Click any item to load that screen:          │
│                                                │
│   [Home] ────► Dashboard                       │
│   [Scan] ────► Single Scan                     │
│   [Batch] ───► Batch Processing                │
│   [Keys] ────► Answer Key Manager              │
│   [History] ─► Results History ──┐             │
│                                  │             │
│                                  ▼             │
│                           [View Details]       │
│                           (popup dialog)       │
│                                                │
└────────────────────────────────────────────────┘
```

**Notes:**
- All navigation is via mouse clicks on sidebar
- No keyboard shortcuts (keeps things simple)
- "View Details" in History opens a popup/dialog showing full scan result

---

## UI Screens

### Screen 1: Home / Dashboard

**Purpose:** Quick access to all features, recent activity

```
┌──────────────────────────────────────────────────────────────────────────┐
│  OMR READER V2                                           [─] [□] [×]    │
├────────────────┬─────────────────────────────────────────────────────────┤
│                │                                                         │
│   NAVIGATION   │              DASHBOARD                                  │
│                │                                                         │
│   ┌──────────┐ │   ┌─────────────────┐  ┌─────────────────┐             │
│   │ 🏠 Home  │ │   │                 │  │                 │             │
│   ├──────────┤ │   │   📄 SCAN       │  │   📁 BATCH      │             │
│   │ 📄 Scan  │ │   │   Single Sheet  │  │   Process Many  │             │
│   ├──────────┤ │   │                 │  │                 │             │
│   │ 📁 Batch │ │   └─────────────────┘  └─────────────────┘             │
│   ├──────────┤ │                                                         │
│   │ 🔑 Keys  │ │   ┌─────────────────┐  ┌─────────────────┐             │
│   ├──────────┤ │   │                 │  │                 │             │
│   │ 📊 Hist. │ │   │   🔑 ANSWER     │  │   📊 HISTORY    │             │
│   └──────────┘ │   │   KEYS          │  │   View Results  │             │
│                │   │                 │  │                 │             │
│                │   └─────────────────┘  └─────────────────┘             │
│                │                                                         │
│                │   ─────────────────────────────────────────────        │
│                │   RECENT ACTIVITY                                       │
│                │   • scan_042.jpg - Student 2021001234 - 85% - 2 min ago│
│                │   • Batch completed: 50 sheets - 10 min ago            │
│                │   • Answer Key "Final 2025" created - 1 hour ago       │
│                │                                                         │
├────────────────┴─────────────────────────────────────────────────────────┤
│  Ready │ Database: Connected │ Last scan: scan_042.jpg                   │
└──────────────────────────────────────────────────────────────────────────┘
```

---

### Screen 2: Single Scan

**Purpose:** Process one sheet, view detailed results

```
┌──────────────────────────────────────────────────────────────────────────┐
│  SINGLE SCAN                                                             │
├────────────────────────────────────┬─────────────────────────────────────┤
│                                    │                                     │
│   IMAGE PREVIEW                    │   EXTRACTED DATA                    │
│   ┌──────────────────────────────┐ │                                     │
│   │                              │ │   Student ID                        │
│   │                              │ │   ┌─────────────────────────────┐   │
│   │                              │ │   │ 2 0 2 1 0 0 1 2 3 4       │   │
│   │   [OMR Sheet Image]          │ │   └─────────────────────────────┘   │
│   │   [with detection overlays]  │ │   Status: ✓ Valid                   │
│   │                              │ │                                     │
│   │                              │ │   Test ID                           │
│   │                              │ │   ┌───────────┐                     │
│   │                              │ │   │ 1 0 0 1   │                     │
│   └──────────────────────────────┘ │   └───────────┘                     │
│                                    │   Status: ✓ Valid                   │
│   Zoom: [─────●─────] 100%         │   Answer Key: Final Exam 2025 (Auto)│
│   ☑ Show Student ID region         │                                     │
│   ☑ Show Test ID region            │   ───────────────────────────────   │
│   ☑ Show Answer blocks             │                                     │
│                                    │   ANSWERS                           │
│   ────────────────────────────     │   ┌───┬───┬───┬─────┐               │
│                                    │   │ # │Ans│Key│ ✓/✗ │               │
│   [📂 Load Image]  [▶ Process]     │   ├───┼───┼───┼─────┤               │
│                                    │   │ 1 │ A │ A │  ✓  │               │
│   ────────────────────────────     │   │ 2 │ B │ C │  ✗  │               │
│   PROCESSING LOG                   │   │ 3 │ - │ A │ Skip│               │
│   ┌──────────────────────────────┐ │   │ 4 │A,B│ B │ Mult│               │
│   │ ✓ Image loaded               │ │   │...│...│...│ ... │               │
│   │ ✓ Deskewing complete         │ │   └───┴───┴───┴─────┘               │
│   │ ✓ Student ID: 2021001234     │ │                                     │
│   │ ✓ Test ID: 1001              │ │   SCORE: 51/60 (85.0%)              │
│   │ ✓ 60 answers detected        │ │   Correct: 51  Wrong: 7             │
│   │ ✓ Grading complete           │ │   Skipped: 1   Invalid: 1           │
│   └──────────────────────────────┘ │                                     │
│                                    │   [💾 Save] [📤 Export] [🔄 Rescan] │
│                                    │                                     │
└────────────────────────────────────┴─────────────────────────────────────┘
```

---

### Screen 3: Batch Processing

**Purpose:** Process multiple sheets efficiently

```
┌──────────────────────────────────────────────────────────────────────────┐
│  BATCH PROCESSING                                                        │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Source Folder: [ C:\Scans\Exam_2025\                    ] [Browse...]   │
│  Default Answer Key: [ Auto-detect by Test ID ▼ ]                        │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ Status │ Filename        │ Student ID  │ Test ID │ Score │ Issues │  │
│  ├────────┼─────────────────┼─────────────┼─────────┼───────┼────────┤  │
│  │   ✓    │ scan_001.jpg    │ 2021001234  │  1001   │ 85.0% │   -    │  │
│  │   ✓    │ scan_002.jpg    │ 2021001235  │  1001   │ 72.5% │   -    │  │
│  │   ⚠    │ scan_003.jpg    │ 20210012_4  │  1001   │ 78.0% │ ID err │  │
│  │   ⏳    │ scan_004.jpg    │     ---     │   ---   │  ---  │   -    │  │
│  │   ○    │ scan_005.jpg    │     ---     │   ---   │  ---  │   -    │  │
│  │   ✗    │ scan_006.jpg    │   ERROR     │ ERROR   │  ---  │ Failed │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  Progress: ████████████░░░░░░░░░░░░░ 35%  (35/100 files)                │
│                                                                          │
│  ┌─ SUMMARY ──────────────────────────────────────────────────────────┐  │
│  │  ✓ Successful: 33    ⚠ Needs Review: 2    ✗ Failed: 0              │  │
│  │  Avg Score: 76.3%    Highest: 98%    Lowest: 45%                   │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  [▶ Start]  [⏸ Pause]  [⏹ Stop]           [👁 Review Issues]            │
│                                            [📤 Export All to CSV]        │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

### Screen 4: Answer Key Manager

**Purpose:** Create and manage answer keys, link to Test IDs

```
┌──────────────────────────────────────────────────────────────────────────┐
│  ANSWER KEY MANAGER                                                      │
├─────────────────────────┬────────────────────────────────────────────────┤
│                         │                                                │
│   SAVED ANSWER KEYS     │   EDITOR                                       │
│   ┌───────────────────┐ │                                                │
│   │ ✓ Final Exam 2025 │ │   Name: [ Final Exam 2025                   ]  │
│   │   Practice Test A │ │   Test ID: [ 1001 ]  (for auto-detection)      │
│   │   Practice Test B │ │                                                │
│   │   Quiz 1          │ │   ─────────────────────────────────────────    │
│   │   Quiz 2          │ │                                                │
│   └───────────────────┘ │   ANSWERS (scrollable list)                    │
│                         │   ┌──────────────────────────────────────────┐ │
│   [➕ New]              │   │                                          │ │
│   [📋 Duplicate]        │   │   Q1:  (●)A  ( )B  ( )C  ( )D            │ │
│   [🗑 Delete]           │   │   Q2:  ( )A  ( )B  (●)C  ( )D            │ │
│                         │   │   Q3:  ( )A  (●)B  ( )C  ( )D            │ │
│   ─────────────────     │   │   Q4:  ( )A  ( )B  ( )C  (●)D            │ │
│                         │   │   Q5:  (●)A  ( )B  ( )C  ( )D            │ │
│   FILTER                │   │   ...                                    │ │
│   [🔍 Search...      ]  │   │   Q60: ( )A  (●)B  ( )C  ( )D            │ │
│                         │   │                                          │ │
│                         │   └──────────────────────────────────────────┘ │
│                         │                                                │
│                         │   QUICK ENTRY (alternative)                    │
│                         │   [ ACBDABCDABCD... ] [Parse]                  │
│                         │   (Type 60 letters: A, B, C, or D)             │
│                         │                                                │
│                         │   ─────────────────────────────────────────    │
│                         │                                                │
│                         │   [💾 Save]  [📥 Import CSV]  [📤 Export CSV]  │
│                         │                                                │
└─────────────────────────┴────────────────────────────────────────────────┘
```

**Field Explanations:**

| Field | Purpose |
|-------|---------|
| `name` | Human-readable label shown in UI (e.g., "Final Exam 2025") |
| `test_id` | 4-digit code from OMR sheet for auto-detection (e.g., "1001") |

**Answer Grid Explanation:**

The answer grid shows all 60 questions in a scrollable list. Each question has 4 radio buttons (A, B, C, D). 

- Click a radio button to select the correct answer
- Only one answer can be selected per question
- Scroll to see all 60 questions

**Two Ways to Enter Answers:**

1. **Radio Buttons (Visual):** Click through each question, select correct answer
2. **Quick Entry (Fast):** Type 60 letters in sequence (e.g., "ACBDABCD..."), click Parse

Example Quick Entry: `ACDBAABCDCBADCBAABCD...` (60 letters total)

---

### Screen 5: Results History

**Purpose:** Browse past scans, filter, search, export

```
┌──────────────────────────────────────────────────────────────────────────┐
│  RESULTS HISTORY                                                         │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  FILTERS                                                                 │
│  Date: [From: ________] [To: ________]   Test ID: [All ▼]               │
│  Student ID: [__________]                Status: [All ▼]    [🔍 Search] │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ Date       │ Student ID  │ Test ID │ Answer Key     │ Score │ Stat │  │
│  ├────────────┼─────────────┼─────────┼────────────────┼───────┼──────┤  │
│  │ 2025-11-25 │ 2021001234  │  1001   │ Final Exam '25 │ 85.0% │  ✓   │  │
│  │ 2025-11-25 │ 2021001235  │  1001   │ Final Exam '25 │ 72.5% │  ✓   │  │
│  │ 2025-11-25 │ 2021001236  │  1001   │ Final Exam '25 │ 91.0% │  ✓   │  │
│  │ 2025-11-24 │ 2021001234  │  1002   │ Practice Test  │ 65.0% │  ✓   │  │
│  │ 2025-11-24 │ 20210012_4  │  1001   │ Final Exam '25 │ 78.0% │  ⚠   │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  Showing 1-50 of 234 results                      [< Prev] [Next >]      │
│                                                                          │
│  ┌─ STATISTICS ─────────────────────────────────────────────────────┐    │
│  │ Total Scans: 234    Avg Score: 74.2%    High: 98.3%   Low: 32.0% │    │
│  └───────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  [👁 View Details]  [🗑 Delete]  [📤 Export Selected]                     │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## UI Components

### JavaFX Controls by Screen

#### Home / Dashboard

| Component | JavaFX Control | Purpose |
|-----------|----------------|---------|
| Quick Action Tiles | `VBox` with click handler | Navigate to screens |
| Activity Feed | `ListView<ActivityItem>` | Show recent scans/actions |
| Stats Labels | `Label` | Display counts from database |

#### Single Scan

| Component | JavaFX Control | Purpose |
|-----------|----------------|---------|
| Image Preview | `ScrollPane` containing `ImageView` | Display loaded image with zoom/pan |
| Zoom Slider | `Slider` | Control zoom level (25% - 400%) |
| Overlay Toggles | `CheckBox` × 4 | Show/hide detection overlays |
| ID Display | `TextField` (readonly) | Show extracted Student/Test ID |
| ID Status | `Label` | Show ✓ Valid / ⚠ Error |
| Answer Key Selector | `ComboBox<AnswerKey>` | Choose or auto-select answer key |
| Answers Table | `TableView<ScanAnswer>` | Display 60 answers with status |
| Score Display | `ProgressBar` + `Label` | Visual score representation |
| Processing Log | `TextArea` | Show processing steps |
| Action Buttons | `Button` | Load, Process, Save, Export |

#### Batch Processing

| Component | JavaFX Control | Purpose |
|-----------|----------------|---------|
| Folder Path | `TextField` + `Button` | Select source folder |
| Answer Key | `ComboBox<AnswerKey>` | Select default key |
| File Table | `TableView<BatchItem>` | List files with status |
| Progress Bar | `ProgressBar` | Show batch progress |
| Time Labels | `Label` | Elapsed / Remaining time |
| Summary Stats | `Label` × 4 | Success/Review/Failed/Pending counts |
| Control Buttons | `Button` | Start, Pause, Stop |

#### Answer Key Manager

| Component | JavaFX Control | Purpose |
|-----------|----------------|---------|
| Keys List | `ListView<AnswerKey>` | Show saved answer keys |
| Search Field | `TextField` | Filter keys list |
| Name Field | `TextField` | Edit answer key name |
| Test ID Field | `TextField` | Edit linked test ID |
| Answer Grid | `ScrollPane` containing `VBox` of `HBox` | 60 rows, each with 4 `RadioButton` |
| Quick Entry | `TextField` + `Button` | Parse string of 60 letters |
| Action Buttons | `Button` | New, Duplicate, Delete, Save, Import, Export |

#### Results History

| Component | JavaFX Control | Purpose |
|-----------|----------------|---------|
| Date Filters | `DatePicker` × 2 | From/To date range |
| Dropdowns | `ComboBox` × 2 | Filter by Test ID, Status |
| Search Field | `TextField` | Search by Student ID |
| Results Table | `TableView<Scan>` | Display scan history |
| Pagination | `Button` + `Label` | Navigate pages |
| Stats Bar | `Label` × 4 | Total, Average, High, Low |
| Action Buttons | `Button` | View Details, Delete, Export |

### FXML Structure

#### main.fxml (Root Layout)

```
BorderPane
├── Left: VBox (Sidebar)
│   ├── Label "OMR Reader V2"
│   └── VBox (Navigation Buttons)
│       ├── Button "Home"
│       ├── Button "Scan"
│       ├── Button "Batch"
│       ├── Button "Keys"
│       └── Button "History"
├── Center: StackPane (Content Host)
│   └── [Dynamically loaded FXML]
└── Bottom: HBox (Status Bar)
    ├── Label (Status message)
    ├── Separator
    └── Label (Database status)
```

#### scan.fxml

```
SplitPane (horizontal, 50/50 split)
├── Left: VBox
│   ├── ScrollPane > ImageView (image preview)
│   ├── HBox (zoom slider + label)
│   ├── VBox (overlay checkboxes)
│   ├── HBox (Load + Process buttons)
│   └── TextArea (processing log)
└── Right: VBox
    ├── TitledPane "Extracted IDs"
    │   └── GridPane (ID fields + status)
    ├── TitledPane "Answers"
    │   └── TableView
    ├── VBox "Score"
    │   ├── ProgressBar
    │   └── HBox (stat labels)
    └── HBox (Save + Export buttons)
```

### User Feedback

| Event | Feedback Type | Description |
|-------|---------------|-------------|
| Processing | Spinner + Log | Show progress in log area |
| Success | Status bar update | "Saved successfully" message |
| Warning | Yellow highlight | Row highlighted in table |
| Error | Red text + dialog | Show error message |
| Long operation | Progress bar | Determinate progress |

---

## Database Schema

### Entity Relationship Diagram

```
┌─────────────────┐
│  answer_keys    │
├─────────────────┤
│ id (PK)         │
│ name            │◄──── Human-readable label
│ test_id         │◄──── For auto-detection from OMR
│ created_at      │
│ updated_at      │
└────────┬────────┘
         │
         │ 1:N
         ▼
┌─────────────────┐
│answer_key_items │
├─────────────────┤
│ id (PK)         │
│ answer_key_id   │───► FK to answer_keys
│ question_number │     (1-60)
│ correct_answer  │     (A, B, C, D)
└─────────────────┘


┌─────────────────┐
│     scans       │
├─────────────────┤
│ id (PK)         │
│ source_file     │◄──── Original filename
│ student_id      │◄──── Extracted from OMR (10 digits)
│ test_id         │◄──── Extracted from OMR (4 digits)
│ answer_key_id   │───► FK to answer_keys (used for grading)
│ total_questions │     (60)
│ total_answered  │
│ correct_count   │
│ incorrect_count │
│ skipped_count   │
│ invalid_count   │
│ score           │◄──── Percentage
│ status          │◄──── success / partial / failed
│ scanned_at      │
└────────┬────────┘
         │
         │ 1:N
         ▼
┌─────────────────┐
│  scan_answers   │
├─────────────────┤
│ id (PK)         │
│ scan_id         │───► FK to scans
│ question_number │     (1-60)
│ detected_answer │     (A, B, C, D, null, "A,B")
│ correct_answer  │     (from answer key)
│ is_correct      │     (boolean)
│ status          │     (correct/wrong/skipped/invalid/unreadable)
└─────────────────┘
```

---

### Table Definitions

#### `answer_keys`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY, AUTO INCREMENT | Unique identifier |
| name | TEXT | NOT NULL | Human-readable label (e.g., "Final Exam 2025") |
| test_id | TEXT | UNIQUE | 4-digit Test ID for auto-detection (e.g., "1001") |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | DATETIME | | Last modification timestamp |

#### `answer_key_items`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY, AUTO INCREMENT | Unique identifier |
| answer_key_id | INTEGER | FOREIGN KEY → answer_keys(id), ON DELETE CASCADE | Parent answer key |
| question_number | INTEGER | NOT NULL, CHECK (1-60) | Question number |
| correct_answer | CHAR(1) | NOT NULL, CHECK (A/B/C/D) | Correct answer choice |

**Unique constraint:** (answer_key_id, question_number)

#### `scans`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY, AUTO INCREMENT | Unique identifier |
| source_file | TEXT | NOT NULL | Original image filename |
| student_id | TEXT | | Extracted 10-digit Student ID (may contain `_` or `?`) |
| test_id | TEXT | | Extracted 4-digit Test ID |
| answer_key_id | INTEGER | FOREIGN KEY → answer_keys(id) | Answer key used for grading |
| total_questions | INTEGER | DEFAULT 60 | Total questions on sheet |
| total_answered | INTEGER | | Questions with valid single answer |
| correct_count | INTEGER | | Correctly answered questions |
| incorrect_count | INTEGER | | Incorrectly answered questions |
| skipped_count | INTEGER | | Unanswered questions |
| invalid_count | INTEGER | | Questions with multiple marks |
| score | REAL | | Percentage score (correct/total × 100) |
| status | TEXT | CHECK (success/partial/failed) | Processing status |
| scanned_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | Scan timestamp |

#### `scan_answers`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY, AUTO INCREMENT | Unique identifier |
| scan_id | INTEGER | FOREIGN KEY → scans(id), ON DELETE CASCADE | Parent scan |
| question_number | INTEGER | NOT NULL, CHECK (1-60) | Question number |
| detected_answer | TEXT | | Detected answer (A/B/C/D/null/"A,B" for multiple) |
| correct_answer | CHAR(1) | | Correct answer from key |
| is_correct | BOOLEAN | | Whether answer matches key |
| status | TEXT | | correct / wrong / skipped / invalid / unreadable |

---

### Key Queries

**Auto-detect answer key by Test ID:**
```sql
SELECT * FROM answer_keys WHERE test_id = '1001';
```

**Get full scan result with answers:**
```sql
SELECT s.*, sa.question_number, sa.detected_answer, sa.correct_answer, sa.status
FROM scans s
JOIN scan_answers sa ON s.id = sa.scan_id
WHERE s.id = 123
ORDER BY sa.question_number;
```

**Results history with answer key name:**
```sql
SELECT s.*, ak.name as answer_key_name
FROM scans s
LEFT JOIN answer_keys ak ON s.answer_key_id = ak.id
ORDER BY s.scanned_at DESC;
```

**Filter by student:**
```sql
SELECT * FROM scans 
WHERE student_id = '2021001234'
ORDER BY scanned_at DESC;
```

**Statistics for a test:**
```sql
SELECT 
    COUNT(*) as total_scans,
    AVG(score) as avg_score,
    MAX(score) as highest,
    MIN(score) as lowest
FROM scans
WHERE test_id = '1001';
```

---

## Project Structure

```
src/main/java/org/example/
├── Main.java                      # Application entry point
│
├── controller/                    # JavaFX Controllers
│   ├── MainController.java        # Root/navigation controller
│   ├── ScanController.java        # Single scan screen
│   ├── BatchController.java       # Batch processing screen
│   ├── AnswerKeyController.java   # Answer key management
│   └── HistoryController.java     # Results history screen
│
├── model/                         # Data models
│   ├── AnswerKey.java             # Answer key entity
│   ├── AnswerKeyItem.java         # Single answer in key
│   ├── Scan.java                  # Scan record entity
│   ├── ScanAnswer.java            # Single answer in scan
│   ├── OMRResult.java             # Processing result (transient)
│   └── Bubble.java                # Detected bubble (transient)
│
├── service/                       # Business logic
│   ├── OMRProcessor.java          # Core image processing
│   ├── DatabaseService.java       # SQLite operations
│   └── ExportService.java         # CSV export
│
└── util/                          # Utilities
    └── ImageUtils.java            # Image helper methods

src/main/resources/
├── fxml/                          # JavaFX layouts
│   ├── main.fxml                  # Root layout with navigation
│   ├── home.fxml                  # Dashboard
│   ├── scan.fxml                  # Single scan screen
│   ├── batch.fxml                 # Batch processing
│   ├── answer-key.fxml            # Answer key manager
│   └── history.fxml               # Results history
│
├── css/                           # Stylesheets
│   └── style.css                  # Application styles
│
└── db/                            # Database
    └── schema.sql                 # Initial schema
```

---

## Implementation Order

| Phase | Components | Description | Status |
|-------|------------|-------------|--------|
| **1** | UI Framework | Main window, sidebar navigation, screen loading | ✅ Complete |
| **2** | Processor Interface | IOMRProcessor interface, MockOMRProcessor for dev | ✅ Complete |
| **3** | Database Setup | SQLite schema, DatabaseService singleton | ✅ Complete |
| **4** | Answer Key Manager | CRUD operations, paginated grid (15 per block) | ✅ Complete |
| **5** | Single Scan - UI | Wire ScanController, MockProcessor integration | 🔄 In Progress |
| **6** | Results History | Browse, filter, view details, delete | ⏳ Pending |
| **7** | Export | CSV export for single and multiple results | ⏳ Pending |
| **8** | Batch Processing | Folder processing, progress tracking | ⏳ Pending |
| **9** | Manual Correction | Edit detected answers in UI | ⏳ Pending |
| **10** | Real Processor | Replace MockOMRProcessor with OpenCV implementation | ⏳ Pending |

### Modular Processor Architecture

The OMR processor is abstracted behind `IOMRProcessor` interface:

```java
public interface IOMRProcessor {
    OMRResult processImage(File imageFile);
    OMRResult processImage(byte[] imageBytes, String fileName);
    boolean isValidImageFile(File file);
    String getProcessorName();
    boolean isReady();
}
```

**Implementations:**
- `MockOMRProcessor` - Generates fake data for UI/DB development
- `OMRProcessor` (future) - Real OpenCV-based image processing

This allows parallel development of UI/DB while the image processing is being built.

---

## Core Workflow

```
┌─────────────────┐
│   Load Image    │
└────────┬────────┘
         ▼
┌─────────────────┐
│    Deskew       │ ← Using L-shaped fiducials
└────────┬────────┘
         ▼
┌─────────────────┐
│  Extract IDs    │ ← Student ID (10 digits) + Test ID (4 digits)
└────────┬────────┘
         ▼
┌─────────────────┐
│ Auto-load Key   │ ← Lookup by Test ID
└────────┬────────┘
         ▼
┌─────────────────┐
│ Detect Answers  │ ← 60 questions × 4 choices
└────────┬────────┘
         ▼
┌─────────────────┐
│     Grade       │ ← Compare to answer key
└────────┬────────┘
         ▼
┌─────────────────┐
│  Display & Save │ ← Show results, store in DB
└─────────────────┘
```

---

## ID Error Handling

| Scenario | Raw Value | Action |
|----------|-----------|--------|
| All digits clear | `2021001234` | ✓ Valid, proceed |
| Missing digit (unfilled) | `20210012_4` | ⚠ Flag for review |
| Multiple marks | `202100?234` | ⚠ Flag for review |
| All missing | `__________` | ❌ Manual entry required |

---

**Document Version:** 2.2  
**Last Updated:** November 2025  
**Status:** Implementation In Progress

---

## Revision History

| Version | Date | Changes |
|---------|------|---------|
| 2.2 | Nov 2025 | Implementation started: UI, DB, AnswerKey CRUD, MockProcessor |
| 2.1 | Nov 2025 | Added UI Design System, Navigation, Components; Clarified Answer Grid |
| 2.0 | Nov 2025 | Initial planning document |

---

## Implemented Files

### Java Classes

| Package | Class | Purpose |
|---------|-------|---------|
| `org.example` | `Main.java` | Application entry point, DB init |
| `org.example.controller` | `MainController.java` | Sidebar navigation, screen loading |
| `org.example.controller` | `AnswerKeyController.java` | Answer key CRUD with paginated grid |
| `org.example.model` | `AnswerKey.java` | Answer key entity |
| `org.example.model` | `AnswerKeyItem.java` | Single answer in key |
| `org.example.model` | `OMRResult.java` | Processing result from IOMRProcessor |
| `org.example.service` | `DatabaseService.java` | SQLite singleton with helper methods |
| `org.example.service` | `AnswerKeyService.java` | Answer key CRUD operations |
| `org.example.service` | `IOMRProcessor.java` | Processor interface |
| `org.example.service` | `MockOMRProcessor.java` | Fake data generator for development |

### Resources

| Path | Purpose |
|------|---------|
| `fxml/main.fxml` | Root layout with sidebar |
| `fxml/home.fxml` | Dashboard with action cards |
| `fxml/scan.fxml` | Single scan screen |
| `fxml/batch.fxml` | Batch processing screen |
| `fxml/answer-key.fxml` | Answer key manager with controller |
| `fxml/history.fxml` | Results history screen |
| `css/style.css` | Complete stylesheet |
| `db/schema.sql` | SQLite schema |
