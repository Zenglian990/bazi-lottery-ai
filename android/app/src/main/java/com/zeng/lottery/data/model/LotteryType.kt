package com.zeng.lottery.data.model

import com.google.gson.annotations.SerializedName

/**
 * Supported lottery types with rules and range specifications.
 */
enum class LotteryType(
    val code: String,
    val title: String,
    val subtitle: String,
    val primaryRange: IntRange,
    val primaryCount: Int,
    val secondaryRange: IntRange? = null,
    val secondaryCount: Int = 0,
    val primaryAllowDuplicate: Boolean = false,
    val secondaryAllowDuplicate: Boolean = false
) {
    @SerializedName("SSQ", alternate = ["ssq"])
    SSQ(
        code = "ssq",
        title = "双色球",
        subtitle = "6红 (1-33) + 1蓝 (1-16)",
        primaryRange = 1..33,
        primaryCount = 6,
        secondaryRange = 1..16,
        secondaryCount = 1,
        primaryAllowDuplicate = false,
        secondaryAllowDuplicate = false
    ),
    @SerializedName("DLT", alternate = ["dlt"])
    DLT(
        code = "dlt",
        title = "大乐透",
        subtitle = "5前区 (1-35) + 2后区 (1-12)",
        primaryRange = 1..35,
        primaryCount = 5,
        secondaryRange = 1..12,
        secondaryCount = 2,
        primaryAllowDuplicate = false,
        secondaryAllowDuplicate = false
    ),
    @SerializedName("FC3D", alternate = ["fc3d"])
    FC3D(
        code = "fc3d",
        title = "福彩3D",
        subtitle = "3位数字 (每位 0-9)",
        primaryRange = 0..9,
        primaryCount = 3,
        secondaryRange = null,
        secondaryCount = 0,
        primaryAllowDuplicate = true,
        secondaryAllowDuplicate = false
    ),
    @SerializedName("QXC", alternate = ["qxc"])
    QXC(
        code = "qxc",
        title = "七星彩",
        subtitle = "前6位 (0-9) + 第7位 (0-14)",
        primaryRange = 0..9,
        primaryCount = 6,
        secondaryRange = 0..14,
        secondaryCount = 1,
        primaryAllowDuplicate = true,
        secondaryAllowDuplicate = false
    );

    val displayName: String get() = title

    companion object {
        fun fromCode(code: String): LotteryType {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SSQ
        }
    }
}
