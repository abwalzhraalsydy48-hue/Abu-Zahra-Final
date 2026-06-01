#!/bin/bash
# Abu-Zahra Admin v3.8.2 - Push to GitHub Script
# Run this script after setting up your GitHub credentials

echo "=========================================="
echo "Abu-Zahra Admin v3.8.2 - GitHub Push"
echo "=========================================="

cd /home/z/my-project/repo-check

# Check current status
echo "Current git status:"
git status

# Check if there are unpushed commits
echo ""
echo "Unpushed commits:"
git log origin/main..HEAD --oneline

echo ""
echo "To push changes, run:"
echo "  git push origin main"
echo ""
echo "Or if you need to authenticate:"
echo "  git remote set-url origin https://YOUR_TOKEN@github.com/abwalzhraalsydy48-hue/Abu-Zahra-Final.git"
echo "  git push origin main"
