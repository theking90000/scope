#!/usr/bin/env python3
"""Export the benchmark reports (JMH + memoryReport) to CSV.

Reads results/performanceTest-<machine>.txt and
results/memoryReport-<machine>.txt, and produces two combined CSVs
(one row per measurement, with a `machine` column) ready for analysis.
"""
import csv
import glob
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))


# --- Memory units -> bytes ----------------------------------------------------
_UNIT = {"B": 1, "KiB": 1024, "MiB": 1024**2, "GiB": 1024**3}


def to_bytes(value, unit):
    return int(round(float(value) * _UNIT[unit]))


# --- JMH parsing --------------------------------------------------------------
# Final summary table row, e.g.:
# SyntheticScopeBenchmarks.createAndCloseNestedChain   N/A   1   N/A  avgt  5  386.668 ± 1.358  ns/op
JMH_RE = re.compile(
    r"^(?P<bench>\S+)\s+"
    r"(?P<count>\S+)\s+"
    r"(?P<depth>\S+)\s+"
    r"(?P<enabled>\S+)\s+"
    r"(?P<mode>\w+)\s+"
    r"(?P<cnt>\d+)\s+"
    r"(?P<score>[\d.]+)\s+±\s+(?P<error>[\d.]+)\s+"
    r"(?P<unit>\S+)\s*$"
)


def parse_jmh(path):
    rows = []
    in_table = False
    with open(path) as f:
        for line in f:
            if line.startswith("Benchmark") and "Mode" in line and "Score" in line:
                in_table = True
                continue
            if not in_table:
                continue
            m = JMH_RE.match(line.rstrip())
            if not m:
                continue
            d = m.groupdict()
            bench = d["bench"]
            # strip class prefix
            method = bench.split(".")[-1]
            rows.append({
                "benchmark": method,
                "count": "" if d["count"] == "N/A" else d["count"],
                "depth": "" if d["depth"] == "N/A" else d["depth"],
                "enabled": "" if d["enabled"] == "N/A" else d["enabled"],
                "mode": d["mode"],
                "cnt": d["cnt"],
                "score": d["score"],
                "error": d["error"],
                "unit": d["unit"],
            })
    return rows


# --- memoryReport parsing -----------------------------------------------------
# E.g.: "100k plain context records   retained=  1.92 MiB after-close=  800.00 B"
MEM_RE = re.compile(
    r"^(?P<scenario>.+?)\s+"
    r"retained=\s*(?P<r_val>[\d.]+)\s*(?P<r_unit>B|KiB|MiB|GiB)\s+"
    r"after-close=\s*(?P<a_val>[\d.]+)\s*(?P<a_unit>B|KiB|MiB|GiB)\s*$"
)


def parse_memory(path):
    rows = []
    with open(path) as f:
        for line in f:
            m = MEM_RE.match(line.rstrip())
            if not m:
                continue
            d = m.groupdict()
            rows.append({
                "scenario": d["scenario"].strip(),
                "retained_bytes": to_bytes(d["r_val"], d["r_unit"]),
                "retained_human": f"{d['r_val']} {d['r_unit']}",
                "after_close_bytes": to_bytes(d["a_val"], d["a_unit"]),
                "after_close_human": f"{d['a_val']} {d['a_unit']}",
            })
    return rows


def main():
    # JMH
    jmh_rows = []
    for path in sorted(glob.glob(os.path.join(HERE, "performanceTest-*.txt"))):
        machine = os.path.basename(path)[len("performanceTest-"):].removesuffix(".txt")
        for row in parse_jmh(path):
            jmh_rows.append({"machine": machine, **row})

    jmh_out = os.path.join(HERE, "jmh.csv")
    with open(jmh_out, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=[
            "machine", "benchmark", "count", "depth", "enabled",
            "mode", "cnt", "score", "error", "unit",
        ])
        w.writeheader()
        w.writerows(jmh_rows)

    # Memory
    mem_rows = []
    for path in sorted(glob.glob(os.path.join(HERE, "memoryReport-*.txt"))):
        machine = os.path.basename(path)[len("memoryReport-"):].removesuffix(".txt")
        for row in parse_memory(path):
            mem_rows.append({"machine": machine, **row})

    mem_out = os.path.join(HERE, "memory.csv")
    with open(mem_out, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=[
            "machine", "scenario", "retained_bytes", "retained_human",
            "after_close_bytes", "after_close_human",
        ])
        w.writeheader()
        w.writerows(mem_rows)

    print(f"jmh.csv    : {len(jmh_rows)} rows -> {jmh_out}")
    print(f"memory.csv : {len(mem_rows)} rows -> {mem_out}")


if __name__ == "__main__":
    main()
