package com.neuron.academy.net

import android.content.Context
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Доверие к системным сертификатам + к корневым сертификатам Минцифры из assets/certs
 * (нужны для GigaChat, серверы которого подписаны российским УЦ). Если файлов нет — только системные.
 */
object TrustedCerts {
    fun create(context: Context): Pair<SSLSocketFactory, X509TrustManager>? {
        val certs = try {
            val cf = CertificateFactory.getInstance("X.509")
            context.assets.list("certs").orEmpty().flatMap { name ->
                context.assets.open("certs/$name").use { cf.generateCertificates(it).filterIsInstance<X509Certificate>() }
            }
        } catch (e: Exception) {
            emptyList()
        }
        if (certs.isEmpty()) return null
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            certs.forEachIndexed { i, c -> setCertificateEntry("ru_$i", c) }
        }
        val custom = tmFrom(ks)
        val system = tmFrom(null)
        val composite = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = system.checkClientTrusted(chain, authType)
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                try {
                    system.checkServerTrusted(chain, authType)
                } catch (e: CertificateException) {
                    custom.checkServerTrusted(chain, authType)
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers + custom.acceptedIssuers
        }
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(composite), null) }
        return ctx.socketFactory to composite
    }

    private fun tmFrom(ks: KeyStore?): X509TrustManager {
        val f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        f.init(ks)
        return f.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}
