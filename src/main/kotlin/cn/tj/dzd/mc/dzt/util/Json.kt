package cn.tj.dzd.mc.dzt.util

import kotlinx.serialization.json.Json

val json = Json {
    ignoreUnknownKeys = true // 忽略 JSON 中多余字段 (兼容后端加字段)
    isLenient = true         // 允许特殊格式 (如单引号)
    encodeDefaults = true    // 编码时包含默认值
}