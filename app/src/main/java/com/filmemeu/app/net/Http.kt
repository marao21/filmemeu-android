package com.filmemeu.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * O unico caminho de rede do aplicativo.
 *
 * No site, cada pedido ao TMDB passava por uma funcao na Vercel. Aqui nao
 * existe servidor nosso no meio: o aparelho fala direto com quem tem a
 * resposta, e este objeto e todo o encanamento que isso precisa.
 */
object Http {

  /**
   * Os cabecalhos que um navegador manda.
   *
   * Servidor de video responde conforme quem pergunta: sem isto, o mesmo
   * endereco que toca no navegador volta 403 para o aplicativo.
   */
  const val UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

  val cliente: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  fun originDe(referer: String?): String? {
    if (referer.isNullOrBlank()) return null
    return try {
      val u = java.net.URI(referer)
      val scheme = u.scheme ?: return null
      val host = u.host ?: return null
      val port = if (u.port > 0 && u.port != 80 && u.port != 443) ":${u.port}" else ""
      "$scheme://$host$port"
    } catch (_: Exception) {
      null
    }
  }

  /** Cabecalhos padrao de navegador para pedidos de video e playlists HLS. */
  fun cabecalhosVideo(referer: String?): Map<String, String> {
    val m = LinkedHashMap<String, String>()
    m["User-Agent"] = UA
    m["Accept"] = "*/*"
    m["Accept-Language"] = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7"
    if (!referer.isNullOrBlank()) {
      m["Referer"] = referer
      originDe(referer)?.let { m["Origin"] = it }
    }
    return m
  }

  private fun pedido(url: String, cabecalhos: Map<String, String>): Request {
    val b = Request.Builder().url(url)
      .header("user-agent", UA)
      .header("accept-language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
    cabecalhos.forEach { (k, v) -> b.header(k, v) }
    return b.build()
  }

  suspend fun texto(url: String, cabecalhos: Map<String, String> = emptyMap()): String =
    withContext(Dispatchers.IO) {
      cliente.newCall(pedido(url, cabecalhos)).execute().use { r ->
        val corpo = r.body?.string() ?: ""
        if (!r.isSuccessful) throw HttpErro(r.code, corpo)
        corpo
      }
    }

  suspend fun objeto(url: String, cabecalhos: Map<String, String> = emptyMap()): JSONObject =
    JSONObject(texto(url, cabecalhos))

  suspend fun lista(url: String, cabecalhos: Map<String, String> = emptyMap()): JSONArray =
    JSONArray(texto(url, cabecalhos))

  suspend fun enviar(
    url: String,
    corpo: RequestBody,
    cabecalhos: Map<String, String> = emptyMap(),
    metodo: String = "POST"
  ): String = withContext(Dispatchers.IO) {
    val b = Request.Builder().url(url).method(metodo, corpo)
      .header("user-agent", UA)
    cabecalhos.forEach { (k, v) -> b.header(k, v) }
    cliente.newCall(b.build()).execute().use { r ->
      val txt = r.body?.string() ?: ""
      if (!r.isSuccessful) throw HttpErro(r.code, txt)
      txt
    }
  }
}

/** O codigo importa: 401 quer dizer "entre de novo", e 404 nao. */
class HttpErro(val codigo: Int, val corpo: String) : Exception("HTTP $codigo: $corpo")
