"""Pytest configuration and environment fixtures."""
import sys
from pathlib import Path

# Add project root to sys.path so 'backend' package is always resolvable
ROOT_DIR = Path(__file__).resolve().parent.parent.parent
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))
