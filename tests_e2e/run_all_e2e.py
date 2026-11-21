"""Standalone entrypoint for running the entire E2E test suite.
Usage:
    python tests_e2e/run_all_e2e.py [--tier 1|2|3|4] [--verbose] [--report report.json]
"""
import sys
import argparse
import json
from pathlib import Path

# Add project root to sys.path
PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from tests_e2e.harness.runner import E2ERunner

def main():
    parser = argparse.ArgumentParser(description="Bazi Lottery Recommender E2E Test Suite Runner")
    parser.add_argument("--tier", type=int, choices=[1, 2, 3, 4, 5], default=None, help="Run only tests from specified tier")
    parser.add_argument("--verbose", "-v", action="store_true", help="Print verbose execution logs")
    parser.add_argument("--report", type=str, default=None, help="Path to write JSON execution report")

    args = parser.parse_args()

    runner = E2ERunner()
    success = runner.run(tier_filter=args.tier, verbose=args.verbose)

    if args.report:
        report_path = Path(args.report)
        report_data = {
            "passed": success,
            "total": len(runner.results),
            "results": [r.to_dict() for r in runner.results],
        }
        with open(report_path, "w", encoding="utf-8") as f:
            json.dump(report_data, f, ensure_ascii=False, indent=2)
        print(f"  [INFO] Report saved to {report_path.resolve()}")

    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()
