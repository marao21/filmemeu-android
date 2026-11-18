package com.filmemeu.app.media

import com.filmemeu.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URI

/**
 * Quanto tempo tem um video, sem baixar o video.
 *
 * E a traducao do api/_lib/media.js: em HLS, somando os #EXTINF da
 * playlist; em MP4, lendo o box mvhd com pedidos Range. Nada do filme e
 * baixado por inteiro — sao alguns bytes, nao o video.
 */
object Medida {

  /**
   * Se o endereco e uma playlist HLS.
   *
   * Nao da para ir so pela extensao: ha servidor que entrega a playlist num
   * arquivo chamado master.txt, que por dentro e um #EXTM3U como qualquer
   * outro. Lido como arquivo de video comum, ele nao toca.
   */
  fun pareceHls(url: String): Boolean =
    Regex("\\.m3u8(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url) ||
      Regex("/hls/", RegexOption.IGNORE_CASE).containsMatchIn(url) ||
      Regex("master\\.txt(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)

  /** Resolve um caminho relativo da playlist contra o endereco dela. */
  fun resolver(base: String, caminho: String): String = try {
    URI(base).resolve(caminho).toString()
  } catch (_: Exception) {
    caminho
  }

  /**
   * A duracao, em segundos, ou nulo quando nao da para medir.
   *
   * Quando nao da, o site mostrava "duracao ?" em vez de inventar um
   * numero: medir errado e pior do que nao medir.
   */
  suspend fun duracao(url: String, referer: String? = null): Double? =
    if (pareceHls(url)) duracaoHls(url, referer) else duracaoMp4(url, referer)

  private fun cabecalhos(referer: String?): Map<String, String> =
    if (referer.isNullOrEmpty()) emptyMap() else mapOf("Referer" to referer)

  /**
   * HLS: baixa a playlist e soma os #EXTINF.
   *
   * Sendo uma master playlist, ela nao tem duracao nenhuma: tem outras
   * playlists dentro. Nesse caso seguimos a primeira variante.
   */
  suspend fun duracaoHls(url: String, referer: String? = null, voltas: Int = 0): Double? {
    if (voltas > 2) return null
    val texto = try {
      Http.texto(url, cabecalhos(referer))
    } catch (_: Exception) {
      return null
    }
    if (!texto.contains("#EXTM3U")) return null

    var soma = 0.0
    var achou = false
    val linhas = texto.lines()

    for (linha in linhas) {
      val corte = linha.trim()
      if (corte.startsWith("#EXTINF:")) {
        val valor = corte.removePrefix("#EXTINF:").substringBefore(",").trim()
        val segundos = valor.toDoubleOrNull()
        if (segundos != null) {
          soma += segundos
          achou = true
        }
      }
    }
    if (achou) return soma

    // E uma master playlist: segue a primeira variante que aparecer.
    val variante = linhas.map { it.trim() }
      .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
      ?: return null
    return duracaoHls(resolver(url, variante), referer, voltas + 1)
  }

  /**
   * MP4: le o box mvhd.
   *
   * O mvhd guarda a escala de tempo e a duracao do filme. Costuma estar no
   * comeco do arquivo, dentro do moov; quando o moov esta no fim, pedimos o
   * fim tambem. Dois pedidos de alguns quilobytes, e nada mais.
   */
  suspend fun duracaoMp4(url: String, referer: String? = null): Double? {
    val comeco = pedaco(url, "bytes=0-262143", referer) ?: return null
    daCabeca(comeco)?.let { return it }

    val fim = pedaco(url, "bytes=-262144", referer) ?: return null
    return daCabeca(fim)
  }

  private suspend fun pedaco(url: String, faixa: String, referer: String?): ByteArray? =
    withContext(Dispatchers.IO) {
      try {
        val b = Request.Builder().url(url)
          .header("user-agent", Http.UA)
          .header("range", faixa)
        if (!referer.isNullOrEmpty()) b.header("referer", referer)
        Http.cliente.newCall(b.build()).execute().use { r ->
          if (!r.isSuccessful && r.code != 206) return@withContext null
          r.body?.bytes()
        }
      } catch (_: Exception) {
        null
      }
    }

  /** Acha o mvhd no pedaco e tira dele a duracao. */
  private fun daCabeca(bytes: ByteArray): Double? {
    var i = 0
    while (i + 8 < bytes.size) {
      if (bytes[i] == 'm'.code.toByte() && bytes[i + 1] == 'v'.code.toByte() &&
        bytes[i + 2] == 'h'.code.toByte() && bytes[i + 3] == 'd'.code.toByte()
      ) {
        val versao = bytes.getOrNull(i + 4)?.toInt() ?: return null
        return if (versao == 1) {
          // versao 1: escala em 20, duracao de 64 bits em 24
          if (i + 32 > bytes.size) return null
          val escala = lerInt(bytes, i + 20)
          val duracao = lerLong(bytes, i + 24)
          if (escala <= 0) null else duracao.toDouble() / escala
        } else {
          // versao 0: escala em 16, duracao de 32 bits em 20
          if (i + 24 > bytes.size) return null
          val escala = lerInt(bytes, i + 16)
          val duracao = lerInt(bytes, i + 20)
          if (escala <= 0) null else duracao.toDouble() / escala
        }
      }
      i++
    }
    return null
  }

  private fun lerInt(b: ByteArray, em: Int): Int =
    ((b[em].toInt() and 0xFF) shl 24) or
      ((b[em + 1].toInt() and 0xFF) shl 16) or
      ((b[em + 2].toInt() and 0xFF) shl 8) or
      (b[em + 3].toInt() and 0xFF)

  private fun lerLong(b: ByteArray, em: Int): Long {
    var v = 0L
    for (k in 0 until 8) v = (v shl 8) or (b[em + k].toLong() and 0xFF)
    return v
  }

  /** 1h 52min, como o site escreve. */
  fun emPalavras(segundos: Double?): String {
    if (segundos == null || segundos <= 0) return "duracao ?"
    val total = segundos.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    return if (h > 0) h.toString() + "h " + m + "min" else m.toString() + "min"
  }
}
