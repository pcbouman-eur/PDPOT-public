# SPDX-License-Identifier: Apache-2.0
# Copyright (C) 2026  Paul Bouman, Rick Willemsen, Gizem Özbaygın

"""Command-line interface entry point for pdpot-analyze."""

import argparse
import logging
import sys
from pathlib import Path
from typing import List, Tuple

import pandas as pd

from ..analysis import common, tables, plots, treatments

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)


def parse_dataset_arg(arg: str) -> Tuple[Path, str]:
    """
    Parse a dataset argument of the form 'filepath:name' or just 'filepath'.
    
    Args:
        arg: String in format 'filepath:name' or 'filepath'
    
    Returns:
        Tuple of (path to file, label for dataset)
    """
    parts = arg.split(":", 1)
    if len(parts) == 1:
        # Just filepath, use stem as name
        path = Path(arg)
        label = path.stem
    else:
        path = Path(parts[0])
        label = parts[1]
    return path, label


def load_datasets(data_dir: Path, dataset_args: List[str]) -> Tuple[pd.DataFrame, List[str]]:
    """
    Load datasets from file paths.
    
    Args:
        data_dir: Base directory for relative paths.
        dataset_args: List of dataset argument strings (filepath:name or filepath).
    
    Returns:
        Tuple of (combined DataFrame, list of dataset labels).
    """
    data_dir = Path(data_dir)
    dataframes = []
    labels = []
    
    for arg in dataset_args:
        path, label = parse_dataset_arg(arg)
        # Resolve relative paths
        if not path.is_absolute():
            path = data_dir / path
        if not path.exists():
            raise FileNotFoundError(f"Dataset file not found: {path}")
        
        df = common.read_solver_data(path)
        df["dataset_label"] = label
        dataframes.append(df)
        labels.append(label)
    
    if len(dataframes) == 1:
        return dataframes[0], labels
    return pd.concat(dataframes, ignore_index=True), labels


def add_dataset_arg(parser: argparse.ArgumentParser) -> None:
    """Add common dataset argument to parser."""
    parser.add_argument(
        "-d", "--dataset",
        action="append",
        type=str,
        help="Dataset to use. Format: 'filepath:name' or 'filepath'. Can be specified multiple times.",
    )


def add_output_dir_arg(parser: argparse.ArgumentParser) -> None:
    """Add common output directory argument to parser."""
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("output"),
        help="Directory to write output files",
    )


def add_data_dir_arg(parser: argparse.ArgumentParser) -> None:
    """Add common data directory argument to parser."""
    parser.add_argument(
        "--data-dir",
        type=Path,
        default=Path("."),
        help="Directory containing input Excel files",
    )


def cmd_tables(args: argparse.Namespace) -> int:
    """Generate LaTeX tables for solver comparison."""
    data_dir = args.data_dir
    output_dir = args.output_dir

    # Check if dataset arguments were provided
    if args.dataset is None:
        # Default to "all" behavior for backward compatibility
        df_small = common.read_solver_data(data_dir / "output-small.xlsx")
        df_large = common.read_solver_data(data_dir / "output-large.xlsx")
        df = pd.concat([df_small, df_large], ignore_index=True)
    else:
        # Use load_datasets for flexible dataset loading
        df, labels = load_datasets(data_dir, args.dataset)

    output_dir.mkdir(parents=True, exist_ok=True)

    # Generate and save tables
    categories = ["Objective", "Runtime"]
    compare_cols = ["CG Better", "MIP Better", "Equal"]

    # Table 1: Solver comparison with Count column
    if args.dataset is None:
        # Backward compatible: use combined small+large
        cmp_small_obj, _ = tables.compare_methods_count(df, col="objective")
        cmp_small_time, _ = tables.compare_methods_count(df, col="solve-time")

        # Get total count for combined data
        total_count = df["uuid"].nunique()
        
        table = tables.format_booktabs_table(
            [[cmp_small_obj, cmp_small_time]], ["small+large"], categories, compare_cols,
            caption="Solver comparison: objective and runtime",
            counts=[total_count],
            include_count_column=True
        )
        (output_dir / "solver-comparison.tex").write_text(table)
    else:
        # New flexible dataset support with Count column
        # Compute comparison for each dataset and get counts
        dataset_comparisons = []
        counts = []
        for dataset_label in labels:
            dataset_df = df[df["dataset_label"] == dataset_label]
            cmp_obj, _ = tables.compare_methods_count(dataset_df, col="objective")
            cmp_time, _ = tables.compare_methods_count(dataset_df, col="solve-time")
            dataset_comparisons.append([cmp_obj, cmp_time])
            counts.append(dataset_df["uuid"].nunique())
        
        table = tables.format_booktabs_table(
            dataset_comparisons, labels, categories, compare_cols,
            caption="Solver comparison: objective and runtime",
            counts=counts,
            include_count_column=True
        )
        (output_dir / "solver-comparison.tex").write_text(table)

    # Table 2: NPV split
    if args.dataset is None:
        # Backward compatible: use combined data with Count column
        # Read small and large datasets separately to determine dataset prefix
        df_small = common.read_solver_data(data_dir / "output-small.xlsx")
        df_large = common.read_solver_data(data_dir / "output-large.xlsx")
        
        # Get unique NPV labels from combined data
        npv_labels = df[common.NPV_LABEL].unique()
        
        # Build matrix and counts with dataset prefix
        matrix = []
        prefixed_rows = []
        counts = []
        
        for row_label in npv_labels:
            # Check which datasets have this NPV label
            small_count = df_small[df_small[common.NPV_LABEL] == row_label]["uuid"].nunique()
            large_count = df_large[df_large[common.NPV_LABEL] == row_label]["uuid"].nunique()
            
            # Create row for each dataset that has this NPV label
            if small_count > 0:
                # Get comparison data for small dataset with this NPV label
                small_df = df_small[df_small[common.NPV_LABEL] == row_label]
                cmp_obj, _ = tables.compare_methods_count(small_df, col="objective")
                cmp_time, _ = tables.compare_methods_count(small_df, col="solve-time")
                matrix.append([cmp_obj, cmp_time])
                prefixed_rows.append(f"small {row_label}")
                counts.append(small_count)
            
            if large_count > 0:
                # Get comparison data for large dataset with this NPV label
                large_df = df_large[df_large[common.NPV_LABEL] == row_label]
                cmp_obj, _ = tables.compare_methods_count(large_df, col="objective")
                cmp_time, _ = tables.compare_methods_count(large_df, col="solve-time")
                matrix.append([cmp_obj, cmp_time])
                prefixed_rows.append(f"large {row_label}")
                counts.append(large_count)
        
        # Use include_count_column=True to show both count and row label
        table2 = tables.format_booktabs_table(matrix, prefixed_rows, categories, compare_cols,
                                              caption="Solver comparison by NPV label",
                                              counts=counts,
                                              include_count_column=True)
        (output_dir / "solver-comparison-npv.tex").write_text(table2)
    else:
        # For flexible datasets, generate separate NPV tables per dataset
        # with dataset name prefixed to NPV labels and Count column
        for dataset_label, dataset_df in df.groupby("dataset_label"):
            matrix, rows, compare_cols = tables.compute_table_matrix(dataset_df, common.NPV_LABEL)
            
            # Get counts for each row
            counts = []
            for row_label in rows:
                count = dataset_df[dataset_df[common.NPV_LABEL] == row_label]["uuid"].nunique()
                counts.append(count)
            
            # Prefix dataset name to rows
            prefixed_rows = [f"{dataset_label} {row}" for row in rows]
            
            # Use include_count_column=True to show both count and row label
            table2 = tables.format_booktabs_table(matrix, prefixed_rows, categories, compare_cols,
                                                  caption=f"Solver comparison by NPV label ({dataset_label})",
                                                  counts=counts,
                                                  include_count_column=True)
            (output_dir / f"solver-comparison-npv-{dataset_label}.tex").write_text(table2)

    # Table 3: Gap and compare with sections (full datasets, p/v split, n split)
    if args.dataset is not None and len(labels) > 0:
        # Use the new function with section headers
        matrix, col_header, cols = tables.compute_gap_and_compare_matrix_with_splits(
            df, categories
        )
        
        # Get section rows
        section1_rows = []
        section2_rows = []
        section3_rows = []
        
        # Section 1: Full datasets
        for dataset_label in labels:
            dataset_df = df[df["dataset_label"] == dataset_label]
            dataset_df_no_label = dataset_df.drop(columns=["dataset_label"]) if "dataset_label" in dataset_df.columns else dataset_df
            gap_table, gap_labels = tables.compute_gap_results(dataset_df_no_label, None)
            cmp_table, cmp_labels, cmp_cats = tables.compute_table_matrix(dataset_df_no_label, None)
            
            if len(gap_table) > 0 and len(cmp_table) > 0:
                row = [dataset_label]
                row.extend(gap_table[0])
                for counter in cmp_table[0]:
                    for col in cmp_cats:
                        row.append(str(counter[col]))
                section1_rows.append(row)
        
        # Section 2: p/v split
        pv_label_col = "pv_label"
        n_label_col = "n_label"
        
        for dataset_label in labels:
            dataset_df = df[df["dataset_label"] == dataset_label]
            dataset_df_no_label = dataset_df.drop(columns=["dataset_label"]) if "dataset_label" in dataset_df.columns else dataset_df
            
            if pv_label_col in dataset_df_no_label.columns:
                pv_groups = dataset_df_no_label.groupby(pv_label_col)
                for pv_label, group in pv_groups:
                    gap_table, gap_labels = tables.compute_gap_results(group, None)
                    cmp_table, cmp_labels, cmp_cats = tables.compute_table_matrix(group, None)
                    
                    if len(gap_table) > 0 and len(cmp_table) > 0:
                        row = [f"{dataset_label} {pv_label}"]
                        row.extend(gap_table[0])
                        for counter in cmp_table[0]:
                            for col in cmp_cats:
                                row.append(str(counter[col]))
                        section2_rows.append(row)
        
        # Section 3: n split
        for dataset_label in labels:
            dataset_df = df[df["dataset_label"] == dataset_label]
            dataset_df_no_label = dataset_df.drop(columns=["dataset_label"]) if "dataset_label" in dataset_df.columns else dataset_df
            
            if n_label_col in dataset_df_no_label.columns:
                n_groups = dataset_df_no_label.groupby(n_label_col)
                for n_label, group in n_groups:
                    gap_table, gap_labels = tables.compute_gap_results(group, None)
                    cmp_table, cmp_labels, cmp_cats = tables.compute_table_matrix(group, None)
                    
                    if len(gap_table) > 0 and len(cmp_table) > 0:
                        row = [f"{dataset_label} {n_label}"]
                        row.extend(gap_table[0])
                        for counter in cmp_table[0]:
                            for col in cmp_cats:
                                row.append(str(counter[col]))
                        section3_rows.append(row)
        
        table3 = tables.gap_and_compare_to_latex_with_sections(
            section1_rows, section2_rows, section3_rows, col_header, cols,
            caption="Gap and comparison matrix with dataset sections"
        )
        (output_dir / "gap-comparison.tex").write_text(table3)

    logger.info(f"Tables written to {output_dir}")
    return 0


def cmd_plots(args: argparse.Namespace) -> int:
    """Generate plots for solver analysis."""
    data_dir = args.data_dir
    output_dir = args.output_dir

    # Load data based on dataset choice
    if args.dataset is None:
        # Default to "all" behavior for backward compatibility
        df_small = common.read_solver_data(data_dir / "output-small.xlsx")
        df_large = common.read_solver_data(data_dir / "output-large.xlsx")
        df = pd.concat([df_small, df_large], ignore_index=True)
        dataset_name = "all"
    else:
        # Use load_datasets for flexible dataset loading
        df, labels = load_datasets(data_dir, args.dataset)
        dataset_name = "+".join(labels) if len(labels) > 1 else labels[0]

    output_dir.mkdir(parents=True, exist_ok=True)

    # Prepare runtime data - split by solver
    pairwise, solvers = tables.prepare_head_to_head(df, col="solve-time")
    data_dict = {solver: [] for solver in solvers}
    for _, group in pairwise.items():
        for solver in solvers:
            if solver in group:
                data_dict[solver].append(group[solver] / 1000)  # ms to s

    # Generate runtime plot with caption
    plots.plot_sorted_runtimes(
        data_dict,
        ylabel="Runtime (s)",
        output_tikz=output_dir / f"runtimes-{dataset_name}.pgf",
        title=None,
        caption=f"Distribution of runtimes for the {dataset_name} instances",
    )

    # Generate objective plot - split by solver
    best_obj = tables.get_best_objectives(df)
    # Get relative gaps for each solver (not flattened)
    relative_gaps = tables.get_best_relative_gaps(df, best_obj)

    # Prepare data dict with solver names as keys
    objective_data_dict = {}
    for solver, gaps in relative_gaps.items():
        objective_data_dict[solver] = gaps

    # Generate objective plot with caption
    plots.plot_sorted_runtimes(
        objective_data_dict,
        output_tikz=output_dir / f"objective-{dataset_name}.pgf",
        title=None,
        xlabel="Instance (sorted by ratio)",
        ylabel="Best-relative ratio",
        caption=f"Distribution of best-relative gaps for the {dataset_name} instances",
    )

    logger.info(f"Plots written to {output_dir}")
    return 0


def cmd_scatter(args: argparse.Namespace) -> int:
    """Generate scatter plots for best solutions."""
    data_dir = args.data_dir
    output_dir = args.output_dir

    # Load data based on dataset choice
    if args.dataset is None:
        # Default to "all" behavior for backward compatibility
        df_small = common.read_solver_data(data_dir / "output-small.xlsx")
        df_large = common.read_solver_data(data_dir / "output-large.xlsx")
        df = pd.concat([df_small, df_large], ignore_index=True)
        dataset_name = "all"
    else:
        # Use load_datasets for flexible dataset loading
        df, labels = load_datasets(data_dir, args.dataset)
        dataset_name = "+".join(labels) if len(labels) > 1 else labels[0]

    output_dir.mkdir(parents=True, exist_ok=True)

    # Generate scatter plot
    plots.plot_scatter_best_solutions(
        df,
        output_tikz=output_dir / f"scatter-{dataset_name}.pgf",
        xlabel="serviceFraction",
        ylabel="servicedInsideTransferRatio",
        caption=f"Best solutions scatter plot for {dataset_name} instances",
    )

    logger.info(f"Scatter plot written to {output_dir}")
    return 0


def load_forbid_datasets(data_dir: Path, dataset_args: List[str]) -> Tuple[pd.DataFrame, List[str]]:
    """
    Load forbid datasets from file paths.
    
    Args:
        data_dir: Base directory for relative paths.
        dataset_args: List of dataset argument strings (filepath:name or filepath).
    
    Returns:
        Tuple of (combined DataFrame, list of dataset labels).
    """
    data_dir = Path(data_dir)
    dataframes = []
    labels = []
    
    for arg in dataset_args:
        path, label = parse_dataset_arg(arg)
        if not path.is_absolute():
            path = data_dir / path
        if not path.exists():
            raise FileNotFoundError(f"Dataset file not found: {path}")
        
        df = common.read_forbid_data(path)
        df["dataset_label"] = label
        dataframes.append(df)
        labels.append(label)
    
    if len(dataframes) == 1:
        return dataframes[0], labels
    return pd.concat(dataframes, ignore_index=True), labels


def cmd_forbid(args: argparse.Namespace) -> int:
    """Generate LaTeX tables for forbidden-variant analysis."""
    data_dir = args.data_dir
    output_dir = args.output_dir

    # Load data based on dataset choice
    if args.dataset is None:
        # Default to "all" behavior for backward compatibility
        df_small = common.read_forbid_data(data_dir / "output-forbid-small.xlsx")
        df_large = common.read_forbid_data(data_dir / "output-forbid-large.xlsx")
        df = pd.concat([df_small, df_large], ignore_index=True)
        df["dataset"] = df_small["networkSize"].apply(
            lambda x: "Small" if x == 6 else "Large"
        )
    else:
        # Use load_datasets for flexible dataset loading
        df, labels = load_datasets(data_dir, args.dataset)
        # Map forbid data to dataset labels
        df["dataset"] = df["networkSize"].apply(
            lambda x: "Small" if x == 6 else "Large"
        )

    output_dir.mkdir(parents=True, exist_ok=True)

    # Generate tables by split
    metrics = ["numOutsideTransfers", "serviceFraction"]
    alt_metrics = ["allPassengerTransportRatio", "servicedPassengerTransportRatio"]

    # Table 1: By dataset
    result = treatments.print_tables_by_split(df, ["dataset"], metrics, caption="Forbidden variants by dataset")
    (output_dir / "forbid-dataset.tex").write_text(result)

    # Table 2: By PV label
    result = treatments.print_tables_by_split(df, [common.PV_LABEL], metrics, caption="Forbidden variants by PV label")
    (output_dir / "forbid-pv.tex").write_text(result)

    # Table 3: Alt metrics
    result = treatments.print_tables_by_split(
        df, ["dataset", common.PV_LABEL], alt_metrics, caption="Alternative metrics comparison"
    )
    (output_dir / "forbid-alt.tex").write_text(result)

    # Table 4: Delta tables
    result = treatments.print_tables_by_split(
        df, ["dataset", common.PV_LABEL], metrics, use_delta=True, caption="Delta comparison of forbidden variants"
    )
    (output_dir / "forbid-delta.tex").write_text(result)

    logger.info(f"Forbidden tables written to {output_dir}")
    return 0


def cmd_delta_table(args: argparse.Namespace) -> int:
    """Generate delta table for derived instances analysis."""
    data_dir = args.data_dir
    output_dir = args.output_dir

    # Load data based on dataset choice
    if args.dataset is None:
        # Default to "all" behavior - load both files combined
        df_small = common.read_solver_data(data_dir / "output-small.xlsx")
        df_large = common.read_solver_data(data_dir / "output-large.xlsx")
        df = pd.concat([df_small, df_large], ignore_index=True)
    else:
        # Use load_datasets for flexible dataset loading
        df, labels = load_datasets(data_dir, args.dataset)

    # Preprocessing: retain best solution per uuid
    df = common.retain_best_solutions(df)

    output_dir.mkdir(parents=True, exist_ok=True)

    # Generate delta table
    result = treatments.generate_delta_table(df, caption="Delta table comparing base and derived instances")
    (output_dir / "delta.tex").write_text(result)

    logger.info(f"Delta table written to {output_dir}")
    return 0


def cmd_comparison(args: argparse.Namespace) -> int:
    """Generate comparison table for Regular vs Forbid treatments."""
    data_dir = args.data_dir
    output_dir = args.output_dir

    # Load data based on dataset choice
    if args.dataset is None:
        # Default to "all" behavior - load both forbid files combined, no split by networkSize
        df_small = common.read_forbid_data(data_dir / "output-forbid-small.xlsx")
        df_large = common.read_forbid_data(data_dir / "output-forbid-large.xlsx")
        df = pd.concat([df_small, df_large], ignore_index=True)
        # Use None to not split by networkSize
        dataset_col = None
    else:
        # Load data - use forbid data if available, otherwise convert regular data
        dataframes = []
        labels = []
        
        for arg in args.dataset:
            path, label = parse_dataset_arg(arg)
            if not path.is_absolute():
                path = data_dir / path
            if not path.exists():
                raise FileNotFoundError(f"Dataset file not found: {path}")
            
            # Try to read as forbid data first (has 'treatment' column)
            try:
                df = common.read_forbid_data(path)
            except KeyError:
                # Fall back to regular data - need to create treatment column
                df = common.read_solver_data(path)
                # Create treatment column based on allowInsideTransfers
                df["treatment"] = df["allowInsideTransfers"].apply(
                    lambda x: "Regular" if x else "No Inside Transfers"
                )
                # Apply treatment mapping
                df["treatment"] = df["treatment"].map(common.TREATMENT_MAP)
                
                # Check if this is a regular data file (only Regular treatment)
                treatment_values = df["treatment"].unique()
                if len(treatment_values) == 1 and treatment_values[0] == "Regular":
                    print(f"Warning: {path.name} only contains Regular treatment data.")
                    print("  The comparison table requires both Regular AND Forbid treatment data.")
                    print("  Please use the forbid dataset files (e.g., output-forbid-small.xlsx).")
            
            df["dataset_label"] = label
            dataframes.append(df)
            labels.append(label)
        
        if len(dataframes) == 1:
            df = dataframes[0]
        else:
            df = pd.concat(dataframes, ignore_index=True)
        
        # Use dataset_label as the split column
        dataset_col = "dataset_label"

    output_dir.mkdir(parents=True, exist_ok=True)

    # Generate comparison table
    result = treatments.generate_comparison_table(df, dataset_col=dataset_col, caption="Comparison of Regular vs Forbid treatments")
    (output_dir / "comparison.tex").write_text(result)

    logger.info(f"Comparison table written to {output_dir}")
    return 0


def cmd_latex(args: argparse.Namespace) -> int:
    """Generate a main.tex file that includes all generated tables/figures."""
    output_dir = args.output_dir

    # Find all generated files
    output_dir.mkdir(parents=True, exist_ok=True)
    tex_files = sorted(output_dir.glob("*.tex"))
    pgf_files = sorted(output_dir.glob("*.pgf"))

    # Generate LaTeX document
    lines = [
        r"\documentclass{article}",
        r"\usepackage{booktabs}",
        r"\usepackage{tikz}",
        r"\usepackage{pgfplots}",
        r"\pgfplotsset{compat=1.18}",
        r"\providecommand{\mathdefault}{}",
        r"\usepackage{graphicx}",
        r"\usepackage{caption}",
        r"\usepackage{pdflscape}",
        r"\usepackage{geometry}",
        r"\geometry{margin=1in}",
        r"\pagestyle{empty}",
        r"",
        r"\begin{document}",
        r"",
        r"\title{Analysis Results}",
        r"\author{}",
        r"\date{}",
        r"\maketitle",
        r"",
        r"\section{Introduction}",
        r"This document was automatically generated by pdpot-analyze.",
        r"It includes all generated tables and figures from the analysis.",
        r"",
    ]

    if not tex_files and not pgf_files:
        lines.extend([
            r"\section{No files found}",
            r"No .tex or .pgf files were found in the output directory.",
            r"Please run one of the analysis commands first (tables, plots, or forbid).",
        ])
    else:
        # Include .tex files (exclude main.tex to avoid infinite loop)
        # Tables use sidewaystable environment with caption inside
        for tex_file in tex_files:
            if tex_file.name == "main.tex":
                continue
            rel_path = tex_file.relative_to(output_dir)
            lines.extend([
                r"",
                r"\input{" + str(rel_path) + r"}",
                r"",
            ])

        # Include .pgf files
        for pgf_file in pgf_files:
            rel_path = pgf_file.relative_to(output_dir)
            stem = pgf_file.stem.replace("-", " ").replace("_", " ").title()
            lines.extend([
                r"",
                r"\begin{figure}[htbp]",
                r"    \centering",
                r"    \input{" + str(rel_path) + r"}",
                r"    \caption{" + stem + r"}",
                r"\end{figure}",
                r"",
            ])

    lines.extend([
        r"\end{document}",
    ])

    main_tex = output_dir / "main.tex"
    main_tex.write_text("\n".join(lines))
    logger.info(f"LaTeX main.tex written to {main_tex}")

    return 0


def cmd_summary(args: argparse.Namespace) -> int:
    """Print a summary of the data to stdout."""
    data_dir = args.data_dir

    # Load data based on dataset choice
    if args.dataset is None:
        # Default to "all" behavior for backward compatibility
        df_small = common.read_solver_data(data_dir / "output-small.xlsx")
        df_large = common.read_solver_data(data_dir / "output-large.xlsx")
        df = pd.concat([df_small, df_large], ignore_index=True)
        dataset_name = "all"
    else:
        # Use load_datasets for flexible dataset loading
        df, labels = load_datasets(data_dir, args.dataset)
        dataset_name = "+".join(labels) if len(labels) > 1 else labels[0]

    print(f"Dataset: {dataset_name}")
    print(f"Shape: {df.shape}")
    print(f"Solvers: {df[common.SOLVER_COLUMN].unique()}")
    print(f"UUIDs: {df['uuid'].nunique()}")
    print("\nObjective stats:")
    print(df["objective"].describe())
    print("\nSolve time stats:")
    print(df["solve-time"].describe())

    return 0


def main(argv: List[str] = sys.argv[1:]) -> int:
    """Main entry point for the CLI."""
    parser = argparse.ArgumentParser(
        prog="pdpot", description="Data analysis pipeline for pdpot experiments."
    )

    subparsers = parser.add_subparsers(dest="command", required=True)

    # Tables command
    tables_parser = subparsers.add_parser(
        "tables", help="Generate LaTeX tables for solver comparison"
    )
    add_dataset_arg(tables_parser)
    add_output_dir_arg(tables_parser)
    add_data_dir_arg(tables_parser)
    tables_parser.set_defaults(func=cmd_tables)

    # Plots command
    plots_parser = subparsers.add_parser(
        "plots", help="Generate plots for solver analysis"
    )
    add_dataset_arg(plots_parser)
    add_output_dir_arg(plots_parser)
    add_data_dir_arg(plots_parser)
    plots_parser.set_defaults(func=cmd_plots)

    # Scatter command
    scatter_parser = subparsers.add_parser(
        "scatter", help="Generate scatter plots for best solutions"
    )
    add_dataset_arg(scatter_parser)
    add_output_dir_arg(scatter_parser)
    add_data_dir_arg(scatter_parser)
    scatter_parser.set_defaults(func=cmd_scatter)

    # Forbid command
    forbid_parser = subparsers.add_parser(
        "forbid", help="Generate LaTeX tables for forbidden-variant analysis"
    )
    add_dataset_arg(forbid_parser)
    add_output_dir_arg(forbid_parser)
    add_data_dir_arg(forbid_parser)
    forbid_parser.set_defaults(func=cmd_forbid)

    # Delta table command
    delta_parser = subparsers.add_parser(
        "delta-table", help="Generate delta table for derived instances analysis"
    )
    add_dataset_arg(delta_parser)
    add_output_dir_arg(delta_parser)
    add_data_dir_arg(delta_parser)
    delta_parser.set_defaults(func=cmd_delta_table)

    # Comparison command
    comparison_parser = subparsers.add_parser(
        "comparison", help="Generate comparison table for Regular vs Forbid treatments"
    )
    add_dataset_arg(comparison_parser)
    add_output_dir_arg(comparison_parser)
    add_data_dir_arg(comparison_parser)
    comparison_parser.set_defaults(func=cmd_comparison)

    # LaTeX main.tex command
    latex_parser = subparsers.add_parser(
        "latex", help="Generate a main.tex that includes all generated tables/figures"
    )
    add_output_dir_arg(latex_parser)
    latex_parser.set_defaults(func=cmd_latex)

    # Summary command
    summary_parser = subparsers.add_parser(
        "summary", help="Print a summary of the data"
    )
    add_dataset_arg(summary_parser)
    add_data_dir_arg(summary_parser)
    summary_parser.set_defaults(func=cmd_summary)

    args = parser.parse_args(argv)

    try:
        return args.func(args)
    except Exception as e:
        logger.error(f"Error: {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())