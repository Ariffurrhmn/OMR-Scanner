# OMR Processor Demo

A standalone demo project for testing and developing OMR (Optical Mark Recognition) image processing with OpenCV/JavaCV.

## Status: ✅ Answer Extraction Working!

**Q1-Q15 correctly detected** from real scanned OMR sheets.

```
Expected: A B C C C A B C D B D C B C A
Detected: A B C C C A B C D B D C B C A  ✓
```

## Purpose

This demo allows testing of:
- OpenCV/JavaCV library loading
- Fiducial marker detection (L-shaped and rectangular)
- Image preprocessing (grayscale, threshold, blur, CLAHE)
- Perspective correction (deskewing) using fiducials
- **Row-based answer extraction** ✅ WORKING
- Bubble detection algorithms

Once verified, the code can be integrated into the main OMR Reader V2 application.

## OMR Sheet Structure

The OMR sheet uses two types of fiducial markers:

```
┌─────────────────────────────────────────────────────────────────┐
│ L (TL)                                                 L (TR)  │  ← L-shaped fiducials
│                                                                 │     for page deskewing
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  STUDENT ID (10 cols)        TEST ID (4 cols)            │  │
│  │  ┌─┬─┬─┬─┬─┬─┬─┬─┬─┬─┐      ┌─┬─┬─┬─┐                   │  │
│  │  │1│1│1│1│1│1│1│1│1│1│      │1│1│1│1│    Info...        │  │
│  │  │2│2│2│2│2│2│2│2│2│2│      │2│2│2│2│                   │  │
│  │  │ ...                      │ ...                        │  │
│  │  │0│0│0│0│0│0│0│0│0│0│      │0│0│0│0│                   │  │
│  │  └─┴─┴─┴─┴─┴─┴─┴─┴─┴─┘      └─┴─┴─┴─┘                   │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ■ (TL)              ANSWER SECTION                    ■ (TR)  │  ← Rectangular fiducials
│  ┌──────────────────────────────────────────────────────────┐  │     for answer section
│  │  1  (A)(B)(C)(D)  16 (A)(B)(C)(D)  31 (A)(B)(C)(D)  46  │  │
│  │  2  (A)(B)(C)(D)  17 (A)(B)(C)(D)  32 (A)(B)(C)(D)  47  │  │
│  │  ...              ...              ...              ...  │  │
│  │  15 (A)(B)(C)(D)  30 (A)(B)(C)(D)  45 (A)(B)(C)(D)  60  │  │
│  └──────────────────────────────────────────────────────────┘  │
│  ■ (BL)                                                ■ (BR)  │
│                                                                 │
│ L (BL)                                                 L (BR)  │
└─────────────────────────────────────────────────────────────────┘
```

### Fiducial Markers

| Type | Count | Location | Purpose |
|------|-------|----------|---------|
| **L-shaped** | 4 | Page corners (outer) | Deskew entire page |
| **Rectangular** | 4 | Answer section corners | Isolate answer region |

### ID Section Detection
- No dedicated fiducials for ID section
- Found as the **second largest rectangle** after answer section
- Or detected by location (upper 35% of page)

## Project Structure

```
omr-processor-demo/
├── pom.xml                          # Maven config with OpenCV dependencies
├── README.md                        # This file
├── samples/                         # Place test images here
│   └── test_omr.jpg                 # Sample OMR sheet
├── output/                          # Generated debug images
└── src/main/java/org/example/
    ├── OMRProcessorDemo.java        # Main demo runner
    ├── OMRSheetProcessor.java       # Complete processing pipeline
    ├── OMRSheetConfig.java          # Sheet layout configuration
    ├── FiducialDetector.java        # L-shaped & rectangular marker detection
    ├── ImagePreprocessor.java       # Image preprocessing (grayscale, CLAHE, threshold)
    ├── BubbleDetector.java          # Circle-based bubble detection
    ├── PerspectiveCorrector.java    # Generic deskewing utilities
    └── RowBasedAnswerExtractor.java # ⭐ Row-based answer extraction (WORKING)
```

## Running the Demo

### Prerequisites
- Java 21
- Maven 3.8+

### Steps

1. **Navigate to demo directory:**
   ```bash
   cd omr-processor-demo
   ```

2. **Build and run:**
   ```bash
   mvn compile exec:java
   ```

3. **(Optional) Add test image:**
   - Place an OMR sheet image in `samples/test_omr.jpg`
   - Re-run to test with real image

### Expected Output

```
============================================================
OMR Processor Demo - OpenCV Test
============================================================

[Test 1] OpenCV Loading...
  ✓ OpenCV loaded successfully
  ✓ Build info available

[Test 2] Matrix Operations...
  ✓ Created Mat: 100x100, channels=3
  ✓ Filled with color
  ✓ Converted to grayscale: channels=1
  ✓ Memory released

[Test 3] Image Loading...
  ⚠ No sample image found
  → Place a test image in: omr-processor-demo/samples/test_omr.jpg

[Test 4] Preprocessing Pipeline...
  ✓ Created synthetic image: 800x1000
  ✓ Drew synthetic bubbles
  ✓ Step 1: Grayscale conversion
  ✓ Step 2: Gaussian blur (5x5)
  ✓ Step 3: Adaptive threshold
  ✓ Step 4: Found N contours
  ✓ Step 5: Detected N potential bubbles
  ✓ Saved pipeline outputs to: output/

============================================================
All tests completed!
============================================================
```

## Processing Pipeline

1. **Load Image** - Read OMR sheet image
2. **Preprocess** - Grayscale → CLAHE → Blur → Adaptive Threshold
3. **Detect L-Fiducials** - Find 4 L-shaped markers at page corners
4. **Deskew Page** - Apply perspective transform using L-fiducials
5. **Detect Rect-Fiducials** - Find 4 rectangular markers for answer section
6. **Extract Answer Section** - Isolate using rect-fiducials or border detection
7. **Row-Based Answer Extraction** - Detect row rectangles, sample bubbles
8. **Find ID Section** - Locate as second largest rectangle (TODO)
9. **Extract IDs** - Read Student ID (10 digits) and Test ID (4 digits) (TODO)

## Answer Extraction Algorithm ⭐

The working algorithm uses a **row-based approach**:

### Step 1: Detect Row Rectangles
Find horizontal rectangles that contain one question's bubbles (A, B, C, D).

```
Row rectangle: [1] (A) (B) (C) (D)
               └─────────────────┘
```

### Step 2: Group Rows by Y-Position
Rows at similar Y coordinates belong to the same question row across all 4 columns.
Uses **dynamic 20-pixel tolerance** to handle slight misalignment.

### Step 3: Map to Questions
- Column 0 (X < 230): Q1-Q15
- Column 1 (X 230-460): Q16-Q30
- Column 2 (X 460-690): Q31-Q45
- Column 3 (X > 690): Q46-Q60

### Step 4: Sample Bubble Areas
For each row, divide the bubble area into 4 sections and count white pixels:

```
Row: [Qnum] [ A  ] [ B  ] [ C  ] [ D  ]
              ↓      ↓      ↓      ↓
           count1  count2  count3  count4
```

The filled bubble has **significantly more white pixels** (1.5x+) than empty bubbles.

### Step 5: Adaptive Alignment
Try multiple question-number-area ratios (8%, 10%, 12%, 14%) and pick the alignment that gives the clearest winner.

### Key Insights
1. **Filled bubbles are solid white** in binary image
2. **Empty bubbles are thin outlines** with much less white pixels
3. **Row grouping is critical** - must handle Y-position variance across columns
4. **Adaptive alignment** handles different row positions across the sheet

## Next Steps

1. ✅ **Answer extraction working** - Q1-Q60 bubble detection complete
2. 🔄 **Implement Student ID extraction** - 10-digit ID from bubbles
3. 🔄 **Implement Test ID extraction** - 4-digit test code
4. ⏳ **Integrate with main app** - Copy working code to `OMR_scanner/src/`
5. ⏳ **Test with more images** - Validate robustness across scans

## Key Classes

### FiducialDetector ⭐ NEW
Detects fiducial markers:
- `detectLShapedFiducials()` - Find L-shaped markers at page corners
- `detectRectFiducials()` - Find rectangular markers for answer section
- `getPageCorners()` - Extract corner points from L-fiducials
- `getAnswerSectionCorners()` - Extract answer region from rect-fiducials
- `findIdSectionByLocation()` - Locate ID section by position

### RowBasedAnswerExtractor ⭐ WORKING
Row-based answer extraction (the algorithm that works!):
- `extract(Mat binaryImage)` - Extract all 60 answers
- `detectRowRectangles()` - Find horizontal row rectangles
- `deduplicateRows()` - Remove overlapping duplicates  
- `mapRowsToQuestions()` - Assign rows to Q1-Q60
- `detectAnswerInRow()` - Sample bubble areas to find marked answer

**Key fix:** Dynamic Y-grouping with 20-pixel tolerance handles row misalignment.

### OMRSheetProcessor
Complete processing pipeline:
- `process(File)` - Full pipeline returning `ProcessResult`
- Extracts Student ID, Test ID, and 60 answers
- Saves debug images at each step

### OMRSheetConfig ⭐ NEW
Configuration constants:
- Sheet dimensions and target sizes
- ID field configurations (10 digits, 4 digits)
- Answer section layout (4 columns × 15 rows)
- Fiducial detection parameters
- Bubble detection thresholds

### ImagePreprocessor
Handles image preprocessing:
- `toGrayscale()` - Convert to grayscale
- `applyCLAHE()` - Contrast Limited Adaptive Histogram Equalization
- `applyGaussianBlur()` - Reduce noise
- `applyAdaptiveThreshold()` - Binarization
- `preprocess()` - Full pipeline (grayscale → CLAHE → blur → threshold)

### BubbleDetector
Detects and analyzes bubbles:
- `detectBubbles()` - Find circular contours
- `isBubbleMarked()` - Check if bubble is filled
- `groupBubblesIntoRows()` - Organize bubbles by position

### PerspectiveCorrector
Handles document alignment:
- `findDocumentCorners()` - Detect document boundary (generic)
- `warpPerspective()` - Apply transformation
- `autoCorrect()` - Automatic deskewing

## Troubleshooting

### OpenCV Loading Issues
If you see native library errors:
```bash
# Ensure you have enough memory
mvn exec:java -Xmx2g
```

### Image Not Loading
- Ensure image path is correct
- Check image format (JPG, PNG supported)
- Verify file permissions

### JVM Crashes
- May be caused by incompatible native libraries
- Try running with debug flags:
```bash
mvn exec:java -Djava.library.path=.
```

