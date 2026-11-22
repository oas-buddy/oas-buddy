#!/usr/bin/env bash
# Regenerates the IntelliJ JSON layout fixtures (#105) by running IntelliJ IDEA's own command-line
# formatter, so the tests compare the JSON writer against what IntelliJ really produces.
#
# Layout: oas-buddy-core/src/test/resources/fixtures/intellij/
#   <source>.source.json   compact input documents (one line each, no layout to inherit)
#   <case>/.editorconfig   the settings of one case
#   <case>/<source>.json   IntelliJ's output for that source under those settings  <- generated
#
# Usage: tools/generate-intellij-json-fixtures.sh   (IDEA=/path/to/idea to pick the launcher)
#
# Runs headless and works while the IDE is open: IntelliJ refuses a second instance on the same
# config, so the formatter gets fresh config/system directories of its own. Fresh ones every run
# also matter for correctness: IntelliJ caches each folder's .editorconfig in its system directory,
# and a stale cache silently formats with the old settings.
#
# The sources are compact on purpose. IntelliJ keeps existing line breaks (ij_json_keep_line_breaks),
# so formatting an already wrapped file is not the same as laying out the document from scratch,
# which is what the writer does.
set -euo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
fixtures="$repo/oas-buddy-core/src/test/resources/fixtures/intellij"
idea=${IDEA:-idea}

work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT

mkdir -p "$work/ij/config" "$work/ij/system" "$work/ij/log" "$work/ij/plugins" "$work/cases"
cat > "$work/ij/idea.properties" <<EOF
idea.config.path=$work/ij/config
idea.system.path=$work/ij/system
idea.log.path=$work/ij/log
idea.plugins.path=$work/ij/plugins
EOF

files=()
for case_dir in "$fixtures"/*/; do
    case_name=$(basename "$case_dir")
    mkdir "$work/cases/$case_name"
    cp "$case_dir/.editorconfig" "$work/cases/$case_name/"
    for source in "$fixtures"/*.source.json; do
        target="$work/cases/$case_name/$(basename "$source" .source.json).json"
        cp "$source" "$target"
        files+=("$target")
    done
done

IDEA_PROPERTIES="$work/ij/idea.properties" "$idea" format -allowDefaults "${files[@]}"

for case_dir in "$work"/cases/*/; do
    cp "$case_dir"/*.json "$fixtures/$(basename "$case_dir")/"
done
echo "Regenerated ${#files[@]} fixtures in $fixtures"
