# GitHub Actions CI/CD Workflows

This directory contains automated workflows for building, testing, and releasing the SonnetPlayer Android app.

## Workflows

### 1. Build CI (`build.yml`)
Runs on every push and pull request to `main` and `develop` branches.

**Features:**
- JDK 17 setup with Temurin distribution
- Gradle caching with read-only cache for non-main branches
- Code style checking (ktlint)
- Unit tests execution
- Debug APK building
- Test results publishing
- Artifact retention: 7 days

**Optimizations:**
- Concurrent builds with auto-cancellation of outdated runs
- Gradle build cache enabled
- Gradle home cache cleanup
- 30-minute timeout to prevent hung builds

### 2. Release CD (`release.yml`)
Runs on push to `main` branch or manually via workflow dispatch.

**Features:**
- Automatic version extraction from `app/build.gradle.kts`
- Tag existence checking to prevent duplicate releases
- Release APK building
- APK signing (using debug keystore for development)
- Automated changelog generation from commits
- GitHub Release creation with APK attached
- Version tagging (`v1.0`, `v1.1`, etc.)
- Release notes auto-generation

**Workflow:**
1. Extracts `versionName` and `versionCode` from build.gradle.kts
2. Checks if version tag already exists
3. Builds release APK if tag doesn't exist
4. Generates changelog from commits since last tag
5. Creates GitHub release with APK attachment
6. Skips release if version already exists

**APK Naming:** `SonnetPlayer-v{version}.apk` (e.g., `SonnetPlayer-v1.0.apk`)

### 3. Dependabot (`dependabot.yml`)
Automated dependency updates with intelligent grouping.

**Features:**
- **Gradle dependencies:** Weekly updates on Mondays at 9:00 UTC
- **GitHub Actions:** Weekly updates on Mondays at 9:00 UTC
- Up to 10 Gradle PRs, 5 Actions PRs
- Grouped updates for related packages:
  - `androidx`: All AndroidX libraries
  - `kotlin`: Kotlin and coroutines
  - `media3`: ExoPlayer/Media3 libraries
  - `testing`: Test frameworks
- Auto-labeled PRs with `dependencies` tag
- Semantic commit prefixes (`deps:`, `ci:`)

## Setup Instructions

### Prerequisites
1. Repository must be hosted on GitHub
2. Actions must be enabled in repository settings

### For Production Releases (Optional)
To use proper APK signing instead of debug signing:

1. Generate a release keystore:
```bash
keytool -genkey -v -keystore release.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000
```

2. Add secrets to GitHub repository settings:
   - `KEYSTORE_FILE`: Base64-encoded keystore file
   - `KEYSTORE_PASSWORD`: Keystore password
   - `KEY_ALIAS`: Key alias
   - `KEY_PASSWORD`: Key password

3. Update `release.yml` to use proper signing (commented section)

### Dependabot Configuration
1. Update `reviewers` and `assignees` in `dependabot.yml` with actual GitHub usernames
2. Customize update groups based on your preferences
3. Adjust `open-pull-requests-limit` if needed

## Usage

### Triggering Builds
- **Automatic:** Push to `main` or `develop` or create a PR
- **Manual:** Not applicable for build workflow

### Creating Releases
- **Automatic:** Push to `main` branch
  1. Update `versionName` and `versionCode` in `app/build.gradle.kts`
  2. Commit and push to `main`
  3. Workflow automatically creates release with tag
- **Manual:** Click "Run workflow" in Actions tab → Release APK

### Version Bumping
Edit `app/build.gradle.kts`:
```kotlin
defaultConfig {
    versionCode = 2  // Increment for each release
    versionName = "1.1"  // Update version number
}
```

## Caching Strategy

### Gradle Caching
- **Main branch:** Read-write cache (updates cache)
- **Other branches:** Read-only cache (uses but doesn't update)
- **Gradle home:** Automatic cleanup after builds
- **Dependencies:** Cached by Gradle actions

### Build Optimization
- Parallel builds enabled (via `gradle.properties`)
- Configuration on demand
- R8 full mode for releases
- Resource optimization enabled

## Artifacts

### Build Workflow
- `debug-apk`: Debug APK (7-day retention)
- `test-results`: Test reports and results (7-day retention)

### Release Workflow
- `release-apk-v{version}`: Release APK (90-day retention)
- GitHub Release with attached APK (permanent)

## Troubleshooting

### Build Fails
- Check Gradle cache: May need to clear cache in Actions settings
- Verify JDK version matches project requirements
- Check `gradlew` permissions

### Release Not Created
- Verify version was updated in `build.gradle.kts`
- Check if tag already exists: `git tag -l`
- Review workflow logs for extraction errors

### Dependabot PRs Not Created
- Verify Dependabot is enabled in repository settings
- Check `dependabot.yml` syntax
- Update reviewer/assignee usernames

## Best Practices

1. **Always bump version** before merging to main
2. **Use semantic versioning** (MAJOR.MINOR.PATCH)
3. **Review Dependabot PRs** before merging
4. **Keep changelog meaningful** with descriptive commit messages
5. **Test locally** before pushing to main
6. **Monitor workflow runs** in Actions tab

## Workflow Status Badges

Add to your README.md:
```markdown
![Build Status](https://github.com/YOUR_USERNAME/YOUR_REPO/workflows/Android%20CI/badge.svg)
![Release](https://github.com/YOUR_USERNAME/YOUR_REPO/workflows/Release%20APK/badge.svg)
```
