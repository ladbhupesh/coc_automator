# OCR Setup for Resource Extraction

This script uses OCR (Optical Character Recognition) to extract gold, elixir, and dark elixir values from screenshots.

## Prerequisites

### 1. Install Tesseract OCR

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install tesseract-ocr
```

**macOS:**
```bash
brew install tesseract
```

**Windows:**
- Download from: https://github.com/UB-Mannheim/tesseract/wiki
- Install and add to PATH

### 2. Install Python Dependencies

```bash
pip install -r requirements.txt
```

This installs:
- `Pillow` - Image processing
- `pytesseract` - Python wrapper for Tesseract OCR

### 3. Verify Installation

```bash
tesseract --version
python -c "import pytesseract; print('OK')"
```

## How It Works

1. **Screenshot**: Takes full screenshot of the device
2. **Crop**: Crops to resource region (x: 100-400, y: 140-400)
3. **OCR**: Extracts text using Tesseract
4. **Parse**: Extracts three numbers (gold, elixir, dark elixir)
5. **Print**: Displays values before placing troops

## Resource Region

The script looks for resources in this region:
- **X**: 100 to 400 pixels
- **Y**: 140 to 400 pixels

This should capture the resource display at the top of the screen during an attack.

## Troubleshooting

### OCR Not Working

1. **Check Tesseract Installation**:
   ```bash
   which tesseract
   tesseract --version
   ```

2. **Check Image Quality**:
   - The cropped image is saved as `resource_region.png` (uncomment in code)
   - Verify the region contains the resource numbers

3. **Adjust OCR Config**:
   - Current config: `--psm 6 -c tessedit_char_whitelist=0123456789,`
   - Try different PSM modes if needed (0-13)

### Wrong Values Extracted

1. **Verify Region Coordinates**:
   - Check if x: 100-400, y: 140-400 captures the resources
   - Adjust coordinates in `extract_resources_from_screenshot()`

2. **Improve Image Quality**:
   - Increase contrast
   - Resize image larger
   - Use image preprocessing

3. **Check OCR Output**:
   - The script prints raw OCR text
   - Verify what Tesseract is reading

### Tesseract Not Found

If you get "TesseractNotFoundError":

**Option 1: Add to PATH**
```bash
export TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata
```

**Option 2: Specify Path in Code**
```python
pytesseract.pytesseract.tesseract_cmd = r'/usr/bin/tesseract'
```

## Customization

### Change Resource Region

Edit coordinates in `extract_resources_from_screenshot()`:
```python
cropped = img.crop((100, 140, 400, 400))  # (left, top, right, bottom)
```

### Improve OCR Accuracy

1. **Preprocess Image**:
```python
from PIL import ImageEnhance
enhancer = ImageEnhance.Contrast(cropped)
cropped = enhancer.enhance(2.0)  # Increase contrast
```

2. **Resize Image**:
```python
cropped = cropped.resize((cropped.width * 2, cropped.height * 2), Image.LANCZOS)
```

3. **Use Different OCR Engine**:
   - Consider `easyocr` as alternative
   - May be more accurate for game text

## Alternative: EasyOCR

If Tesseract doesn't work well, you can use EasyOCR:

```bash
pip install easyocr
```

Then modify `extract_resources_from_screenshot()` to use EasyOCR instead.

## Notes

- OCR accuracy depends on:
  - Image quality
  - Font clarity
  - Background contrast
  - Screen resolution

- The script extracts the first 3 numbers found:
  - First = Gold
  - Second = Elixir  
  - Last = Dark Elixir

- If extraction fails, the script continues but prints a warning.
