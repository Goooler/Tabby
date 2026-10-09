package com.github.kr328.clash.service

import android.app.PendingIntent
import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import co.touchlab.kermit.Logger
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.util.mainIntent
import com.github.kr328.clash.service.clash.ClashRuntime
import com.github.kr328.clash.service.clash.clashRuntime
import com.github.kr328.clash.service.clash.module.AppListCacheModule
import com.github.kr328.clash.service.clash.module.CloseModule
import com.github.kr328.clash.service.clash.module.ConfigurationModule
import com.github.kr328.clash.service.clash.module.DynamicNotificationModule
import com.github.kr328.clash.service.clash.module.NetworkObserveModule
import com.github.kr328.clash.service.clash.module.StaticNotificationModule
import com.github.kr328.clash.service.clash.module.SuspendModule
import com.github.kr328.clash.service.clash.module.TimeZoneModule
import com.github.kr328.clash.service.clash.module.TunModule
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.ClashServiceController
import com.github.kr328.clash.service.util.IPNet
import com.github.kr328.clash.service.util.cancelAndJoinBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class TunService : VpnService(), CoroutineScope by CoroutineScope(Dispatchers.Default) {
  private val self: TunService
    get() = this

  private var reason: String? = null
  private val controller = ClashServiceController(this) { runtime.launch() }

  private val runtime: ClashRuntime = clashRuntime {
    val store = ServiceStore(self)

    val close = install(CloseModule(self))
    val tun = install(TunModule(self))
    val config = install(ConfigurationModule(self))
    val network = install(NetworkObserveModule(self))

    if (store.dynamicNotification) install(DynamicNotificationModule(self))
    else install(StaticNotificationModule(self))

    install(AppListCacheModule(self))
    install(TimeZoneModule(self))
    install(SuspendModule(self))

    try {
      tun.open()

      while (isActive) {
        val quit = select {
          close.onEvent { true }
          config.onEvent {
            reason = it.message

            true
          }
          network.onEvent { n ->
            if (Build.VERSION.SDK_INT in 22..28) {
              setUnderlyingNetworks(arrayOf(n))
            }

            false
          }
        }

        if (quit) break
      }
    } catch (e: Exception) {
      Logger.e("Create clash runtime: ${e.message}", e)

      reason = e.message
    } finally {
      withContext(NonCancellable) {
        tun.close()
        controller.onFinished(reason)
      }
    }
  }

  override fun onCreate() {
    super.onCreate()

    StaticNotificationModule.createNotificationChannel(this)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    controller.onStart(startId)

    return super.onStartCommand(intent, flags, startId)
  }

  override fun onDestroy() {
    TunModule.requestStop()

    controller.onDestroy(reason)

    cancelAndJoinBlocking()

    Logger.i("TunService destroyed: ${reason ?: "successfully"}")

    super.onDestroy()
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)

    runtime.requestGc()
  }

  private fun TunModule.open() {
    val store = ServiceStore(self)

    val device =
      with(Builder()) {
        // Interface address
        addAddress(TUN_GATEWAY, TUN_SUBNET_PREFIX)
        if (store.allowIpv6) {
          addAddress(TUN_GATEWAY6, TUN_SUBNET_PREFIX6)
        }

        // Route
        if (store.bypassPrivateNetwork) {
          BYPASS_PRIVATE_ROUTE_V4.forEach { addRoute(it.ip, it.prefix) }
          if (store.allowIpv6) {
            BYPASS_PRIVATE_ROUTE_V6.forEach { addRoute(it.ip, it.prefix) }
          }

          // Route of virtual DNS
          addRoute(TUN_DNS, 32)
          if (store.allowIpv6) {
            addRoute(TUN_DNS6, 128)
          }
        } else {
          addRoute(NET_ANY, 0)
          if (store.allowIpv6) {
            addRoute(NET_ANY6, 0)
          }
        }

        // Access Control
        when (store.accessControlMode) {
          AcceptAll -> Unit
          AcceptSelected -> {
            (store.accessControlPackages + packageName).forEach {
              runCatching { addAllowedApplication(it) }
                .onFailure { e -> Logger.e("Add allowed application $it failed: ${e.message}", e) }
            }
          }
          DenySelected -> {
            (store.accessControlPackages - packageName).forEach {
              runCatching { addDisallowedApplication(it) }
                .onFailure { e ->
                  Logger.e("Add disallowed application $it failed: ${e.message}", e)
                }
            }
          }
        }

        // Blocking
        setBlocking(false)

        // Mtu
        setMtu(TUN_MTU)

        // Session Name
        setSession("Clash")

        // Virtual Dns Server
        addDnsServer(TUN_DNS)
        if (store.allowIpv6) {
          addDnsServer(TUN_DNS6)
        }

        // Open MainActivity
        setConfigureIntent(
          PendingIntent.getActivity(
            self,
            R.id.nf_vpn_status,
            mainIntent {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
          ),
        )

        // Metered
        if (Build.VERSION.SDK_INT >= 29) {
          setMetered(false)
        }

        // System Proxy
        if (Build.VERSION.SDK_INT >= 29 && store.systemProxy) {
          listenHttp()?.let {
            setHttpProxy(
              ProxyInfo.buildDirectProxy(
                it.address.hostAddress,
                it.port,
                HTTP_PROXY_BLACK_LIST +
                  if (store.bypassPrivateNetwork) HTTP_PROXY_LOCAL_LIST else emptyList(),
              ),
            )
          }
        }

        if (store.allowBypass) {
          allowBypass()
        }

        TunModule.TunDevice(
          fd =
            establish()?.detachFd()
              ?: throw NullPointerException("Establish VPN rejected by system"),
          stack = store.tunStackMode,
          gateway =
            "$TUN_GATEWAY/$TUN_SUBNET_PREFIX" +
              if (store.allowIpv6) ",$TUN_GATEWAY6/$TUN_SUBNET_PREFIX6" else "",
          portal = TUN_PORTAL + if (store.allowIpv6) ",$TUN_PORTAL6" else "",
          dns =
            if (store.dnsHijacking) NET_ANY
            else (TUN_DNS + if (store.allowIpv6) ",$TUN_DNS6" else ""),
        )
      }

    attach(device)
  }

  companion object {
    private const val TUN_MTU = 9000
    private const val TUN_SUBNET_PREFIX = 30
    private const val TUN_GATEWAY = "172.19.0.1"
    private const val TUN_SUBNET_PREFIX6 = 126
    private const val TUN_GATEWAY6 = "fdfe:dcba:9876::1"
    private const val TUN_PORTAL = "172.19.0.2"
    private const val TUN_PORTAL6 = "fdfe:dcba:9876::2"
    private const val TUN_DNS = TUN_PORTAL
    private const val TUN_DNS6 = TUN_PORTAL6
    private const val NET_ANY = "0.0.0.0"
    private const val NET_ANY6 = "::"
    /** Public IPv4 route set used when bypassing private and other special-use ranges. */
    private val BYPASS_PRIVATE_ROUTE_V4: List<IPNet> =
      listOf(
          "1.0.0.0/8",
          "2.0.0.0/7",
          "4.0.0.0/6",
          "8.0.0.0/7",
          "11.0.0.0/8",
          "12.0.0.0/6",
          "16.0.0.0/4",
          "32.0.0.0/3",
          "64.0.0.0/3",
          "96.0.0.0/4",
          "112.0.0.0/5",
          "120.0.0.0/6",
          "124.0.0.0/7",
          "126.0.0.0/8",
          "128.0.0.0/3",
          "160.0.0.0/5",
          "168.0.0.0/8",
          "169.0.0.0/9",
          "169.128.0.0/10",
          "169.192.0.0/11",
          "169.224.0.0/12",
          "169.240.0.0/13",
          "169.248.0.0/14",
          "169.252.0.0/15",
          "169.255.0.0/16",
          "170.0.0.0/7",
          "172.0.0.0/12",
          "172.32.0.0/11",
          "172.64.0.0/10",
          "172.128.0.0/9",
          "173.0.0.0/8",
          "174.0.0.0/7",
          "176.0.0.0/4",
          "192.0.0.0/9",
          "192.128.0.0/11",
          "192.160.0.0/13",
          "192.169.0.0/16",
          "192.170.0.0/15",
          "192.172.0.0/14",
          "192.176.0.0/12",
          "192.192.0.0/10",
          "193.0.0.0/8",
          "194.0.0.0/7",
          "196.0.0.0/6",
          "200.0.0.0/5",
          "208.0.0.0/4",
          "240.0.0.0/5",
          "248.0.0.0/6",
          "252.0.0.0/7",
          "254.0.0.0/8",
          "255.0.0.0/9",
          "255.128.0.0/10",
          "255.192.0.0/11",
          "255.224.0.0/12",
          "255.240.0.0/13",
          "255.248.0.0/14",
          "255.252.0.0/15",
          "255.254.0.0/16",
          "255.255.0.0/17",
          "255.255.128.0/18",
          "255.255.192.0/19",
          "255.255.224.0/20",
          "255.255.240.0/21",
          "255.255.248.0/22",
          "255.255.252.0/23",
          "255.255.254.0/24",
          "255.255.255.0/25",
          "255.255.255.128/26",
          "255.255.255.192/27",
          "255.255.255.224/28",
          "255.255.255.240/29",
          "255.255.255.248/30",
          "255.255.255.252/31",
          "255.255.255.254/32",
        )
        .map(IPNet::parse)
    /** Exclude fc00::/7, fe80::/10, ff00::/8 */
    private val BYPASS_PRIVATE_ROUTE_V6: List<IPNet> =
      listOf(
          "::/1",
          "8000::/2",
          "c000::/3",
          "e000::/4",
          "f000::/5",
          "f800::/6",
          "fe00::/9",
          "fec0::/10",
        )
        .map(IPNet::parse)
    private val HTTP_PROXY_LOCAL_LIST: List<String> =
      listOf(
        "localhost",
        "*.local",
        "127.*",
        "10.*",
        "172.16.*",
        "172.17.*",
        "172.18.*",
        "172.19.*",
        "172.2*",
        "172.30.*",
        "172.31.*",
        "192.168.*",
      )
    private val HTTP_PROXY_BLACK_LIST: List<String> =
      listOf("*zhihu.com", "*zhimg.com", "*jd.com", "100ime-iat-api.xfyun.cn", "*360buyimg.com")
  }
}
