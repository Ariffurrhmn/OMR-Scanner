# Desktop OMR Reader

A Windows desktop application to design, scan, and grade Optical Mark Recognition (OMR) sheets with a visual template editor. Built with **JavaFX** for the UI and **JavaCV (OpenCV)** for image processing.

## 🎯 Project Context

  * **Course:** Advanced Programming (university project)
  * **Goal:** Achieve feature parity with an existing Python OMR pipeline, while removing manual JSON editing by enabling users to visually define fields on a sheet.

## ✨ Features

### Visual Template Designer

  * Draw regions on a sample sheet and name fields.
  * Choose field types:
      * MCQ (single/multi-select)
      * Numeric digits (fixed choices 0–9)
      * Text blocks (for manual review)
  * Clone fields for multi-digit IDs (e.g., `Roll1`, `Roll2`, …).
  * Includes grid/snap functionality and overlap validation.

### Reusable Configurations

  * Save/load auto-generated `template.v1.json` and `scoring.v1.json`.
  * Users never need to edit the JSON configuration by hand.

### Batch Processing

  * Process entire folders of scanned sheets.
  * Provides live progress updates and per-file status.
  * Includes throughput metrics and clear error flags (e.g., multi-marked, missing markers).

### Annotated Previews

  * Optional overlay showing detected bubbles and the system's decisions.

### Scoring

  * Define per-field/section answers, weights, and negative marks.
  * Supports CSV import/export of answer keys.

### Exports

  * Consolidated CSV reports.
  * Annotated images for review.
  * Evaluation summaries.

### Windows-First

  * Primary testing focus is on Windows; cross-platform compatibility will be considered later.

## 🛠️ Tech Stack

  * **UI:** JavaFX
  * **Image Processing:** JavaCV (OpenCV bindings)
  * **Build:** Gradle
  * **Logging:** SLF4J + Logback
  * **Java:** 17+

## 📂 Repository Layout

```
java-desktop-app/
├── src/main/java/com/omrchecker/desktop/  # JavaFX application code
├── src/main/resources/                   # FXML, CSS, icons
├── docs/                                 # UX notes, design artifacts
├── build.gradle
└── settings.gradle
```

## 📋 Prerequisites

  * Windows 10/11
  * Java 17+
  * Gradle (or use the Gradle wrapper when added)
  * Internet access to resolve dependencies on the first run

## 🚀 Build and Run

From the `java-desktop-app/` directory:

```sh
# Run using the Gradle wrapper (if available)
gradlew run

# Or run using a system-installed Gradle
gradle run
```

> **Note:**
>
>   * Packaging (e.g., an installer) is intentionally deferred until after testing.
>   * OpenCV native binaries are provided automatically via the JavaCV platform dependency.

## ⚡ Quick Start

1.  Launch the application.
2.  **Create a new project:**
      * Load a blank OMR sheet image.
      * Use the rectangle tool to mark fields.
      * Choose field types (MCQ, Numeric 0–9, Text) and set field names.
3.  Save your template (`template.v1.json`) and scoring config (`scoring.v1.json`).
4.  Load a directory of scanned sheets and start processing.
5.  Export results to CSV and review annotated images for any edge cases.

## 🔧 Usage Details

### Numeric Fields (0–9)

  * Choices are fixed to digits 0–9.
  * For multi-digit IDs, clone fields and name them sequentially (e.g., `ID1`, `ID2`, …).

### Scoring Setup

  * Set correct answers, scores, and negative marking per field or section.
  * Import/export answer keys as CSV (using the format `FieldName,Answer`).

### Review

  * Filter results by errors or multi-marked answers.
  * Inspect annotated overlays to validate detection accuracy.

## 🏛️ Architecture Overview

### UI Modules

  * Welcome Screen
  * Template Designer
  * Scoring Configuration
  * Batch Processing Dashboard
  * Review & Results

### Services

  * **Image Service:** Loading, preprocessing, and alignment (JavaCV)
  * **Detection Service:** Bubble detection and response extraction
  * **Evaluation Service:** Scoring and evaluation logic
  * **Export Service:** CSV and image generation

### Persistence

  * Templates and scoring are saved as versioned JSON files.
  * Includes a `schemaVersion` for forward compatibility.

## 🗺️ Roadmap

  * [ ] Template designer MVP (drawing, naming, duplication, validation)
  * [ ] Scoring import/export and section-level rules
  * [ ] JavaCV port of preprocessing (marker detection, cropping, alignment)
  * [ ] JavaCV bubble analysis
  * [ ] Batch processing with progress and detailed logs
  * [ ] Manual review and overrides (optional)
  * [ ] Windows installer (after testing)
  * [ ] Evaluate macOS/Linux builds (later)

## 🎓 Academic Notes

This project demonstrates advanced GUI development with JavaFX, image processing via OpenCV (JavaCV), and robust file I/O. It emphasizes a modular architecture, versioned data formats, and extensible services.
