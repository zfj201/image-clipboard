#!/bin/sh
set -eu
task_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
task_out=$(mktemp -d)
trap 'rm -rf "$task_out"' EXIT
javac -d "$task_out" "$task_root/app/src/main/java/com/zfj/imageclipboard/Rules.java" "$task_root/app/src/main/java/com/zfj/imageclipboard/DeliveryOrder.java" "$task_root/tests/CoreTest.java"
java -cp "$task_out" com.zfj.imageclipboard.CoreTest
