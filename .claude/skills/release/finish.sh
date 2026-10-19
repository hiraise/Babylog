#!/bin/bash
# Последний шаг скилла /release (ADR-0012): дождаться проверок релизного PR, слить его обычным
# merge (не squash — тогда вошедшие в релиз PR GitHub отметит слитыми), поставить тег vX.Y
# на коммит слияния и дождаться, пока release.yml опубликует APK.
#
# Запуск: bash .claude/skills/release/finish.sh X.Y
#
# Запускает владелец: авто-режим агента не даёт сливать PR и ставить теги. Его /bin/bash 3.2
# в локали C читает первый байт кириллицы после $var как часть имени, и set -u падает —
# поэтому перед кириллицей и «» пиши ${var}.
set -euo pipefail

VERSION="${1:?Укажите версию, например: finish.sh 1.7}"
TAG="v${VERSION}"
BRANCH="release/${TAG}"
cd "$(dirname "$0")/../../.."

fail() {
    echo "$1" >&2
    exit 1
}

git fetch -q origin
PR="$(gh pr view "${BRANCH}" --json number --jq .number)" || fail "Нет PR из ветки ${BRANCH}"
[[ "$(gh pr view "${PR}" --json state --jq .state)" == OPEN ]] || fail "PR #${PR} не открыт"
[[ "$(gh pr view "${PR}" --json isDraft --jq .isDraft)" == false ]] ||
    fail "PR #${PR} — черновик: сначала gh pr ready ${PR}"
NAME="$(git show "origin/${BRANCH}:app/build.gradle.kts" | sed -n 's/^ *versionName = "\(.*\)"$/\1/p')"
[[ "${NAME}" == "${VERSION}" ]] || fail "В ${BRANCH} versionName = ${NAME}, а не ${VERSION}"
[[ -z "$(git ls-remote --tags origin "refs/tags/${TAG}")" ]] || fail "Тег ${TAG} уже есть на GitHub"
if git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
    fail "Тег ${TAG} уже есть локально"
fi

echo "Жду проверки PR #${PR}…"
gh pr checks "${PR}" --watch --fail-fast

gh pr merge "${PR}" --merge
SHA="$(gh pr view "${PR}" --json mergeCommit --jq .mergeCommit.oid)"
[[ -n "${SHA}" ]] || fail "GitHub не вернул коммит слияния PR #${PR}"
echo "Коммит слияния: ${SHA}"

git fetch -q origin main
git tag "${TAG}" "${SHA}"
git push -q origin "refs/tags/${TAG}"

echo "Жду, пока запустится release.yml…"
RUN=""
for _ in $(seq 30); do
    RUN="$(gh run list --workflow release.yml --branch "${TAG}" --limit 1 --json databaseId --jq '.[0].databaseId // empty')"
    [[ -n "${RUN}" ]] && break
    sleep 5
done
[[ -n "${RUN}" ]] || fail "release.yml не запустился по тегу ${TAG} — проверьте Actions"
gh run watch "${RUN}" --exit-status
gh release view "${TAG}" --json url --jq .url
