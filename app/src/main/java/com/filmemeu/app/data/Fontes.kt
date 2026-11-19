package com.filmemeu.app.data

import com.filmemeu.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/**
 * Onde o video de cada titulo e encontrado.
 *
 * No site isto era uma funcao na Vercel: o navegador pedia `/api/fonte` e o
 * nosso servidor ia conversar com a origem. Aqui nao ha servidor nosso no
 * meio - o aparelho pergunta direto, e e isso que tira o video da conta da
 * hospedagem.
 *
 * E da para ser direto por um motivo simples: a regra que obriga o
 * navegador a pedir licenca ao servidor de video nao vale fora do
 * navegador. O mesmo endereco que o site precisa buscar por desvio, o
 * aplicativo busca de frente.
 *
 * A origem lista varios players por titulo. Sabemos abrir dois:
 *
 *  - WatchPlay, que escreve o endereco na propria pagina do player;
 *  - VIP Player, que entrega um codigo e so devolve o endereco se a gente
 *    pedir com ele.
 *
 * O terceiro, o Premium, fica de fora: ele exige um navegador de verdade
 * para passar por um desafio de robo, e isso nao e caminho que a gente
 * percorre.
 *
 * Credito: o jeito de conversar com a playerflix - a rota de listagem e o
 * formato dos dois extratores - foi aprendido do projeto embed-movies, de
 * Jonas Caetano (github.com/JonasCaetanoSz/embed-movies), sob licenca MIT.
 * O codigo aqui e nosso e em Kotlin; a descoberta do caminho e dele.
 */
object Fontes {

  private const val ORIGEM = "https://playerflix.ink"

  /** O endereco da pagina do player, que a origem usa para liberar. */
  private fun refererDaOrigem(tipo: Tipo, id: Int, temporada: Int, episodio: Int): String =
    if (tipo.ehSerie) "$ORIGEM/serie/$id/$temporada/$episodio" else "$ORIGEM/filme/$id"

  /** Um player que a origem conhece para este titulo. */
  private data class Opcao(val nome: String, val embed: String, val idioma: String)

  /** Resultado da conferencia real do endereco antes de entregar ao player. */
  sealed class ResultadoVerificacao {
    data class Valida(
      val urlFinal: String,
      val referer: String?,
      val ehHls: Boolean
    ) : ResultadoVerificacao()

    data class Invalida(val motivo: String) : ResultadoVerificacao()
  }

  /**
   * Procura uma fonte que abra de verdade, e devolve a primeira que passar
   * na conferencia de conteudo.
   *
   * Nunca devolve um endereco que ja falhou na conferencia (por exemplo, um
   * servidor devolvendo HTML ou 403 no lugar de `#EXTM3U`), para que o player
   * nao tente abrir uma pagina de erro como se fosse playlist HLS.
   */
  suspend fun procurar(
    tipo: Tipo,
    id: Int,
    temporada: Int = 1,
    episodio: Int = 1,
    ignorarUrl: String? = null
  ): Fonte? {
    val referer = refererDaOrigem(tipo, id, temporada, episodio)
    val opcoes = listar(tipo, id, temporada, episodio, referer)

    for (opcao in opcoes) {
      val candidatos = try {
        when {
          opcao.nome.equals("WatchPlay", true) -> doWatchPlay(opcao.embed)
          opcao.nome.equals("VIP Player", true) -> doVip(opcao.embed)
          else -> emptyList()
        }
      } catch (_: Exception) {
        emptyList()
      }

      for (saida in candidatos) {
        if (!ignorarUrl.isNullOrBlank() && saida.url == ignorarUrl) continue
        val ver = verificar(saida.url, saida.referer)
        if (ver is ResultadoVerificacao.Valida) {
          return Fonte(
            tipo = tipo,
            id = id,
            temporada = temporada,
            episodio = episodio,
            url = ver.urlFinal,
            referer = ver.referer,
            nome = opcao.nome
          )
        }
      }
    }

    return null
  }

  /** Os players que a origem conhece para este titulo. */
  private suspend fun listar(
    tipo: Tipo,
    id: Int,
    temporada: Int,
    episodio: Int,
    referer: String
  ): List<Opcao> {
    val serie = tipo.ehSerie
    val endereco = ORIGEM + "/inc/Ajax.php" +
      "?type=" + (if (serie) "tv" else "movie") +
      "&id=" + id +
      "&season=" + (if (serie) temporada.toString() else "null") +
      "&episode=" + (if (serie) episodio.toString() else "null")

    val corpo = Http.texto(
      endereco,
      mapOf(
        "accept" to "*/*",
        "referer" to referer,
        "x-requested-with" to "XMLHttpRequest"
      )
    )

    val opcoes = JSONObject(corpo)
      .optJSONObject("data")
      ?.optJSONArray("options")
      ?: return emptyList()

    return (0 until opcoes.length()).mapNotNull { i ->
      val o = opcoes.optJSONObject(i) ?: return@mapNotNull null
      val embed = o.optString("embed")
      val nome = o.optString("label")
      if (embed.isBlank() || nome.isBlank()) null
      else Opcao(nome, embed, o.optString("lang"))
    }
  }

  /**
   * WatchPlay: o endereco esta escrito na propria pagina do player.
   *
   * Preferimos a faixa dublada quando existe e mantemos outros `.m3u8` da
   * pagina como alternativas caso a primeira nao abra.
   */
  private suspend fun doWatchPlay(embed: String): List<Achado> = withContext(Dispatchers.IO) {
    val pedido = Request.Builder()
      .url(embed)
      .header("user-agent", Http.UA)
      .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      .build()

    Http.cliente.newCall(pedido).execute().use { r ->
      if (!r.isSuccessful) return@use emptyList()
      val pagina = r.body?.string() ?: return@use emptyList()
      val refFinal = r.request.url.toString()

      val urls = LinkedHashSet<String>()
      DUBLADO.find(pagina)?.groupValues?.getOrNull(1)
        ?.replace("\\/", "/")?.trim()
        ?.takeIf { it.startsWith("http") }
        ?.let(urls::add)

      QUALQUER.findAll(pagina).forEach { m ->
        m.groupValues.getOrNull(1)
          ?.replace("\\/", "/")?.trim()
          ?.takeIf { it.startsWith("http") }
          ?.let(urls::add)
      }

      urls.map { Achado(url = it, referer = refFinal) }
    }
  }

  /** Um endereco de video e o referer com que ele deve ser pedido. */
  private data class Achado(val url: String, val referer: String)

  private val DUBLADO = Regex(
    "window\\.MyPlayerAudio=\"Dublado\"[\\s\\S]*?url:\"(https?:\\\\?/\\\\?/[^\"]+)\""
  )
  private val QUALQUER = Regex(
    "url:\"(https?:\\\\?/\\\\?/[^\"]+\\.m3u8[^\"]*)\""
  )
  private val CODIGO_VIP = Regex("/video/([a-f0-9]+)", RegexOption.IGNORE_CASE)

  /**
   * VIP Player: a pagina nao traz o endereco, traz um codigo.
   *
   * Devolve todas as URLs distintas informadas (`videoSource` e `securedLink`)
   * para testar qual delas realmente entrega um stream valido.
   */
  private suspend fun doVip(embed: String): List<Achado> {
    val codigo = CODIGO_VIP.find(embed)?.groupValues?.get(1) ?: return emptyList()
    val base = try {
      val u = java.net.URL(embed)
      u.protocol + "://" + u.host
    } catch (_: Exception) {
      return emptyList()
    }

    val resposta = Http.enviar(
      url = "$base/player/index.php?data=$codigo&do=getVideo",
      corpo = FormBody.Builder().add("hash", codigo).add("r", "").build(),
      cabecalhos = mapOf(
        "accept" to "*/*",
        "origin" to base,
        "referer" to embed,
        "x-requested-with" to "XMLHttpRequest"
      )
    )

    val json = JSONObject(resposta)
    val urls = LinkedHashSet<String>()
    json.optString("videoSource").trim().takeIf { it.startsWith("http") }?.let(urls::add)
    json.optString("securedLink").trim().takeIf { it.startsWith("http") }?.let(urls::add)
    return urls.map { Achado(url = it, referer = embed) }
  }

  /**
   * Verifica se o endereco realmente entrega um stream reproduzivel.
   *
   * - Se o corpo comeca com `#EXTM3U` (inclusive removendo BOM UTF-8): confirma
   *   HLS. Quando for uma master playlist (`#EXT-X-STREAM-INF`), tambem confere
   *   a primeira variante para evitar falha do `HlsPlaylistParser` logo apos o
   *   inicio.
   * - Se nao comeca com `#EXTM3U`: rejeita respostas HTML/JSON/texto de erro ou
   *   URLs que se dizem playlist sem entregar `#EXTM3U`; aceita streams binarios
   *   diretos (MP4/MKV/etc.) como nao-HLS.
   */
  suspend fun verificar(
    url: String,
    referer: String?,
    profundidade: Int = 0
  ): ResultadoVerificacao = withContext(Dispatchers.IO) {
    if (profundidade > 2) return@withContext ResultadoVerificacao.Invalida("Redirecionamento excessivo")
    try {
      val b = Request.Builder().url(url)
      Http.cabecalhosVideo(referer).forEach { (k, v) -> b.header(k, v) }

      Http.cliente.newCall(b.build()).execute().use { r ->
        if (!r.isSuccessful) {
          return@use ResultadoVerificacao.Invalida("servidor respondeu HTTP ${r.code}")
        }

        val finalUrl = r.request.url.toString()
        val contentType = r.header("Content-Type")
          ?.lowercase()
          ?.substringBefore(";")
          ?.trim()
          .orEmpty()

        val amostraBytes = r.peekBody(512).bytes()
        if (amostraBytes.isEmpty()) {
          return@use ResultadoVerificacao.Invalida("resposta vazia")
        }

        val inicio = String(amostraBytes, Charsets.UTF_8)
          .trimStart('\uFEFF')
          .trimStart()

        if (inicio.startsWith("#EXTM3U")) {
          // Se for master playlist, valida a primeira variante antes de tocar.
          if (profundidade == 0) {
            val textoPlaylist = r.peekBody(16_384).string()
              .trimStart('\uFEFF')
            val variante = primeiraVarianteHls(textoPlaylist)
            if (variante != null) {
              val urlVariante = r.request.url.resolve(variante)?.toString() ?: variante
              val verVariante = verificar(urlVariante, referer, profundidade + 1)
              if (verVariante !is ResultadoVerificacao.Valida || !verVariante.ehHls) {
                val motivo = (verVariante as? ResultadoVerificacao.Invalida)?.motivo
                  ?: "variante HLS invalida"
                return@use ResultadoVerificacao.Invalida(motivo)
              }
            }
          }
          return@use ResultadoVerificacao.Valida(
            urlFinal = finalUrl,
            referer = referer,
            ehHls = true
          )
        }

        val pareceTextoOuErro =
          contentType.startsWith("text/") ||
            contentType.contains("json") ||
            contentType.contains("xml") ||
            contentType.contains("javascript") ||
            inicio.startsWith("<!", ignoreCase = true) ||
            inicio.startsWith("<html", ignoreCase = true) ||
            inicio.startsWith("<head", ignoreCase = true) ||
            inicio.startsWith("<body", ignoreCase = true) ||
            inicio.startsWith("{") ||
            inicio.startsWith("[")

        if (pareceTextoOuErro) {
          return@use ResultadoVerificacao.Invalida("resposta nao e um video valido")
        }

        val ehVideoBinario =
          contentType.startsWith("video/") ||
            contentType.startsWith("audio/") ||
            pareceBinarioVideo(amostraBytes)

        val pareciaPlaylist = url.substringBefore("?").lowercase().let {
          it.endsWith(".m3u8") || it.endsWith("master.txt") || it.contains("/hls/")
        } || contentType.contains("mpegurl")

        if (pareciaPlaylist && !ehVideoBinario) {
          return@use ResultadoVerificacao.Invalida("playlist HLS sem cabecalho #EXTM3U")
        }

        ResultadoVerificacao.Valida(
          urlFinal = finalUrl,
          referer = referer,
          ehHls = false
        )
      }
    } catch (e: Exception) {
      ResultadoVerificacao.Invalida(e.message ?: "falha de conexao")
    }
  }

  private fun primeiraVarianteHls(playlist: String): String? {
    val linhas = playlist.lines()
    var aguardandoUri = false
    for (linha in linhas) {
      val t = linha.trim()
      if (t.isEmpty()) continue
      if (t.startsWith("#EXT-X-STREAM-INF")) {
        aguardandoUri = true
        continue
      }
      if (aguardandoUri && !t.startsWith("#")) {
        return t
      }
    }
    return null
  }

  private fun pareceBinarioVideo(bytes: ByteArray): Boolean {
    if (bytes.size >= 8 &&
      bytes[4] == 'f'.code.toByte() &&
      bytes[5] == 't'.code.toByte() &&
      bytes[6] == 'y'.code.toByte() &&
      bytes[7] == 'p'.code.toByte()
    ) return true // MP4 / QuickTime / M4V
    if (bytes.size >= 4 &&
      bytes[0] == 0x1A.toByte() &&
      bytes[1] == 0x45.toByte() &&
      bytes[2] == 0xDF.toByte() &&
      bytes[3] == 0xA3.toByte()
    ) return true // MKV / WebM
    if (bytes.size >= 189 && bytes[0] == 0x47.toByte() && bytes[188] == 0x47.toByte()) {
      return true // MPEG-TS
    }
    return false
  }
}
