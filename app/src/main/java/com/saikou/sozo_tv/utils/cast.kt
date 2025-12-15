package com.saikou.sozo_tv.utils

import com.saikou.sozo_tv.parser.base.BaseParser

inline fun <reified T : BaseParser> BaseParser.cast(): T? {
    return this as? T
}
