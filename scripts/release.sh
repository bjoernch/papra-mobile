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
  git tag --list "v*" --sort=-v:refname | head -n 1
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
tag="${tag#v}"
tag="v${tag}"
tag="${tag// /}"
if ! [[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Tag must look like v1.2.3 (got: $tag)" >&2
  exit 1
fi
if [[ -z "$tag" ]]; then
  echo "Tag cannot be empty." >&2
  exit 1
fi
if git rev-parse "$tag" >/dev/null 2>&1; then
  echo "Tag already exists: $tag" >&2
  exit 1
fi

version_title="${tag#v}"
read -r -p "Release notes (type 'draft since recent update' to auto-generate): " notes_input
notes_file="release-notes.md"

if [[ "$notes_input" == "draft since recent update" ]]; then
  {
    echo "# Release $version_title"
    echo
    draft_notes
  } > "$notes_file"
else
  {
    echo "# Release $version_title"
    echo
    echo "$notes_input"
  } > "$notes_file"
fi

echo "Building release APK..."
gradle_cmd=(./gradlew assembleRelease)

read -r -p "Sign APK? (y/N): " sign_choice
if [[ "$sign_choice" == "y" || "$sign_choice" == "Y" ]]; then
  default_keystore="keystore/release.keystore"
  read -r -p "Keystore path (default ${default_keystore}): " keystore_path
  if [[ -z "$keystore_path" ]]; then
    keystore_path="$default_keystore"
  fi
  read -r -p "Key alias (default papra): " key_alias
  if [[ -z "$key_alias" ]]; then
    key_alias="papra"
  fi
  read -r -s -p "Keystore password: " store_pass
  echo
  read -r -s -p "Key password (leave blank to reuse keystore password): " key_pass
  echo
  if [[ -z "$key_pass" ]]; then
    key_pass="$store_pass"
  fi

  if [[ "$keystore_path" != /* ]]; then
    keystore_path="$(pwd)/$keystore_path"
  fi
  mkdir -p "$(dirname "$keystore_path")"
  if [[ ! -f "$keystore_path" ]]; then
    echo "Creating keystore at $keystore_path..."
    keytool -genkeypair -v \
      -keystore "$keystore_path" \
      -alias "$key_alias" \
      -keyalg RSA \
      -keysize 2048 \
      -validity 10000 \
      -storepass "$store_pass" \
      -keypass "$key_pass" \
      -dname "CN=Papra, OU=Papra, O=Papra, L=, S=, C=US"
  fi

  gradle_cmd+=(
    -PRELEASE_STORE_FILE="$keystore_path"
    -PRELEASE_STORE_PASSWORD="$store_pass"
    -PRELEASE_KEY_ALIAS="$key_alias"
    -PRELEASE_KEY_PASSWORD="$key_pass"
  )
fi

"${gradle_cmd[@]}"

apk_path=$(find app/build/outputs/apk/release -maxdepth 1 -name "*.apk" | head -n 1)
if [[ -z "$apk_path" || ! -f "$apk_path" ]]; then
  echo "APK not found in app/build/outputs/apk/release" >&2
  exit 1
fi

git tag -a "$tag" -m "Release $tag"

echo "Pushing commits and tags..."
git push
git push --tags

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
