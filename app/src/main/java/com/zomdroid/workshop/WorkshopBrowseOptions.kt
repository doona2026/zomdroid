package com.zomdroid.workshop

enum class SteamLanguagePreference(
    val requestValue: String,
    val acceptLanguageValue: String,
) {
    SimplifiedChinese("schinese", "zh-CN,zh;q=0.9"),
    English("english", "en-US,en;q=0.9"),
}

enum class WorkshopBrowseSortOption(
    val browseSortValue: String,
    val actualSortValue: String,
    val supportsTimeWindow: Boolean,
) {
    MostPopular("trend", "trend", true),
    MostRecent("mostrecent", "mostrecent", false),
    LastUpdated("lastupdated", "lastupdated", false),
    MostSubscribed("totaluniquesubscribers", "totaluniquesubscribers", false),
}

enum class WorkshopBrowseTimeWindow(val daysValue: Int) {
    Today(1),
    OneWeek(7),
    ThirtyDays(30),
    ThreeMonths(90),
    SixMonths(180),
    OneYear(365),
    AllTime(-1),
}

fun WorkshopBrowseSortOption.displayName(): String = when (this) {
    WorkshopBrowseSortOption.MostPopular -> "热门"
    WorkshopBrowseSortOption.MostRecent -> "最新发布"
    WorkshopBrowseSortOption.LastUpdated -> "最近更新"
    WorkshopBrowseSortOption.MostSubscribed -> "订阅最多"
}

fun WorkshopBrowseTimeWindow.displayName(): String = when (this) {
    WorkshopBrowseTimeWindow.Today -> "今日"
    WorkshopBrowseTimeWindow.OneWeek -> "本周"
    WorkshopBrowseTimeWindow.ThirtyDays -> "30天"
    WorkshopBrowseTimeWindow.ThreeMonths -> "3个月"
    WorkshopBrowseTimeWindow.SixMonths -> "6个月"
    WorkshopBrowseTimeWindow.OneYear -> "1年"
    WorkshopBrowseTimeWindow.AllTime -> "全部时间"
}
