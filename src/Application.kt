@file:Suppress("EXPERIMENTAL_API_USAGE")

package org.jetbrains

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.json.*
import java.io.*
import java.util.concurrent.*

suspend fun main() {
    val json = JSON.nonstrict

    HttpClient(Apache) {
        install(JsonFeature) {
            serializer = KotlinxSerializer(json)
        }

        engine {
            socketTimeout = 60_000
            connectTimeout = 30_000
            connectionRequestTimeout = 30_000

            customizeClient {
                setMaxConnTotal(5)
            }
        }
    }.use { http ->

        val downloadScope = CoroutineScope(newFixedThreadPoolContext(5, "Downloader"))
        val cacheFolder = File("cache")
        cacheFolder.mkdirs()

        println("Fetching initial data…")
        val firstPage = http.get<WakaLeaders>("https://wakatime.com/api/v1/leaders")
        val numpages = firstPage.total_pages

        println("Fetching $numpages pages…")
        val pages = (2..numpages).map { page ->
            downloadScope.async {
                http.get<WakaLeaders>("https://wakatime.com/api/v1/leaders?page=$page").also {
                    print(".")
                }
            }
        }.awaitAll() + firstPage
        println()

        val users = pages.flatMap { it.data }.map { it.user }
        val throttle = 0L
        println("Fetching ${users.size} users…")
        val usersInfo = users.mapAsync {
            val cacheFile = File(cacheFolder, "${it.id}.json")
            if (cacheFile.exists()) {
                val cacheText = cacheFile.readText()
                print(" _")
                json.parse(WakaUser.serializer(), cacheText)
            } else {
                val url = "https://wakatime.com/api/v1/users/${it.id}/stats/last_7_days"
                try {
                    delay(throttle)
                    val response = http.get<HttpResponse>(url)
                    print("^")
                    if (response.status.isSuccess()) {
                        val user = response.receive<WakaUser>()
                        if (user.data.is_up_to_date) {
                            print(".")
                            val cache = json.stringify(WakaUser.serializer(), user)
                            cacheFile.writeText(cache)
                            user
                        } else {
                            print("?")
                            null
                        }
                    } else {
                        print("x")
                        null
                    }

                } catch (e: Throwable) {
                    when (e) {
                        is TimeoutException -> {
                            print("T")
                            null
                        }
                        is ReceivePipelineException -> {
                            println(
                                "Cannot fetch user ${it.username
                                    ?: it.id}: $url -- ${e.cause::class} '${e.cause.message}'"
                            )
                            null
                        }
                        else -> {
                            println("Cannot fetch user ${it.username ?: it.id}: $url -- ${e::class} '${e.message}'")
                            null
                        }
                    }
                }
            }
        }.filterNotNull().filter { it.data.is_up_to_date }
        println()
        println("Fetched ${usersInfo.size} users.")

        languageStats(usersInfo)
        osStats(usersInfo)
        editorStats(usersInfo)
        activityStats(usersInfo)

        filteredStats(usersInfo, "Kotlin") { it.languages.any { it.name == "Kotlin" } }
        filteredStats(usersInfo, "IntelliJ") { it.editors.any { it.name == "IntelliJ" } }
        filteredStats(usersInfo, "VS Code") { it.editors.any { it.name == "VS Code" } }
    }
}

fun filteredStats(info: List<WakaUser>, name: String, filter: (UserData) -> Boolean) {
    val filtered = info.filter { filter(it.data) }
    println()
    println("======== ${filtered.size} $name users ==========")
    osStats(filtered)
    editorStats(filtered)
    languageStats(filtered)
    activityStats(filtered)
}

suspend fun <T, R> Iterable<T>.mapAsync(limit: Int = 10, body: suspend (T) -> R): List<R> = coroutineScope {
    val ch1 = produce { this@mapAsync.forEach { send(it) } }
    produce {
        repeat(limit) {
            launch {
                for (t in ch1) send(body(t))
            }
        }
    }.toList()
}

fun osStats(info: List<WakaUser>) {
    val oses = info.flatMap { it.data.operating_systems }
        .groupBy { it.name }
        .mapValues { it.value.sumBy { it.total_seconds } }
        .toList()
        .sortedByDescending { it.second }.take(10)

    printStats(oses, "OS stats")
}

fun activityStats(info: List<WakaUser>) {
    val oses = info.flatMap { it.data.categories }
        .groupBy { it.name }
        .mapValues { it.value.sumBy { it.total_seconds } }
        .toList()
        .sortedByDescending { it.second }.take(10)

    printStats(oses, "Category stats")
}

fun editorStats(info: List<WakaUser>) {
    val oses = info.flatMap { it.data.editors }
        .groupBy { it.name }
        .mapValues { it.value.sumBy { it.total_seconds } }
        .toList()
        .sortedByDescending { it.second }.take(10)

    printStats(oses, "Editor stats")
}

private fun languageStats(languages: List<WakaUser>) {
    val stats = languages.flatMap { it.data.languages }
        .groupBy { it.name }
        .mapValues { it.value.sumBy { it.total_seconds } }
        .toList()
        .sortedByDescending { it.second }.take(20)

    printStats(stats, "Language stats")
}

private fun printStats(stats: List<Pair<String, Int>>, title: String) {
    println()
    println("$title:")
    val max = stats.maxBy { it.second }!!.second.toDouble()
    stats.forEach {
        val message = "${it.first}: ${it.second.toDuration()}"
        val bar = "=".repeat((it.second / max * 60).toInt())
        println("${message.padEnd(30)} |$bar")
    }
}

private fun Int.toDuration(): String {
    return ((this / 36) / 100f).toString()
    val h = this / 3600
    val m = (this - h * 3600) / 60
    val s = (this - h * 3600 - m * 60)
    return "${h.pad()}:${m.pad()}:${s.pad()}"
}

private fun Int.pad(width: Int = 2) = this.toString().padStart(width, '0')
