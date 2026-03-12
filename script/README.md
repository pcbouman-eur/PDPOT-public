# pdpot-analyze

Data analysis pipeline for PDPoT (Passenger-Delivery-on-the-Route) experiments.

## Project Structure

```
pdpot-analyze/
├── pyproject.toml      # Project dependencies and metadata
├── README.md           # This file
├── src/
│   ├── __init__.py
│   ├── __main__.py     # Entry point for `python -m pdpot`
│   ├── analysis/
│   │   ├── __init__.py
│   │   ├── common.py   # Shared data loading and processing utilities
│   │   ├── tables.py   # LaTeX table generation utilities
│   │   ├── plots.py    # Plot generation with matplotlib/tikz
│   │   └── treatments.py  # Treatment and pair comparison utilities
│   └── cli/
│       ├── __init__.py
│       └── main.py     # Command-line interface
├── data/               # Input Excel files (not included)
│   ├── output-small.xlsx
│   ├── output-large.xlsx
│   ├── output-all.xlsx
│   ├── output-forbid-small.xlsx
│   ├── output-forbid-large.xlsx
│   └── output-forbid-all.xlsx
└── output/             # Generated LaTeX tables and figures
```

## Installation

Install dependencies using `uv`:

```bash
uv sync
```

Or using pip:

```bash
pip install pandas numpy matplotlib
```

## Command-Line Interface

The CLI provides four main commands:

### `summary` - Print data summary

```bash
pdpot summary --dataset all
```

Options:
- `--dataset` - Choose dataset: `small`, `large`, or `all` (default: `all`)
- `--data-dir` - Directory containing input files (default: current directory)

### `tables` - Generate LaTeX tables for solver comparison

```bash
pdpot tables --dataset all
```

Generates:
- `solver-comparison.tex` - Solver comparison table
- `solver-comparison-npv.tex` - Solver comparison by network size and passenger-vehicle configuration
- `gap-comparison.tex` - Optimality gap comparison

### `plots` - Generate plots for solver analysis

```bash
pdpot plots --dataset small
```

Generates:
- `runtimes-{small,large}.pgf` - Sorted runtimes per solver (TikZ/PGF format)
- `objective-{small,large}.pgf` - Best-relative ratio plots (TikZ/PGF format)

### `forbid` - Generate LaTeX tables for forbidden-variant analysis

```bash
pdpot forbid --dataset all
```

Generates:
- `forbid-dataset.tex` - Tables split by dataset
- `forbid-pv.tex` - Tables split by passenger-vehicle configuration
- `forbid-alt.tex` - Alternative metrics tables
- `forbid-delta.tex` - Delta comparison tables

## Usage Examples

```bash
# Run all analysis on the small dataset
pdpot summary --dataset small
pdpot tables --dataset small
pdpot plots --dataset small

# Run all analysis on all datasets combined
pdpot summary --dataset all
pdpot tables --dataset all
pdpot forbid --dataset all
```

## Output Directory

All generated files are written to the `output/` directory. The directory is created if it doesn't exist.

## LaTeX Dependencies

The generated LaTeX tables use the `booktabs` package. Include this in your LaTeX document:

```latex
\usepackage{booktabs}
```

The plots are generated in TikZ/PGF format and require:

```latex
\usepackage{tikz}
\usepackage{pgfplots}
\pgfplotsset{compat=1.18}
```

## Adding New Features

1. Add new analysis functions to the appropriate module (`tables.py`, `plots.py`, `treatments.py`)
2. Add a new CLI subcommand in `src/cli/main.py`
3. Update the README with new commands and options

## License

This project is for internal research use only.