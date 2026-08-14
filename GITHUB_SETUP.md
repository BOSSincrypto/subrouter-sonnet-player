# GitHub Setup Instructions

## Step 1: Create GitHub Repository

1. Go to https://github.com/new
2. Repository name: `subrouter-sonnet-player` (or your preferred name)
3. Description: `High-performance Android video player with ExoPlayer`
4. Public repository
5. **Do NOT** initialize with README, .gitignore, or license (already included)
6. Click "Create repository"

## Step 2: Push Code to GitHub

```bash
# Add remote origin (replace YOUR_USERNAME with your GitHub username)
git remote add origin https://github.com/YOUR_USERNAME/subrouter-sonnet-player.git

# Push to GitHub
git push -u origin main
```

If you're on 'master' branch, rename it to 'main':
```bash
git branch -M main
git push -u origin main
```

## Step 3: Configure GitHub Actions

The workflows are already set up in `.github/workflows/`. They will:
- Run on every push/PR (build.yml)
- Create releases automatically on merge to main (release.yml)

### First Release

To create your first release:

1. Ensure version in `app/build.gradle.kts` is set:
   ```kotlin
   versionCode = 1
   versionName = "1.0"
   ```

2. Push to main branch:
   ```bash
   git push origin main
   ```

3. GitHub Actions will automatically:
   - Build the release APK
   - Create a GitHub release with tag `v1.0`
   - Upload the APK as an asset
   - Generate changelog from commits

## Step 4: Update Dependabot Configuration

Edit `.github/dependabot.yml` and replace `octocat` with your GitHub username:

```yaml
reviewers:
  - "YOUR_USERNAME"  # Replace with your username
```

## Step 5: Add Repository Badges (Optional)

Add these to the top of README.md:

```markdown
[![Build](https://github.com/YOUR_USERNAME/subrouter-sonnet-player/workflows/Android%20CI/badge.svg)](https://github.com/YOUR_USERNAME/subrouter-sonnet-player/actions)
[![Release](https://img.shields.io/github/v/release/YOUR_USERNAME/subrouter-sonnet-player)](https://github.com/YOUR_USERNAME/subrouter-sonnet-player/releases)
[![License](https://img.shields.io/github/license/YOUR_USERNAME/subrouter-sonnet-player)](LICENSE)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)
```

## Step 6: Enable GitHub Pages (Optional)

For project documentation:
1. Go to repository Settings > Pages
2. Source: Deploy from a branch
3. Branch: main, folder: / (root)
4. Save

## Troubleshooting

### Actions Not Running
- Check Actions tab in repository
- Ensure Actions are enabled in Settings > Actions

### Release Not Created
- Check workflow logs in Actions tab
- Verify version in build.gradle.kts changed
- Ensure you're pushing to 'main' branch

### APK Not Signed
For production signing, add secrets:
1. Settings > Secrets and variables > Actions
2. Add: `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
3. Update release.yml to use proper signing

## Next Steps

1. Clone on another machine and test build
2. Create issues for planned features
3. Set up GitHub Discussions
4. Add screenshots to README
5. Publish to Google Play Store (optional)

## Support

For questions or issues:
- Open an issue: https://github.com/YOUR_USERNAME/subrouter-sonnet-player/issues
- Check documentation in README.md

Happy coding! 🚀
