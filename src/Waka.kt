package org.jetbrains

import kotlinx.serialization.*

@Serializable
data class WakaUser(
    val `data`: UserData
)

@Serializable
data class UserData(
    val id: String,
    val is_already_updating: Boolean,
    val is_up_to_date: Boolean,
    val range: String,
    val status: String,
    val user_id: String,
    val username: String?,

    @Optional
    val categories: List<Category> = emptyList(),
    @Optional
    val daily_average: Int = 0,
    @Optional
    val editors: List<Editor> = emptyList(),
    @Optional
    val languages: List<UserLanguage> = emptyList(),
    @Optional
    val operating_systems: List<OperatingSystem> = emptyList(),
    @Optional
    val message: String? = null,
    @Optional
    val total_seconds: Int = 0
)

@Serializable
data class Category(
    val digital: String,
    val hours: Int,
    val minutes: Int,
    val name: String,
    val percent: Double,
    val text: String,
    val total_seconds: Int
)

@Serializable
data class Editor(
    val digital: String,
    val hours: Int,
    val minutes: Int,
    val name: String,
    val percent: Double,
    val text: String,
    val total_seconds: Int
)

@Serializable
data class OperatingSystem(
    val digital: String,
    val hours: Int,
    val minutes: Int,
    val name: String,
    val percent: Double,
    val text: String,
    val total_seconds: Int
)

@Serializable
data class UserLanguage(
    val digital: String,
    val hours: Int,
    val minutes: Int,
    val name: String,
    val percent: Double,
    val text: String,
    val total_seconds: Int
)

@Serializable
data class WakaLeaders(
    val current_user: String?,
    val `data`: List<Data>,
    val language: String?,
    val modified_at: String,
    val page: Int,
    val range: String,
    val total_pages: Int
)

@Serializable
data class User(
    val display_name: String?,
    val email: String?,
    val email_public: Boolean,
    val full_name: String?,
    val human_readable_website: String?,
    val id: String,
    val location: String?,
    val photo: String?,
    val photo_public: Boolean,
    val username: String?,
    val website: String?
)

@Serializable
data class RunningTotal(
    @Optional
    val daily_average: Int = 0,
    val human_readable_daily_average: String,
    val human_readable_total: String,
    val languages: List<Language>,
    val modified_at: String,
    val total_seconds: Int
)

@Serializable
data class Data(
    val rank: Int,
    val running_total: RunningTotal,
    val user: User
)

@Serializable
data class Language(
    val name: String,
    val total_seconds: Int
)