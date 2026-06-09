#!/usr/bin/env python3
"""Analyze the benchmark CSVs and produce derived metrics + charts.

Usage:
    uv run --with matplotlib --with pandas python3 results/analyze.py

Reads results/jmh.csv and results/memory.csv (produced by export_csv.py),
computes production-oriented metrics (cost per scope, throughput, scopes per
tick, memory per scope, leak ratio, hook overhead) and generates PNGs in
results/charts/. Auto-discovers every machine present.
"""
import os
import re

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd
from matplotlib import font_manager
from matplotlib.ticker import FuncFormatter

HERE = os.path.dirname(os.path.abspath(__file__))
CHARTS = os.path.join(HERE, "charts")
os.makedirs(CHARTS, exist_ok=True)

# Reference budget: 1 game-server tick = 50 ms (20 TPS).
TICK_NS = 50_000_000.0

# ---------------------------------------------------------------------------
# THEME
# ---------------------------------------------------------------------------
INK = "#1F2933"          # near-black for text/spines
SUBTLE = "#7B8794"       # muted grey for secondary text/ticks
GRID = "#E1E5EA"         # soft grid
PAGE = "#FFFFFF"         # figure background
PANEL = "#F7F8FA"        # axes background
ACCENT = "#D7263D"       # crimson for budget/floor lines
GOOD = "#2A9D8F"         # teal-green for "safe zone" highlights

# Curated categorical palette — stable colour per machine across every chart.
PALETTE = ["#2E6F95", "#E8772E", "#6A4C93", "#1FA187", "#C8553D", "#3D5A80"]
_MACHINE_COLOR = {}


def init_colors(machine_list):
    for i, m in enumerate(sorted(machine_list)):
        _MACHINE_COLOR[m] = PALETTE[i % len(PALETTE)]


def color_for(machine):
    return _MACHINE_COLOR.get(machine, PALETTE[0])


def setup_style():
    preferred = ["Inter", "Helvetica Neue", "Helvetica", "Arial", "DejaVu Sans"]
    available = {f.name for f in font_manager.fontManager.ttflist}
    family = [f for f in preferred if f in available] or ["DejaVu Sans"]
    plt.rcParams.update({
        "figure.facecolor": PAGE,
        "savefig.facecolor": PAGE,
        "axes.facecolor": PANEL,
        "axes.edgecolor": GRID,
        "axes.linewidth": 1.0,
        "axes.grid": True,
        "axes.grid.axis": "y",
        "grid.color": GRID,
        "grid.linewidth": 1.0,
        "axes.spines.top": False,
        "axes.spines.right": False,
        "axes.titlesize": 14,
        "axes.titleweight": "bold",
        "axes.titlecolor": INK,
        "axes.titlelocation": "left",
        "axes.titlepad": 14,
        "axes.labelsize": 10.5,
        "axes.labelcolor": SUBTLE,
        "axes.labelweight": "medium",
        "text.color": INK,
        "font.family": family,
        "font.size": 10.5,
        "xtick.color": SUBTLE,
        "ytick.color": SUBTLE,
        "xtick.labelsize": 9.5,
        "ytick.labelsize": 9.5,
        "legend.frameon": False,
        "legend.fontsize": 9,
        "figure.dpi": 130,
    })


def finish(ax):
    """Common per-axes polish applied after plotting."""
    ax.set_axisbelow(True)
    for side in ("left", "bottom"):
        ax.spines[side].set_color(GRID)
    ax.tick_params(length=0)
    return ax


def human_count(v):
    if v >= 1e6:
        return f"{v / 1e6:.1f}M"
    if v >= 1e3:
        return f"{v / 1e3:.0f}k"
    return f"{v:.0f}"


def human_ns(v):
    if v >= 1e6:
        return f"{v / 1e6:.1f} ms"
    if v >= 1e3:
        return f"{v / 1e3:.1f} µs"
    return f"{v:.0f} ns"


def human_bytes(v):
    for unit, div in (("MiB", 1024**2), ("KiB", 1024), ("B", 1)):
        if v >= div:
            return f"{v / div:.1f} {unit}"
    return "0 B"

# "create+close N scopes" benchmark families (unit ms/op, count = number of scopes).
SCOPE_FAMILY = {
    "createAndCloseEmptyScopes": "empty scope",
    "createAndCloseScopesWithEightBeans": "scope + 8 beans",
    "createAndCloseScopesWithThirtyTwoBeans": "scope + 32 beans",
}

UNIT_NS = {"ns/op": 1.0, "us/op": 1e3, "ms/op": 1e6, "s/op": 1e9}


def to_ns(score, unit):
    return score * UNIT_NS[unit]


def machines(df):
    return sorted(df["machine"].unique())


def grouped_bar(ax, df, index_col, value_col, machine_col="machine", label_fmt=None):
    """Grouped bars: one group per index_col, one bar per machine."""
    pivot = df.pivot_table(index=index_col, columns=machine_col,
                           values=value_col, sort=False)
    cats = list(pivot.index)
    machs = list(pivot.columns)
    n = len(machs)
    width = 0.78 / max(n, 1)
    x = range(len(cats))
    for i, m in enumerate(machs):
        offsets = [xi + (i - (n - 1) / 2) * width for xi in x]
        vals = pivot[m].values
        bars = ax.bar(offsets, vals, width=width, label=m,
                      color=color_for(m), edgecolor=PAGE, linewidth=1.2, zorder=3)
        if label_fmt:
            for rect, v in zip(bars, vals):
                ax.annotate(label_fmt(v), (rect.get_x() + rect.get_width() / 2, v),
                            textcoords="offset points", xytext=(0, 4), ha="center",
                            fontsize=8, color=INK, fontweight="medium")
    ax.set_xticks(list(x))
    ax.set_xticklabels(cats, rotation=0, ha="center", color=INK, fontsize=10)
    ax.margins(y=0.18)
    return pivot


# ---------------------------------------------------------------------------
def load():
    jmh = pd.read_csv(os.path.join(HERE, "jmh.csv"))
    mem = pd.read_csv(os.path.join(HERE, "memory.csv"))
    return jmh, mem


# --- Derived metrics: cost per scope ---------------------------------------
def derive_perscope(jmh):
    fam = jmh[jmh["benchmark"].isin(SCOPE_FAMILY)].copy()
    fam["scenario"] = fam["benchmark"].map(SCOPE_FAMILY)
    fam["total_ns"] = fam.apply(lambda r: to_ns(r["score"], r["unit"]), axis=1)
    fam["ns_per_scope"] = fam["total_ns"] / fam["count"]
    fam["scopes_per_sec"] = 1e9 / fam["ns_per_scope"]
    fam["scopes_per_tick"] = TICK_NS / fam["ns_per_scope"]
    fam["rel_error_pct"] = 100 * fam["error"] / fam["score"]
    return fam[["machine", "scenario", "count", "total_ns", "ns_per_scope",
                "scopes_per_sec", "scopes_per_tick", "rel_error_pct"]]


# --- Derived metrics: memory per scope -------------------------------------
def derive_memory(mem):
    m = mem.copy()
    cnt = m["scenario"].str.extract(r"^(\d+)k", expand=False)
    m["count"] = pd.to_numeric(cnt, errors="coerce") * 1000
    m["is_scope"] = m["scenario"].str.contains("sibling scopes")
    m["bytes_per_scope"] = m["retained_bytes"] / m["count"]
    m["leak_pct"] = 100 * m["after_close_bytes"] / m["retained_bytes"]
    return m


# --- Derived metrics: hook overhead ----------------------------------------
def derive_hook(jmh):
    h = jmh[jmh["benchmark"] == "materializeBeanWithOptionalHook"].copy()
    rows = []
    for mach, g in h.groupby("machine"):
        off = g[g["enabled"] == False]["score"]
        on = g[g["enabled"] == True]["score"]
        if len(off) and len(on):
            off, on = off.iloc[0], on.iloc[0]
            rows.append({"machine": mach, "ns_without_hook": off, "ns_with_hook": on,
                         "overhead_pct": 100 * (on - off) / off})
    return pd.DataFrame(rows)


# ===========================================================================
# CHARTS
# ===========================================================================
def _save(fig, name):
    fig.tight_layout()
    fig.savefig(os.path.join(CHARTS, name))
    plt.close(fig)


def chart_scopes_per_tick(ps):
    fig, ax = plt.subplots(figsize=(9, 5.5))
    big = ps[ps["count"] == ps.groupby("scenario")["count"].transform("max")]
    grouped_bar(ax, big, "scenario", "scopes_per_tick", label_fmt=human_count)
    ax.set_yscale("log")
    ax.set_ylabel("create+close cycles per 50 ms tick (log)")
    ax.set_title("How many scopes fit in a single 20 TPS tick?")
    ax.axhline(1, color=ACCENT, ls=(0, (4, 3)), lw=1.4, zorder=2)
    ax.text(0.995, 1.3, "1 scope / tick — floor", color=ACCENT, fontsize=8.5,
            fontweight="bold", ha="right", transform=ax.get_yaxis_transform())
    ax.legend()
    finish(ax)
    _save(fig, "scopes_per_tick.png")


def chart_cost_per_scope(ps):
    fig, ax = plt.subplots(figsize=(9, 5.5))
    big = ps[ps["count"] == ps.groupby("scenario")["count"].transform("max")]
    grouped_bar(ax, big, "scenario", "ns_per_scope", label_fmt=human_ns)
    ax.set_yscale("log")
    ax.yaxis.set_major_formatter(FuncFormatter(lambda v, _: human_ns(v)))
    ax.set_ylabel("Latency per create+close cycle (log)")
    ax.set_title("Scope lifecycle cost — lower is lighter")
    ax.legend()
    finish(ax)
    _save(fig, "cost_per_scope.png")


def chart_scaling_nested(jmh):
    nc = jmh[jmh["benchmark"] == "createAndCloseNestedChain"].copy()
    nc["ns"] = nc.apply(lambda r: to_ns(r["score"], r["unit"]), axis=1)
    fig, ax = plt.subplots(figsize=(9, 5.5))
    # Realistic production zone: 2 to 8 levels of nesting.
    ax.axvspan(1, 8, color=GOOD, alpha=0.08, zorder=0)
    ax.text(1.15, nc["ns"].max(), "realistic prod depths (≤ ~8)\n→ a few µs",
            fontsize=8.5, color=GOOD, fontweight="bold", va="top")
    for m in machines(nc):
        g = nc[nc["machine"] == m].sort_values("depth")
        ax.plot(g["depth"], g["ns"], marker="o", markersize=6, lw=2.2,
                color=color_for(m), markeredgecolor=PAGE, markeredgewidth=1.2,
                label=m, zorder=3)
    ax.set_xscale("log", base=2)
    ax.set_yscale("log")
    ax.yaxis.set_major_formatter(FuncFormatter(lambda v, _: human_ns(v)))
    ax.set_xlabel("Nested scope chain depth")
    ax.set_ylabel("create+close latency (log)")
    ax.set_title("Nested-chain cost vs depth — superlinear (~quadratic)")
    ax.grid(which="both", axis="both")
    ax.legend()
    finish(ax)
    _save(fig, "scaling_nested.png")


def chart_resolve_depth(jmh):
    rb = jmh[jmh["benchmark"] == "resolveInheritedBean"].copy()
    fig, ax = plt.subplots(figsize=(9, 5.5))
    for m in machines(rb):
        g = rb[rb["machine"] == m].sort_values("depth")
        ax.plot(g["depth"], g["score"], marker="o", markersize=6, lw=2.2,
                color=color_for(m), markeredgecolor=PAGE, markeredgewidth=1.2,
                label=m, zorder=3)
    ax.set_xlabel("Hierarchy depth traversed")
    ax.set_ylabel("Resolution latency (ns)")
    ax.set_title("Inherited bean resolution cost vs depth")
    ax.legend()
    finish(ax)
    _save(fig, "resolve_depth.png")


def chart_memory_leak(mem):
    scopes = mem[mem["is_scope"]].copy()
    one = machines(scopes)[0]
    g = scopes[scopes["machine"] == one]
    fig, ax = plt.subplots(figsize=(9, 5.5))
    x = range(len(g))
    w = 0.38
    ax.bar([i - w / 2 for i in x], g["retained_bytes"] / 1024**2, w,
           label="retained (scope open)", color=color_for(one),
           edgecolor=PAGE, linewidth=1.2, zorder=3)
    ax.bar([i + w / 2 for i in x], g["after_close_bytes"] / 1024**2, w,
           label="after close()", color=ACCENT,
           edgecolor=PAGE, linewidth=1.2, zorder=3)
    ax.set_xticks(list(x))
    ax.set_xticklabels([s.replace("sibling ", "") for s in g["scenario"]],
                       rotation=15, ha="right", color=INK, fontsize=9)
    ax.set_ylabel("Retained heap (MiB)")
    ax.set_title(f"close() reclaims ~everything — no leak · {one}")
    ax.margins(y=0.16)
    for i, (_, r) in zip(x, g.iterrows()):
        ax.annotate(f"{r['leak_pct']:.4f}% left",
                    (i + w / 2, r['after_close_bytes'] / 1024**2),
                    textcoords="offset points", xytext=(0, 5), ha="center",
                    fontsize=7.5, color=ACCENT, fontweight="bold")
    ax.legend()
    finish(ax)
    _save(fig, "memory_leak.png")


def chart_memory_per_scope(mem):
    scopes = mem[mem["is_scope"]].copy()
    scopes["scenario_short"] = scopes["scenario"].str.replace(
        r"^\d+k ", "", regex=True).str.replace("sibling ", "")
    big = scopes[scopes["count"] == scopes.groupby("scenario_short")["count"].transform("max")]
    fig, ax = plt.subplots(figsize=(9, 5.5))
    grouped_bar(ax, big, "scenario_short", "bytes_per_scope", label_fmt=human_bytes)
    ax.yaxis.set_major_formatter(FuncFormatter(lambda v, _: human_bytes(v)))
    ax.set_ylabel("Retained memory per scope")
    ax.set_title("Per-scope memory footprint")
    ax.legend()
    finish(ax)
    _save(fig, "memory_per_scope.png")


def chart_hook(hook):
    if hook.empty:
        return
    fig, ax = plt.subplots(figsize=(8.5, 5))
    x = range(len(hook))
    w = 0.36
    ax.bar([i - w / 2 for i in x], hook["ns_without_hook"], w, label="without hook",
           color=PALETTE[0], edgecolor=PAGE, linewidth=1.2, zorder=3)
    ax.bar([i + w / 2 for i in x], hook["ns_with_hook"], w, label="with hook",
           color=PALETTE[1], edgecolor=PAGE, linewidth=1.2, zorder=3)
    ax.set_xticks(list(x))
    ax.set_xticklabels(hook["machine"], rotation=0, ha="center", color=INK, fontsize=9)
    ax.set_ylabel("Bean materialization latency (ns)")
    ax.set_title("OnCreatedHook overhead")
    ax.margins(y=0.16)
    for i, (_, r) in zip(x, hook.iterrows()):
        ax.annotate(f"+{r['overhead_pct']:.0f}%", (i + w / 2, r["ns_with_hook"]),
                    textcoords="offset points", xytext=(0, 5), ha="center",
                    fontsize=9, color=INK, fontweight="bold")
    ax.legend()
    finish(ax)
    _save(fig, "hook_overhead.png")


def main():
    jmh, mem = load()
    setup_style()
    init_colors(machines(jmh))

    ps = derive_perscope(jmh)
    md = derive_memory(mem)
    hook = derive_hook(jmh)

    ps.to_csv(os.path.join(HERE, "analysis_perscope.csv"), index=False)
    md.to_csv(os.path.join(HERE, "analysis_memory.csv"), index=False)
    hook.to_csv(os.path.join(HERE, "analysis_hook.csv"), index=False)

    chart_scopes_per_tick(ps)
    chart_cost_per_scope(ps)
    chart_scaling_nested(jmh)
    chart_resolve_depth(jmh)
    chart_memory_leak(md)
    chart_memory_per_scope(md)
    chart_hook(hook)

    print(f"Machines detected: {', '.join(machines(jmh))}")
    print("\n--- Cost per scope (largest config) ---")
    big = ps[ps["count"] == ps.groupby("scenario")["count"].transform("max")]
    for _, r in big.sort_values(["scenario", "machine"]).iterrows():
        print(f"  {r['machine']:<26} {r['scenario']:<16} "
              f"{r['ns_per_scope']:>9.1f} ns/scope  "
              f"{r['scopes_per_tick']:>10,.0f} scopes/tick")
    print("\n--- Leak after close() ---")
    for _, r in md[md["is_scope"]].iterrows():
        print(f"  {r['machine']:<26} {r['scenario']:<28} leak={r['leak_pct']:.4f}%")
    print(f"\nAnalysis CSVs + {len(os.listdir(CHARTS))} charts -> {CHARTS}")


if __name__ == "__main__":
    main()
