#!/usr/bin/env bash
set -euo pipefail

TAG="${1:?Usage: pre-publish.sh <tag> <changelog-file>}"
CHANGELOG_FILE="${2:-}"
VERSION="${TAG#v}"

echo "Setting version to: $VERSION"

# Update version in build.gradle.kts
sed -i'' -e "s/^version = \".*\"/version = \"$VERSION\"/" build.gradle.kts

# Update changeNotes in build.gradle.kts
if [ -n "$CHANGELOG_FILE" ] && [ -f "$CHANGELOG_FILE" ]; then
  echo "Updating changelog from: $CHANGELOG_FILE"

  # Build replacement block into a temp file
  TMPFILE=$(mktemp)
  trap 'rm -f "$TMPFILE"' EXIT

  {
    echo '    changeNotes ='
    echo '      """'
    while IFS= read -r line || [ -n "$line" ]; do
      echo "      ${line}"
    done < "$CHANGELOG_FILE"
    echo '      """.trimIndent()'
  } > "$TMPFILE"

  export TMPFILE
  awk '
    /changeNotes =/ { found=1 }
    found && /\.trimIndent\(\)/ {
      while ((getline rep < ENVIRON["TMPFILE"]) > 0) print rep
      found=0
      next
    }
    found { next }
    { print }
  ' build.gradle.kts > build.gradle.kts.tmp && mv build.gradle.kts.tmp build.gradle.kts
else
  echo "No changelog file provided or file not found, skipping changeNotes update."
fi

echo "build.gradle.kts updated:"
grep '^version' build.gradle.kts
