package com.github.kr328.clash.core

import co.touchlab.kermit.Logger
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.core.bridge.ClashException
import com.github.kr328.clash.core.bridge.FetchCallback
import com.github.kr328.clash.core.bridge.LogcatInterface
import com.github.kr328.clash.core.bridge.TunInterface
import com.github.kr328.clash.core.model.AgeKeyPair
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.Traffic
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.model.UiConfiguration
import com.github.kr328.clash.core.util.parseInetSocketAddress
import java.io.File
import java.net.InetSocketAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

object Clash {
  enum class OverrideSlot {
    Persist,
    Session,
  }

  fun reset() {
    Bridge.nativeReset()
  }

  fun forceGc() {
    Bridge.nativeForceGc()
  }

  fun suspendCore(suspended: Boolean) {
    Bridge.nativeSuspend(suspended)
  }

  fun queryTunnelState(): TunnelState {
    return json.decodeFromString(Bridge.nativeQueryTunnelState())
  }

  fun queryTrafficNow(): Traffic {
    return Traffic(Bridge.nativeQueryTrafficNow())
  }

  fun queryTrafficTotal(): Traffic {
    return Traffic(Bridge.nativeQueryTrafficTotal())
  }

  fun notifyDnsChanged(dns: List<String>) {
    Bridge.nativeNotifyDnsChanged(dns.toSet().joinToString(separator = ","))
  }

  fun notifyTimeZoneChanged(name: String, offset: Int) {
    Bridge.nativeNotifyTimeZoneChanged(name, offset)
  }

  fun notifyInstalledAppsChanged(uids: List<Pair<Int, String>>) {
    val uidList = uids.joinToString(separator = ",") { "${it.first}:${it.second}" }

    Bridge.nativeNotifyInstalledAppChanged(uidList)
  }

  fun startTun(
    fd: Int,
    stack: String,
    gateway: String,
    portal: String,
    dns: String,
    markSocket: (Int) -> Boolean,
    querySocketUid: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> Int,
  ) {
    Bridge.nativeStartTun(
      fd,
      stack,
      gateway,
      portal,
      dns,
      object : TunInterface {
        override fun markSocket(fd: Int) {
          markSocket(fd)
        }

        override fun querySocketUid(protocol: Int, source: String, target: String): Int {
          return querySocketUid(
            protocol,
            parseInetSocketAddress(source),
            parseInetSocketAddress(target),
          )
        }
      },
    )
  }

  fun stopTun() {
    Bridge.nativeStopTun()
  }

  fun startHttp(listenAt: String): String? {
    return Bridge.nativeStartHttp(listenAt)
  }

  fun stopHttp() {
    Bridge.nativeStopHttp()
  }

  fun queryGroupNames(excludeNotSelectable: Boolean): List<String> {
    val names = json.decodeFromString<JsonArray>(Bridge.nativeQueryGroupNames(excludeNotSelectable))

    return names.map {
      require(it.jsonPrimitive.isString)

      it.jsonPrimitive.content
    }
  }

  fun queryGroup(name: String, sort: ProxySort): ProxyGroup {
    return Bridge.nativeQueryGroup(name, sort.name)?.let { json.decodeFromString(it) }
      ?: ProxyGroup(Proxy.Type.Unknown, emptyList(), "")
  }

  fun healthCheck(name: String): CompletableDeferred<Unit> {
    return CompletableDeferred<Unit>().apply { Bridge.nativeHealthCheck(this, name) }
  }

  fun healthCheckProxy(groupName: String, proxyName: String): CompletableDeferred<Unit> {
    return CompletableDeferred<Unit>().apply {
      Bridge.nativeHealthCheckProxy(this, groupName, proxyName)
    }
  }

  fun healthCheckAll() {
    Bridge.nativeHealthCheckAll()
  }

  fun patchSelector(selector: String, name: String): Boolean {
    return Bridge.nativePatchSelector(selector, name)
  }

  suspend fun fetchAndValid(
    path: File,
    url: String,
    force: Boolean,
    ageSecretKey: String? = null,
    reportStatus: (FetchStatus) -> Unit,
  ) {
    ageSecretKeyLock.withLock {
      try {
        setAgeSecretKey(ageSecretKey)
        CompletableDeferred<Unit>()
          .apply {
            Bridge.nativeFetchAndValid(
              object : FetchCallback {
                override fun report(statusJson: String) {
                  reportStatus(json.decodeFromString(statusJson))
                }

                override fun complete(error: String?) {
                  if (error != null) completeExceptionally(ClashException(error))
                  else complete(Unit)
                }
              },
              path.absolutePath,
              url,
              force,
            )
          }
          .await()
      } finally {
        setAgeSecretKey(null)
      }
    }
  }

  suspend fun load(path: File, ageSecretKey: String? = null) {
    ageSecretKeyLock.withLock {
      try {
        setAgeSecretKey(ageSecretKey)
        CompletableDeferred<Unit>().apply { Bridge.nativeLoad(this, path.absolutePath) }.await()
      } finally {
        setAgeSecretKey(null)
      }
    }
  }

  fun queryProviders(): List<Provider> {
    val providers = json.decodeFromString<JsonArray>(Bridge.nativeQueryProviders())

    return List(providers.size) { json.decodeFromJsonElement<Provider>(providers[it]) }
  }

  fun updateProvider(type: Provider.Type, name: String): CompletableDeferred<Unit> {
    return CompletableDeferred<Unit>().apply {
      Bridge.nativeUpdateProvider(this, type.toString(), name)
    }
  }

  fun queryOverride(slot: OverrideSlot): ConfigurationOverride {
    return try {
      json.decodeFromString(Bridge.nativeReadOverride(slot.ordinal))
    } catch (e: Exception) {
      Logger.e("Read override failed: ${e.message}", e)
      ConfigurationOverride()
    }
  }

  fun patchOverride(slot: OverrideSlot, configuration: ConfigurationOverride) {
    Bridge.nativeWriteOverride(slot.ordinal, json.encodeToString(configuration))
  }

  fun clearOverride(slot: OverrideSlot) {
    Bridge.nativeClearOverride(slot.ordinal)
  }

  fun queryConfiguration(): UiConfiguration {
    return json.decodeFromString(Bridge.nativeQueryConfiguration())
  }

  fun subscribeLogcat(): ReceiveChannel<LogMessage> {
    return Channel<LogMessage>(32).apply {
      Bridge.nativeSubscribeLogcat(
        object : LogcatInterface {
          override fun received(jsonPayload: String) {
            trySend(json.decodeFromString(jsonPayload))
          }
        },
      )
    }
  }

  private fun setAgeSecretKey(key: String?) {
    Bridge.nativeSetAgeSecretKey(key)
  }

  fun genX25519KeyPair(): AgeKeyPair {
    val payload =
      Bridge.nativeGenX25519KeyPair()
        ?: throw ClashException("Failed to generate Age X25519 key pair")
    return json.decodeFromString(payload)
  }

  fun genHybridKeyPair(): AgeKeyPair {
    val payload =
      Bridge.nativeGenHybridKeyPair()
        ?: throw ClashException("Failed to generate Age MLKEM768-X25519 key pair")
    return json.decodeFromString(payload)
  }

  fun verifySecretKeys(vararg secretKeys: String): Boolean {
    if (secretKeys.isEmpty()) return true
    return secretKeys.all { key ->
      key.isNotBlank() && Bridge.nativeVerifySecretKeys(key)
    }
  }

  fun toPublicKeys(vararg secretKeys: String): List<String> {
    if (secretKeys.isEmpty()) return emptyList()
    return secretKeys.flatMap { key ->
      Bridge.nativeToPublicKeys(key)
        ?.let { json.decodeFromString(ListSerializer(String.serializer()), it) }
        .orEmpty()
    }
  }

  fun verifyPublicKeys(vararg publicKeys: String): Boolean {
    if (publicKeys.isEmpty()) return true
    return publicKeys.all { key ->
      key.isNotBlank() && Bridge.nativeVerifyPublicKeys(key)
    }
  }
}

private val ageSecretKeyLock = Mutex()

private val json = Json { ignoreUnknownKeys = true }
