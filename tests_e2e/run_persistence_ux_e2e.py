#!/usr/bin/env python3
"""
Standalone runner for the 4-Tier Persistence & UX E2E Test Suite.
Usage:
    python tests_e2e/run_persistence_ux_e2e.py
"""
import sys
from pathlib import Path

# Add project root to sys.path
PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from tests_e2e.test_persistence_ux_suite import run_all_persistence_ux_tests

if __name__ == "__main__":
    success = run_all_persistence_ux_tests()
    sys.exit(0 if success else 1)
