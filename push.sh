#!/bin/bash
# ----------------------------------------------------------
# Automatically push back the generated JavaDocs to gh-pages
# ----------------------------------------------------------
# based on https://gist.github.com/willprice/e07efd73fb7f13f917ea

# only for main pushes, for now
# if [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" != "" ]; then
if [ "$TRAVIS_PULL_REQUEST" == "true" ]; then
	echo -e "Pull request detected, skipping JavaDocs pushback."
	exit 0
fi

# check if the token is actually there
if [ "$GITHUB_TOKEN" == "" ]; then
	echo -e "No access to GitHub, skipping JavaDocs pushback."
	exit 0
fi

# create directory for gh-pages branch
mkdir javadoc

cd javadoc

# prepare the git information
git config --global user.email "travis@travis-ci.org"
git config --global user.name "Travis CI"

# clone repo again
git clone https://github.com/akarnokd/RxJava2Jdk8Interop.git

# get the gh-pages
git checkout gh-pages

# copy and overwrite new doc
yes | cp -rf ../build/docs/javadoc javadoc

# stage all changed and new files
git add --all

# commit all
git commit --message "Travis build: $TRAVIS_BUILD_NUMBER"

# setup the remote
git remote add origin-pages https://${GITHUB_TOKEN}@github.com/akarnokd/RxJava2Jdk8Interop.git > /dev/null 2>&1

# push it
git push --quiet --set-upstream origin-pages gh-pages

# we are done
cd ..
