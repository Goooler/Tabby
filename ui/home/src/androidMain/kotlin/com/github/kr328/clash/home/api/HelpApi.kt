package com.github.kr328.clash.home.api

import com.github.kr328.clash.glue.util.TABBY_REPO
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class HelpApi(
  private val client: HttpClient =
    HttpClient(CIO) {
      defaultRequest {
        url("https://api.github.com/")
        contentType(ContentType.Application.Json)
        accept(ContentType.Application.Json)
      }
      install(ContentNegotiation) {
        json(json = Json { ignoreUnknownKeys = true })
      }
    },
) {
  suspend fun getLatestRelease(): String? {
    val response = client.get("repos/$TABBY_REPO/releases/latest")
    return when {
      response.status.isSuccess() -> response.body<GithubRelease>().tagName
      else -> null
    }
  }
}

@Serializable data class GithubRelease(@SerialName("tag_name") val tagName: String)
