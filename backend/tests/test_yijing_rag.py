"""Tests for Yijing & Metaphysics RAG Service."""
import pytest
from backend.app.services.yijing_rag_service import (
    load_corpus,
    match_classics_evidence,
    format_rag_context_for_prompt,
)
from backend.app.models.bazi import ClassicsEvidenceModel


def test_load_corpus_integrity():
    corpus = load_corpus()
    assert len(corpus) >= 50, f"Expected at least 50 chunks, got {len(corpus)}"
    sample = corpus[0]
    assert "book" in sample
    assert "chapter" in sample
    assert "quote" in sample
    assert "vernacular" in sample
    assert "auspicious_numbers" in sample
    assert len(sample["auspicious_numbers"]) > 0


def test_match_classics_evidence_various_tickets():
    # Ticket 1: Water numbers
    ev1 = match_classics_evidence(["01", "06", "11", "16"], ["water"], ticket_index=0)
    assert isinstance(ev1, ClassicsEvidenceModel)
    assert ev1.book.startswith("《")
    assert len(ev1.auspicious_numbers) > 0
    assert ev1.category in ["hexagram", "qimen", "tiao_hou", "meihua"]

    # Ticket 2: Fire numbers
    ev2 = match_classics_evidence(["02", "07", "12", "17", "27"], ["fire"], ticket_index=1)
    assert isinstance(ev2, ClassicsEvidenceModel)
    assert ev2.book.startswith("《")
    assert len(ev2.auspicious_numbers) > 0

    # Rotation should ensure different categories or chapters
    assert ev1.category != ev2.category or ev1.chapter != ev2.chapter


def test_format_rag_context_for_prompt():
    ev = match_classics_evidence(["03", "08", "13", "18"], ["wood"], ticket_index=0)
    prompt_ctx = format_rag_context_for_prompt([ev], "甲", ["wood", "fire"])
    assert "【古籍绝学典籍引证与数理依据 (RAG Grounding)】" in prompt_ctx
    assert "甲" in prompt_ctx
    assert ev.book in prompt_ctx
    assert ev.chapter in prompt_ctx
