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
   * Este endereco entrega mesmo um video?
   *
   * Conferimos o CONTEUDO, e nao so o codigo da resposta - e essa
   * diferenca e a que faz o filme tocar.
   *
   * A primeira versao pedia dois bytes com `Range` e aceitava qualquer
   * resposta bem-sucedida. Dois problemas apareceram no aparelho:
   *
   * Servidor de video assinado costuma recusar pedido parcial, entao a
   * conferida reprovava endereco bom. Reprovando os dois, a reserva ficava
   * sendo o primeiro da fila - e o primeiro e o que estava fechado.
   *
   * E um 403 tambem "responde": vem com uma pagina de erro no corpo. Pelo
   * codigo da resposta da para enganar; pelo conteudo, nao. O player
   * recebia essa pagina e dizia o obvio:
   *
   *   ParserException: Input does not start with the #EXTM3U header
   *
   * Agora lemos o comeco do arquivo. Playlist de verdade abre com
   * `#EXTM3U`; qualquer outra coisa, por mais que o servidor diga 200,
   * nao e video.
   *
   * Para `.mp4` o teste e outro: ali basta a resposta vir bem, porque o
   * conteudo e binario e nao tem marca de texto para conferir.
   */
  private suspend fun abre(url: String, referer: String): Boolean =
    withContext(Dispatchers.IO) {
      try {
        val pedido = Request.Builder()
          .url(url)
          .header("user-agent", Http.UA)
          .header("referer", referer)
          .build()
        Http.cliente.newCall(pedido).execute().use { r ->
          if (!r.isSuccessful) return@use false

          val playlist = url.substringBefore("?").lowercase().let {
            it.endsWith(".m3u8") || it.endsWith("master.txt") || it.contains("/hls/")
          }
          if (!playlist) return@use true

          // So o comeco interessa. peekBody le ate esse tanto sem consumir
          // o corpo e sem quebrar quando o arquivo e menor que o pedido.
          val inicio = r.peekBody(64).string()
          inicio.trimStart().startsWith("#EXTM3U")
        }
      } catch (e: Exception) {
        false
      }
    }
}
