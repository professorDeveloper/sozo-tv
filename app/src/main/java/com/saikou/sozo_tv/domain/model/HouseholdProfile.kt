package com.saikou.sozo_tv.domain.model

data class HouseholdProfile(
    val id: String,
    val name: String,
    val avatar: String? = null,
    val color: String? = null,
    val isKids: Boolean = false,
    val isDefault: Boolean = false,
) {
    /** The original database stays the default profile's home. */
    val localNamespace: String? get() = if (isDefault) null else id
}
