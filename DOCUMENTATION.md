# OMR Reader V2 - Complete Technical Documentation

## Table of Contents
1. [Overview](#overview)
2. [OMR Sheet Structure](#omr-sheet-structure)
3. [Processing Pipeline](#processing-pipeline)
4. [Input Validation](#input-validation)
5. [Stage 0: Deskewing](#stage-0-deskewing)
6. [Stage 1: Preprocessing](#stage-1-preprocessing)
7. [Stage 2: Extract Answer Region](#stage-2-extract-answer-region)
8. [Stage 3: Find Answer Blocks](#stage-3-find-answer-blocks)
9. [Stage 4: Find Bubbles & Sort](#stage-4-find-bubbles--sort)
10. [Stage 5: Mark Recognition](#stage-5-mark-recognition)
11. [Stage 6: Answer Extraction](#stage-6-answer-extraction)
12. [Data Structures](#data-structures)
13. [Constants & Configuration](#constants--configuration)
14. [Error Handling](#error-handling)
15. [Implementation Decisions](#implementation-decisions)

---

## Overview

This OMR (Optical Mark Recognition) Reader is designed specifically for a fixed-format answer sheet containing **60 multiple-choice questions** arranged in **4 vertical columns** (blocks), with **15 questions per block**. Each question has **4 answer choices** (A, B, C, D) represented as circular bubbles.

**Key Features:**
- Hierarchical detection approach (large to small structures)
- Adaptive tolerance calculation for robust sorting
- Comprehensive error handling and validation
- Designed for a specific OMR sheet format

**Technical Stack:**
- Java 21
- JavaFX 21.0.1 (UI)
- OpenCV 4.9.0 (Image Processing via JavaCV)
- Maven (Build Tool)

---

## OMR Sheet Structure

### Physical Layout

**Fiducials (Registration Marks):**

**L-Shaped Fiducials (4 outer corners of entire sheet):**
- **Top-Left Corner:** L-shaped marker with interior angle pointing toward sheet center
- **Top-Right Corner:** Mirrored L-shaped marker
- **Bottom-Left Corner:** Upside-down L-shaped marker
- **Bottom-Right Corner:** Upside-down and mirrored L-shaped marker
- **Purpose:** Used for deskewing the entire scanned document (Stage 0)
- **Location:** Extreme corners of the entire sheet

**Rectangular/Square Fiducials (4 corners of answer section):**
- **Top-Left Corner:** Rectangular/square marker at answer section corner
- **Top-Right Corner:** Rectangular/square marker at answer section corner
- **Bottom-Left Corner:** Rectangular/square marker at answer section corner
- **Bottom-Right Corner:** Rectangular/square marker at answer section corner
- **Purpose:** Used to define the bounding box for the answer region (Stage 2)
- **Location:** Corners of the answer section (inside the sheet, around the answer blocks)

**Answer Blocks:**
- **4 Large Rectangular Blocks** with thick borders
- Arranged horizontally (side-by-side)
- Each block contains **15 questions** (Q1-15, Q16-30, Q31-45, Q46-60)
- Blocks are significantly taller than wide (aspect ratio > 4.0)

**Question Rows:**
- Within each block, questions are arranged vertically
- Each question row contains:
  - Question number (1-60)
  - 4 circular bubbles labeled A, B, C, D
- Rows are separated by thin horizontal lines

**Answer Bubbles:**
- Circular bubbles
- Uniform size and spacing
- 4 bubbles per question, arranged horizontally

### Fiducial Layout Diagram

```
┌─────────────────────────────────────────────┐
│ L (TL)                           L (TR)    │ ← Outer sheet corners
│                                              │   (L-shaped fiducials)
│    ┌──────────────────────────────┐         │
│    │ Rect (TL)         Rect (TR)  │         │ ← Answer section corners
│    │                               │         │   (Rectangular fiducials)
│    │    Answer Blocks (4 columns)  │         │
│    │                               │         │
│    │ Rect (BL)         Rect (BR)  │         │
│    └──────────────────────────────┘         │
│                                              │
│ L (BL)                           L (BR)    │
└─────────────────────────────────────────────┘

Total: 8 fiducials
- 4 L-shaped (outer): For deskewing entire sheet
- 4 Rectangular (inner): For answer region extraction
```

---

## Processing Pipeline

The processing follows a **hierarchical approach**, progressively narrowing the search space:

```
Raw Image
  ↓
Input Validation (check image validity)
  ↓
Stage 0: Deskew (using fiducials)
  ↓
Stage 1: Preprocessing (grayscale, normalize, blur, threshold)
  ↓
Stage 2: Extract Answer Region (mask using fiducials)
  ↓
Stage 3: Find Answer Blocks (4 largest tall rectangles)
  ↓
Stage 4: Find Question Row Rectangles (small rectangles within blocks)
  ↓
Stage 5: Find Bubbles (circles within question rectangles)
  ↓
Stage 6: Sort Bubbles (adaptive clustering into rows)
  ↓
Stage 7: Mark Recognition (fill percentage detection)
  ↓
Stage 8: Answer Extraction (map bubbles to questions)
```

---

## Input Validation

### Purpose
Validate input image before processing to fail fast with clear error messages.

### Implementation

**Validation Method:**
```java
/**
 * Validates input image before processing.
 * Throws IllegalArgumentException with descriptive message if validation fails.
 * 
 * @param image Input image to validate
 * @param fileName Optional file name for error messages
 * @throws IllegalArgumentException if image is invalid
 */
private void validateInputs(Mat image, String fileName) {
    if (image == null || image.empty()) {
        throw new IllegalArgumentException(
            String.format("Input image is null or empty. File: %s", 
                fileName != null ? fileName : "unknown")
        );
    }
    
    if (image.cols() < MIN_IMAGE_WIDTH || image.rows() < MIN_IMAGE_HEIGHT) {
        throw new IllegalArgumentException(
            String.format(
                "Image too small: %dx%d (minimum: %dx%d). File: %s",
                image.cols(), image.rows(), 
                MIN_IMAGE_WIDTH, MIN_IMAGE_HEIGHT,
                fileName != null ? fileName : "unknown"
            )
        );
    }
    
    if (image.channels() < 1 || image.channels() > 4) {
        throw new IllegalArgumentException(
            String.format(
                "Unsupported image format: %d channels. Expected 1 (grayscale) or 3 (BGR). File: %s",
                image.channels(),
                fileName != null ? fileName : "unknown"
            )
        );
    }
}
```

### Constants
```java
// Input Validation
private static final int MIN_IMAGE_WIDTH = 640;  // pixels
private static final int MIN_IMAGE_HEIGHT = 480;  // pixels
```

### When to Use
- Call `validateInputs()` at the start of `processOMR()` method
- Before Stage 0 (deskewing)
- Provides early failure with clear error messages

---

## Stage 0: Deskewing

### Purpose
Corrects for rotation and skew in scanned images using **L-shaped fiducials** located at the four outer corners of the entire sheet. The sharpest interior vertex of each L-shape defines the exact corner of the sheet.

### Implementation Steps

1. **Preprocessing:**
   - Convert image to grayscale
   - Apply binary threshold (Otsu's method)
   - Find all contours

2. **Filter Large Contours:**
   - Filter contours by area: `area > L_FIDUCIAL_AREA_THRESHOLD` (500px)
   - This gives us all 8 fiducials (4 L-shapes + 4 rectangles)

3. **Classify Fiducials:**
   - For each contour, apply `Imgproc.approxPolyDP()` to simplify
   - Count vertices:
     - **L-shapes:** Typically 5-7 vertices (after approximation)
     - **Rectangles:** Typically 4 vertices (or close to 4)
   - Filter L-shapes: `vertexCount >= L_SHAPE_MIN_VERTICES` (5)
   - Store rectangles for Stage 2 (or re-detect them after deskewing)

4. **Find Sharpest Vertex in Each L-Shape:**
   - For each L-shape contour:
     - Apply `approxPolyDP()` to get vertices
     - For each vertex, calculate the angle formed by (prev → curr → next)
     - Use `calculateAngle(prev, curr, next)` helper method
     - Find vertex with minimum angle (sharpest corner)
     - This vertex is the sheet corner point

5. **Map Vertices to Corner Positions:**
   - After finding 4 corner vertices (one from each L-shape):
     - **Top-Left:** Smallest `(x + y)` sum
     - **Bottom-Right:** Largest `(x + y)` sum
     - **Top-Right:** Smallest `(x - y)` difference
     - **Bottom-Left:** Largest `(x - y)` difference

6. **Validate Corner Count:**
   - Need exactly 4 corner vertices
   - If < 4: Return original image, log error, abort processing

7. **Compute Target Rectangle:**
   - Calculate bounding rectangle of the 4 corner vertices
   - This defines the "ideal" axis-aligned target space

8. **Calculate Affine Transformation:**
   - Map 4 detected corner vertices → 4 target corners
   - Use `Imgproc.getAffineTransform()` (minimum 3 points) or `getPerspectiveTransform()` (4 points)

9. **Apply Transformation:**
   - Use `Imgproc.warpAffine()` or `Imgproc.warpPerspective()` to deskew image
   - Return deskewed image

### Helper Method: calculateAngle

```java
/**
 * Calculates the angle (in degrees) formed by three points (prev, curr, next).
 * Used to find the sharpest interior angle of L-shaped fiducials.
 * 
 * @param prev Previous point
 * @param curr Current point (vertex)
 * @param next Next point
 * @return Angle in degrees (0-180)
 */
private double calculateAngle(Point prev, Point curr, Point next) {
    // Vector from curr to prev
    double dx1 = prev.x - curr.x;
    double dy1 = prev.y - curr.y;
    
    // Vector from curr to next
    double dx2 = next.x - curr.x;
    double dy2 = next.y - curr.y;
    
    // Dot product: angle = arccos((v1·v2) / (|v1|*|v2|))
    double dot = dx1 * dx2 + dy1 * dy2;
    double mag1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
    double mag2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);
    
    double cosine = dot / (mag1 * mag2);
    
    // Clamp to avoid floating point errors
    if (cosine > 1.0) cosine = 1.0;
    if (cosine < -1.0) cosine = -1.0;
    
    return Math.toDegrees(Math.acos(cosine));
}
```

### Error Handling
- Returns original image on failure (< 4 L-shapes found)
- Logs error to `resultsArea`
- Sets internal status flag to skip subsequent stages

### Constants
```java
// L-Shaped Fiducials (for deskewing)
private static final int L_FIDUCIAL_AREA_THRESHOLD = 500; // pixels
private static final double L_SHAPE_APPROX_EPSILON = 0.02; // 2% of perimeter for approxPolyDP
private static final int L_SHAPE_MIN_VERTICES = 5; // L-shapes have 5+ vertices
private static final double MIN_CORNER_ANGLE_DEG = 60.0; // Minimum angle for corner detection
```

---

## Stage 1: Preprocessing

### Purpose
Prepares the deskewed image for reliable shape detection.

### Implementation Steps

1. **Grayscale Conversion:**
   - Convert BGR image to grayscale using `Imgproc.cvtColor(COLOR_BGR2GRAY)`

2. **Image Normalization (Recommended):**
   - Normalize image to ensure consistent intensity range
   - `Core.normalize(grayscale, normalized, 0, 255, Core.NORM_MINMAX)`
   - This helps handle varying lighting conditions and scan quality
   - **Note:** Optional but recommended for robustness

3. **Gaussian Blur:**
   - Apply blur to reduce noise and paper texture
   - Kernel size: 5x5
   - `Imgproc.GaussianBlur()`

4. **Binary Threshold:**
   - Apply Otsu's automatic threshold
   - Inverted: Paper = black (0), marks/bubbles = white (255)
   - `Imgproc.threshold(THRESH_BINARY_INV | THRESH_OTSU)`

### Helper Method: Image Normalization

```java
/**
 * Normalizes image to 0-255 range for consistent processing.
 * Helps handle varying lighting conditions and scan quality.
 * 
 * @param image Input grayscale image
 * @return Normalized image
 */
private Mat normalizeImage(Mat image) {
    Mat normalized = new Mat();
    Core.normalize(image, normalized, 0, 255, Core.NORM_MINMAX);
    return normalized;
}
```

### Output
Binary image where:
- Background (paper) = 0 (black)
- Foreground (marks, bubbles, borders) = 255 (white)

---

## Stage 2: Extract Answer Region

### Purpose
Isolates the answer area by using **rectangular/square fiducials** located at the four corners of the answer section. These fiducials define the bounding box for the answer region.

### Implementation Steps

1. **Preprocess Deskewed Image:**
   - After Stage 0 deskewing, preprocess again:
   - Convert to grayscale
   - Apply binary threshold (Otsu's method)
   - Find all contours

2. **Filter Large Contours:**
   - Filter contours by area: `area > RECT_FIDUCIAL_AREA_THRESHOLD` (400px)

3. **Identify Rectangular Fiducials:**
   - For each contour, apply `Imgproc.approxPolyDP()` to simplify
   - Count vertices:
     - **Rectangles:** Should have approximately 4 vertices
   - Filter: `vertexCount ≈ 4` (within tolerance)
   - These are the rectangular/square fiducials at answer section corners
   - **Note:** These are different from the L-shaped fiducials used in Stage 0

4. **Find 4 Corner Positions:**
   - Use coordinate method to identify corners:
     - **Top-Left:** Smallest `(x + y)` sum
     - **Bottom-Right:** Largest `(x + y)` sum
     - **Top-Right:** Smallest `(x - y)` difference
     - **Bottom-Left:** Largest `(x - y)` difference
   - Or use bounding box centers (simpler, since rectangles are axis-aligned after deskewing)

5. **Validate Fiducial Count:**
   - Need exactly 4 rectangular fiducials
   - If ≠ 4: Log error, use fallback method or fail

6. **Calculate Bounding Box:**
   - Calculate rectangle encompassing all 4 rectangular fiducials
   - This defines the answer region boundaries

7. **Add Margin:**
   - Add `ANSWER_REGION_MARGIN` (10px) inward from edges
   - Prevents cutting off answer block boundaries

8. **Extract ROI:**
   - Use `Mat.submat(rect)` to extract region of interest
   - More efficient than masking (painting black)

### Constants
```java
// Rectangular Fiducials (for answer region)
private static final int RECT_FIDUCIAL_AREA_THRESHOLD = 400; // pixels
private static final double RECT_APPROX_EPSILON = 0.02; // for approxPolyDP
private static final int RECT_VERTEX_COUNT = 4; // Rectangles have 4 vertices
private static final double RECT_VERTEX_TOLERANCE = 1; // ±1 vertex tolerance
private static final double ANSWER_REGION_MARGIN = 10; // pixels
```

### Output
ROI Mat containing only the answer region

---

## Stage 3: Find Answer Blocks

### Purpose
Identifies the 4 large rectangular answer blocks (Q1-15, Q16-30, Q31-45, Q46-60).

### Implementation Steps

1. **Find Contours:**
   - Use `Imgproc.findContours()` on answer region
   - Mode: `RETR_EXTERNAL` (only outermost shapes)

2. **Filter by Area:**
   - Keep contours with area > `MIN_BLOCK_AREA` (5000px)
   - Filters out small noise

3. **Filter by Aspect Ratio:**
   - Calculate aspect ratio: `height / width`
   - Keep only contours with aspect ratio ≥ 4.0
   - Ensures we get tall rectangles (answer blocks), not wide headers

4. **Filter Out Fiducials:**
   - Rectangular fiducials are large but located at answer section corners
   - Filter by position: Remove contours near answer section corners
   - Or filter by shape: Rectangular fiducials have 4 vertices, not tall rectangles

5. **Sort by X-Coordinate:**
   - Sort remaining blocks by leftmost X-coordinate
   - Ensures order: Block 1 (Q1-15), Block 2 (Q16-30), Block 3 (Q31-45), Block 4 (Q46-60)

6. **Sanity Check:**
   - Verify exactly 4 blocks found
   - If ≠ 4: Log error, mark block as failed, skip processing

### Constants
```java
private static final double MIN_BLOCK_AREA = 5000.0; // pixels
private static final double MIN_BLOCK_ASPECT_RATIO = 4.0; // height/width
```

### Output
List of 4 `Rect` objects, sorted left-to-right

---

## Stage 4: Find Question Row Rectangles

### Purpose
Identifies the smaller rectangles (question rows) within each of the 4 large answer blocks. Each rectangle contains one question with its 4 answer bubbles.

### Implementation Steps (Per Block)

1. **Extract Block ROI:**
   - Use `Mat.submat(blockRect)` for each block

2. **Find Contours:**
   - Use `Imgproc.findContours()` on block ROI
   - Mode: `RETR_EXTERNAL` or `RETR_TREE` (depending on rectangle nesting)

3. **Filter by Area:**
   - Keep contours with area > `MIN_QUESTION_RECT_AREA` (smaller than blocks, larger than bubbles)
   - Filters out noise and very small shapes

4. **Filter by Aspect Ratio:**
   - Calculate aspect ratio: `width / height`
   - Keep only contours with aspect ratio > `MIN_QUESTION_RECT_ASPECT_RATIO` (wide rectangles, not tall)
   - Question rows are horizontal rectangles (wider than tall)

5. **Filter by Size:**
   - Must be smaller than the block itself
   - Must be larger than individual bubbles
   - Typical size: width ~200-400px, height ~30-50px

6. **Sort by Y-Coordinate:**
   - Sort rectangles by top Y-coordinate (top to bottom)
   - This gives us the question order (Q1, Q2, Q3, etc. within the block)

7. **Sanity Check:**
   - Expected: ~15 rectangles per block (one per question)
   - Total across all blocks: 40 rectangles (as per sheet design)
   - If count is significantly off: Log warning

### Constants
```java
// Question Row Rectangle Detection
private static final double MIN_QUESTION_RECT_AREA = 1000.0; // pixels
private static final double MIN_QUESTION_RECT_ASPECT_RATIO = 3.0; // width/height (wide rectangles)
private static final double MAX_QUESTION_RECT_AREA = 20000.0; // pixels (must be smaller than blocks)
private static final int EXPECTED_QUESTION_RECTS_PER_BLOCK = 15; // one per question
```

### Output
List of question row rectangles per block, sorted vertically (top to bottom)

---

## Stage 5: Find Bubbles (Circles)

### Purpose
Detects the 4 circular bubbles within each question row rectangle.

### Implementation Steps (Per Question Rectangle)

1. **Extract Question Rectangle ROI:**
   - Use `Mat.submat(questionRect)` for each question rectangle

2. **Run HoughCircles:**
   - Execute on question rectangle ROI (localized detection)
   - Parameters tuned for uniform bubble size:
     - `minRadius`: Based on expected bubble size
     - `maxRadius`: Based on expected bubble size
     - `minDist`: Based on bubble spacing (horizontal)
   - Returns list of bubbles in this rectangle (should be exactly 4: A, B, C, D)

3. **Sort Bubbles by X-Coordinate:**
   - Within each rectangle, sort bubbles left to right
   - Ensures order: A, B, C, D

4. **Sanity Check:**
   - Verify exactly 4 bubbles per rectangle
   - If ≠ 4: Mark question as "Unreadable"

### Constants
```java
// Bubble Detection (HoughCircles)
private static final double HOUGH_DP = 1.0;
private static final double HOUGH_MIN_DIST = 30.0;
private static final double HOUGH_PARAM1 = 100.0;
private static final double HOUGH_PARAM2 = 30.0;
private static final int HOUGH_MIN_RADIUS = 10; // pixels
private static final int HOUGH_MAX_RADIUS = 50; // pixels
```

### Output
List of bubbles per question rectangle (4 bubbles each, sorted A→B→C→D)

---

## Stage 6: Sort & Validate Bubbles

### Purpose
Validates and organizes detected bubbles, ensuring proper question-to-bubble mapping. Since bubbles are already detected within their question rectangles (Stage 5), this stage primarily handles validation and organization.

### Implementation Steps

1. **Group Bubbles by Question:**
   - Bubbles are already organized by their question rectangles (from Stage 4)
   - Each rectangle = one question = 4 bubbles
   - Maintain this organization

2. **Validate Bubble Count:**
   - For each question rectangle, verify exactly 4 bubbles found
   - If ≠ 4: Mark question as "Unreadable"
   - Log warning for questions with missing/extra bubbles

3. **Validate Bubble Order:**
   - Within each rectangle, bubbles should be sorted by X (A, B, C, D)
   - Verify X-coordinates are in increasing order
   - If order is incorrect: Attempt to correct or mark as "Unreadable"

4. **Map to Question Numbers:**
   - Block 1 (leftmost): Questions 1-15
   - Block 2: Questions 16-30
   - Block 3: Questions 31-45
   - Block 4 (rightmost): Questions 46-60
   - Map rectangle index within block to question number
   - Formula: `questionNumber = (blockIndex * 15) + rectangleIndex + 1`

5. **Create Question-Bubble Mapping:**
   - For each question, store the 4 bubbles in order (A, B, C, D)
   - This mapping will be used in Stage 7 (Mark Recognition) and Stage 8 (Answer Extraction)

### Constants
```java
// Bubble Validation
private static final int EXPECTED_BUBBLES_PER_ROW = 4; // A, B, C, D
```

### Output
Organized mapping: Question Number → List of 4 Bubbles (A, B, C, D)

---

## Stage 7: Mark Recognition

### Purpose
Determines if each bubble is marked (filled) or unmarked.

### Implementation Steps

For each bubble:

1. **Extract Bubble ROI:**
   - Create square region around bubble center
   - Size: `radius * 2` (diameter)

2. **Create Circular Mask:**
   - Create mask with circle filled (radius = bubble radius)
   - `Imgproc.circle(mask, center, radius, Scalar(255), -1)`

3. **Count Total Pixels:**
   - Count non-zero pixels in mask = total bubble area

4. **Count Marked Pixels:**
   - Use `Core.bitwise_and(bubbleROI, mask, result)`
   - Count non-zero pixels in result = marked pixels

5. **Calculate Fill Ratio:**
   - `fillRatio = markedPixels / totalPixels`

6. **Apply Threshold:**
   - If `fillRatio > FILL_THRESHOLD` (0.3 = 30%): Bubble is marked
   - Otherwise: Bubble is unmarked

### Constants
```java
private static final double FILL_THRESHOLD = 0.3; // 30% filled = marked
```

### Output
Boolean status for each bubble: `marked` or `unmarked`

---

## Stage 8: Answer Extraction

### Purpose
Maps detected bubbles to questions and extracts marked answers.

### Implementation Steps

For each question row:

1. **Check Mark Status:**
   - For each of 4 bubbles (A, B, C, D), check if marked
   - Count marked bubbles

2. **Apply Rules:**
   - **0 marked:** Question is "Skipped"
   - **1 marked:** Extract answer (A, B, C, or D)
   - **2+ marked:** Question is "Invalid (Multiple Answers)"

3. **Map to Question Number:**
   - Block 1 (leftmost): Questions 1-15
   - Block 2: Questions 16-30
   - Block 3: Questions 31-45
   - Block 4 (rightmost): Questions 46-60

4. **Store Results:**
   - Question number → Answer choice (or null if skipped/invalid)
   - Question number → Status (Correct/Incorrect/Skipped/Invalid)

5. **Optional: Grading:**
   - If answer key provided, compare detected answer to correct answer
   - Mark as "Correct" or "Incorrect"

### Output
Complete result object containing:
- Question → Answer mapping
- Question → Status mapping
- Statistics (total, answered, correct, incorrect, skipped, invalid)

---

## Data Structures

### ToleranceResult
```java
record ToleranceResult(
    double tolerance,      // Calculated Y_TOLERANCE
    boolean isValid,       // Whether calculation succeeded
    String message         // Warning/error message (nullable)
) {}
```

### Bubble
```java
record Bubble(
    Point center,         // Bubble center coordinates
    int radius         // Bubble radius
) {
    public int x() { return (int) center.x; }
    public int y() { return (int) center.y; }
}
```

### OMRResult
```java
class OMRResult {
    Map<Integer, Character> questionAnswers;  // Q# → Answer (A, B, C, D)
    Map<Integer, String> questionStatus;     // Q# → Status
    List<Integer> multipleMarks;              // Questions with 2+ marks
    List<Integer> unanswered;                 // Skipped questions
    List<Integer> unreadable;                // Unreadable questions
    int totalQuestions;
    int totalAnswered;
    int correctAnswers;
    int incorrectAnswers;
    String timestamp;
    String sourceFileName;
}
```

---

## Constants & Configuration

### All Constants (Recommended Organization)

```java
// ============================================================================
// INPUT VALIDATION
// ============================================================================
private static final int MIN_IMAGE_WIDTH = 640;  // pixels
private static final int MIN_IMAGE_HEIGHT = 480;  // pixels

// ============================================================================
// STAGE 0: L-SHAPED FIDUCIAL DETECTION (for deskewing)
// ============================================================================
private static final int L_FIDUCIAL_AREA_THRESHOLD = 500; // pixels
private static final double L_SHAPE_APPROX_EPSILON = 0.02; // 2% of perimeter
private static final int L_SHAPE_MIN_VERTICES = 5; // L-shapes have 5+ vertices
private static final double MIN_CORNER_ANGLE_DEG = 60.0; // degrees

// ============================================================================
// STAGE 2: RECTANGULAR FIDUCIAL DETECTION (for answer region)
// ============================================================================
private static final int RECT_FIDUCIAL_AREA_THRESHOLD = 400; // pixels
private static final double RECT_APPROX_EPSILON = 0.02; // for approxPolyDP
private static final int RECT_VERTEX_COUNT = 4; // Rectangles have 4 vertices
private static final double RECT_VERTEX_TOLERANCE = 1; // ±1 vertex tolerance
private static final double ANSWER_REGION_MARGIN = 10; // pixels

// ============================================================================
// STAGE 3: BLOCK DETECTION
// ============================================================================
private static final double MIN_BLOCK_AREA = 5000.0; // pixels
private static final double BLOCK_ASPECT_RATIO_MIN = 4.0; // height/width

// ============================================================================
// BUBBLE DETECTION (HoughCircles)
// ============================================================================
private static final double HOUGH_DP = 1.0;
private static final double HOUGH_MIN_DIST = 30.0;
private static final double HOUGH_PARAM1 = 100.0;
private static final double HOUGH_PARAM2 = 30.0;
private static final int HOUGH_MIN_RADIUS = 10; // pixels
private static final int HOUGH_MAX_RADIUS = 50; // pixels

// ============================================================================
// STAGE 4: QUESTION ROW RECTANGLE DETECTION
// ============================================================================
private static final double MIN_QUESTION_RECT_AREA = 1000.0; // pixels
private static final double MIN_QUESTION_RECT_ASPECT_RATIO = 3.0; // width/height (wide)
private static final double MAX_QUESTION_RECT_AREA = 20000.0; // pixels (smaller than blocks)
private static final int EXPECTED_QUESTION_RECTS_PER_BLOCK = 15; // one per question

// ============================================================================
// STAGE 5: BUBBLE DETECTION (HoughCircles)
// ============================================================================
private static final double HOUGH_DP = 1.0;
private static final double HOUGH_MIN_DIST = 30.0;
private static final double HOUGH_PARAM1 = 100.0;
private static final double HOUGH_PARAM2 = 30.0;
private static final int HOUGH_MIN_RADIUS = 10; // pixels
private static final int HOUGH_MAX_RADIUS = 50; // pixels

// ============================================================================
// STAGE 6: BUBBLE VALIDATION
// ============================================================================
private static final int EXPECTED_BUBBLES_PER_ROW = 4; // A, B, C, D

// ============================================================================
// MARK RECOGNITION
// ============================================================================
private static final double FILL_THRESHOLD = 0.3; // 30% filled = marked

// ============================================================================
// SHEET SPECIFICATION
// ============================================================================
private static final int TOTAL_QUESTIONS = 60;
private static final int QUESTIONS_PER_BLOCK = 15;
private static final int CHOICES_PER_QUESTION = 4; // A, B, C, D
```

---

## Error Handling

### Error Handling Strategy

**General Approach:**
- Return special values (null, -1) or result objects with validity flags
- Log all errors/warnings to `resultsArea` with detailed context
- Set internal status flags to prevent cascading failures
- Include file name, stage number, and relevant values in error messages

### Error Message Format

**Best Practice:** Always include context in error messages:

```java
// ❌ Bad: Generic error message
logger.error("Failed to find 4 corners");

// ✅ Good: Detailed error message with context
logger.error(String.format(
    "Stage %d (Deskewing) failed: Found %d L-shaped fiducials (expected 4), %d rectangular fiducials. " +
    "File: %s. Cannot proceed with deskewing.",
    STAGE_DESKEWING, lShapesFound, rectanglesFound, fileName
));
```

**Error Message Template:**
```java
String.format(
    "Stage %d (%s) failed: %s. " +
    "Found: %s. Expected: %s. " +
    "File: %s. " +
    "Action: %s",
    stageNumber,
    stageName,
    errorDescription,
    actualValue,
    expectedValue,
    fileName,
    actionTaken
);
```

### Stage-Specific Error Handling

**Input Validation:**
- **Null or empty image:** Throw `IllegalArgumentException` with file name
- **Image too small:** Throw `IllegalArgumentException` with dimensions
- **Unsupported format:** Throw `IllegalArgumentException` with channel count

**Stage 0 (Deskewing):**
- **< 4 L-shaped fiducials found:** 
  ```java
  logger.error(String.format(
      "Stage 0 (Deskewing) failed: Found %d L-shaped fiducials (expected 4), %d rectangular fiducials. " +
      "File: %s. Returning original image and aborting processing.",
      lShapesFound, rectanglesFound, fileName
  ));
  ```
  Return original image, log error, abort processing
- **Unable to find sharpest vertex in L-shape:** 
  ```java
  logger.warning(String.format(
      "Stage 0 (Deskewing): Could not find sharpest vertex in L-shape %d. " +
      "File: %s. Skipping this fiducial.",
      lShapeIndex, fileName
  ));
  ```
  Skip that fiducial, need at least 3 valid corners
- **< 3 valid corner vertices:** 
  ```java
  logger.error(String.format(
      "Stage 0 (Deskewing) failed: Only %d valid corner vertices found (need at least 3). " +
      "File: %s. Cannot compute transformation, aborting processing.",
      validCornersCount, fileName
  ));
  ```
  Cannot compute transformation, abort processing

**Stage 2 (Answer Region):**
- **< 4 rectangular fiducials found:** 
  ```java
  logger.error(String.format(
      "Stage 2 (Answer Region) failed: Found %d rectangular fiducials (expected 4). " +
      "File: %s. May indicate deskewing failure.",
      rectFiducialsFound, fileName
  ));
  ```
  Log error, use fallback method or fail
- **Rectangular fiducials not axis-aligned:** May indicate deskewing failure

**Stage 3 (Block Detection):**
- **≠ 4 blocks:** 
  ```java
  logger.error(String.format(
      "Stage 3 (Block Detection) failed: Found %d blocks (expected 4). " +
      "File: %s. Marking as failed, skipping processing.",
      blocksFound, fileName
  ));
  ```
  Log error, mark block as failed, skip processing

**Stage 4 (Question Row Rectangles):**
- **Significantly fewer/more rectangles than expected:** 
  ```java
  logger.warning(String.format(
      "Stage 4 (Question Rows): Found %d rectangles in block %d (expected ~15). " +
      "File: %s. Proceeding with detected rectangles.",
      rectanglesFound, blockIndex, fileName
  ));
  ```
  Log warning, proceed with detected rectangles
- **Unable to find rectangles in block:** 
  ```java
  logger.error(String.format(
      "Stage 4 (Question Rows) failed: No rectangles found in block %d. " +
      "File: %s. Marking entire block as failed.",
      blockIndex, fileName
  ));
  ```
  Mark entire block as failed

**Stage 5 (Bubble Detection):**
- **≠ 4 bubbles in rectangle:** 
  ```java
  logger.warning(String.format(
      "Stage 5 (Bubble Detection): Found %d bubbles in question %d (expected 4). " +
      "File: %s. Marking question as 'Unreadable'.",
      bubblesFound, questionNumber, fileName
  ));
  ```
  Mark question as "Unreadable"
- **No bubbles found in rectangle:** 
  ```java
  logger.warning(String.format(
      "Stage 5 (Bubble Detection): No bubbles found in question %d. " +
      "File: %s. Marking question as 'Unreadable'.",
      questionNumber, fileName
  ));
  ```
  Mark question as "Unreadable"

**Stage 6 (Bubble Validation):**
- **Bubbles not in proper X-order:** 
  ```java
  logger.warning(String.format(
      "Stage 6 (Bubble Validation): Bubbles not in proper X-order for question %d. " +
      "File: %s. Attempting to correct, or marking as 'Unreadable'.",
      questionNumber, fileName
  ));
  ```
  Log warning, attempt to correct or mark as "Unreadable"

**Stage 7 (Mark Recognition):**
- **Bubble outside image bounds:** 
  ```java
  logger.warning(String.format(
      "Stage 7 (Mark Recognition): Bubble at (%d, %d) for question %d is outside image bounds (%dx%d). " +
      "File: %s. Skipping bubble, marking question as 'Unreadable'.",
      bubbleX, bubbleY, questionNumber, imageWidth, imageHeight, fileName
  ));
  ```
  Skip bubble, mark question as "Unreadable"

**Stage 8 (Answer Extraction):**
- **Multiple marks:** 
  ```java
  logger.warning(String.format(
      "Stage 8 (Answer Extraction): Question %d has %d marks (expected 0 or 1). " +
      "File: %s. Marking as 'Invalid (Multiple Answers)'.",
      questionNumber, marksCount, fileName
  ));
  ```
  Mark question as "Invalid (Multiple Answers)"
- **No marks:** 
  ```java
  logger.info(String.format(
      "Stage 8 (Answer Extraction): Question %d has no marks. " +
      "File: %s. Marking as 'Skipped'.",
      questionNumber, fileName
  ));
  ```
  Mark question as "Skipped"

---

## Implementation Decisions

### Key Design Decisions

1. **Hierarchical Detection:** Large structures first, then progressively smaller (fiducials → blocks → rows → bubbles)

2. **ROI Extraction vs Masking:** Use `Mat.submat()` for efficiency, not painting masks

3. **Adaptive Tolerance:** Calculate Y_TOLERANCE dynamically based on detected spacing

4. **Outlier Filtering:** Remove outliers (2 std dev) before calculating final metrics

5. **Two-Tier Consistency Check:** Warning at 30%, failure at 50% std dev

6. **Defensive Programming:** Multiple validation checks at each stage

7. **Error Recovery:** Fail gracefully, continue processing other blocks when possible

8. **Fixed Format Assumptions:** Constants hardcoded for specific OMR sheet format

### Method Architecture

- **Return Values:** Use result objects (`ToleranceResult`) instead of magic numbers
- **Error Messages:** Log to `resultsArea` for user visibility
- **Status Flags:** Use boolean flags to track processing state

---

## Implementation Order

### Recommended Coding Sequence

1. **Stage 0: Deskewing** (Foundation - needed for everything)
2. **Stage 1: Preprocessing** (Simple, needed for all detection)
3. **Stage 2: Extract Answer Region** (Simple ROI extraction)
4. **Stage 3: Find Blocks** (Moderate complexity)
5. **Stage 4: Find Question Row Rectangles** (Moderate complexity - detect small rectangles)
6. **Stage 5: Find Bubbles** (Moderate complexity - HoughCircles per rectangle)
7. **Stage 6: Sort & Validate Bubbles** (Simple - organization and validation)
8. **Stage 7: Mark Recognition** (Moderate complexity)
9. **Stage 8: Answer Extraction** (Simple logic, complex data structures)

### Testing Strategy

1. Test each stage independently with known test images
2. Validate intermediate outputs (visualize detected fiducials, blocks, bubbles)
3. Test edge cases (missing fiducials, merged rows, missing bubbles)
4. Tune constants based on real scan results

---

## Notes

- All detection parameters are tuned for the specific OMR sheet format
- The code is modular to allow future adjustments
- Constants are centralized for easy tuning
- Error handling is comprehensive to prevent crashes
- The system is designed to be robust against poor scan quality

---

**Document Version:** 1.0  
**Last Updated:** [Current Date]  
**Status:** Ready for Implementation

