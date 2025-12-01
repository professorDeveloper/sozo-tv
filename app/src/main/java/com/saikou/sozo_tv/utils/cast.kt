package com.saikou.sozo_tv.utils

import com.saikou.sozo_tv.parser.BaseParser

inline fun <reified T : BaseParser> BaseParser.cast(): T? {
    return this as? T
}
