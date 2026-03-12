# SPDX-License-Identifier: Apache-2.0
# Copyright (C) 2026  Paul Bouman, Rick Willemsen, Gizem Özbaygın

"""Entry point for running the package as a module."""

from .cli.main import main

if __name__ == "__main__":
    import sys

    sys.exit(main())
