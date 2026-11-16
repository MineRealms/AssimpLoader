"""Render the checked-in benchmark tables. Does not launch Java or Minecraft."""

import argparse
import csv
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from matplotlib.ticker import ScalarFormatter


ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "docs" / "benchmarks"
COLORS = ["#bd563e", "#587da0", "#13806e", "#c4982e"]
BACKENDS = ["IMMEDIATE", "INSTANCED", "MDI", "MDI_HIZ"]
LABELS = ["Immediate", "Instanced", "MDI", "MDI + Hi-Z"]


def read_csv(name):
    with (BASE / "data" / name).open(encoding="utf-8", newline="") as source:
        return list(csv.DictReader(source))


def style():
    plt.rcParams.update({
        "font.family": "DejaVu Sans", "font.size": 11,
        "axes.titlesize": 14, "axes.titleweight": "bold",
        "axes.labelcolor": "#34404a", "text.color": "#202930",
        "axes.edgecolor": "#cbd2d6", "xtick.color": "#52616b",
        "ytick.color": "#34404a", "axes.spines.top": False,
        "axes.spines.right": False, "figure.facecolor": "white",
        "savefig.facecolor": "white", "svg.fonttype": "none",
    })


def grid(ax, axis="x"):
    ax.set_axisbelow(True)
    ax.grid(axis=axis, color="#e5e9ec", linewidth=0.8)


def save(fig, directory, name, title):
    fig.suptitle(title, fontsize=20, fontweight="bold", x=0.04, ha="left")
    fig.tight_layout(rect=(0.015, 0.025, 0.99, 0.90), w_pad=3)
    for extension in ("png", "svg"):
        fig.savefig(directory / f"{name}.{extension}", dpi=180)
    plt.close(fig)


def submission(rows, directory):
    fig, axes = plt.subplots(1, 2, figsize=(13.5, 5.4))
    for ax, count in zip(axes, (70, 262)):
        group = [r for r in rows if int(r["models"]) == count]
        mean = np.array([float(r["cpuMeanMs"]) for r in group])
        p95 = np.array([float(r["cpuP95Ms"]) for r in group])
        y = np.arange(4)
        ax.barh(y, mean, color=COLORS, height=0.5)
        ax.scatter(p95, y, marker="D", color="#202930", s=27, label="p95", zorder=3)
        for i, (m, p) in enumerate(zip(mean, p95)):
            ax.text(max(m, p) + p95.max() * 0.025, i, f"{m:.3f} / {p:.3f}", va="center", fontsize=10)
        ax.set(yticks=y, yticklabels=LABELS, xlabel="CPU submission (ms): mean / p95",
               title=f"{count} models", xlim=(0, p95.max() * 1.42))
        ax.invert_yaxis()
        ax.legend(loc="lower right", frameon=False)
        grid(ax)
    save(fig, directory, "01_cpu_submission", "AssimpLoader | CPU submission")


def gpu_and_calls(rows, directory):
    fig, axes = plt.subplots(1, 2, figsize=(13.5, 5.8))
    x = np.arange(4)
    for j, count in enumerate((70, 262)):
        group = [r for r in rows if int(r["models"]) == count]
        offset = (j - 0.5) * 0.36
        for ax, key in zip(axes, ("gpuMedianMs", "submissions")):
            vals = [float(r[key]) for r in group]
            bars = ax.bar(x + offset, vals, width=0.32, color=("#587da0", "#13806e")[j], label=f"{count} models")
            for bar, value in zip(bars, vals):
                ax.annotate(f"{value:.3f}" if key == "gpuMedianMs" else f"{value:.0f}",
                            (bar.get_x() + bar.get_width() / 2, value), xytext=(0, 5),
                            textcoords="offset points", ha="center", fontsize=9)
    axes[0].set(yscale="log", ylabel="GPU elapsed median (ms)", title="Timed renderer interval")
    axes[0].set_ylim(0.25, 45)
    axes[1].set(ylabel="API submissions per frame", title="Material groups and draw calls", ylim=(0, 340))
    for ax in axes:
        ax.set(xticks=x, xticklabels=LABELS)
        ax.legend(frameon=False, loc="upper right")
        grid(ax, "y")
    save(fig, directory, "02_gpu_and_calls", "AssimpLoader | GPU interval and draw calls")


def cpu_pose(rows, directory):
    fig, axes = plt.subplots(1, 2, figsize=(13.5, 5.8))
    for ax, bones in zip(axes, (64, 256)):
        for path, label, color in zip(("pose_only", "pose_and_cpu_skinning"),
                                      ("Pose + bounds", "Pose + CPU skinning"), (COLORS[2], COLORS[0])):
            group = [r for r in rows if int(r["bones"]) == bones and r["path"] == path]
            x = np.array([int(r["instances"]) for r in group])
            y = np.array([float(r["medianMs"]) for r in group])
            p = np.array([float(r["p95Ms"]) for r in group])
            ax.plot(x, y, "o-", color=color, label=label, linewidth=2)
            ax.fill_between(x, y, p, color=color, alpha=0.12)
            for xx, yy in zip(x, y):
                ax.annotate(f"{yy:.2f}", (xx, yy), xytext=(0, 7), textcoords="offset points", ha="center", fontsize=9)
        ax.set(title=f"{bones} bones | 4095 vertices / instance", xlabel="Instances", ylabel="CPU evaluation (ms, log scale)",
               xticks=[1, 10, 50], yscale="log", ylim=(0.055, 80))
        ax.set_yticks([0.1, 0.5, 1, 5, 10, 50])
        ax.yaxis.set_major_formatter(ScalarFormatter())
        ax.legend(frameon=False, loc="upper left")
        grid(ax, "y")
    save(fig, directory, "03_pose_and_skinning", "AssimpLoader | CPU pose and deformation")


def scaling(rows, assessment, directory):
    config = assessment["scaling"]
    x = np.array(config["models"])
    margin = np.array(config["relative_margin"])
    fig, axes = plt.subplots(1, 2, figsize=(13.5, 5.8))
    for backend, label, color in zip(BACKENDS, LABELS, COLORS):
        anchors = sorted((int(r["models"]), float(r["cpuMeanMs"])) for r in rows if r["backend"] == backend)
        (x0, y0), (x1, y1) = anchors
        y = y0 + (x - x0) * (y1 - y0) / (x1 - x0)
        for i, ax in enumerate(axes):
            if i == 1 and backend == "IMMEDIATE":
                continue
            ax.plot(x, y, "o-", color=color, label=label, linewidth=2)
            ax.fill_between(x, y * (1 - margin), y * (1 + margin), color=color, alpha=0.09)
    for ax in axes:
        ax.set(xlabel="Models", ylabel="CPU submission mean (ms)", xticks=[70, 262, 518, 1030], xlim=(40, 1060))
        ax.legend(frameon=False, loc="upper left")
        grid(ax, "y")
    axes[0].set_title("Fixed 19 texture groups")
    axes[1].set_title("Batched backends")
    save(fig, directory, "04_model_scaling", "AssimpLoader | Renderer scaling")


def feature_costs(assessment, directory):
    rows = assessment["feature_costs"]
    fig, ax = plt.subplots(figsize=(13.5, 7.5))
    for i, row in enumerate(rows):
        ax.plot([row["low"], row["high"]], [i, i], linewidth=9, solid_capstyle="round", color=COLORS[i % 4])
        ax.text(row["high"] * 1.14, i, f'{row["low"]:g} - {row["high"]:g}', va="center", fontsize=11)
    ax.set(xscale="log", yticks=np.arange(len(rows)), yticklabels=[r["label"] for r in rows],
           xlabel="CPU cost per population evaluation (ms)", xlim=(0.15, 230),
           title="256 instances | 4176 vertices | 35 bones per instance")
    ax.set_xticks([0.2, 0.5, 1, 2, 5, 10, 20, 50, 100, 200])
    ax.xaxis.set_major_formatter(ScalarFormatter())
    ax.invert_yaxis()
    grid(ax)
    save(fig, directory, "05_pipeline_costs", "AssimpLoader | Feature cost ranges")


def resources(assessment, directory):
    fig, axes = plt.subplots(1, 2, figsize=(13.5, 5.8))
    config = assessment["memory"]
    count = np.array([256, 512, 1024, 2048, 4096])
    fixed = config["max_instances"] * config["id_bytes_per_capacity"] + config["padding_bytes"]
    record = sum(config[k] for k in ("instance_record_bytes", "bounds_bytes", "command_bytes", "visibility_bytes"))
    for bones, color in zip((35, 64, 256), (COLORS[2], COLORS[1], COLORS[0])):
        mib = (fixed + count * (record + bones * config["bone_matrix_bytes"])) / 1024 ** 2
        axes[0].plot(count, mib, "o-", color=color, label=f"{bones} bones", linewidth=2)
    axes[0].axhline(config["default_frame_budget_mib"], color="#34404a", linestyle="--", linewidth=1, label="32 MiB budget")
    axes[0].set(title="Frame admission accounting | capacity 4096", xlabel="Mesh instances", ylabel="Accounted payload (MiB)", xticks=[256, 1024, 2048, 4096])
    axes[0].legend(frameon=False, loc="upper left")
    grid(axes[0], "y")
    cadence = assessment["cadence"]
    fraction = list(cadence["population_fraction"].values())
    for i, (mode, intervals) in enumerate(cadence["interval_ticks"].items()):
        ratio = sum(f / step for f, step in zip(fraction, intervals)) * 100
        axes[1].bar(i, ratio, width=0.55, color=(COLORS[1], COLORS[2], COLORS[3])[i])
        axes[1].text(i, ratio + 2, f"{ratio:g}%", ha="center", fontsize=12, fontweight="bold")
    axes[1].set(title="20% near / 50% far / 30% hidden", ylabel="Pose evaluations relative to QUALITY (%)",
                xticks=np.arange(3), xticklabels=list(cadence["interval_ticks"]), ylim=(0, 115))
    grid(axes[1], "y")
    save(fig, directory, "06_memory_and_cadence", "AssimpLoader | Memory and update budgets")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=BASE / "charts")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    style()
    rows = read_csv("gpu_batch.csv")
    assessment = json.loads((BASE / "data" / "pipeline_assessment.json").read_text(encoding="utf-8"))
    submission(rows, args.output)
    gpu_and_calls(rows, args.output)
    cpu_pose(read_csv("cpu_pose.csv"), args.output)
    scaling(rows, assessment, args.output)
    feature_costs(assessment, args.output)
    resources(assessment, args.output)
    print(f"Generated 6 PNG and 6 SVG charts in {args.output}")


if __name__ == "__main__":
    main()
