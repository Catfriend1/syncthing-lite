# Firewall Allowlist for syncthing-lite

This document specifies the required network endpoints for building and testing syncthing-lite.

## Required Endpoints

### Gradle Build Dependencies
- `dl.google.com` - Google's Maven repository for Android SDK components
- `repo1.maven.org` - Maven Central repository
- `jitpack.io` - JitPack repository for GitHub-based dependencies

### Android SDK Components
- `dl.google.com/android/repository/` - Android SDK and build tools
- `services.gradle.org` - Gradle services

## GitHub Actions Configuration

Add these endpoints to your GitHub Actions firewall allowlist:

```yaml
firewall:
  allow_hosts:
    - "dl.google.com"
    - "repo1.maven.org"
    - "jitpack.io"
    - "services.gradle.org"
    - "github.com"
    - "api.github.com"
    - "ghcr.io"
```

## Alternative: Offline Mode Configuration

If firewall restrictions cannot be modified, consider using Gradle's offline mode with pre-cached dependencies:

1. Create a `gradle-cache` directory in your CI environment
2. Pre-populate it with required dependencies
3. Use `--offline` flag during builds

## Docker Build Environment

The Docker container `ghcr.io/catfriend1/syncthing-lite-builder` should include pre-cached dependencies to minimize network requests during builds.