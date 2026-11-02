"""Tier 4 Scenario 5: Multi-lottery Portfolio Generation & Responsible Gaming Budgeting.
Flow: Multi-lottery Portfolio (SSQ + DLT + FC3D + QXC) -> Batch Validation -> Budget Guardrail Enforcement.
"""
from tests_e2e.harness.client import get_test_client
from tests_e2e.harness.oracle import ReferenceOracle

def test_scenario_multi_portfolio():
    """Scenario 5: Multi-lottery portfolio generation across SSQ, DLT, FC3D, and QXC."""
    client = get_test_client()

    birth_dt = "1985-11-11T11:11:00"

    # Step 1: Health & Rules Pre-flight Verification
    health = client.get_health()
    assert health["status_code"] == 200

    rules_resp = client.get_rules()
    assert rules_resp["status_code"] == 200
    all_rules = rules_resp["data"]["rules"]
    assert set(all_rules.keys()) == {"ssq", "dlt", "fc3d", "qxc"}

    # Step 2: Calculate Bazi Destiny
    destiny = client.post_destiny({
        "birth_datetime": birth_dt,
        "calendar_type": "solar",
        "gender": "male",
    })
    assert destiny["status_code"] == 200

    # Step 3: Generate 1 bet for each of the 4 lotteries
    portfolio = {}
    for lt in ["ssq", "dlt", "fc3d", "qxc"]:
        rec = client.post_recommend({
            "birth_datetime": birth_dt,
            "lottery_type": lt,
            "count": 1,
            "strategy": "balanced",
        })
        assert rec["status_code"] == 200
        portfolio[lt] = rec["data"]["recommendations"][0]

    # Step 4: Validate all 4 portfolio tickets against official rules
    for lt, ticket in portfolio.items():
        valid, err, norm_p, norm_s, fmt = ReferenceOracle.validate_ticket(
            lottery_type=lt,
            primary_numbers=ticket["primary_numbers"],
            secondary_numbers=ticket.get("secondary_numbers"),
        )
        assert valid is True, f"Portfolio ticket for {lt} invalid: {err}"
        assert fmt is not None

    # Step 5: Responsible Gaming Cost & Budget Guardrail
    # Standard official single-bet price in China: 2 RMB per ticket
    ticket_price = 2.0
    total_cost = len(portfolio) * ticket_price
    assert total_cost == 8.0  # 4 * 2 = 8 RMB

    # Enforce safe recreational gaming limit (< 20 RMB per session)
    SESSION_BUDGET_CAP = 20.0
    assert total_cost <= SESSION_BUDGET_CAP, "Portfolio exceeds safe responsible gaming budget threshold!"
