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
 */
object Fontes {

  private const val ORIGEM = "https://playerflix.ink"

  /** O endereco da pagina do player, que a origem usa para liberar. */
  private fun refererDaOrigem(tipo: Tipo, id: Int, temporada: Int, episodio: Int): String =
    if (tipo.ehSerie) "$ORIGEM/serie/$id/$temporada/$episodio" else "$ORIGEM/filme/$id"

  /** Um player que a origem conhece para este titulo. */
  private data class Opcao(val nome: String, val embed: String, val idioma: String)

  /**
   * Procura uma fonte que abra, e devolve a primeira que abrir.
   *
   * A ordem importa pouco, mas a conferida importa muito: achar o endereco e
   * o endereco funcionar sao coisas diferentes. A pagina do player entrega
   * um link bem formado mesmo quando o servidor de video, logo adiante, vai
   * recusar.
   *
   * Por isso cada endereco e testado com um pedido de dois bytes antes de
   * ser dado como bom. Se nenhum passar, devolvemos o primeiro que ao menos
   * apareceu - tentar e falhar e melhor do que dizer que nao existe.
   */
  suspend fun procurar(
    tipo: Tipo,
    id: Int,
    temporada: Int = 1,
    episodio: Int = 1
  ): Fonte? {
    val referer = refererDaOrigem(tipo, id, temporada, episodio)
    val opcoes = listar(tipo, id, temporada, episodio, referer)

    var reserva: Fonte? = null

    for (opcao in opcoes) {
      val url = try {
        when {
          opcao.nome.equals("WatchPlay", true) -> doWatchPlay(opcao.embed)
          opcao.nome.equals("VIP Player", true) -> doVip(opcao.embed)
          else -> null
        }
      } catch (e: Exception) {
        null
      } ?: continue

      val achada = Fonte(
        tipo = tipo,
        id = id,
        temporada = temporada,
        episodio = episodio,
        url = url,
        referer = opcao.embed,
        nome = opcao.nome
      )

      if (abre(url, opcao.embed)) return achada
      if (reserva == null) reserva = achada
    }

    return reserva
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
   * Ele aparece logo depois da marca de audio. Preferimos a faixa dublada
   * quando ela existe, que e o que a maioria das pessoas quer - e so caimos
   * em qualquer `.m3u8` quando nao ha marca nenhuma.
   */
  private suspend fun doWatchPlay(embed: String): String? {
    val pagina = Http.texto(
      embed,
      mapOf("accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    )

    val achado = DUBLADO.find(pagina) ?: QUALQUER.find(pagina) ?: return null
    // O endereco vem com as barras escapadas, como JavaScript escreve.
    return achado.groupValues[1].replace("\\/", "/")
  }

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
   * O endereco so vem de um pedido feito com esse codigo - e e por isso que
   * nao adianta ler a pagina dele procurando `.m3u8`.
   */
  private suspend fun doVip(embed: String): String? {
    val codigo = CODIGO_VIP.find(embed)?.groupValues?.get(1) ?: return null
    val base = try {
      val u = java.net.URL(embed)
      u.protocol + "://" + u.host
    } catch (e: Exception) {
      return null
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
    val url = json.optString("videoSource").ifBlank { json.optString("securedLink") }
    return url.ifBlank { null }
  }

  /**
   * O servidor de video atende neste endereco?
   *
   * Pedimos so os dois primeiros bytes, com `Range`: o que interessa e o
   * codigo da resposta, nao o conteudo. GET e nao HEAD de proposito, porque
   * ha servidor de video que recusa HEAD com 405 e nos faria descartar um
   * endereco bom.
   *
   * Qualquer tropeco conta como fechado - para quem vai assistir, da no
   * mesmo.
   */
  private suspend fun abre(url: String, referer: String): Boolean =
    withContext(Dispatchers.IO) {
      try {
        val pedido = Request.Builder()
          .url(url)
          .header("user-agent", Http.UA)
          .header("referer", referer)
          .header("range", "bytes=0-1")
          .build()
        Http.cliente.newCall(pedido).execute().use { r ->
          r.isSuccessful || r.code == 206
        }
      } catch (e: Exception) {
        false
      }
    }
}
