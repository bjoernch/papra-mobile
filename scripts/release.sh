#!/usr/bin/env bash
set -euo pipefail

require_clean_worktree() {
  if [[ -n "$(git status --porcelain)" ]]; then
    echo "Working tree is not clean. Commit or stash changes before releasing." >&2
    exit 1
  fi
}

require_no_unpushed_commits() {
  local upstream
  upstream=$(git rev-parse --abbrev-ref --symbolic-full-name @{u} 2>/dev/null || true)
  if [[ -z "$upstream" ]]; then
    echo "No upstream tracking branch set. Set one or push the branch before releasing." >&2
    exit 1
  fi
  local ahead
  ahead=$(git rev-list --count "$upstream"..HEAD)
  if [[ "$ahead" != "0" ]]; then
    echo "There are unpushed commits. Push them before releasing." >&2
    exit 1
  fi
}

latest_tag() {
  git describe --tags --abbrev=0 2>/dev/null || true
}

next_version() {
  local last tag stripped major minor patch
  last=$(latest_tag)
  if [[ -z "$last" ]]; then
    echo "1.0.0"
    return
  fi
  tag="$last"
  stripped="${tag#v}"
  IFS='.' read -r major minor patch <<< "$stripped"
  if [[ -z "${major:-}" || -z "${minor:-}" || -z "${patch:-}" ]]; then
    echo "1.0.0"
    return
  fi
  patch=$((patch + 1))
  echo "${major}.${minor}.${patch}"
}

draft_notes() {
  local last_tag
  last_tag=$(latest_tag)
  if [[ -n "$last_tag" ]]; then
    git log "$last_tag"..HEAD --pretty=format:"- %s" || true
  else
    git log --pretty=format:"- %s" || true
  fi
}

require_clean_worktree
require_no_unpushed_commits

default_version=$(next_version)
read -r -p "Release tag (default v${default_version}): " tag
if [[ -z "$tag" ]]; then
  tag="v${default_version}"
fi
if [[ -z "$tag" ]]; then
  echo "Tag cannot be empty." >&2
  exit 1
fi
if git rev-parse "$tag" >/dev/null 2>&1; then
  echo "Tag already exists: $tag" >&2
  exit 1
fi

read -r -p "Release notes (type 'draft since recent update' to auto-generate): " notes_input
notes_file="release-notes.md"

if [[ "$notes_input" == "draft since recent update" ]]; then
  {
    echo "# Release $tag"
    echo
    draft_notes
  } > "$notes_file"
else
  {
    echo "# Release $tag"
    echo
    echo "$notes_input"
  } > "$notes_file"
fi

echo "Building release APK..."
./gradlew assembleRelease

apk_path="app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$apk_path" ]]; then
  echo "APK not found at $apk_path" >&2
  exit 1
fi

git tag -a "$tag" -m "Release $tag"

echo "Drafting release..."
if command -v gh >/dev/null 2>&1; then
  gh release create "$tag" "$apk_path" --draft --notes-file "$notes_file"
  echo "Draft release created with APK attached."
else
  echo "GitHub CLI (gh) not found. Upload manually:" >&2
  echo "- Tag: $tag" >&2
  echo "- Notes: $notes_file" >&2
  echo "- APK: $apk_path" >&2
fi
