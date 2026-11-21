#!/usr/bin/env bash
# Plots net lines added over time (insertions minus deletions, summed commit by commit),
# one line per kind of file:
#   test            anything under a src/test/ directory (test code and its resources)
#   production      .java and .sh files outside src/test/
#   non-executable  everything else (POMs, workflows, docs, resources, icons)
# Reads only. Needs git, awk and gnuplot.
#
#   tools/lines.sh                 open the plot in a window
#   tools/lines.sh loc.png         write a PNG instead
#   tools/lines.sh loc.png '*.java'  restrict to files matching a pathspec
set -euo pipefail

output="${1:-}"
pathspec="${2:-}"

data="$(mktemp)"
trap 'rm -f "$data"' EXIT

# numstat lines are tab-separated (added, deleted, path), so a path with spaces stays one field.
# A rename shows as "dir/{old => new}/File.java"; matching on the whole string still classifies it.
git log --reverse --numstat --format='C %ad' --date=short ${pathspec:+-- "$pathspec"} \
  | awk -F'\t' '
      /^C / { split($0, c, " "); d = c[2]; next }
      NF == 3 && $1 != "-" {
        n = $1 - $2
        if ($3 ~ /(^|\/)src\/test\//)        test += n
        else if ($3 ~ /\.(java|sh)}?$/)      prod += n
        else                                 other += n
        v[d] = test " " prod " " other
      }
      END { for (k in v) print k, v[k] }' \
  | sort > "$data"

if [ -n "$output" ]; then
  terminal="set terminal png size 900,500; set output \"$output\";"
  persist=()
else
  terminal=""
  persist=(-persist)
fi

gnuplot "${persist[@]}" -e "$terminal set xdata time; set timefmt '%Y-%m-%d'; set format x '%b %d'; set xtics rotate by -45; set grid; set key top left; set title 'Net lines added over time'; set ylabel 'lines'; plot '$data' using 1:3 with linespoints title 'production', '' using 1:2 with linespoints title 'test', '' using 1:4 with linespoints title 'non-executable'"
