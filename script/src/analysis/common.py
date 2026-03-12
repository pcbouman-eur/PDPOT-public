# SPDX-License-Identifier: Apache-2.0
# Copyright (C) 2026  Paul Bouman, Rick Willemsen, Gizem Özbaygın

"""Common utilities and constants for pdpot-analyze."""

import pandas as pd
from pathlib import Path
from typing import Dict, List, Union

# =====================
# Constants
# =====================

# Data column labels
N_LABEL = "n_label"
PV_LABEL = "pv_label"
NPV_LABEL = "npv_label"

# Solver column
SOLVER_COLUMN = "solver"
REMAP_SOLVERS = {"ColgenSolverRoot": "CG", "MIPSolver-timeslice": "MIP"}

# Treatment column
TREATMENT_COL = "treatment"
REGULAR_TREATMENT = "Regular"
TREATMENT_MAP = {
    "FORBID_INSIDE_TRANSFERS": "No Inside Transfers",
    REGULAR_TREATMENT: REGULAR_TREATMENT,
}

# UUID columns
UUID_COL = "uuid"
DERIVED_COL = "derivedFrom"
FILE_COL = "filename"
ISO_FILE_COL = "iso_file"

# Base/Comparison labels
BASE_POSTFIX = "(base values)"
TO_INFIX = "to"

# Equal token for comparison
EQUAL_TOKEN = "Equal"

# =====================
# Data Loading
# =====================


def read_data(file_path: Union[str, Path]) -> pd.DataFrame:
    """
    Load an Excel dataset and add computed columns.

    Args:
        file_path: Path to the Excel file.

    Returns:
        DataFrame with additional computed columns (n_label, pv_label, npv_label).
    """
    data = pd.read_excel(file_path)

    # Add label columns
    data[N_LABEL] = data["networkSize"].astype(str) + "n"
    data[PV_LABEL] = (
        data["passengers"].astype(str) + "p," + data["vehicles"].astype(str) + "v"
    )
    data[NPV_LABEL] = data[N_LABEL] + "," + data[PV_LABEL]

    return data


def read_solver_data(file_path: Union[str, Path]) -> pd.DataFrame:
    """
    Load a solver experiment dataset and remap solver names.

    Args:
        file_path: Path to the Excel file.

    Returns:
        DataFrame with remapped solver names and label columns.
    """
    data = pd.read_excel(file_path)
    data[SOLVER_COLUMN] = data[SOLVER_COLUMN].map(REMAP_SOLVERS)

    # Add label columns
    data[N_LABEL] = data["networkSize"].astype(str) + "n"
    data[PV_LABEL] = (
        data["passengers"].astype(str) + "p," + data["vehicles"].astype(str) + "v"
    )
    data[NPV_LABEL] = data[N_LABEL] + "," + data[PV_LABEL]

    return data


def read_forbid_data(file_path: Union[str, Path]) -> pd.DataFrame:
    """
    Load a forbidden-variant dataset and remap treatment names.

    Args:
        file_path: Path to the Excel file.

    Returns:
        DataFrame with remapped treatment names and label columns.
    """
    data = pd.read_excel(file_path)
    data[TREATMENT_COL] = data[TREATMENT_COL].fillna(REGULAR_TREATMENT)
    data[TREATMENT_COL] = data[TREATMENT_COL].map(TREATMENT_MAP)

    # Add label columns
    data[N_LABEL] = data["networkSize"].astype(str) + "n"
    data[PV_LABEL] = (
        data["passengers"].astype(str) + "p," + data["vehicles"].astype(str) + "v"
    )
    data[NPV_LABEL] = data[N_LABEL] + "," + data[PV_LABEL]

    return data


def get_dataset_paths(data_dir: Union[str, Path]) -> Dict[str, Path]:
    """
    Get paths to all dataset files in a directory.

    Args:
        data_dir: Directory containing Excel files.

    Returns:
        Dictionary mapping dataset names to file paths.
    """
    data_dir = Path(data_dir)
    return {
        "small": data_dir / "output-small.xlsx",
        "large": data_dir / "output-large.xlsx",
        "all": data_dir / "output-all.xlsx",
        "forbid-small": data_dir / "output-forbid-small.xlsx",
        "forbid-large": data_dir / "output-forbid-large.xlsx",
        "forbid-all": data_dir / "output-forbid-all.xlsx",
    }


# =====================
# Data Processing Helpers
# =====================


def retain_best_solutions(df: pd.DataFrame) -> pd.DataFrame:
    """
    For each uuid, retain the row with the best solution.

    The best solution is defined as the one with:
    - Minimum objective
    - Maximum serviceFraction
    - Minimum numOutsideTransfers
    - Minimum numInsideTransfers

    Args:
        df: Input DataFrame.

    Returns:
        DataFrame with one row per uuid representing the best solution.
    """
    sorted_df = df.sort_values(
        by=[
            "uuid",
            "objective",
            "serviceFraction",
            "numOutsideTransfers",
            "numInsideTransfers",
        ],
        ascending=[True, True, False, True, True],
    )
    return sorted_df.drop_duplicates(subset="uuid", keep="first")


def ms_to_s(values: List[float]) -> List[float]:
    """Convert milliseconds to seconds."""
    return [v / 1000 for v in values]


def fraction_of_min(values: List[float]) -> List[float]:
    """Compute fraction of minimum value for each item in list."""
    min_val = min(values)
    return [v / min_val for v in values]


def relative_gap_min(values: List[float]) -> List[float]:
    """Compute relative gap to minimum value for each item in list."""
    best = min(values)
    return [(v - best) / best for v in values]


def get_best_objectives(df: pd.DataFrame) -> Dict[str, float]:
    """
    Get the best objective value for each uuid.

    Args:
        df: Input DataFrame with 'uuid' and 'objective' columns.

    Returns:
        Dictionary mapping uuid to best objective value.
    """
    best = df.groupby("uuid")["objective"].min()
    return best.to_dict()
