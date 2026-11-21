"""Yijing & Metaphysics RAG Service.
Loads curated classical literature (I Ching, Qimen Dunjia, Qingtong Baojian, Meihua Yishu),
and matches tickets with authentic classical evidence and grounded divination citations.
"""
import json
import logging
from pathlib import Path
from typing import List, Dict, Any, Optional

from backend.app.models.bazi import ClassicsEvidenceModel

logger = logging.getLogger(__name__)

CORPUS_PATH = Path(__file__).resolve().parent.parent / "data" / "classics_corpus.json"

_CACHED_CORPUS: Optional[List[Dict[str, Any]]] = None


def load_corpus() -> List[Dict[str, Any]]:
    """Load or return cached classics corpus."""
    global _CACHED_CORPUS
    if _CACHED_CORPUS is not None:
        return _CACHED_CORPUS

    if not CORPUS_PATH.exists():
        logger.warning(f"Classics corpus not found at {CORPUS_PATH}")
        _CACHED_CORPUS = []
        return _CACHED_CORPUS

    try:
        with open(CORPUS_PATH, "r", encoding="utf-8") as f:
            _CACHED_CORPUS = json.load(f)
            logger.info(f"Loaded {len(_CACHED_CORPUS)} classics corpus chunks.")
    except Exception as e:
        logger.error(f"Failed to load classics corpus: {e}")
        _CACHED_CORPUS = []

    return _CACHED_CORPUS


def match_classics_evidence(
    primary_numbers: List[str],
    favorable_elements: List[str],
    ticket_index: int = 0
) -> ClassicsEvidenceModel:
    """Match a lottery ticket to the most resonant classical text chunk.
    Rotates through distinct categories (Hexagram, Qimen, Tiao-Hou, Meihua) for multiple tickets.
    """
    corpus = load_corpus()
    if not corpus:
        # Fallback safe model
        return ClassicsEvidenceModel(
            book="《周易》",
            chapter="乾坤易理",
            quote="大象曰：天行健，君子以自强不息；地势坤，君子以厚德载物。",
            vernacular="顺应天地运行之道，刚柔并济，谋财有度，积善之家必有余庆。",
            auspicious_numbers=[1, 6, 8, 9],
            category="hexagram"
        )

    # Convert string numbers to ints
    int_nums = set()
    for n in primary_numbers:
        try:
            int_nums.add(int(n))
        except (ValueError, TypeError):
            pass

    # Target category rotation for diverse multi-bet tickets
    categories = ["hexagram", "qimen", "tiao_hou", "meihua"]
    target_cat = categories[ticket_index % len(categories)]

    best_chunk: Optional[Dict[str, Any]] = None
    best_overlap = -1

    # First pass: try matching within target category
    for chunk in corpus:
        if chunk.get("category") == target_cat:
            c_nums = set(chunk.get("auspicious_numbers", []))
            overlap = len(int_nums.intersection(c_nums))
            # Bonus if primary element matches user's favorable element
            if chunk.get("primary_element") in favorable_elements:
                overlap += 2
            if overlap > best_overlap:
                best_overlap = overlap
                best_chunk = chunk

    # Fallback to any category if no good match
    if best_chunk is None or best_overlap <= 0:
        for chunk in corpus:
            c_nums = set(chunk.get("auspicious_numbers", []))
            overlap = len(int_nums.intersection(c_nums))
            if overlap > best_overlap:
                best_overlap = overlap
                best_chunk = chunk

    if best_chunk is None:
        best_chunk = corpus[0]

    matched_nums = sorted(list(int_nums.intersection(set(best_chunk.get("auspicious_numbers", [])))))
    if not matched_nums:
        matched_nums = best_chunk.get("auspicious_numbers", [])[:4]

    return ClassicsEvidenceModel(
        book=f"《{best_chunk.get('book', '周易')}》",
        chapter=best_chunk.get("chapter", ""),
        quote=best_chunk.get("quote", ""),
        vernacular=best_chunk.get("vernacular", ""),
        auspicious_numbers=matched_nums,
        category=best_chunk.get("category", "hexagram")
    )


def format_rag_context_for_prompt(
    evidence_list: List[ClassicsEvidenceModel],
    day_master: str,
    favorable_elements: List[str]
) -> str:
    """Format grounded RAG evidence context for LLM prompt injection."""
    lines = [
        "【古籍绝学典籍引证与数理依据 (RAG Grounding)】",
        f"缘主日元：{day_master}，命中喜用神：{'、'.join(favorable_elements)}。",
        "推荐号码所对应的正统易学典籍印证如下，请在解盘批语中务必引经据典，详释以下卦象与典故："
    ]
    for i, ev in enumerate(evidence_list[:3], 1):
        lines.append(
            f"{i}. {ev.book} · {ev.chapter}：\n"
            f"   原文：『{ev.quote}』\n"
            f"   易解：{ev.vernacular}\n"
            f"   印证神数：{', '.join(str(n) for n in ev.auspicious_numbers)}"
        )
    return "\n".join(lines)
