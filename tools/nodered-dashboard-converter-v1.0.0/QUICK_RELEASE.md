# Quick Release Guide

## ✅ What's Already Done

1. ✅ Git tag `v1.0.0` has been created locally
2. ✅ Release notes template created (`.github/release-template.md`)
3. ✅ Release package script created (`create-release.sh`)

## 🚀 Next Steps to Publish

### 1. Push the Tag to GitHub

```bash
git push origin v1.0.0
```

### 2. Create Release Package (Optional but Recommended)

```bash
./create-release.sh v1.0.0
```

This creates `nodered-dashboard-converter-v1.0.0.zip` ready for download.

### 3. Create GitHub Release

**Option A: Via Web Interface**
1. Go to: https://github.com/amitbet/nodered-ui2-paste-conv/releases/new
2. Select tag: `v1.0.0`
3. Title: `v1.0.0`
4. Description: Copy from `.github/release-template.md`
5. Upload: `nodered-dashboard-converter-v1.0.0.zip` (if created)
6. Click "Publish release"

**Option B: Via GitHub CLI**
```bash
gh release create v1.0.0 \
  --title "v1.0.0" \
  --notes-file .github/release-template.md \
  nodered-dashboard-converter-v1.0.0.zip
```

## 📦 What Users Get

When users download the release:
- Complete source code
- Run scripts for Windows and Unix
- README with full documentation
- INSTALL.txt with quick start guide
- All dependencies listed in package.json

Users can:
1. Download the zip file
2. Extract it
3. Run `./run.sh` (macOS/Linux) or `run.bat` (Windows)
4. Start converting immediately!

## ✨ Release Highlights

- Web-based conversion interface
- Automatic conversion on paste
- Support for all major Dashboard v1 nodes
- Cross-platform run scripts
- Beautiful, responsive UI

