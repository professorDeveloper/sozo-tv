package com.saikou.sozo_tv.data.model

import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter

data class AdModel(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val clickUrl: String = "",
    val ctaText: String = "Learn More",
    val ctaUrl: String = "", // Added ctaUrl field for separate CTA button action
    val backgroundColor: String = "#1a1a2e",
    val textColor: String = "#ffffff",
    val isActive: Boolean = true,
    val priority: Int = 0,
    val startDate: String = "",
    val endDate: String = "",
    val targetAudience: List<String> = emptyList(),
    val adType: String = "banner", // banner, video, native
    val impressions: Int = 0,
    val clicks: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "", override val viewType: Int,
) : HomeAdapter.HomeData

data class AdsContainer(
    val ads: List<AdModel> = emptyList(),
    val title: String = "Sponsored",
    val showTitle: Boolean = true, override val viewType: Int,
) : HomeAdapter.HomeData
