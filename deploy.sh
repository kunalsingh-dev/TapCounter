#!/data/data/com.termux/files/usr/bin/bash
# Termux deploy: copy -> commit -> push to a branch with a GitHub token -> Actions builds the APK.
# Optional env vars: GH_USER, REPO (default TapCounter), BRANCH (default main), GH_TOKEN, MSG, DEST
# Examples:
#   bash deploy.sh                      # push to main
#   BRANCH=test-build bash deploy.sh    # push to a branch called test-build (created if missing)
set -euo pipefail

SRC="$(cd "$(dirname "$0")" && pwd)"
DEST="${DEST:-$HOME/TapCounter}"
REPO="${REPO:-TapCounter}"
BRANCH="${BRANCH:-main}"

if [ ! -d "$DEST/.git" ]; then
  echo "No git repository at $DEST."
  echo "Create it first:  mkdir -p $DEST && cd $DEST && git init -b main"
  exit 1
fi

# GitHub username: env var, else from the repo's 'origin' remote, else ask.
GH_USER="${GH_USER:-}"
if [ -z "$GH_USER" ]; then
  url="$(git -C "$DEST" remote get-url origin 2>/dev/null || true)"
  GH_USER="$(printf '%s' "$url" | sed -nE 's#.*github\.com[:/]([^/@]+)/.*#\1#p')"
fi
if [ -z "$GH_USER" ]; then
  read -r -p "Your GitHub username: " GH_USER
fi

# Termux has no git identity by default; commit would fail without one.
if [ -z "$(git -C "$DEST" config user.email || true)" ]; then
  git -C "$DEST" config user.email "${GH_USER}@users.noreply.github.com"
  git -C "$DEST" config user.name "$GH_USER"
fi

echo "1/3 Copying files into $DEST ..."
if [ "$SRC" != "$DEST" ]; then
  cp -r "$SRC"/. "$DEST"/
fi
cd "$DEST"

echo "2/3 Committing ..."
git add -A
if git diff --cached --quiet; then
  echo "(nothing new to commit, pushing what is already committed)"
else
  git commit -q -m "${MSG:-Final version}"
fi

echo "3/3 Pushing to github.com/$GH_USER/$REPO  (branch: $BRANCH)"
echo "Token: GitHub > Settings > Developer settings > Personal access tokens (classic): tick repo + workflow."
T="${GH_TOKEN:-}"
if [ -z "$T" ]; then
  read -r -s -p "Paste token (hidden) and press Enter: " T
  echo
fi
if [ -z "$T" ]; then
  echo "No token entered."
  exit 1
fi

# HEAD:refs/heads/BRANCH pushes whatever you have checked out to BRANCH on GitHub.
if ! git push "https://$GH_USER:$T@github.com/$GH_USER/$REPO.git" "HEAD:refs/heads/$BRANCH"; then
  unset T
  echo
  echo "Push failed. Common causes:"
  echo " - 403 / Authentication failed: token expired, or missing 'repo' + 'workflow' scopes"
  echo " - 404 / not found: wrong GH_USER or REPO name, or the repo does not exist yet"
  echo " - rejected (non-fast-forward): branch '$BRANCH' on GitHub has commits you do not have locally"
  exit 1
fi
unset T

echo
echo "Done. Watch the build:  https://github.com/$GH_USER/$REPO/actions"
echo "When green, APK is at:   https://github.com/$GH_USER/$REPO/releases"
echo "Now delete the token on GitHub."
