# SPDX-License-Identifier: Apache-2.0
# Copyright (C) 2026  Paul Bouman, Rick Willemsen, Gizem Özbaygın

"""Plot generation utilities for pdpot-analyze."""

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from pathlib import Path
from typing import Dict, List, Optional, Union

from .common import (
    relative_gap_min,
    get_best_objectives,
)

# Configure Matplotlib tikz backend
plt.rcParams.update(
    {
        "pgf.texsystem": "pdflatex",
        "font.family": "serif",
        "text.usetex": False,
        "pgf.rcfonts": False,
    }
)


def plot_sorted_runtimes(
    data_dict: Dict[str, List[float]],
    log_scale: bool = False,
    output_tikz: Optional[Union[str, Path]] = None,
    title: Optional[str] = "Sorted Runtimes per Solver",
    xlabel: str = "Instance (sorted by runtime)",
    ylabel: str = "Runtime (ms)",
    caption: Optional[str] = None,
) -> None:
    """
    Plot sorted runtimes per solver/method, optionally export to TikZ/PGF.

    Args:
        data_dict: Keys are solver names, values are lists of runtimes.
        log_scale: Whether to use log scale for the y-axis.
        output_tikz: Path to save the TikZ/PGF file (e.g., 'runtime.pgf').
        title: Optional plot title.
        xlabel: X-axis label.
        ylabel: Y-axis label.
        caption: Optional caption to use instead of title (for TikZ output).
    """
    fig = plt.figure(figsize=(3, 3))

    for solver, runtimes in data_dict.items():
        sorted_times = np.sort(runtimes)
        x = np.arange(1, len(sorted_times) + 1)
        plt.plot(x, sorted_times, label=solver)

    plt.xlabel(xlabel)
    plt.ylabel(ylabel)
    if log_scale:
        plt.yscale("log")
        plt.ylabel(ylabel + " (log scale)")
    if title:
        plt.title(title)
    plt.grid(True, linestyle="--", alpha=0.6)
    plt.legend()
    plt.tight_layout()

    if output_tikz:
        fig.savefig(output_tikz)
        print(f"PGF plot saved to: {output_tikz}")
    else:
        plt.show()


def generate_runtime_plots(
    df,
    dataset_name: str,
    output_dir: Union[str, Path] = "output",
) -> None:
    """
    Generate runtime plots for a solver dataset.

    Args:
        df: Input DataFrame (solver experiment data).
        dataset_name: Name of the dataset (e.g., 'small', 'large').
        output_dir: Directory to save output files.
    """
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Convert runtimes to seconds and plot
    df["solve-time-s"] = df["solve-time"] / 1000

    # Get unique solvers from the data
    solvers = sorted(df["solver"].unique())

    data_dict = {solver: [] for solver in solvers}
    for _, group in df.groupby("uuid"):
        for solver in solvers:
            mask = group["solver"] == solver
            if mask.any():
                data_dict[solver].append(group.loc[mask, "solve-time-s"].iloc[0])

    # Plot small/large based on dataset name
    if dataset_name == "small":
        output_tikz = output_dir / "runtimes-small.pgf"
    else:
        output_tikz = output_dir / "runtimes-large.pgf"

    plot_sorted_runtimes(
        data_dict,
        ylabel="Runtime (s)",
        output_tikz=str(output_tikz),
        title=None,
        caption=f"Distribution of runtimes for the {dataset_name} instances",
    )


def generate_objective_plots(
    df,
    dataset_name: str,
    output_dir: Union[str, Path] = "output",
) -> None:
    """
    Generate objective ratio plots for a solver dataset, one line per solver.

    Args:
        df: Input DataFrame (solver experiment data).
        dataset_name: Name of the dataset (e.g., 'small', 'large').
        output_dir: Directory to save output files.
    """
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Get best objectives for each uuid
    best_obj = get_best_objectives(df)

    # Get unique solvers from the data
    solvers = sorted(df["solver"].unique())

    # Compute relative gaps for each solver
    data_dict = {solver: [] for solver in solvers}
    for solver in solvers:
        solver_df = df[df["solver"] == solver]
        # Get all objective values for this solver
        solver_objectives = [best_obj[uuid] for uuid in solver_df["uuid"].unique() if uuid in best_obj]
        # Compute relative gaps to minimum
        if solver_objectives:
            data_dict[solver] = relative_gap_min(solver_objectives)

    # Plot small/large based on dataset name
    if dataset_name == "small":
        output_tikz = output_dir / "objective-small.pgf"
    else:
        output_tikz = output_dir / "objective-large.pgf"

    plot_sorted_runtimes(
        data_dict,
        output_tikz=str(output_tikz),
        title=None,
        xlabel="Instance (sorted by ratio)",
        ylabel="Best-relative ratio",
        caption=f"Distribution of best-relative gaps for the {dataset_name} instances",
    )


def plot_scatter_best_solutions(
    df: pd.DataFrame,
    output_tikz: Optional[Union[str, Path]] = None,
    title: Optional[str] = "Best Solutions Scatter Plot",
    xlabel: str = "serviceFraction",
    ylabel: str = "insideTransferRatio",
    caption: Optional[str] = None,
) -> None:
    """
    Plot scatter plot of best solutions with serviceFraction vs insideTransferRatio.
    
    For each uuid, finds the best solution (minimum objective) and plots the
    serviceFraction (x-axis) against insideTransferRatio (y-axis).
    
    Args:
        df: Input DataFrame with solver experiment data.
        output_tikz: Path to save the TikZ/PGF file (e.g., 'scatter.pgf').
        title: Optional plot title.
        xlabel: X-axis label.
        ylabel: Y-axis label.
        caption: Optional caption for the plot.
    """
    from .common import retain_best_solutions
    
    # Ensure dataset_label column exists
    if "dataset_label" not in df.columns:
        df = df.copy()
        df["dataset_label"] = "dataset"
    
    # Get best solutions for each uuid
    best_df = retain_best_solutions(df)
    
    # Get unique datasets
    datasets = best_df["dataset_label"].unique().tolist()
    
    fig = plt.figure(figsize=(4, 4))
    
    # Color map for different datasets
    colors = plt.cm.tab10.colors  # Up to 10 colors
    
    for i, dataset in enumerate(datasets):
        dataset_df = best_df[best_df["dataset_label"] == dataset]
        
        x_values = dataset_df["serviceFraction"].values
        y_values = dataset_df["insideTransferRatio"].values
        
        plt.scatter(
            x_values,
            y_values,
            label=dataset,
            color=colors[i % len(colors)],
            marker='o',
            facecolors='none',  # Hollow marks
            edgecolors=colors[i % len(colors)],
            alpha=0.5,
            s=30,
        )
    
    plt.xlabel(xlabel)
    plt.ylabel(ylabel)
    plt.xlim(0, 1)
    plt.ylim(0, 1)
    plt.grid(True, linestyle="--", alpha=0.6)
    plt.legend()
    plt.tight_layout()
    
    if output_tikz:
        fig.savefig(output_tikz)
        print(f"PGF scatter plot saved to: {output_tikz}")
    else:
        plt.show()
