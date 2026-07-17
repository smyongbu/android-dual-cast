package com.example.androiddualcast.receiver.adb;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 通过 Android 无线调试的 mDNS 服务自动查找动态连接端口。 */
public final class AdbPortDiscovery {
    private AdbPortDiscovery() {}

    public static int discover(Context context, String expectedIp, long timeoutMs) {
        NsdManager nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) return -1;
        CountDownLatch found = new CountDownLatch(1);
        AtomicInteger port = new AtomicInteger(-1);
        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String type) {}
            @Override public void onDiscoveryStopped(String type) {}
            @Override public void onStartDiscoveryFailed(String type, int code) { found.countDown(); }
            @Override public void onStopDiscoveryFailed(String type, int code) {}
            @Override public void onServiceLost(NsdServiceInfo service) {}
            @Override public void onServiceFound(NsdServiceInfo service) {
                nsd.resolveService(service, new NsdManager.ResolveListener() {
                    @Override public void onResolveFailed(NsdServiceInfo info, int code) {}
                    @Override public void onServiceResolved(NsdServiceInfo info) {
                        InetAddress host = info.getHost();
                        if (host != null && (expectedIp == null || expectedIp.equals(host.getHostAddress()))) {
                            port.set(info.getPort()); found.countDown();
                        }
                    }
                });
            }
        };
        try {
            nsd.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, listener);
            found.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        } finally {
            try { nsd.stopServiceDiscovery(listener); } catch (Exception ignored) {}
        }
        return port.get();
    }
}
