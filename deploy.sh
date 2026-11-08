#!/data/data/com.termux/files/usr/bin/bash
# One-command deploy from Termux.
# Copies this folder over your existing ~/TapCounter git repo, commits, and pushes to GitHub.
# GitHub then builds the APK (Actions tab) and publishes it under Releases.
set -e
SRC="$(cd "$(dirname "$0")" && pwd)"
DEST="$HOME/TapCounter"
GH_USER="${GH_USER:-kunalsingh-dev}"
REPO="${REPO:-TapCounter}"

if [ ! -d "$DEST/.git" ]; then
  echo "No git repository found at $DEST."
  echo "Create it first (see README), or run:  GH_USER=yourname bash deploy.sh"
  exit 1
fi

echo "1/3 Copying files into $DEST ..."
cp -r "$SRC"/. "$DEST"/
cd "$DEST"

echo "2/3 Committing ..."
git add -A
git commit -m "${MSG:-Final version}" || echo "(nothing new to commit)"

echo "3/3 Pushing to github.com/$GH_USER/$REPO"
echo "Create a token first: GitHub > Settings > Developer settings > Personal access tokens (classic), tick repo + workflow."
read -r -p "Paste the token here and press Enter (it is shown on screen): " T
git push "https://$GH_USER:$T@github.com/$GH_USER/$REPO.git" main
echo
echo "Done. Watch the build: https://github.com/$GH_USER/$REPO/actions"
echo "When it is green, download the APK from: https://github.com/$GH_USER/$REPO/releases"
echo "Then delete the token on GitHub."
