package com.filmemeu.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * O que fica no aparelho: onde parou, a Minha MovFlix e as fontes.
 *
 * No site isto vivia no localStorage do navegador. Aqui e um arquivo de
 * preferencias do proprio Android, por perfil, para que o que uma pessoa
 * da casa deixou pela metade nao apareca na tela da outra.
 */
object Guardados {

  private fun prefs(ctx: Context) =
    ctx.getSharedPreferences("guardados", Context.MODE_PRIVATE)

  /** Cada perfil tem a sua gaveta. */
  private fun gaveta(nome: String): String {
    val p = Conta.perfil?.id ?: "sozinho"
    return nome + ":" + p
  }

  /* ----- continuar assistindo ----- */

  private fun marcaDe(o: JSONObject) = Marca(
    tipo = Tipo.de(o.optString("tipo")),
    id = o.optInt("id"),
    titulo = o.optString("titulo"),
    cartaz = o.optString("cartaz").takeIf { it.isNotEmpty() && it != "null" },
    temporada = o.optInt("temporada", 1),
    episodio = o.optInt("episodio", 1),
    posicao = o.optLong("posicao"),
    duracao = o.optLong("duracao"),
    quando = o.optLong("quando")
  )

  private fun jsonDe(m: Marca) = JSONObject()
    .put("tipo", m.tipo.chave)
    .put("id", m.id)
    .put("titulo", m.titulo)
    .put("cartaz", m.cartaz ?: "")
    .put("temporada", m.temporada)
    .put("episodio", m.episodio)
    .put("posicao", m.posicao)
    .put("duracao", m.duracao)
    .put("quando", m.quando)

  fun continuar(ctx: Context): List<Marca> {
    val cru = prefs(ctx).getString(gaveta("continuar"), null) ?: return emptyList()
    return try {
      val arr = JSONArray(cru)
      val saida = ArrayList<Marca>(arr.length())
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        saida.add(marcaDe(o))
      }
      saida.sortedByDescending { it.quando }
    } catch (_: Exception) {
      emptyList()
    }
  }

  /**
   * Guarda onde a pessoa parou.
   *
   * O fim nao conta: terminado o filme, ele sai da fileira em vez de ficar
   * ali dizendo "99%" para sempre. E o comeco tambem nao: trinta segundos
   * de quem abriu e desistiu nao e "continuar assistindo".
   */
  fun marcar(ctx: Context, marca: Marca) {
    val lista = continuar(ctx).toMutableList()
    lista.removeAll { it.tipo == marca.tipo && it.id == marca.id }

    val fracao = marca.fracao
    val vale = marca.posicao > 30_000 && fracao < 0.95f
    if (vale) lista.add(0, marca)

    val arr = JSONArray()
    lista.take(20).forEach { arr.put(jsonDe(it)) }
    prefs(ctx).edit().putString(gaveta("continuar"), arr.toString()).apply()
  }

  fun posicaoDe(ctx: Context, tipo: Tipo, id: Int, temporada: Int, episodio: Int): Long {
    val m = continuar(ctx).firstOrNull {
      it.tipo == tipo && it.id == id &&
        (!tipo.ehSerie || (it.temporada == temporada && it.episodio == episodio))
    }
    return m?.posicao ?: 0L
  }

  fun esquecer(ctx: Context, tipo: Tipo, id: Int) {
    val lista = continuar(ctx).toMutableList()
    lista.removeAll { it.tipo == tipo && it.id == id }
    val arr = JSONArray()
    lista.forEach { arr.put(jsonDe(it)) }
    prefs(ctx).edit().putString(gaveta("continuar"), arr.toString()).apply()
  }

  /* ----- Minha MovFlix ----- */

  fun minha(ctx: Context): List<Item> {
    val cru = prefs(ctx).getString(gaveta("minha"), null) ?: return emptyList()
    return try {
      val arr = JSONArray(cru)
      val saida = ArrayList<Item>(arr.length())
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        saida.add(
          Item(
            id = o.optInt("id"),
            tipo = Tipo.de(o.optString("tipo")),
            titulo = o.optString("titulo"),
            ano = o.optInt("ano").takeIf { it > 0 },
            nota = o.optDouble("nota").takeIf { it > 0 },
            cartaz = o.optString("cartaz").takeIf { it.isNotEmpty() },
            fundo = o.optString("fundo").takeIf { it.isNotEmpty() },
            sinopse = o.optString("sinopse")
          )
        )
      }
      saida
    } catch (_: Exception) {
      emptyList()
    }
  }

  fun estaNaMinha(ctx: Context, tipo: Tipo, id: Int): Boolean =
    minha(ctx).any { it.tipo == tipo && it.id == id }

  /** Entra ou sai da lista, e devolve se ficou dentro. */
  fun virarNaMinha(ctx: Context, item: Item): Boolean {
    val lista = minha(ctx).toMutableList()
    val tinha = lista.removeAll { it.tipo == item.tipo && it.id == item.id }
    if (!tinha) lista.add(0, item)

    val arr = JSONArray()
    lista.forEach {
      arr.put(
        JSONObject()
          .put("id", it.id).put("tipo", it.tipo.chave).put("titulo", it.titulo)
          .put("ano", it.ano ?: 0).put("nota", it.nota ?: 0.0)
          .put("cartaz", it.cartaz ?: "").put("fundo", it.fundo ?: "")
          .put("sinopse", it.sinopse)
      )
    }
    prefs(ctx).edit().putString(gaveta("minha"), arr.toString()).apply()
    return !tinha
  }

  /* ----- as fontes de video ----- */

  /**
   * O endereco do video de cada titulo, informado por quem usa o aplicativo.
   *
   * Guardado por titulo, temporada e episodio, para que escolher Assistir
   * de novo nao peca o endereco outra vez.
   */
  fun fonte(ctx: Context, tipo: Tipo, id: Int, temporada: Int, episodio: Int): Fonte? {
    val chave = Fonte.chaveDe(tipo, id, temporada, episodio)
    val cru = prefs(ctx).getString("fonte:" + chave, null) ?: return null
    return try {
      val o = JSONObject(cru)
      Fonte(
        tipo = tipo,
        id = id,
        temporada = temporada,
        episodio = episodio,
        url = o.optString("url"),
        referer = o.optString("referer").takeIf { it.isNotEmpty() },
        nome = o.optString("nome").takeIf { it.isNotEmpty() }
      )
    } catch (_: Exception) {
      null
    }
  }

  fun guardarFonte(ctx: Context, fonte: Fonte) {
    val o = JSONObject()
      .put("url", fonte.url)
      .put("referer", fonte.referer ?: "")
      .put("nome", fonte.nome ?: "")
    prefs(ctx).edit().putString("fonte:" + fonte.chave, o.toString()).apply()
  }

  fun apagarFonte(ctx: Context, tipo: Tipo, id: Int, temporada: Int, episodio: Int) {
    prefs(ctx).edit().remove("fonte:" + Fonte.chaveDe(tipo, id, temporada, episodio)).apply()
  }
}
