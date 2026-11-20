package com.filmemeu.app.data

import com.filmemeu.app.net.Http
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * O catalogo, falando direto com o TMDB pela ponte do Supabase.
 *
 * E a traducao do api/catalog.js para dentro do aparelho. A chave do TMDB
 * continua onde estava: numa funcao que roda no Supabase, onde a chave de
 * servico ja existe. Nos pedimos a ela o resultado, com a credencial
 * publicavel, que nao e segredo e por isso pode viver dentro do APK.
 *
 * Nenhuma linha daqui passa pela Vercel.
 */
object Tmdb {

  private const val PONTE = "https://xedlipdgeuzcmmenuxvq.supabase.co/functions/v1/tmdb"
  private const val PUBLICA = "sb_publishable_AkeqeZYZwdkNweRaBeKNmQ_PGPdUlRJ"

  private const val IMG = "https://image.tmdb.org/t/p/w342"
  private const val IMG_LARGA = "https://image.tmdb.org/t/p/w1280"

  private const val ANIME_GENERO = "16"

  /** ID virtual da categoria "Lancamentos" (todos os titulos novos dentro de 1 ano). */
  const val GENERO_LANCAMENTOS = -100

  /**
   * Generos de serie que o TMDB nao traduz para portugues.
   *
   * Nao e falha do pedido: esses nomes nao tem traducao cadastrada em
   * pt-BR. Como viram titulo de fileira, ficavam oito nomes em ingles no
   * meio de um catalogo em portugues.
   */
  private val EM_PORTUGUES = mapOf(
    "Action & Adventure" to "Acao e Aventura",
    "Sci-Fi & Fantasy" to "Ficcao e Fantasia",
    "War & Politics" to "Guerra e Politica",
    "Kids" to "Infantil",
    "News" to "Noticias",
    "Soap" to "Novela",
    "Talk" to "Entrevistas"
  )

  /*
   * O recorte do perfil infantil, igual ao do site.
   *
   * Nao e selo etario de verdade, isso o TMDB nao entrega de forma
   * confiavel pais a pais. E um recorte por genero, que erra para o lado
   * seguro: deixa de fora coisa boa antes de deixar entrar coisa ruim.
   */
  private const val INFANTIL_FILME = "16,10751"
  private const val INFANTIL_SERIE = "10762,16,10751"
  private const val INFANTIL_FORA = "27,53,80,9648,10752,10749,37"

  private val cache = HashMap<String, JSONObject>()

  /** Pede ao TMDB pela ponte. */
  private suspend fun pedir(
    rota: String,
    params: Map<String, String> = emptyMap(),
    semIdioma: Boolean = false
  ): JSONObject {
    val url = StringBuilder(PONTE).append("?path=").append(enc(rota))
    params.forEach { (k, v) -> url.append("&").append(k).append("=").append(enc(v)) }
    if (!semIdioma) url.append("&language=pt-BR")
    url.append("&include_adult=false")

    val chave = url.toString()
    cache[chave]?.let { return it }

    val resposta = Http.objeto(
      chave,
      mapOf("apikey" to PUBLICA, "Authorization" to "Bearer " + PUBLICA)
    )
    // O catalogo e o mesmo para todo mundo e muda devagar. Guardado aqui,
    // voltar uma tela nao refaz o pedido: numa caixa de TV isso e a
    // diferenca entre a casa abrir na hora e a casa piscar.
    if (cache.size > 120) cache.clear()
    cache[chave] = resposta
    return resposta
  }

  private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

  /** Deixa filme e serie com o mesmo formato. */
  private fun normalizar(cru: JSONObject, tipo: Tipo): Item? {
    var titulo = cru.optString("title")
    if (titulo.isEmpty()) titulo = cru.optString("name")
    if (titulo.isEmpty()) return null

    var lancamento = cru.optString("release_date")
    if (lancamento.isEmpty()) lancamento = cru.optString("first_air_date")

    val cartaz = cru.optString("poster_path").takeIf { it.isNotEmpty() && it != "null" }
    val fundo = cru.optString("backdrop_path").takeIf { it.isNotEmpty() && it != "null" }
    val nota = cru.optDouble("vote_average", 0.0)

    return Item(
      id = cru.optInt("id"),
      tipo = tipo,
      titulo = titulo,
      ano = lancamento.take(4).toIntOrNull(),
      nota = if (nota > 0) Math.round(nota * 10) / 10.0 else null,
      cartaz = if (cartaz != null) IMG + cartaz else null,
      fundo = if (fundo != null) IMG_LARGA + fundo else null,
      sinopse = cru.optString("overview")
    )
  }

  private fun itens(resposta: JSONObject, tipo: Tipo, infantil: Boolean): List<Item> {
    val arr = resposta.optJSONArray("results") ?: return emptyList()
    val fora = INFANTIL_FORA.split(",").map { it.toInt() }
    val permitidos = (if (tipo == Tipo.FILME) INFANTIL_FILME else INFANTIL_SERIE)
      .split(",").map { it.toInt() }

    val saida = ArrayList<Item>(arr.length())
    for (i in 0 until arr.length()) {
      val cru = arr.optJSONObject(i) ?: continue
      /*
       * Busca e parecidos nao passam pelo recorte de descoberta: a
       * primeira e o nome que a pessoa escreveu, a segunda e uma lista
       * pronta do TMDB. Nos dois casos o recorte entra aqui, pelos
       * generos de cada titulo, que e o que temos a mao.
       */
      if (infantil) {
        val generos = cru.optJSONArray("genre_ids")
        if (generos == null || generos.length() == 0) continue
        val ids = (0 until generos.length()).map { generos.optInt(it) }
        if (ids.any { fora.contains(it) }) continue
        if (ids.none { permitidos.contains(it) }) continue
      }
      val item = normalizar(cru, tipo)
      if (item != null) saida.add(item)
    }
    return saida
  }

  /* ----- as listas da casa ----- */

  /** O que o mundo mais ve nesta semana. */
  suspend fun emAlta(tipo: Tipo, pagina: Int = 1, infantil: Boolean = false): List<Item> {
    if (infantil) return doRecorteInfantil(tipo, pagina, null)
    if (tipo == Tipo.ANIME) return porGenero(tipo, null, pagina)
    return itens(pedir("/trending/" + tipo.tmdb + "/week", mapOf("page" to "$pagina")), tipo, false)
  }

  /** Lancamentos: o que esta em cartaz agora. Em serie, o que esta no ar. */
  suspend fun lancamentos(tipo: Tipo, pagina: Int = 1, infantil: Boolean = false): List<Item> {
    if (infantil) return doRecorteInfantil(tipo, pagina, null)
    val resposta = if (tipo == Tipo.FILME) {
      pedir("/movie/now_playing", mapOf("page" to "$pagina", "region" to "BR"))
    } else {
      pedir("/tv/on_the_air", mapOf("page" to "$pagina"))
    }
    return itens(resposta, tipo, false)
  }

  /**
   * Consagrados: nota alta e muita gente tendo votado.
   *
   * Sem o piso de votos, nota alta nao quer dizer nada: qualquer titulo
   * obscuro com meia duzia de notas dez apareceria na frente de um
   * classico. Em animacao o piso cai, porque anime tem muito menos gente
   * votando do que serie americana.
   */
  suspend fun consagrados(tipo: Tipo, pagina: Int = 1, infantil: Boolean = false): List<Item> {
    if (infantil) return doRecorteInfantil(tipo, pagina, null)
    val params = HashMap<String, String>()
    params["sort_by"] = "popularity.desc"
    params["page"] = "$pagina"
    params["vote_average.gte"] = "8.3"
    if (tipo == Tipo.ANIME) {
      params["with_genres"] = ANIME_GENERO
      params["with_original_language"] = "ja"
      params["vote_count.gte"] = "150"
    } else {
      params["vote_count.gte"] = "400"
    }
    return itens(pedir("/discover/" + tipo.tmdb, params), tipo, false)
  }

  /**
   * Todos os lancamentos mais novos dentro de um ano (ultimos 12 meses ate hoje),
   * ordenados do mais recente para o mais antigo, com paginacao completa.
   */
  suspend fun lancamentosDoAno(
    tipo: Tipo,
    pagina: Int = 1,
    infantil: Boolean = false
  ): List<Item> {
    val formato = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val hoje = Calendar.getInstance()
    val dataFim = formato.format(hoje.time)
    val umAnoAtras = (hoje.clone() as Calendar).apply { add(Calendar.YEAR, -1) }
    val dataInicio = formato.format(umAnoAtras.time)

    val params = HashMap<String, String>()
    params["page"] = "$pagina"

    if (tipo == Tipo.FILME) {
      params["primary_release_date.gte"] = dataInicio
      params["primary_release_date.lte"] = dataFim
      params["sort_by"] = "primary_release_date.desc"
      params["vote_count.gte"] = if (infantil) "5" else "10"
    } else {
      params["first_air_date.gte"] = dataInicio
      params["first_air_date.lte"] = dataFim
      params["sort_by"] = "first_air_date.desc"
      params["vote_count.gte"] = if (tipo == Tipo.ANIME || infantil) "5" else "10"
    }

    if (infantil) {
      val base = if (tipo == Tipo.FILME) INFANTIL_FILME else INFANTIL_SERIE
      params["with_genres"] = if (tipo == Tipo.ANIME) "$ANIME_GENERO,$base" else base
      params["without_genres"] = INFANTIL_FORA
      if (tipo == Tipo.ANIME) params["with_original_language"] = "ja"
    } else if (tipo == Tipo.ANIME) {
      params["with_genres"] = ANIME_GENERO
      params["with_original_language"] = "ja"
    }

    return itens(pedir("/discover/" + tipo.tmdb, params), tipo, false)
  }

  suspend fun porGenero(
    tipo: Tipo,
    generoId: Int?,
    pagina: Int = 1,
    infantil: Boolean = false
  ): List<Item> {
    if (generoId == GENERO_LANCAMENTOS) return lancamentosDoAno(tipo, pagina, infantil)
    if (infantil) return doRecorteInfantil(tipo, pagina, generoId)

    val params = HashMap<String, String>()
    params["sort_by"] = "popularity.desc"
    params["page"] = "$pagina"

    if (tipo == Tipo.ANIME) {
      params["with_genres"] = if (generoId != null) ANIME_GENERO + "," + generoId else ANIME_GENERO
      params["with_original_language"] = "ja"
    } else {
      if (generoId == null) return emAlta(tipo, pagina)
      params["with_genres"] = "$generoId"
    }
    return itens(pedir("/discover/" + tipo.tmdb, params), tipo, false)
  }

  private suspend fun doRecorteInfantil(tipo: Tipo, pagina: Int, generoId: Int?): List<Item> {
    val base = if (tipo == Tipo.FILME) INFANTIL_FILME else INFANTIL_SERIE
    val params = mapOf(
      "with_genres" to (if (generoId != null) base + "," + generoId else base),
      "without_genres" to INFANTIL_FORA,
      "sort_by" to "popularity.desc",
      "page" to "$pagina"
    )
    return itens(pedir("/discover/" + tipo.tmdb, params), tipo, false)
  }

  suspend fun buscar(
    tipo: Tipo,
    termo: String,
    pagina: Int = 1,
    infantil: Boolean = false
  ): List<Item> {
    if (termo.isBlank()) return emptyList()
    val resposta = pedir("/search/" + tipo.tmdb, mapOf("query" to termo, "page" to "$pagina"))
    return itens(resposta, tipo, infantil)
  }

  suspend fun parecidos(tipo: Tipo, id: Int, infantil: Boolean = false): List<Item> {
    val resposta = pedir("/" + tipo.tmdb + "/" + id + "/recommendations", mapOf("page" to "1"))
    return itens(resposta, tipo, infantil)
  }

  suspend fun generos(tipo: Tipo): List<Genero> {
    val resposta = pedir("/genre/" + tipo.tmdb + "/list")
    val arr = resposta.optJSONArray("genres") ?: return emptyList()
    val saida = ArrayList<Genero>(arr.length())
    for (i in 0 until arr.length()) {
      val g = arr.optJSONObject(i) ?: continue
      val nome = g.optString("name")
      saida.add(Genero(g.optInt("id"), EM_PORTUGUES[nome] ?: nome))
    }
    return saida
  }

  /** Temporadas de uma serie. A zero e o cesto de especiais, e fica de fora. */
  suspend fun temporadas(id: Int): List<Temporada> {
    val resposta = pedir("/tv/$id")
    val arr = resposta.optJSONArray("seasons") ?: return emptyList()
    val saida = ArrayList<Temporada>()
    for (i in 0 until arr.length()) {
      val s = arr.optJSONObject(i) ?: continue
      val numero = s.optInt("season_number")
      val quantos = s.optInt("episode_count")
      if (numero <= 0 || quantos <= 0) continue
      var nome = s.optString("name")
      if (nome.isEmpty()) nome = "Temporada $numero"
      saida.add(Temporada(numero, nome, quantos))
    }
    return saida
  }

  /**
   * Quanto um video do TMDB serve como trailer nosso.
   *
   * O idioma pesa mais do que tudo: quem abre o catalogo em portugues quer
   * ouvir o trailer em portugues, e so cai no ingles quando nao houver
   * nada. O TMDB nao tem campo dizendo "dublado", entao a marca esta no
   * nome, que e como os canais escrevem.
   */
  private fun nota(v: JSONObject): Int {
    val nome = v.optString("name")
    val ehPt = v.optString("iso_639_1").lowercase() == "pt"
    val doBrasil = v.optString("iso_3166_1").uppercase() == "BR"
    val dublado = Regex("dublad|dublagem|dobrad|dobragem|dub", RegexOption.IGNORE_CASE)
      .containsMatchIn(nome)
    val legendado = Regex("legendad|legenda|subtitul", RegexOption.IGNORE_CASE)
      .containsMatchIn(nome)

    val idioma = if (!ehPt) 1 else if (dublado) 4 else if (legendado) 2 else 3
    val tipo = when (v.optString("type")) {
      "Trailer" -> 3
      "Teaser" -> 2
      else -> 1
    }
    return idioma * 1000 + (if (ehPt && doBrasil) 100 else 0) + tipo * 10 +
      (if (v.optBoolean("official")) 1 else 0)
  }

  /**
   * O trailer do titulo, se houver.
   *
   * Pedir language=pt-BR aqui faz o contrario do que parece: o TMDB passa a
   * devolver so o que esta marcado exatamente como pt-BR na ficha, e os
   * dublados ficam de fora. Sem o idioma, o include_video_language trabalha
   * e eles aparecem.
   */
  suspend fun trailer(tipo: Tipo, id: Int): String? {
    val resposta = pedir(
      "/" + tipo.tmdb + "/" + id + "/videos",
      mapOf("include_video_language" to "pt-BR,pt,en"),
      semIdioma = true
    )
    val arr = resposta.optJSONArray("results") ?: return null
    val videos = ArrayList<JSONObject>()
    for (i in 0 until arr.length()) {
      val v = arr.optJSONObject(i) ?: continue
      if (v.optString("site") == "YouTube" && v.optString("key").isNotEmpty()) videos.add(v)
    }
    return videos.maxByOrNull { nota(it) }?.optString("key")
  }
}
