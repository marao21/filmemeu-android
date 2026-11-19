package com.filmemeu.app.data

import com.filmemeu.app.net.Http
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Onde a pessoa parou, na CONTA - e nao so neste aparelho.
 *
 * Ate aqui o aplicativo guardava isso em casa, nas preferencias do Android.
 * Funcionava enquanto se assistia sempre no mesmo lugar; mas quem comecava
 * um filme no computador e pegava o telefone encontrava a fileira
 * "Continuar assistindo" vazia, como se nunca tivesse assistido nada - e e
 * exatamente o que a tela do site mostra, da mesma conta, no mesmo minuto.
 *
 * Esta e a mesma tabela `progresso` que o site usa, com as mesmas colunas e
 * a mesma chave de conflito. O que e escrito aqui aparece la, e o que e
 * escrito la aparece aqui.
 */
object Progresso {

  private val JSON = "application/json; charset=utf-8".toMediaType()

  /** Quantos titulos a fileira mostra, como no site. */
  private const val QUANTOS = 20

  private const val COLUNAS =
    "tmdb_id,kind,titulo,poster,temporada,episodio,segundo,duracao,terminou,visto_em"

  /**
   * A fileira da conta, do mais recente para o mais antigo.
   *
   * Filme terminado sai da lista: ele ficaria ali dizendo "99%" para
   * sempre. Serie terminada fica, porque o proximo episodio e justamente o
   * que a pessoa volta para buscar.
   */
  suspend fun listar(): List<Marca> {
    val perfil = Conta.perfil?.id ?: return emptyList()
    if (!Conta.entrou) return emptyList()

    val url = Conta.base + "/rest/v1/progresso?select=" + COLUNAS +
      "&perfil_id=eq." + perfil +
      "&order=visto_em.desc&limit=" + QUANTOS

    val arr: JSONArray = Http.lista(url, Conta.cabecalhosDaConta())
    val saida = ArrayList<Marca>(arr.length())
    for (i in 0 until arr.length()) {
      val o = arr.optJSONObject(i) ?: continue
      val tipo = Tipo.de(o.optString("kind"))
      if (o.optBoolean("terminou") && tipo == Tipo.FILME) continue
      saida.add(
        Marca(
          tipo = tipo,
          id = o.optInt("tmdb_id"),
          titulo = o.optString("titulo").ifEmpty { "Sem nome" },
          cartaz = o.optString("poster").takeIf { it.isNotEmpty() && it != "null" },
          temporada = o.optInt("temporada", 1).coerceAtLeast(1),
          episodio = o.optInt("episodio", 1).coerceAtLeast(1),
          // O site conta em segundos; o player daqui, em milesimos.
          posicao = o.optLong("segundo") * 1000L,
          duracao = o.optLong("duracao") * 1000L,
          quando = instante(o.optString("visto_em"))
        )
      )
    }
    return saida
  }

  /**
   * Manda para a conta onde a pessoa parou.
   *
   * A chave de conflito e a mesma do site - conta, titulo e tipo -, entao
   * assistir de novo corrige a linha em vez de criar outra.
   */
  suspend fun guardar(marca: Marca, terminou: Boolean) {
    val dono = Conta.usuarioId ?: return
    val perfil = Conta.perfil?.id ?: return

    val linha = JSONObject()
      .put("user_id", dono)
      .put("perfil_id", perfil)
      .put("tmdb_id", marca.id)
      .put("kind", marca.tipo.chave)
      .put("titulo", marca.titulo)
      .put("poster", marca.cartaz ?: JSONObject.NULL)
      .put("temporada", if (marca.tipo.ehSerie) marca.temporada else JSONObject.NULL)
      .put("episodio", if (marca.tipo.ehSerie) marca.episodio else JSONObject.NULL)
      .put("segundo", Math.round(marca.posicao / 1000.0))
      .put("duracao", if (marca.duracao > 0) Math.round(marca.duracao / 1000.0) else JSONObject.NULL)
      .put("terminou", terminou)
      .put("visto_em", agoraEmTexto())

    Http.enviar(
      Conta.base + "/rest/v1/progresso?on_conflict=user_id,tmdb_id,kind",
      linha.toString().toRequestBody(JSON),
      Conta.cabecalhosDaConta() +
        ("Prefer" to "resolution=merge-duplicates,return=minimal")
    )
  }

  /* ----- datas ----- */

  private fun instante(texto: String?): Long {
    if (texto.isNullOrEmpty()) return 0L
    return try {
      // "2026-11-19T14:03:11.123456+00:00" e primo do formato que o Android
      // le; cortar os microssegundos e o fuso basta para comparar quem e
      // mais recente, que e tudo o que se pede dele aqui.
      val limpo = texto.substringBefore('.').substringBefore('+').trimEnd('Z')
      val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
      f.timeZone = java.util.TimeZone.getTimeZone("UTC")
      f.parse(limpo)?.time ?: 0L
    } catch (_: Exception) {
      0L
    }
  }

  private fun agoraEmTexto(): String {
    val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
    f.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return f.format(java.util.Date())
  }
}
