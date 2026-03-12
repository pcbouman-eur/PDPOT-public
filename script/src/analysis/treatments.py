# SPDX-License-Identifier: Apache-2.0
# Copyright (C) 2026  Paul Bouman, Rick Willemsen, Gizem Özbaygın

"""Treatment and pair comparison utilities for pdpot-analyze."""

from io import StringIO
from typing import Dict, List, Optional, Tuple

import numpy as np
import pandas as pd

from .common import (
    BASE_POSTFIX,
    DERIVED_COL,
    PV_LABEL,
    REGULAR_TREATMENT,
    TREATMENT_COL,
    TO_INFIX,
    UUID_COL,
)

# =====================
# Treatment Preparation
# =====================


def prepare_head_to_head_fname(
    df: pd.DataFrame, split: str = TREATMENT_COL
) -> Tuple[Dict, List[str]]:
    """
    Prepare pairwise head-to-head values by filename.

    Args:
        df: Input DataFrame.
        split: Column to use for grouping/splitting.

    Returns:
        Tuple of (pairwise dict, list of treatments).
    """
    pairwise = {}
    treatments = set()
    for id, group in df.groupby("iso_file"):
        pairdict = {}
        for _, row in group.iterrows():
            treatment = row[split]
            pairdict[treatment] = row
            treatments.add(treatment)
        pairwise[id] = pairdict
    return pairwise, list(treatments)


def prepare_head_to_head(
    df: pd.DataFrame, split: str = TREATMENT_COL
) -> Tuple[Dict, List[str]]:
    """
    Prepare pairwise head-to-head values by treatment.

    Args:
        df: Input DataFrame.
        split: Column to use for grouping/splitting.

    Returns:
        Tuple of (pairwise dict, list of treatments).
    """
    pairwise = {}
    treatments = {REGULAR_TREATMENT}

    regular_df = df[df[TREATMENT_COL] == REGULAR_TREATMENT]
    treatment_df = df[df[TREATMENT_COL] != REGULAR_TREATMENT]

    for _, row in regular_df.iterrows():
        uuid = row[UUID_COL]
        pairwise[uuid] = {REGULAR_TREATMENT: row}

    for _, row in treatment_df.iterrows():
        uuid = row[DERIVED_COL]
        if uuid not in pairwise:
            print("Unexpected: no regular treatment for uuid " + uuid)
            pairwise[uuid] = {}
        treatment = row[split]
        pairwise[uuid][treatment] = row
        treatments.add(treatment)

    return pairwise, list(treatments)


def prepare_distributions(
    h2h: Dict, cols: List[str]
) -> Dict[str, Dict[str, List[float]]]:
    """
    Prepare distributions from head-to-head data.

    Args:
        h2h: Head-to-head dictionary.
        cols: Columns to extract.

    Returns:
        Nested dictionary of distributions.
    """
    result = {}
    for _, group in h2h.items():
        for treatment, row in group.items():
            if treatment not in result:
                result[treatment] = {}
            for col in cols:
                if col not in result[treatment]:
                    result[treatment][col] = []
                result[treatment][col].append(row[col])
    return result


def prepare_delta_distributions(
    h2h: Dict, cols: List[str]
) -> Dict[str, Dict[str, List[float]]]:
    """
    Prepare delta distributions from head-to-head data.

    Args:
        h2h: Head-to-head dictionary.
        cols: Columns to compute deltas for.

    Returns:
        Nested dictionary of delta distributions.
    """
    result = {}
    for _, group in h2h.items():
        regular_row = group[REGULAR_TREATMENT]
        for treatment, row in group.items():
            if treatment == REGULAR_TREATMENT:
                label = treatment + " " + BASE_POSTFIX
            else:
                label = REGULAR_TREATMENT + " " + TO_INFIX + " " + treatment
            if label not in result:
                result[label] = {}
            for col in cols:
                if col not in result[label]:
                    result[label][col] = []
                if treatment == REGULAR_TREATMENT:
                    result[label][col].append(row[col])
                else:
                    delta = row[col] - regular_row[col]
                    result[label][col].append(delta)
    return result


# =====================
# Helper functions for LaTeX generation
# =====================


def _write_table_header(
    write,
    metrics: List[str],
    stat_labels: List[str] = ["Mean", "Min", "25\\%", "50\\%", "75\\%", "Max"],
) -> Tuple[int, int]:
    """
    Write the header part of a table section.
    
    Args:
        write: Write function.
        metrics: List of metric names.
        stat_labels: Labels for statistics.
    
    Returns:
        Tuple of (start column for first metric, total columns).
    """
    num_stats = len(stat_labels)
    
    # First header row
    write(" & ")
    for metric in metrics:
        write(f"& \\multicolumn{{{num_stats}}}{{c}}{{{metric}}} ")
    write("\\\\\n")
    
    # cmidrules
    col_start = 3
    for _ in metrics:
        col_end = col_start + num_stats - 1
        write(f"\\cmidrule(lr){{{col_start}-{col_end}}} ")
        col_start = col_end + 1
    write("\n")
    
    # Second header row
    write("Treatment & Count & " + " & ".join(stat_labels * len(metrics)))
    write(" \\\\\n")
    write("\\midrule\n")
    
    return 3, len(metrics) * num_stats


def _write_data_rows(
    write,
    h2h: Dict,
    metrics: List[str],
    stat_labels: List[str] = ["Mean", "Min", "25%", "50%", "75%", "Max"],
    include_delta: bool = True,
) -> None:
    """
    Write data rows for Regular, Forbid, and optionally Delta.
    
    Args:
        write: Write function.
        h2h: Head-to-head dictionary.
        metrics: List of metric names.
        stat_labels: Labels for statistics.
        include_delta: Whether to include Delta row.
    """
    # Get distributions
    dists = prepare_distributions(h2h, metrics)
    delta_dists = prepare_delta_distributions(h2h, metrics)
    
    # Get counts (use labels from dists which have exact treatment names)
    count_regular = len(dists.get("Regular", {}).get(metrics[0], []))
    count_forbid = len(dists.get("No Inside Transfers", {}).get(metrics[0], []))
    
    # Check if we have enough data to generate rows
    has_regular = count_regular > 0
    has_forbid = count_forbid > 0
    
    # Write Regular row (base values)
    if has_regular:
        write("Regular (base values) & " + str(count_regular))
        _write_stats(write, dists, "Regular", metrics, stat_labels)
        write(" \\\\\n")
    
    # Write Forbid row (base values)
    if has_forbid:
        write("Forbid (base values) & " + str(count_forbid))
        _write_stats(write, dists, "No Inside Transfers", metrics, stat_labels)
        write(" \\\\\n")
    
    # Write Delta row (Forbid - Regular) only if both treatments exist
    if include_delta and has_regular and has_forbid:
        write(r"$\Delta$ (Forbid - Regular) & " + str(count_forbid))
        _write_stats(write, delta_dists, "Regular to No Inside Transfers", metrics, stat_labels)
        write(" \\\\\n")


def _write_stats(
    write,
    dists: Dict,
    label: str,
    metrics: List[str],
    stat_labels: List[str] = ["Mean", "Min", "25%", "50%", "75%", "Max"],
) -> None:
    """
    Write statistics for a given treatment.
    
    Args:
        write: Write function.
        dists: Distributions dictionary.
        label: Label for the treatment.
        metrics: List of metric names.
        stat_labels: Labels for statistics.
    """
    for metric in metrics:
        values = np.array(dists.get(label, {}).get(metric, []))
        stats = [
            np.nanmean(values),
            np.nanmin(values),
            np.nanpercentile(values, 25),
            np.nanpercentile(values, 50),
            np.nanpercentile(values, 75),
            np.nanmax(values),
        ]
        write(" & " + " & ".join(f"{s:.2f}" for s in stats))


# =====================
# LaTeX Generation
# =====================


def generate_latex_table(
    nested_dict: Dict[str, Dict[str, List[float]]],
    metrics: List[str],
    stat_labels: List[str] = ["Mean", "Min", "25\\%", "50\\%", "75\\%", "Max"],
    caption: Optional[str] = None,
) -> str:
    """
    Generate a LaTeX table from nested dictionary data.

    Args:
        nested_dict: Nested dictionary with treatment -> metric -> values.
        metrics: List of metric names.
        stat_labels: Labels for statistics.
        caption: Optional caption for the table.

    Returns:
        LaTeX table string.
    """
    output = StringIO()
    write = output.write

    # Header
    write("\\begin{landscape}\\begin{table}\\centering\n")
    if caption:
        write("\\caption{" + caption + "}\n")
    write(
        "\\begin{tabular}{lr"
        + "r" * (len(metrics) * len(stat_labels))
        + "}\n"
    )
    write("\\toprule\n")

    # First header row
    write(" & ")
    for metric in metrics:
        write(f"& \\multicolumn{{{len(stat_labels)}}}{{c}}{{{metric}}} ")
    write("\\\\\n")

    # cmidrules
    col_start = 3
    for _ in metrics:
        col_end = col_start + len(stat_labels) - 1
        write(f"\\cmidrule(lr){{{col_start}-{col_end}}} ")
        col_start = col_end + 1
    write("\n")

    # Second header row
    write("Treatment & Count & " + " & ".join(stat_labels * len(metrics)))
    write(" \\\\\n")
    write("\\midrule\n")

    # Data rows
    for treatment, subdict in nested_dict.items():
        write_treatment_rows(write, subdict, treatment, metrics)

    # Footer
    write("\\bottomrule\n\\end{tabular}\n")
    write("\\end{table}\\end{landscape}\n")

    return output.getvalue()


def generate_delta_table(
    df: pd.DataFrame,
    caption: Optional[str] = None,
    delta_cols: List[str] = ["serviceFraction", "insideTransferRatio", "allPassengerTransportRatio", "servicedPassengerTransportRatio"],
    include_base: bool = True,
    float_format: str = "%.2f",
) -> str:
    """
    Generate a delta table comparing base and derived instances.
    
    This function follows the pattern from the reference code to compute pairwise
    deltas between derived instances and their base instances.
    
    For each derivedFrom group:
    - Base instance: uuid == derivedFrom (the instance's own derivedFrom points to itself)
    - Derived instances: uuid != derivedFrom (their derivedFrom points to a base instance)
    
    For each derived instance, computes (derived - base) for each metric.
    Base instances are labeled with BASE_POSTFIX (" base").
    
    Args:
        df: Input DataFrame with at least columns: uuid, derivedFrom, passengers, vehicles,
            serviceFraction, insideTransferRatio
        caption: Optional caption for the table.
        delta_cols: Columns to compute deltas for.
        include_base: Whether to include base instance values.
        group_label: Column to use for grouping/labeling (default: PV_LABEL)
        float_format: Format string for floating point values.
    
    Returns:
        LaTeX table string.
    """
    from io import StringIO
    

    # Add pv_label column if it doesn't exist (for compatibility with raw data)
    if PV_LABEL not in df.columns:
        df = df.copy()
        df[PV_LABEL] = df["passengers"].astype(str) + "p," + df["vehicles"].astype(str) + "v"
    
    # Get base and derived instances
    base_rows = df[df["uuid"] == df["derivedFrom"]]
    derived_rows = df[df["uuid"] != df["derivedFrom"]]
    
    # Create base lookup dictionary
    base_dict = {row["uuid"]: row for _, row in base_rows.iterrows()}
    
    # Check if base label is unique
    if base_rows[PV_LABEL].nunique() == 1:
        base_label = base_rows[PV_LABEL].unique()[0]
    else:
        base_label = None
    
    # Compute head-to-head deltas
    grouped = derived_rows.groupby("derivedFrom")
    result = {}

    order_tracking = {}
    # Include base instances
    if include_base:
        for _, row in base_rows.iterrows():
            label = f"$(\\mbox{{{row[PV_LABEL]}}})$ {BASE_POSTFIX}"
            if not label in result:
                result[label] = []
            row_data = {col: row[col] for col in delta_cols}
            result[label].append(row_data)
            order_tracking[row[PV_LABEL]] = [label]

    # Compute the delta values
    for derivedFrom, group in grouped:
        for _, row in group.iterrows():
            cmp_row = base_dict[derivedFrom]
            base_label = cmp_row[PV_LABEL]
            label = f"$\\Delta (\\mbox{{{row[PV_LABEL]}}} - \\mbox{{{base_label}}})$"
            
            if label not in result:
                result[label] = []
            row_data = {col: row[col] - cmp_row[col] for col in delta_cols}
            result[label].append(row_data)
            order_tracking[base_label].append(label)

    # Convert to DataFrame and compute summary statistics
    list_data = []
    for variant, data in result.items():
        for group_row in data:
            row = {"variant": variant, **group_row}
            list_data.append(row)
    frame = pd.DataFrame(list_data)

    # Group by variant and compute summary statistics
    features = [f for f in frame.columns if f != "variant"]
    
    cat_list = []
    
    # Count
    count_col = (
        frame.groupby("variant")[features[:1]]
        .agg("count")
        .rename(columns=lambda x: "Count")
    )
    cat_list.append(count_col)
    
    # Mean
    for feature in features:
        stats = frame.groupby("variant")[[feature]].agg("mean")
        cat_list.append(stats.rename(columns=lambda x: f"{x}_mean"))
    
    # Quantiles (0, 0.25, 0.5, 0.75, 1.0)
    quantiles = [0, 0.25, 0.5, 0.75, 1.0]
    quantile_labels = ["0%", "25%", "50%", "75%", "100%"]
    for feature in features:
        for q, label in zip(quantiles, quantile_labels):
            qgroup = (
                frame.groupby("variant")[[feature]]
                .quantile(q)
                .rename(columns=lambda x: f"{x}_{label}")
            )
            cat_list.append(qgroup)
    
    # Combine all into one DataFrame
    summary = pd.concat(cat_list, axis=1)
    summary.columns = [
        "_".join(col).strip() if isinstance(col, tuple) else col
        for col in summary.columns
    ]
    summary = summary.reset_index()
    
    # Generate LaTeX output manually to match expected format
    output = StringIO()
    write = output.write
    
    stat_labels = ["Mean", "Min", "25\\%", "50\\%", "75\\%", "Max"]
    num_stats = len(stat_labels)
    
    write("\\begin{landscape}\\begin{table}\\centering\n")
    if caption:
        write("\\caption{" + caption + "}\n")
    
    # Table format: 2 columns (variant, Count) + 2 metrics * 6 stats = 14 columns
    write("\\begin{tabular}{ll" + "r" * (2 * num_stats) + "}\n")
    write("\\toprule\n")
    
    # Header row 1: Distribution, Count, multicolumns for serviceFraction and insideTransferRatio
    write("\\multicolumn{2}{c}{} & \\multicolumn{6}{c}{serviceFraction} & \\multicolumn{6}{c}{insideTransferRatio} \\\\\n")
    
    # cmidrules from 3-8 (serviceFraction) and 9-14 (insideTransferRatio)
    write("\\cmidrule(lr){3-8} \\cmidrule(lr){9-14} \n")
    
    # Header row 2: variant, Count, then stats for each metric
    write("variant & Count & " + " & ".join(stat_labels * 2))
    write(" \\\\\n")
    for _, group_rows in order_tracking.items():
        base_variant = group_rows[0]
        delta_variants = group_rows[1:]
        base_summary = summary.loc[summary['variant'] == base_variant]
        delta_summary = summary[summary['variant'].isin(delta_variants)]
        write("\\midrule\n")
        write_delta_rows(base_summary, output, 'serviceFraction', 'insideTransferRatio', float_format)
        write("\\midrule\n")
        write_delta_rows(delta_summary, output, 'serviceFraction', 'insideTransferRatio', float_format)
    
    write("\\midrule\n")
    write("\\multicolumn{2}{c}{} & \\multicolumn{6}{c}{allPassengerTransportRatio} & \\multicolumn{6}{c}{servicedPassengerTransportRatio} \\\\\n")
    write("\\cmidrule(lr){3-8} \\cmidrule(lr){9-14} \n")
    write("variant & Count & " + " & ".join(stat_labels * 2))
    write(" \\\\\n")

    for _, group_rows in order_tracking.items():
        base_variant = group_rows[0]
        delta_variants = group_rows[1:]
        base_summary = summary.loc[summary['variant'] == base_variant]
        delta_summary = summary[summary['variant'].isin(delta_variants)]
        write("\\midrule\n")
        write_delta_rows(base_summary, output, 'allPassengerTransportRatio', 'servicedPassengerTransportRatio', float_format)
        write("\\midrule\n")
        write_delta_rows(delta_summary, output, 'allPassengerTransportRatio', 'servicedPassengerTransportRatio', float_format)

    # Footer
    write("\\bottomrule\n\\end{tabular}\n")
    write("\\end{table}\\end{landscape}\n")
    
    return output.getvalue()


def write_delta_rows(summary: pd.DataFrame,
                     output: StringIO,
                     feature1: str,
                     feature2: str,
                     float_format: str = "%.2f",
                     ) -> None:
    write = output.write

    # Data rows
    for _, row in summary.iterrows():
        variant = row["variant"]
        count = int(row["Count"])
        
        write(f"{variant} & {count}")
        
        # Stats for first feature
        write(f" & {float_format % row[f'{feature1}_mean']}")
        for q in ["0%", "25%", "50%", "75%", "100%"]:
            write(f" & {float_format % row[f'{feature1}_{q}']}")
        
        # Stats for second feature (if defined)
        if feature2:
            write(f" & {float_format % row[f'{feature2}_mean']}")
            for q in ["0%", "25%", "50%", "75%", "100%"]:
                write(f" & {float_format % row[f'{feature2}_{q}']}")
        
        write(" \\\\\n")


def write_treatment_rows(
    write,
    subdict: Dict[str, List[float]],
    treatment: str,
    metrics: List[str],
    stat_labels: List[str] = ["Mean", "Min", "25%", "50%", "75%", "Max"],
) -> None:
    """
    Write a single treatment row to the LaTeX table.

    Args:
        write: Write function (e.g., StringIO.write).
        subdict: Dictionary of metric -> values.
        treatment: Treatment name.
        metrics: List of metric names.
        stat_labels: Labels for statistics.
    """
    count = len(next(iter(subdict.values())))
    write(treatment + " & " + str(count))
    for metric in metrics:
        values = np.array(subdict[metric])
        stats = [
            np.nanmean(values),
            np.nanmin(values),
            np.nanpercentile(values, 25),
            np.nanpercentile(values, 50),
            np.nanpercentile(values, 75),
            np.nanmax(values),
        ]
        write(" & " + " & ".join(f"{s:.2f}" for s in stats))
    write(" \\\\\n")


def generate_latex_split_table(
    split_dict: Dict[str, Dict[str, Dict[str, List[float]]]],
    metrics: List[str],
    caption: Optional[str] = None,
) -> str:
    """
    Generate a split LaTeX table (one section per dataset).

    Args:
        split_dict: Nested dictionary with dataset -> treatment -> metric -> values.
        metrics: List of metric names.
        caption: Optional caption for the table.

    Returns:
        LaTeX table string.
    """
    output = StringIO()
    write = output.write

    # Header
    write("\\begin{landscape}\\begin{table}\\centering\n")
    if caption:
        write("\\caption{" + caption + "}\n")
    write(
        "\\begin{tabular}{lr"
        + "r" * (len(metrics) * 6)
        + "}\n"
    )
    write("\\toprule\n")

    # First header row
    write(" & ")
    for metric in metrics:
        write(f"& \\multicolumn{{6}}{{c}}{{{metric}}} ")
    write("\\\\\n")

    # cmidrules
    col_start = 3
    for _ in metrics:
        col_end = col_start + 5
        write(f"\\cmidrule(lr){{{col_start}-{col_end}}} ")
        col_start = col_end + 1
    write("\n")

    # Second header row
    write("Treatment & Count & Mean & Min & 25\\% & 50\\% & 75\\% & Max & Mean & Min & 25\\% & 50\\% & 75\\% & Max ")
    write(" \\\\\n")
    write("\\midrule\n")

    # Data rows
    for label, nested_dict in split_dict.items():
        write("\\textbf{" + label + "} \\\\\n")
        for treatment, subdict in nested_dict.items():
            write_treatment_rows(write, subdict, treatment, metrics)

    # Footer
    write("\\bottomrule\n\\end{tabular}\n")
    write("\\end{table}\\end{landscape}\n")

    return output.getvalue()


def print_tables_by_split(
    df: pd.DataFrame,
    splits: List[str],
    cols: List[str],
    use_delta: bool = False,
    caption: Optional[str] = None,
) -> str:
    """
    Generate tables split by multiple criteria.

    Args:
        df: Input DataFrame.
        splits: List of columns to split by.
        cols: Columns to include in tables.
        use_delta: Whether to compute deltas.
        caption: Optional caption for the table.

    Returns:
        LaTeX tables as string.
    """
    split_dict = {}
    for split in splits:
        for label, group in df.groupby(split):
            h2h, _ = prepare_head_to_head(group)
            if use_delta:
                dists = prepare_delta_distributions(h2h, cols)
            else:
                dists = prepare_distributions(h2h, cols)
            split_dict[label] = dists

    return generate_latex_split_table(split_dict, cols, caption)


def generate_comparison_table(
    df: pd.DataFrame,
    dataset_col: Optional[str] = None,
    caption: Optional[str] = None,
) -> str:
    """
    Generate a comparison table comparing Regular vs Forbid treatments.
    
    The table has two separate sections:
    1. numOutsideTransfers and serviceFraction (first half)
    2. allPassengerTransportRatio and servicedPassengerTransportRatio (second half)
    
    For each dataset, shows Regular, Forbid, and Delta (Forbid - Regular) rows.
    
    Args:
        df: Input DataFrame with Regular and Forbid treatments.
        dataset_col: Column containing dataset identifier. If None, treats all data as one dataset.
        caption: Optional caption for the table.
    
    Returns:
        LaTeX table string.
    """
    output = StringIO()
    write = output.write

    # First section metrics
    section1_metrics = ["numOutsideTransfers", "serviceFraction"]
    # Second section metrics
    section2_metrics = ["allPassengerTransportRatio", "servicedPassengerTransportRatio"]
    
    # Stat labels
    stat_labels = ["Mean", "Min", "25\\%", "50\\%", "75\\%", "Max"]
    
    # Determine dataset labels
    if dataset_col is None:
        dataset_labels = ["Combined"]
    else:
        dataset_labels = df[dataset_col].unique().tolist()
        
    # Column positions:
    # Columns 1-2: Treatment, Count
    # Columns 3-8: numOutsideTransfers (6 stats)
    # Columns 9-14: serviceFraction (6 stats)
    
    # Section widths
    section1_cols = 14  # Treatment + Count + 2 metrics * 6 stats = 2 + 12
    
    # Header
    write("\\begin{landscape}\\begin{table}\\centering\n")
    if caption:
        write("\\caption{" + caption + "}\n")
    write("\\begin{tabular}{")
    # ll for Treatment, Count + r for each metric stat
    write("ll" + "r" * 12 + "}\n")
    write("\\toprule\n")

    # ======== HEADER ROW 1 ========
    # Header row 1: multi-cols for section 1 (two 6-col multi-cols)
    write("\\multicolumn{2}{c}{} & \\multicolumn{6}{c}{numOutsideTransfers} & \\multicolumn{6}{c}{serviceFraction} \\\\\n")
    
    # ======== HEADER ROW 2 (cmidrules) ========
    # cmidrules from 3-8 (numOutsideTransfers), 9-14 (serviceFraction)
    # Skip 2 columns, then cmidrules from 17-22 (allPassengerTransportRatio), 23-28 (servicedPassengerTransportRatio)
    write("\\cmidrule(lr){3-8} \\cmidrule(lr){9-14} \n")
    
    # Second header row
    write("Treatment & Count & Mean & Min & 25\\% & 50\\% & 75\\% & Max & Mean & Min & 25\\% & 50\\% & 75\\% & Max \\\\\n")
    write("\\midrule\n")
    
    # ======== DATA ROWS ========
    for dataset_label in dataset_labels:
        if dataset_col is None:
            dataset_df = df
        else:
            dataset_df = df[df[dataset_col] == dataset_label]
        
        h2h, _ = prepare_head_to_head(dataset_df)
        
        # Get all UUIDs in this dataset
        uuids = list(h2h.keys())
        
        if not uuids:
            continue
        
        # Write dataset label as spanning row (spanning section 1 columns, left aligned)
        write(f"\\multicolumn{{{14}}}{{l}}{{\\textbf{{{dataset_label}}}}} \\\\\n")
        
        # Write data rows for section 1
        _write_data_rows(write, h2h, section1_metrics, stat_labels, include_delta=True)
        
        # Write data rows for section 2
        # Add midrule before section 2
    write("\\midrule\n")
    write("\\multicolumn{2}{c}{} & \\multicolumn{6}{c}{allPassengerTransportRatio} & \\multicolumn{6}{c}{servicedPassengerTransportRatio} \\\\\n")
    write("\\cmidrule(lr){3-8} \\cmidrule(lr){9-14} \n")
    write("Treatment & Count & Mean & Min & 25\\% & 50\\% & 75\\% & Max & Mean & Min & 25\\% & 50\\% & 75\\% & Max \\\\\n")
    write("\\midrule\n")
    for dataset_label in dataset_labels:
        if dataset_col is None:
            dataset_df = df
        else:
            dataset_df = df[df[dataset_col] == dataset_label]
        
        h2h, _ = prepare_head_to_head(dataset_df)
        
        # Get all UUIDs in this dataset
        uuids = list(h2h.keys())
        
        if not uuids:
            continue
        
        # Write dataset label as spanning row (spanning section 1 columns, left aligned)
        write(f"\\multicolumn{{{14}}}{{l}}{{\\textbf{{{dataset_label}}}}} \\\\\n")

        # Write data rows for section 2
        _write_data_rows(write, h2h, section2_metrics, stat_labels, include_delta=True)
        
        # Add midrule after each dataset section

    # Footer
    write("\\bottomrule\n\\end{tabular}\n")
    write("\\end{table}\\end{landscape}\n")

    return output.getvalue()
