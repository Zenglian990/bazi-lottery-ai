"""Chinese Name Numerology: Character strokes, Five Grids (五格剖象法), 81-stroke numerology, and personal lucky numbers."""
from typing import Dict, List, Tuple, Any, Optional

# Extensive stroke count table for Chinese surnames and common naming characters
STROKE_DICT: Dict[str, int] = {
    # Common surnames & characters
    "曾": 12, "练": 8, "张": 7, "王": 4, "李": 7, "赵": 9, "钱": 10, "孙": 6,
    "周": 8, "吴": 7, "郑": 8, "冯": 5, "陈": 7, "褚": 13, "卫": 3, "蒋": 12,
    "沈": 7, "韩": 12, "杨": 7, "朱": 6, "秦": 10, "尤": 4, "许": 6, "何": 7,
    "吕": 6, "施": 9, "孔": 4, "曹": 11, "严": 7, "华": 6, "金": 8, "魏": 17,
    "陶": 10, "姜": 9, "戚": 11, "谢": 12, "邹": 7, "喻": 12, "柏": 9, "水": 4,
    "窦": 13, "章": 11, "云": 4, "苏": 7, "潘": 15, "葛": 12, "奚": 10, "范": 8,
    "彭": 12, "郎": 8, "鲁": 12, "韦": 4, "昌": 8, "马": 3, "苗": 8, "凤": 4,
    "花": 7, "方": 4, "俞": 9, "任": 6, "袁": 10, "柳": 9, "鲍": 13, "史": 5,
    "唐": 10, "费": 9, "廉": 13, "岑": 7, "薛": 16, "雷": 13, "贺": 9, "倪": 10,
    "汤": 6, "滕": 15, "殷": 10, "罗": 8, "毕": 6, "郝": 9, "邬": 7, "安": 6,
    "常": 11, "乐": 5, "于": 3, "时": 7, "傅": 12, "皮": 5, "齐": 6, "康": 11,
    "伍": 6, "余": 7, "元": 4, "卜": 2, "顾": 10, "孟": 8, "平": 5, "黄": 11,
    "和": 8, "穆": 16, "萧": 11, "尹": 4, "姚": 9, "邵": 7, "湛": 12, "汪": 7,
    "祁": 6, "毛": 4, "禹": 9, "狄": 7, "米": 6, "贝": 4, "明": 8, "臧": 14,
    "计": 4, "伏": 6, "成": 6, "戴": 17, "谈": 10, "宋": 7, "茅": 8, "庞": 8,
    "熊": 14, "纪": 6, "舒": 12, "屈": 8, "项": 9, "祝": 9, "董": 12, "梁": 11,
    "杜": 7, "阮": 7, "蓝": 13, "闵": 7, "席": 10, "季": 8, "麻": 11, "强": 12,
    "贾": 10, "路": 13, "娄": 9, "危": 6, "江": 6, "童": 12, "颜": 15, "郭": 10,
    "梅": 11, "盛": 11, "林": 8, "刁": 2, "钟": 9, "徐": 10, "邱": 7, "骆": 10,
    "高": 10, "夏": 10, "蔡": 14, "田": 5, "樊": 15, "胡": 9, "凌": 10, "霍": 16,
    "虞": 13, "万": 3, "支": 4, "柯": 9, "管": 14, "卢": 5, "莫": 10, "经": 8,
    "房": 8, "裘": 13, "缪": 14, "干": 3, "解": 13, "应": 7, "宗": 8, "丁": 2,
    "宣": 9, "邓": 4, "郁": 8, "单": 8, "杭": 8, "洪": 9, "包": 5, "诸": 10,
    "左": 5, "石": 5, "崔": 11, "吉": 6, "钮": 9, "龚": 11, "程": 12, "陆": 7,
    "翁": 10, "荀": 9, "惠": 12, "甄": 13, "家": 10, "封": 9, "松": 8, "井": 4,
    "段": 9, "富": 12, "巫": 7, "焦": 12, "巴": 4, "弓": 3, "牧": 8, "山": 3,
    "谷": 7, "车": 4, "侯": 9, "全": 6, "秋": 9, "仲": 6, "伊": 6, "宫": 9,
    "宁": 5, "仇": 4, "甘": 5, "厉": 5, "祖": 9, "武": 8, "符": 11, "刘": 6,
    "景": 12, "詹": 13, "龙": 5, "叶": 5, "幸": 8, "司": 5, "黎": 15, "白": 5,
    "卓": 8, "屠": 11, "池": 6, "乔": 6, "阴": 6, "胥": 9, "能": 10, "苍": 7,
    "双": 4, "闻": 9, "翟": 14, "谭": 14, "贡": 7, "姬": 10, "申": 5, "扶": 7,
    "桑": 10, "桂": 10, "牛": 4, "寿": 7, "通": 10, "边": 5, "燕": 16, "温": 12,
    "庄": 6, "晏": 10, "柴": 10, "阎": 11, "充": 6, "慕": 14, "连": 7, "茹": 9,
    "习": 3, "艾": 5, "鱼": 8, "容": 10, "向": 6, "古": 5, "易": 8, "慎": 13,
    "廖": 14, "庾": 12, "步": 7, "都": 10, "耿": 10, "满": 13, "弘": 5, "国": 8,
    "文": 4, "广": 3, "禄": 12, "东": 5, "欧": 8, "利": 7, "越": 12, "隆": 11,
    "师": 6, "巩": 6, "聂": 10, "勾": 4, "敖": 10, "融": 16, "冷": 7, "辛": 7,
    "简": 13, "饶": 9, "关": 6, "查": 9, "红": 6, "游": 12, "权": 6, "盖": 11,
    "益": 10, "公": 4,
    # Common given name characters & auspicious characters
    "一": 1, "二": 2, "三": 3, "四": 5, "五": 4, "六": 4, "七": 2, "八": 2, "九": 2, "十": 2,
    "天": 4, "地": 6, "人": 2, "大": 3, "小": 3, "中": 4, "子": 3, "生": 5, "德": 15,
    "志": 7, "勇": 9, "俊": 9, "伟": 6, "超": 12, "博": 12, "杰": 8, "峰": 10, "海": 10,
    "洋": 9, "宇": 6, "辰": 7, "浩": 10, "晨": 11, "曦": 20, "瑞": 13, "祥": 10, "福": 13,
    "康": 11, "寿": 7, "贵": 9, "富": 12, "荣": 9, "华": 6, "茂": 8, "盛": 11, "昌": 8,
    "春": 9, "秋": 9, "冬": 5, "光": 6, "辉": 12, "星": 9, "月": 4, "阳": 6, "晴": 12,
    "彩": 11, "宝": 8, "财": 7, "丰": 4, "玉": 5, "锦": 13, "秀": 7, "丽": 7, "美": 9,
    "佳": 8, "欣": 8, "悦": 10, "安": 6, "顺": 9, "达": 6, "通": 10, "胜": 9, "利": 7,
    "凯": 8, "旋": 11, "智": 12, "慧": 15, "贤": 8, "良": 7, "忠": 8, "义": 3, "仁": 4,
    "礼": 5, "信": 9, "诚": 8, "敬": 12, "尊": 12, "友": 4, "爱": 10, "恩": 10, "泽": 8,
    "沛": 7, "润": 10, "涵": 11, "清": 11, "源": 13, "涛": 10, "泉": 9, "流": 10,
}

# Traditional compound surnames (复姓)
COMPOUND_SURNAMES = {
    "欧阳", "太史", "端木", "上官", "司马", "东方", "独孤", "南宫", "万俟", "闻人",
    "夏侯", "诸葛", "尉迟", "公羊", "赫连", "澹台", "皇甫", "宗政", "濮阳", "淳于",
    "单于", "太叔", "申屠", "公孙", "仲孙", "轩辕", "令狐", "钟离", "宇文", "长孙",
    "慕容", "鲜于", "闾丘", "司徒", "司空", "亓官", "司寇", "仉督", "子车", "颛孙",
    "巫马", "公西", "漆雕", "乐正", "壤驷", "公良", "拓跋", "夹谷", "宰父", "谷梁",
}

# 81-stroke numerology auspiciousness and meanings (81数理吉凶)
FORTUNE_81: Dict[int, Tuple[str, str]] = {
    1: ("大吉", "太极之数，万物开泰，生发无穷，利禄亨通"),
    2: ("凶", "两仪之数，混沌未开，进退失守，波折叠起"),
    3: ("大吉", "三才之数，阴阳和合，吉祥福禄，求谋如意"),
    4: ("凶", "四象之数，待香组织，进退维谷，坎坷多灾"),
    5: ("大吉", "五行俱全，生意欣荣，福禄双收，圆满之数"),
    6: ("大吉", "六爻之数，吉人天相，富贵荣华，发展成功"),
    7: ("吉", "七政之数，精力旺盛，独立权威，刚毅果断"),
    8: ("吉", "八卦之成，意志坚强，克难进取，终成大业"),
    9: ("凶", "大成之数，利去劫来，逆境频仍，艰难困顿"),
    10: ("凶", "终结之数，雪暗飘零，幼小薄弱，险阻重重"),
    11: ("大吉", "旱苗逢雨，草木逢春，枝叶沾露，稳健着实"),
    12: ("凶", "掘井无泉，意志薄弱，家庭寂寞，难得平安"),
    13: ("大吉", "天赋吉运，能得人望，充满智慧，文武皆宜"),
    14: ("凶", "破兆之数，浮沉不定，知己难逢，多病多难"),
    15: ("大吉", "福寿双全，立身兴家，德高望重，吉祥安康"),
    16: ("大吉", "厚德载物，安富尊荣，财官双美，功成名就"),
    17: ("吉", "权威刚强，突破万难，如日方升，成大事业"),
    18: ("吉", "经商发达，利市百倍，智谋兼备，荣华显达"),
    19: ("凶", "风云蔽月，暗淡无光，多智多难，破财伤体"),
    20: ("凶", "破运之数，百事难成，志向虽高，坎坷困厄"),
    21: ("大吉", "明月光照，独立权威，光风霁月，大展鸿图"),
    22: ("凶", "秋草逢霜，怀才不遇，忧闷频生，事与愿违"),
    23: ("大吉", "旭日东升，名显四方，势如破竹，伟大之昌"),
    24: ("大吉", "锦绣前程，家门余庆，金钱丰盛，白手起家"),
    25: ("吉", "资性英敏，柔中带硬，克成大功，成功发达"),
    26: ("半吉", "变怪奇异，波澜叠起，英雄慷慨，历险成功"),
    27: ("半吉", "迎新去旧，吉凶互见，自我心强，多受诽谤"),
    28: ("凶", "豪气生离，飘泊无定，家属缘薄，多灾多难"),
    29: ("吉", "泉舟顺展，智谋兼备，财力充裕，进取有功"),
    30: ("半吉", "吉凶相半，浮沉不定，投机取巧，得失参半"),
    31: ("大吉", "智勇得志，名利双收，心慈理达，大业成就"),
    32: ("大吉", "池中之龙，风云际会，品德兼优，如龙升天"),
    33: ("大吉", "旭日升天，名闻天下，家门隆昌，才德兼备"),
    34: ("凶", "破家之兆，灾难叠起，孤独不顺，难得平安"),
    35: ("吉", "温和平静，优雅发展，文笔畅达，财禄丰盈"),
    36: ("凶", "波澜重叠，沉浮万状，侠气过盛，舍己成仁"),
    37: ("吉", "权威显达，吉人天相，热诚忠信，克成大业"),
    38: ("半吉", "薄德缺乏，徒劳无功，宜扬长避短，保守自安"),
    39: ("吉", "富贵荣华，财帛丰盈，光明磊落，盛运至极"),
    40: ("凶", "谨慎自重，浮沉退难，投机涉险，多遭失败"),
    41: ("大吉", "天赋吉运，德高望重，事事如意，名利双收"),
    42: ("半吉", "博识多能，十艺九不成，精通一艺，始可成功"),
    43: ("凶", "散财破产，外祥内苦，意志薄弱，虚名虚利"),
    44: ("凶", "事不遂心，暗淡无光，难逆境遇，破败悲凉"),
    45: ("吉", "新生泰和，万事如意，富贵荣华，子孙代代"),
    46: ("凶", "坎坷沉浮，难望成功，悲哀苦难，徒劳无功"),
    47: ("大吉", "有贵人助，可成大业，盛运亨通，名利双全"),
    48: ("大吉", "美德丰盛，顾问之数，师表人伦，德智兼备"),
    49: ("半吉", "吉凶各半，变怪多端，吉不掩凶，吉者自重"),
    50: ("半吉", "一成一败，吉凶互见，晚景凄凉，谨防衰运"),
    51: ("半吉", "盛衰交加，沉浮不常，自重自守，免遭厄运"),
    52: ("吉", "先见之明，理想实现，名利双收，善谋奇策"),
    53: ("半吉", "内实外虚，盛衰相伴，吉运将尽，宜守和平"),
    54: ("凶", "横来祸殃，困难重重，大业难成，多招灾患"),
    55: ("半吉", "外美内苦，和顺表面，吉凶相半，宜修心性"),
    56: ("凶", "事与愿违，终难成功，缺乏精力，万事蹉跎"),
    57: ("吉", "寒雪青松，白手起家，富贵荣华，晚景甚佳"),
    58: ("半吉", "先苦后甜，浮沉不定，晚运亨通，享受安宁"),
    59: ("凶", "意志衰退，缺乏勇气，志向虽大，半途而废"),
    60: ("凶", "黑暗无光，摇动不安，历尽艰难，难望成功"),
    61: ("吉", "名利双收，修炼成功，繁荣富贵，得享盛誉"),
    62: ("凶", "基础虚弱，渐入衰境，志大才疏，多遭失败"),
    63: ("大吉", "富贵荣华，万事如意，内外和睦，亨通之极"),
    64: ("凶", "骨肉分离，沉浮难定，多病灾患，大受波折"),
    65: ("大吉", "巨流归海，富贵长寿，名扬四海，家运昌隆"),
    66: ("凶", "内外交困，艰难重重，暗云遮月，难成大业"),
    67: ("大吉", "通达利达，发迹成功，富贵荣华，福禄双收"),
    68: ("大吉", "兴家立业，智虑周密，发财兴家，显达荣华"),
    69: ("凶", "坐立不安，沉浮多变，动摇难定，难享清福"),
    70: ("凶", "废疾灭亡，晚景悲惨，家运衰颓，破灭离散"),
    71: ("半吉", "吉凶各半，得失相伴，守正自保，方免祸端"),
    72: ("凶", "未遂之兆，多灾多难，前路坎坷，劳苦无功"),
    73: ("吉", "志高力微，徒劳无功，平安自守，可得安康"),
    74: ("凶", "逆境沉浮，坐立不安，徒劳无功，多灾难逃"),
    75: ("半吉", "守则可安，发则招灾，知足常乐，可得小康"),
    76: ("凶", "骨肉离散，内外不和，艰难险阻，倾家荡产"),
    77: ("半吉", "吉凶各半，先苦后甜，守正待时，晚年享福"),
    78: ("半吉", "华而不实，得失相半，浮沉不定，难望大业"),
    79: ("凶", "挽回乏力，前路昏暗，劳苦受难，难望成功"),
    80: ("凶", "一生多灾，艰难困苦，波折不断，孤苦伶仃"),
    81: ("大吉", "最极之数，还本归元，尊荣显达，吉祥如意"),
}

# Grid tail modulo 10 to Wuxing Element
GRID_TAIL_TO_WUXING: Dict[int, str] = {
    1: "木", 2: "木",
    3: "火", 4: "火",
    5: "土", 6: "土",
    7: "金", 8: "金",
    9: "水", 0: "水",
}


def get_character_stroke(char: str) -> int:
    """Retrieve Kangxi/simplified stroke count for a single Chinese character."""
    if not char:
        return 0
    if char in STROKE_DICT:
        return STROKE_DICT[char]
    # Algorithmic fallback based on Unicode codepoint
    cp = ord(char)
    return max(1, min(25, 3 + (cp % 18)))


def split_chinese_name(name: str) -> Tuple[str, str]:
    """Split a Chinese name into (surname, given_name)."""
    clean_name = name.strip()
    if len(clean_name) >= 2 and clean_name[:2] in COMPOUND_SURNAMES:
        return clean_name[:2], clean_name[2:]
    elif len(clean_name) >= 1:
        return clean_name[:1], clean_name[1:]
    return "", ""


def compute_grid_wuxing(value: int) -> str:
    """Convert grid numeric value to Wuxing element."""
    tail = value % 10
    return GRID_TAIL_TO_WUXING[tail]


def compute_grid_fortune(value: int) -> Tuple[str, str]:
    """Map integer to 81-stroke numerology auspiciousness and meaning."""
    v_81 = (value - 1) % 81 + 1 if value > 0 else 1
    return FORTUNE_81.get(v_81, ("平", "数理平平，勤勉自立"))


def analyze_name_numerology(name: str) -> Dict[str, Any]:
    """
    Perform complete Five Grids (五格剖象法), 81-stroke numerology,
    and personal lucky number extraction for a Chinese name.
    """
    clean_name = name.strip()
    if not clean_name:
        return {}

    surname, given_name = split_chinese_name(clean_name)
    s_strokes = [get_character_stroke(c) for c in surname]
    g_strokes = [get_character_stroke(c) for c in given_name]

    # Calculate Five Grids
    if len(surname) == 1:
        s1 = s_strokes[0]
        tian_ge = s1 + 1
        if len(given_name) == 1:
            g1 = g_strokes[0]
            ren_ge = s1 + g1
            di_ge = g1 + 1
            zong_ge = s1 + g1
            wai_ge = 2
        elif len(given_name) >= 2:
            g1 = g_strokes[0]
            g2 = g_strokes[1]
            g_rest = sum(g_strokes[2:])
            ren_ge = s1 + g1
            di_ge = g1 + g2 + g_rest
            zong_ge = s1 + di_ge
            wai_ge = zong_ge - ren_ge + 1
        else:  # Only surname entered
            ren_ge = s1
            di_ge = 1
            zong_ge = s1
            wai_ge = 1
    else:  # Compound surname
        s1, s2 = s_strokes[0], s_strokes[1]
        tian_ge = s1 + s2
        if len(given_name) == 1:
            g1 = g_strokes[0]
            ren_ge = s2 + g1
            di_ge = g1 + 1
            zong_ge = s1 + s2 + g1
            wai_ge = s1 + 1
        elif len(given_name) >= 2:
            g1 = g_strokes[0]
            g2 = g_strokes[1]
            g_rest = sum(g_strokes[2:])
            ren_ge = s2 + g1
            di_ge = g1 + g2 + g_rest
            zong_ge = s1 + s2 + di_ge
            wai_ge = zong_ge - ren_ge
        else:
            ren_ge = s2
            di_ge = 1
            zong_ge = s1 + s2
            wai_ge = s1

    def make_grid_info(val: int) -> Dict[str, Any]:
        fortune, desc = compute_grid_fortune(val)
        return {
            "value": val,
            "element": compute_grid_wuxing(val),
            "fortune": fortune,
            "description": desc,
        }

    tian_info = make_grid_info(tian_ge)
    ren_info = make_grid_info(ren_ge)
    di_info = make_grid_info(di_ge)
    wai_info = make_grid_info(wai_ge)
    zong_info = make_grid_info(zong_ge)

    # Derive personal lucky numbers from Ren Ge, Zong Ge, and Auspicious grids
    lucky_numbers_set = set()
    # 1. Ren Ge & Zong Ge tail digits
    r_tail = ren_ge % 10 or 10
    z_tail = zong_ge % 10 or 10
    lucky_numbers_set.add(r_tail)
    lucky_numbers_set.add(z_tail)

    # 2. Add numbers where grid values are auspicious and within lottery ball ranges (1-35)
    for g_val, g_info in [
        (tian_ge, tian_info), (ren_ge, ren_info), (di_ge, di_info),
        (wai_ge, wai_info), (zong_ge, zong_info),
    ]:
        if "吉" in g_info["fortune"] and 1 <= g_val <= 35:
            lucky_numbers_set.add(g_val)

    # 3. Hetu harmonic numbers matching Ren Ge element
    ren_elem = ren_info["element"]
    hetu_tails = {
        "木": [3, 8],
        "火": [2, 7],
        "土": [5, 10],
        "金": [4, 9],
        "水": [1, 6],
    }.get(ren_elem, [1, 6])
    for ht in hetu_tails:
        lucky_numbers_set.add(ht)

    name_lucky_numbers = sorted(list(lucky_numbers_set))

    character_strokes = {c: get_character_stroke(c) for c in clean_name}

    summary = (
        f"缘主【{clean_name}】姓名五格剖象：人格{ren_ge}数理({ren_info['fortune']})属{ren_info['element']}，"
        f"总格{zong_ge}数理({zong_info['fortune']})属{zong_info['element']}。"
        f"吉祥数理共振号：{', '.join(str(n) for n in name_lucky_numbers[:8])}。"
    )

    return {
        "user_name": clean_name,
        "surname": surname,
        "given_name": given_name,
        "character_strokes": character_strokes,
        "tian_ge": tian_info,
        "ren_ge": ren_info,
        "di_ge": di_info,
        "wai_ge": wai_info,
        "zong_ge": zong_info,
        "primary_element": ren_info["element"],
        "name_lucky_numbers": name_lucky_numbers,
        "summary": summary,
    }
