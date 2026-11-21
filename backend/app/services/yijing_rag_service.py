"""易学古籍 RAG 服务：加载完整 64 卦与经典命理语料，执行五行归一化精准匹配。"""
import json
import logging
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from backend.app.models.bazi import ClassicsEvidenceModel

logger = logging.getLogger(__name__)

CORPUS_PATH = Path(__file__).resolve().parent.parent / "data" / "classics_corpus.json"
_CACHED_CORPUS: Optional[List[Dict[str, Any]]] = None

_EN_TO_ZH_ELEMENT: Dict[str, str] = {
    "wood": "木", "fire": "火", "earth": "土", "metal": "金", "water": "水",
    "木": "木", "火": "火", "土": "土", "金": "金", "水": "水",
}


def load_corpus() -> List[Dict[str, Any]]:
    """加载或返回内存缓存的古籍语料库。"""
    global _CACHED_CORPUS
    if _CACHED_CORPUS is not None:
        return _CACHED_CORPUS

    if not CORPUS_PATH.exists():
        logger.warning("Classics corpus not found at %s", CORPUS_PATH)
        _CACHED_CORPUS = []
        return _CACHED_CORPUS

    try:
        with open(CORPUS_PATH, "r", encoding="utf-8") as f:
            _CACHED_CORPUS = json.load(f)
            logger.info("Loaded %d classics corpus chunks.", len(_CACHED_CORPUS))
    except Exception as e:
        logger.error("Failed to load classics corpus: %s", type(e).__name__)
        _CACHED_CORPUS = []

    return _CACHED_CORPUS


def _normalize_elements_zh(elements: List[str]) -> set[str]:
    """将中英文五行名称统一归一化为简体中文集合，避免 'wood' 与 '木' 失配。"""
    normalized: set[str] = set()
    for item in elements or []:
        key = str(item).strip().lower()
        zh = _EN_TO_ZH_ELEMENT.get(key) or _EN_TO_ZH_ELEMENT.get(str(item).strip())
        if zh:
            normalized.add(zh)
    return normalized


def match_classics_evidence(
    primary_numbers: List[str],
    favorable_elements: List[str],
    ticket_index: int = 0,
) -> ClassicsEvidenceModel:
    """
    为单注推荐号码匹配最契合的古籍典籍印证。
    - 归一化中英文喜用神五行，确保 'wood'/'木' 匹配结果一致；
    - 仅返回真实落在当前票面中的印证交集数字（若无交集则如实返回空列表，绝不伪造不在票内的神数）。
    """
    corpus = load_corpus()
    if not corpus:
        return ClassicsEvidenceModel(
            book="《周易》",
            chapter="乾坤易理",
            quote="大象曰：天行健，君子以自强不息；地势坤，君子以厚德载物。",
            vernacular="顺应天地运行之道，刚柔并济，量力而行。",
            auspicious_numbers=[],
            category="hexagram",
        )

    int_nums = set()
    for n in primary_numbers:
        try:
            int_nums.add(int(n))
        except (ValueError, TypeError):
            pass

    fav_zh = _normalize_elements_zh(favorable_elements)
    categories = ["hexagram", "qimen", "tiao_hou", "meihua"]
    target_cat = categories[ticket_index % len(categories)]

    def _score_chunk(chunk: Dict[str, Any]) -> Tuple[int, int]:
        c_nums = set(chunk.get("auspicious_numbers", []))
        num_overlap = len(int_nums.intersection(c_nums))
        elem_bonus = 2 if chunk.get("primary_element") in fav_zh else 0
        return (num_overlap + elem_bonus, num_overlap)

    best_chunk: Optional[Dict[str, Any]] = None
    best_score = (-1, -1)

    for chunk in corpus:
        if chunk.get("category") == target_cat:
            sc = _score_chunk(chunk)
            if sc > best_score:
                best_score = sc
                best_chunk = chunk

    if best_chunk is None or best_score[0] <= 0:
        for chunk in corpus:
            sc = _score_chunk(chunk)
            if sc > best_score:
                best_score = sc
                best_chunk = chunk

    if best_chunk is None:
        best_chunk = corpus[0]

    # 严格取票面与典籍的真实交集，绝不硬截典籍未在票内的数字冒充印证
    matched_nums = sorted(list(int_nums.intersection(set(best_chunk.get("auspicious_numbers", [])))))

    return ClassicsEvidenceModel(
        book=f"《{best_chunk.get('book', '周易')}》",
        chapter=best_chunk.get("chapter", ""),
        quote=best_chunk.get("quote", ""),
        vernacular=best_chunk.get("vernacular", ""),
        auspicious_numbers=matched_nums,
        category=best_chunk.get("category", "hexagram"),
    )


def format_rag_context_for_prompt(
    evidence_list: List[ClassicsEvidenceModel],
    day_master: str,
    favorable_elements: List[str],
) -> str:
    """格式化古籍 RAG 证据供 LLM Prompt 引用。"""
    fav_zh = sorted(list(_normalize_elements_zh(favorable_elements))) or [str(e) for e in favorable_elements]
    lines = [
        "【古籍绝学典籍引证与数理依据 (RAG Grounding)】",
        f"缘主日元：{day_master}，命中喜用神：{'、'.join(fav_zh)}。",
        "推荐号码所对应的正统易学典籍印证如下，请在解盘批语中引经据典解读：",
    ]
    for i, ev in enumerate(evidence_list[:3], 1):
        nums_desc = (
            ", ".join(str(n) for n in ev.auspicious_numbers)
            if ev.auspicious_numbers
            else "以卦象五行意蕴呼应本注格局"
        )
        lines.append(
            f"{i}. {ev.book} · {ev.chapter}：\n"
            f"   原文：『{ev.quote}』\n"
            f"   易解：{ev.vernacular}\n"
            f"   印证神数：{nums_desc}"
        )
    return "\n".join(lines)
