# SPDX-License-Identifier: Apache-2.0
# Copyright (C) 2026  Paul Bouman, Rick Willemsen, Gizem Özbaygın

"""LaTeX table generation utilities for pdpot-analyze."""

import math
from collections import Counter
from itertools import combinations
from typing import Dict, List, Optional, Tuple

import numpy as np
import pandas as pd

from .common import (
    EQUAL_TOKEN,
    SOLVER_COLUMN,
)


def minimize_cmp(
    label1: str, val1: float, label2: str, val2: float, threshold: float = 1e-4
) -> str:
    r"""Compare two values and return the label of the better one (or 'Equal')."""
    if abs(val1 - val2) <= threshold:
        return EQUAL_TOKEN
    if val1 < val2:
        return label1
    return label2


def prepare_head_to_head(
    df: pd.DataFrame, col: str = "objective", split: str = "solver"
) -> Tuple[Dict, set]:
    """
    Prepare pairwise head-to-head values for a given column.

    Args:
        df: Input DataFrame.
        col: Column to compare.
        split: Column to use for grouping/splitting.

    Returns:
        Tuple of (pairwise dict, set of unique labels).
    """
    grouped = df.groupby("uuid")
    pairwise = {}
    solvers = set()
    for uuid, group in grouped:
        pairwise[uuid] = {}
        for _, row in group.iterrows():
            solver = row[split]
            pairwise[uuid][solver] = row[col]
            solvers.add(solver)
    return pairwise, solvers


def get_best_objectives(
    df: pd.DataFrame, id_col: str = "uuid", obj_col: str = "objective"
) -> Dict[str, float]:
    """
    Get the best (minimum) objective for each uuid.

    Args:
        df: Input DataFrame.
        id_col: Column containing the identifier.
        obj_col: Column containing the objective value.

    Returns:
        Dictionary mapping uuid to best objective.
    """
    result = {}
    for _, row in df.iterrows():
        uuid = row[id_col]
        obj = row[obj_col]
        if uuid in result:
            result[uuid] = min(result[uuid], obj)
        else:
            result[uuid] = obj
    return result


def compare_methods_count(
    df: pd.DataFrame,
    col: str = "objective",
    split: str = "solver",
    cmp=minimize_cmp,
) -> Tuple[Counter, List[str]]:
    """
    Compare methods pairwise and count which is better.

    Args:
        df: Input DataFrame.
        col: Column to compare.
        split: Column to use for grouping/splitting.
        cmp: Comparison function.

    Returns:
        Tuple of (Counter of comparisons, list of category labels).
    """
    pairwise, solvers = prepare_head_to_head(df, col, split)

    categories = [solver + " Better" for solver in sorted(solvers)] + ["Equal"]
    result = {}
    for solver_pair in combinations(solvers, 2):
        solver1, solver2 = solver_pair
        count = Counter()
        for _, group in pairwise.items():
            value1 = group[solver1]
            value2 = group[solver2]
            label1 = solver1 + " Better"
            label2 = solver2 + " Better"
            cmp_result = cmp(label1, value1, label2, value2)
            count.update([cmp_result])
        result[solver_pair] = count

    if len(result) == 1:
        for _, v in result.items():
            return v, categories

    return result, categories


def compare_methods_dist(
    df: pd.DataFrame, col: str = "objective", split: str = "solver", proc=None
) -> Dict[str, List[float]]:
    """
    Get distribution of values for each method.

    Args:
        df: Input DataFrame.
        col: Column to compare.
        split: Column to use for grouping/splitting.
        proc: Optional processing function to apply to values.

    Returns:
        Dictionary mapping solver to list of values.
    """
    pairwise, solvers = prepare_head_to_head(df, col, split)

    result = {solver: [] for solver in solvers}
    for _, group in pairwise.items():
        values = [group[solver] for solver in solvers]
        if proc:
            values = proc(values)
        for solver, value in zip(solvers, values):
            result[solver].append(value)
    return result


def get_best_relative_gaps(
    df: pd.DataFrame,
    best_obj: Dict[str, float],
    id_col: str = "uuid",
    obj_col: str = "objective",
    split: str = "solver",
) -> Dict[str, List[float]]:
    """
    Compute relative gaps to best objective for each solver.

    Args:
        df: Input DataFrame.
        best_obj: Dictionary mapping uuid to best objective.
        id_col: Column containing the identifier.
        obj_col: Column containing the objective value.
        split: Column to use for grouping/splitting.

    Returns:
        Dictionary mapping solver to list of relative gaps.
    """
    result = {}
    for _, row in df.iterrows():
        solver = row[split]
        obj = row[obj_col]
        uuid = row[id_col]
        best = best_obj[uuid]
        relative_ratio = abs(obj - best) / max(abs(best), abs(obj))

        if relative_ratio < 0:
            print("**STRANGE**", obj, best, uuid, solver)

        if solver not in result:
            result[solver] = []
        result[solver].append(relative_ratio)
    return result


# ===================##
# Table Formatting ##
# ==============######


def format_booktabs_table(
    data: List[List[Counter]],
    dataset_labels: List[str],
    measurement_labels: List[str],
    cmp_labels: List[str],
    caption: Optional[str] = None,
    counts: Optional[List[int]] = None,
    include_count_column: bool = False,
) -> str:
    """
    Format data as a LaTeX booktabs table.

    Args:
        data: Nested list of Counter objects.
        dataset_labels: Labels for each dataset row.
        measurement_labels: Labels for each measurement column group.
        cmp_labels: Labels for comparison categories.
        caption: Optional caption for the table.
        counts: Optional list of counts to include as first column (if include_count_column is True).
        include_count_column: If True, include counts as first column with row labels in second column.

    Returns:
        LaTeX table string.
    """
    # Calculate number of columns per measurement
    num_cols_per_measurement = len(cmp_labels)
    
    headers = [
        r"\toprule",
    ]
    
    # First header row with multicolumn headers
    # Need to add & for each column before measurement groups (Dataset and Count columns)
    if include_count_column and counts is not None:
        # Two columns before measurements: Dataset and Count
        header_line1 = " & & " + " & ".join([f"\\multicolumn{{{num_cols_per_measurement}}}{{c}}{{{m}}}" for m in measurement_labels])
    else:
        # One column before measurement: Dataset
        header_line1 = " & " + " & ".join([f"\\multicolumn{{{num_cols_per_measurement}}}{{c}}{{{m}}}" for m in measurement_labels])
    header_line1 += r" \\"
    headers.append(header_line1)
    
    # Generate cmidrules between measurement groups
    # The "Dataset" and "Count" columns are at positions 1-2, so measurement groups start at position 3
    # We need to offset the cmidrules by 2 to account for the "Dataset" and "Count" columns
    header_widths = [(num_cols_per_measurement, label) for label in measurement_labels]
    cmidrules = generate_cmidrules(header_widths, offset=2)
    headers.append(cmidrules)
    
    # Second header row with comparison labels
    if include_count_column and counts is not None:
        # First column is Dataset label, second is Count, then comparison columns
        header_line2 = "Dataset & Count & " + " & ".join(cmp_labels * len(measurement_labels))
    elif counts is not None:
        # First column is Count (replaces Dataset label), then comparison labels
        header_line2 = "Count" + " & " + " & ".join(cmp_labels * len(measurement_labels))
    else:
        header_line2 = "Dataset" + " & " + " & ".join(cmp_labels * len(measurement_labels))
    header_line2 += r" \\"
    headers.append(header_line2)
    
    headers.append(r"\midrule")

    rows = []
    for i, (name, counters) in enumerate(zip(dataset_labels, data)):
        row = []
        if include_count_column and counts is not None:
            row.append(name)  # Dataset label in first column
            row.append(str(counts[i]))  # Count in second column
        elif counts is not None:
            row.append(str(counts[i]))
        else:
            row = [name]
        for counter in counters:
            for label in cmp_labels:
                row.append(str(counter.get(label, 0)))
        rows.append(" & ".join(row) + r" \\")
    table_body = "\n".join(headers + rows + [r"\bottomrule"])

    latex_lines = [
        r"\begin{landscape}\begin{table}\centering",
    ]
    
    if caption:
        latex_lines.append(r"\caption{" + caption + "}")
    
    if include_count_column and counts is not None:
        num_cols = 2 + len(cmp_labels) * len(measurement_labels)
    else:
        num_cols = 1 + len(cmp_labels) * len(measurement_labels)
    latex_lines.append(r"\begin{tabular}{ll" + "r" * (num_cols - 2) + "}")
    latex_lines.append(table_body)
    latex_lines.append(r"\end{tabular}")
    latex_lines.append(r"\end{table}\end{landscape}")
    
    return "\n".join(latex_lines)


def summarize_solver_stats(
    data_dict: Dict[str, List[float]],
    percentiles: List[int] = [0, 25, 50, 75, 100],
) -> str:
    """
    Compute summary statistics and generate LaTeX booktabs table.

    Args:
        data_dict: Keys are solver/method names, values are lists of numeric values.
        percentiles: List of percentiles to compute.

    Returns:
        LaTeX table string.
    """
    headers = ["Solver", "Mean"] + [f"{p}\\%" for p in percentiles]
    rows = []

    for solver, values in data_dict.items():
        values = np.array(values)
        mean = np.mean(values)
        perc_values = np.percentile(values, percentiles)
        row = [solver, f"{mean:.2f}"] + [f"{pv:.2f}" for pv in perc_values]
        rows.append(row)

    col_fmt = "l" + "r" * (len(headers) - 1)
    latex = []
    latex.append("\\begin{tabular}{" + col_fmt + "}")
    latex.append("\\toprule")
    latex.append(" & ".join(headers) + " \\\\")
    latex.append("\\midrule")
    for row in rows:
        latex.append(" & ".join(row) + " \\\\")
    latex.append("\\bottomrule")
    latex.append("\\end{tabular}")

    return "\n".join(latex)


def compute_gap_results(
    df: pd.DataFrame,
    split_label: Optional[str],
    solver: str = "MIP",
    col: str = "optimality-gap",
    thresholds: List[float] = [0.01, 1e-6],
) -> Tuple[List[List[str]], List[str]]:
    """
    Compute gap statistics for a given solver.

    Args:
        df: Input DataFrame.
        split_label: Column to split by, or None to treat all as one group.
        solver: Solver to filter by.
        col: Column containing the gap value.
        thresholds: Thresholds to count.

    Returns:
        Tuple of (table data, row labels).
    """
    sub_df = df[df[SOLVER_COLUMN] == solver]
    table = []
    rows = []
    if split_label:
        groups = sub_df.groupby(split_label)
    else:
        groups = sub_df.groupby(lambda _: 0)

    for label, group in groups:
        group_count = group.shape[0]
        matrix_row = []
        for threshold in thresholds:
            gap_count = group[group[col] < threshold].shape[0]
            matrix_row.append(f"{gap_count} / {group_count}")
        table.append(matrix_row)
        rows.append(label if split_label else "")
    return table, rows


def compute_table_matrix(
    df: pd.DataFrame,
    split_label: Optional[str],
    cols: List[str] = ["objective", "solve-time"],
) -> Tuple[List[List[Counter]], List[str], List[str]]:
    """
    Compute comparison matrix for multiple columns.

    Args:
        df: Input DataFrame.
        split_label: Column to split by, or None.
        cols: Columns to compare.

    Returns:
        Tuple of (matrix, row labels, comparison categories).
    """
    rows = []
    matrix = []
    compare_cols = set()

    if split_label:
        groups = df.groupby(split_label)
    else:
        groups = df.groupby(lambda _: 0)

    for label, group in groups:
        matrix_row = []
        for col in cols:
            cell, c = compare_methods_count(group, col=col)
            matrix_row.append(cell)
            compare_cols.update(c)
        rows.append(label if split_label else "")
        matrix.append(matrix_row)

    if "Equal" in compare_cols:
        compare_cols.remove("Equal")
        compare_cols = list(sorted(compare_cols)) + ["Equal"]
    else:
        compare_cols = list(sorted(compare_cols))

    return matrix, rows, compare_cols


def compute_gap_and_compare_matrix(
    df: pd.DataFrame,
    split_label: Optional[str],
    measurement_labels: List[str],
    gap_solver: str = "MIP",
    gap_col: str = "optimality-gap",
    gap_threshold: List[float] = [0.01, 1e-6],
    cmp_cols: List[str] = ["objective", "solve-time"],
    row_header: str = "Dataset",
    gap_header: str = "MIP gap",
) -> Tuple[List[List[str]], List[Tuple[int, str]], List[str]]:
    """
    Compute combined gap and comparison matrix.

    Args:
        df: Input DataFrame.
        split_label: Column to split by, or None.
        measurement_labels: Labels for comparison columns.
        gap_solver: Solver to use for gap computation.
        gap_col: Column containing gap values.
        gap_threshold: Thresholds for gap comparison.
        cmp_cols: Columns for comparison.
        row_header: Header for row labels.
        gap_header: Header for gap section.

    Returns:
        Tuple of (result matrix, header info, column labels).
    """
    gap_table, gap_labels = compute_gap_results(
        df, split_label, gap_solver, gap_col, gap_threshold
    )
    cmp_table, cmp_labels, cmp_cats = compute_table_matrix(df, split_label, cmp_cols)

    if len(gap_labels) != len(cmp_labels):
        raise Exception(
            "This should not happen; when grouping with the same split label, "
            "we should keep the same number of groups."
        )

    res_header = [
        (1, row_header),
        (len(gap_threshold), gap_header),
    ] + [(len(cmp_cats), label) for label in measurement_labels]

    res_cols = (
        [""]
        + [format_threshold(x) for x in gap_threshold]
        + cmp_cats * len(measurement_labels)
    )

    res_matrix = []
    for gap_label, cmp_label, gap_row, cmp_row in zip(
        gap_labels, cmp_labels, gap_table, cmp_table
    ):
        if gap_label != cmp_label:
            raise Exception(
                "This is unexpected - the labels of the split groups were "
                "assumed to be in a consistent order"
            )
        res_row = [gap_label] + gap_row
        for counter in cmp_row:
            for col in cmp_cats:
                res_row.append(str(counter[col]))
        res_matrix.append(res_row)

    return res_matrix, res_header, res_cols


def format_threshold(x: float, decimals: int = 2, prefix: str = "<") -> str:
    """Format a threshold value for LaTeX."""
    if x >= 10**-decimals:
        return f"${prefix} {x:.{decimals}f}$"
    else:
        exponent = int(math.floor(math.log10(x)))
        return rf"${prefix} 10^{{{exponent}}}$"


def generate_cmidrules(width_header_pairs: List[Tuple[int, str]], offset: int = 0) -> str:
    r"""
    Generate \cmidrule commands for a LaTeX table.

    Args:
        width_header_pairs: List of (width, header) tuples.
        offset: Number of columns to skip before starting cmidrules (e.g., 1 for a label column).

    Returns:
        String of cmidrule commands.
    """
    cmidrules = []
    position = 1 + offset  # Start position after offset

    total_width = sum(width for width, _ in width_header_pairs)

    for width, _ in width_header_pairs:
        if width > 1:
            start = position
            end = position + width - 1

            left = "" if start == 1 + offset else "l"
            right = "" if end == total_width + offset else "r"
            trim = f"{left}{right}"

            cmidrules.append(rf"\cmidrule({trim}){{{start}-{end}}}")

        position += width

    return " ".join(cmidrules)


def gap_and_compare_to_latex(
    matrix: List[List[str]],
    header: List[Tuple[int, str]],
    cols: List[str],
    fontsize: str = "",
    caption: Optional[str] = None,
) -> str:
    """
    Format gap and comparison matrix as LaTeX table.

    Args:
        matrix: Table data.
        header: Header info (width, label pairs).
        cols: Column labels.
        fontsize: Optional font size command.
        caption: Optional caption for the table.

    Returns:
        LaTeX table string.
    """
    latex = []
    latex.append("\\begin{landscape}\\begin{table}\\centering")
    if caption:
        latex.append("\\caption{" + caption + "}")
    latex.append("{" + fontsize)
    latex.append("\\begin{tabular}{l" + "r" * (len(cols) - 1) + "}")
    latex.append("\\toprule")
    header_line = []
    for width, head in header:
        if width == 1:
            header_line.append(head)
        else:
            header_line.append(f"\\multicolumn{{{width}}}{{c}}{{{head}}}\\")
    latex.append(" & ".join(header_line) + "\\\\")
    latex.append(generate_cmidrules(header))
    latex.append(" & ".join(cols) + "\\\\")
    latex.append("\\midrule")
    for row in matrix:
        latex.append(" & ".join(row) + "\\\\")
    latex.append("\\bottomrule")
    latex.append("\\end{tabular}")
    latex.append("}")
    latex.append("\\end{table}\\end{landscape}")

    return "\n".join(latex)


def gap_and_compare_to_latex_with_sections(
    section1_rows: List[List[str]],
    section2_rows: List[List[str]],
    section3_rows: List[List[str]],
    header: List[Tuple[int, str]],
    cols: List[str],
    fontsize: str = "",
    caption: Optional[str] = None,
    dataset_header: str = "Dataset",
) -> str:
    """
    Format gap and comparison matrix as LaTeX table with three sections:
    1. Full Datasets (no split)
    2. Datasets split by passengers (p) and vehicles (v)
    3. Datasets split by network size (n)

    Each section has a section header row with bold text, separated by \\midrule.

    Args:
        section1_rows: Rows for section 1 (full datasets).
        section2_rows: Rows for section 2 (p/v split).
        section3_rows: Rows for section 3 (network size split).
        header: Header info (width, label pairs).
        cols: Column labels.
        fontsize: Optional font size command.
        caption: Optional caption for the table.
        dataset_header: Header for the dataset column (default: "Dataset").

    Returns:
        LaTeX table string.
    """
    latex = []
    latex.append("\\begin{landscape}\\begin{table}\\centering")
    if caption:
        latex.append("\\caption{" + caption + "}")
    latex.append("{" + fontsize)
    latex.append("\\begin{tabular}{l" + "r" * (len(cols) - 1) + "}")
    latex.append("\\toprule")
    header_line = []
    for width, head in header:
        if width == 1:
            header_line.append(head)
        else:
            header_line.append(f"\\multicolumn{{{width}}}{{c}}{{{head}}}")
    latex.append(" & ".join(header_line) + "\\\\")
    latex.append(generate_cmidrules(header))
    latex.append(" & ".join(cols) + "\\\\")
    latex.append("\\midrule")
    
    # Section 1: Full Datasets
    latex.append(r"\multicolumn{" + str(len(cols)) + r"}{l}{\textbf{" + dataset_header + r"}}\\")
    for row in section1_rows:
        latex.append(" & ".join(row) + "\\\\")
    
    # Section 2: Datasets split by p/v
    latex.append(r"\midrule")
    latex.append(r"\multicolumn{" + str(len(cols)) + r"}{l}{\textbf{Datasets split by passengers (p) and vehicles (v)}}\\")
    for row in section2_rows:
        latex.append(" & ".join(row) + "\\\\")
    
    # Section 3: Datasets split by network size (n)
    latex.append(r"\midrule")
    latex.append(r"\multicolumn{" + str(len(cols)) + r"}{l}{\textbf{Datasets split by network size (n)}}\\")
    for row in section3_rows:
        latex.append(" & ".join(row) + "\\\\")
    
    latex.append("\\bottomrule")
    latex.append("\\end{tabular}")
    latex.append("}")
    latex.append("\\end{table}\\end{landscape}")

    return "\n".join(latex)


def print_split_tables(
    dfs: List[pd.DataFrame],
    labels: List[str],
    categories: List[str],
) -> str:
    """
    Print tables for multiple dataframes and split labels.

    Args:
        dfs: List of DataFrames.
        labels: Labels to split by.
        categories: Categories for comparison.

    Returns:
        LaTeX tables as string.
    """
    output = []
    for label in labels:
        for idx, df in enumerate(dfs):
            output.append(f"Table for Dataframe {idx} split by {label}")
            matrix, rows, compare_cols = compute_table_matrix(df, label)
            table = format_booktabs_table(matrix, rows, categories, compare_cols)
            output.append(table)
    return "\n\n".join(output)


def compute_gap_and_compare_matrix_with_splits(
    df: pd.DataFrame,
    measurement_labels: List[str],
    gap_solver: str = "MIP",
    gap_col: str = "optimality-gap",
    gap_threshold: List[float] = [0.01, 1e-6],
    cmp_cols: List[str] = ["objective", "solve-time"],
    dataset_label_col: str = "dataset_label",
    n_label_col: str = "n_label",
    pv_label_col: str = "pv_label",
) -> Tuple[List[List[str]], List[Tuple[int, str]], List[str]]:
    """
    Compute combined gap and comparison matrix with three sections:
    1. Full Datasets
    2. Datasets split by passengers (p) and vehicles (v)
    3. Datasets split by network size (n)

    Args:
        df: Input DataFrame.
        measurement_labels: Labels for comparison columns.
        gap_solver: Solver to use for gap computation.
        gap_col: Column containing gap values.
        gap_threshold: Thresholds for gap comparison.
        cmp_cols: Columns for comparison.
        dataset_label_col: Column containing dataset label.
        n_label_col: Column containing network size label (e.g., "6n").
        pv_label_col: Column containing p/v split label (e.g., "10p,5v").

    Returns:
        Tuple of (result matrix, header info, column labels).
    """
    res_matrix = []
    
    # Get all unique datasets
    if dataset_label_col in df.columns:
        datasets = df[dataset_label_col].unique().tolist()
    else:
        datasets = ["Dataset"]

    if len(datasets) == 0:
        datasets = ["Dataset"]

    # Section 1: Full Datasets (no split)
    # Get all UUIDs from the combined dataframe
    full_uuids = df["uuid"].unique().tolist() if len(datasets) > 1 else []
    
    # Collect rows for section 1: full dataset data
    section1_rows = []
    for dataset in datasets:
        dataset_df = df[df[dataset_label_col] == dataset] if dataset_label_col in df.columns else df
        if dataset_label_col in dataset_df.columns:
            dataset_df = dataset_df.drop(columns=[dataset_label_col])
        
        gap_table, gap_labels = compute_gap_results(
            dataset_df, None, gap_solver, gap_col, gap_threshold
        )
        cmp_table, cmp_labels, cmp_cats = compute_table_matrix(dataset_df, None, cmp_cols)
        
        if len(gap_table) > 0 and len(cmp_table) > 0:
            row = [dataset]
            row.extend(gap_table[0])
            for counter in cmp_table[0]:
                for col in cmp_cats:
                    row.append(str(counter[col]))
            section1_rows.append(row)

    # Section 2: Datasets split by p/v
    section2_rows = []
    for dataset in datasets:
        dataset_df = df[df[dataset_label_col] == dataset] if dataset_label_col in df.columns else df
        if dataset_label_col in dataset_df.columns:
            dataset_df = dataset_df.drop(columns=[dataset_label_col])
        
        # Group by p/v label
        if pv_label_col in dataset_df.columns:
            pv_groups = dataset_df.groupby(pv_label_col)
            for pv_label, group in pv_groups:
                gap_table, gap_labels = compute_gap_results(
                    group, None, gap_solver, gap_col, gap_threshold
                )
                cmp_table, cmp_labels, cmp_cats = compute_table_matrix(group, None, cmp_cols)
                
                if len(gap_table) > 0 and len(cmp_table) > 0:
                    # Format: "dataset_name Xp,Yv"
                    row = [f"{dataset} {pv_label}"]
                    row.extend(gap_table[0])
                    for counter in cmp_table[0]:
                        for col in cmp_cats:
                            row.append(str(counter[col]))
                    section2_rows.append(row)

    # Section 3: Datasets split by network size (n)
    section3_rows = []
    for dataset in datasets:
        dataset_df = df[df[dataset_label_col] == dataset] if dataset_label_col in df.columns else df
        if dataset_label_col in dataset_df.columns:
            dataset_df = dataset_df.drop(columns=[dataset_label_col])
        
        # Group by n label
        if n_label_col in dataset_df.columns:
            n_groups = dataset_df.groupby(n_label_col)
            for n_label, group in n_groups:
                gap_table, gap_labels = compute_gap_results(
                    group, None, gap_solver, gap_col, gap_threshold
                )
                cmp_table, cmp_labels, cmp_cats = compute_table_matrix(group, None, cmp_cols)
                
                if len(gap_table) > 0 and len(cmp_table) > 0:
                    # Format: "dataset_name Xn"
                    row = [f"{dataset} {n_label}"]
                    row.extend(gap_table[0])
                    for counter in cmp_table[0]:
                        for col in cmp_cats:
                            row.append(str(counter[col]))
                    section3_rows.append(row)

    # Combine all rows
    res_matrix = section1_rows + section2_rows + section3_rows

    # Build header info
    total_gap_cols = len(gap_threshold)
    total_cmp_cols = len(cmp_cats)

    # Calculate width for measurement labels
    # Each measurement has the same number of comparison columns
    measurement_width = total_cmp_cols

    res_header = [
        (1, "Dataset"),
        (total_gap_cols, "MIP gap"),
    ] + [(measurement_width, label) for label in measurement_labels]

    # Build column labels
    res_cols = (
        [""]
        + [format_threshold(x) for x in gap_threshold]
        + cmp_cats * len(measurement_labels)
    )

    return res_matrix, res_header, res_cols


def print_split_gap_tables(
    dfs: List[pd.DataFrame],
    labels: List[str],
    categories: List[str],
) -> str:
    """
    Print gap comparison tables for multiple dataframes and split labels.

    Args:
        dfs: List of DataFrames.
        labels: Labels to split by.
        categories: Categories for comparison.

    Returns:
        LaTeX tables as string.
    """
    output = []
    for label in labels:
        for idx, df in enumerate(dfs):
            output.append(f"Table for Dataframe {idx} split by {label}")
            matrix, col_header, cols = compute_gap_and_compare_matrix(
                df, label, categories
            )
            table = gap_and_compare_to_latex(matrix, col_header, cols)
            output.append(table)
    return "\n\n".join(output)
