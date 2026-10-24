#!/usr/bin/env bash

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/.." && pwd)"
jar="$root/target/z-compiler-1.0-SNAPSHOT-all.jar"
out="${Z_BENCH_OUT:-$(mktemp -d)}"
repeats="${Z_BENCH_REPEATS:-7}"

mkdir -p "$out"

if [ ! -f "$jar" ] || [ -n "$(find "$root/src" "$root/pom.xml" -newer "$jar" -print -quit)" ]; then
  (cd "$root" && mvn -q -Dmaven.test.skip=true package)
fi

cflags=(-O3 -march=native -flto -funroll-loops)

build_z() {
  java -jar "$jar" "$here/$1.z" -o "$out/$1.zbin" >/dev/null
}

build_c() {
  clang "${cflags[@]}" "$here/$1.c" -o "$out/$1.cbin"
}

time_best() {
  local binary="$1" values=()
  for _ in $(seq "$repeats"); do
    local start end
    start=$(date +%s%N)
    "$binary" >/dev/null 2>&1 || true
    end=$(date +%s%N)
    values+=($(( (end - start) / 1000000 )))
  done
  printf '%s %s' "$(printf '%s\n' "${values[@]}" | sort -n | head -1)" \
      "$(printf '%s\n' "${values[@]}" | sort -n \
          | awk '{a[NR]=$1} END{print "[" a[1] ".." a[NR] "]"}')"
}

names=("$@")
if [ ${#names[@]} -eq 0 ]; then
  names=()
  for z in "$here"/*.z; do names+=("$(basename "$z" .z)"); done
fi

printf '%-14s   %10s    %10s   %9s    %s\n' benchmark Z C ratio spread
printf '%s\n' "---------------------------------------------------------------------"

worst=0
worst_name=""

for name in "${names[@]}"; do
  if [ ! -f "$here/$name.z" ] || [ ! -f "$here/$name.c" ]; then
    printf '%-14s %10s\n' "$name" "MISSING"
    continue
  fi

  build_z "$name"
  build_c "$name"

  z_status=0; "$out/$name.zbin" >/dev/null 2>&1 || z_status=$?
  c_status=0; "$out/$name.cbin" >/dev/null 2>&1 || c_status=$?
  if [ "$z_status" -ne "$c_status" ]; then
    printf '%-14s %10s  Z exits %s, C exits %s\n' "$name" "MISMATCH" "$z_status" "$c_status"
    continue
  fi

  z_a="$(time_best "$out/$name.zbin")"
  c_a="$(time_best "$out/$name.cbin")"
  c_b="$(time_best "$out/$name.cbin")"
  z_b="$(time_best "$out/$name.zbin")"

  read -r z_ms c_ms z_spread c_spread < <(
    awk -v za="${z_a%% *}" -v zb="${z_b%% *}" -v zs="${z_a#* }" \
        -v ca="${c_a%% *}" -v cb="${c_b%% *}" -v cs="${c_a#* }" \
        'BEGIN{ print (za<zb?za:zb), (ca<cb?ca:cb), zs, cs }')

  read -r ratio value < <(awk -v z="$z_ms" -v c="$c_ms" \
    'BEGIN{ if (c==0) print "n/a", "0"; else printf "%.2fx %.4f\n", z/c, z/c }')
  printf '%-14s %8s ms %8s ms %9s  Z %s  C %s\n' \
      "$name" "$z_ms" "$c_ms" "$ratio" "$z_spread" "$c_spread"

  if awk -v a="$value" -v b="$worst" 'BEGIN{ exit !(a>b) }'; then
    worst=$value
    worst_name=$name
  fi
done

printf '%s\n' "---------------------------------------------------------------------"
if [ "$worst_name" != "" ]; then
  printf 'slowest relative to C: %s at %s\n' "$worst_name" \
      "$(awk -v w="$worst" 'BEGIN{ printf "%.2fx", w }')"
  printf 'binaries in %s\n' "$out"
else
  printf 'nothing ran\n'
  exit 1
fi