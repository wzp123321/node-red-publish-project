# Release Instructions

This document explains how to create and publish a release for the Node-RED Dashboard Converter.

## Prerequisites

- Git repository is up to date
- All changes are committed
- You have push access to the repository

## Step 1: Create and Push the Tag

The tag has already been created locally. To push it to GitHub:

```bash
git push origin v1.0.0
```

Or to push all tags:

```bash
git push origin --tags
```

## Step 2: Create Release Package (Optional)

To create a downloadable zip file:

```bash
./create-release.sh v1.0.0
```

This will create `nodered-dashboard-converter-v1.0.0.zip` that users can download and extract.

## Step 3: Create GitHub Release

1. Go to your GitHub repository
2. Click on "Releases" in the right sidebar
3. Click "Draft a new release"
4. Select the tag `v1.0.0`
5. Set the release title to: `v1.0.0`
6. Copy the content from `.github/release-template.md` into the description
7. If you created a zip file, upload it as a release asset
8. Click "Publish release"

## Step 4: Verify the Release

After publishing, verify:
- The release appears on the Releases page
- The tag is visible
- Download links work (if you uploaded assets)
- The release notes are displayed correctly

## Quick Commands Summary

```bash
# Create tag (already done)
git tag -a v1.0.0 -m "Release v1.0.0"

# Push tag
git push origin v1.0.0

# Create release package
./create-release.sh v1.0.0

# Then create the release on GitHub web interface
```

## Alternative: Using GitHub CLI

If you have GitHub CLI installed:

```bash
gh release create v1.0.0 \
  --title "v1.0.0" \
  --notes-file .github/release-template.md \
  nodered-dashboard-converter-v1.0.0.zip
```

