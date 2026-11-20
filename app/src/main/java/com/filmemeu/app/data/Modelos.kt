package com.filmemeu.app.data

/**
 * Filme, serie e anime com o mesmo formato.
 *
 * E o mesmo acordo do site: a tela nao quer saber de onde veio o item nem
 * se o campo do TMDB se chamava `title` ou `name`.
 */
enum class Tipo(val chave: String, val rotulo: String) {
  FILME("filme", "Filmes"),
  SERIE("serie", "Series"),
  ANIME("anime", "Animacoes");

  /** No TMDB, serie e anime sao os dois `tv`. */
  val tmdb: String get() = if (this == FILME) "movie" else "tv"

  val ehSerie: Boolean get() = this != FILME

  companion object {
    fun de(chave: String?): Tipo = entries.firstOrNull { it.chave == chave } ?: FILME
  }
}

data class Item(
  val id: Int,
  val tipo: Tipo,
  val titulo: String,
  val ano: Int?,
  val nota: Double?,
  val cartaz: String?,
  val fundo: String?,
  val sinopse: String
)

data class Genero(val id: Int, val nome: String)

data class Temporada(val numero: Int, val nome: String, val episodios: Int)

/** De qual endpoint uma fileira puxa mais paginas ao rolar para o lado. */
enum class OrigemFileira { LANCAMENTOS, CONSAGRADOS, GENERO }

/** Uma fileira da casa: o nome em cima e os cartazes embaixo. */
data class Fileira(
  val nome: String,
  val itens: List<Item>,
  val tipo: Tipo,
  val generoId: Int? = null,
  val origem: OrigemFileira = OrigemFileira.GENERO,
  val paginaAtual: Int = 1,
  val temMais: Boolean = true
)

data class Perfil(
  val id: String,
  val nome: String,
  val infantil: Boolean,
  val cor: String,
  val figura: String,
  val sessao: String? = null,
  val vistoEm: String? = null
)

/**
 * De onde sai o video de um titulo.
 *
 * No site isto vinha de uma funcao que varria sites de terceiros. Aqui a
 * fonte e informada por quem usa o aplicativo: o endereco do arquivo que a
 * pessoa tem direito de assistir, com os cabecalhos que aquele servidor
 * exigir. Fica guardada no aparelho, por titulo, temporada e episodio.
 */
data class Fonte(
  val tipo: Tipo,
  val id: Int,
  val temporada: Int,
  val episodio: Int,
  val url: String,
  val referer: String? = null,
  val nome: String? = null
) {
  val chave: String get() = chaveDe(tipo, id, temporada, episodio)

  companion object {
    fun chaveDe(tipo: Tipo, id: Int, temporada: Int, episodio: Int): String =
      if (tipo.ehSerie) "${tipo.chave}:$id:$temporada:$episodio" else "${tipo.chave}:$id"
  }
}

/** Onde a pessoa parou. */
data class Marca(
  val tipo: Tipo,
  val id: Int,
  val titulo: String,
  val cartaz: String?,
  val temporada: Int,
  val episodio: Int,
  val posicao: Long,
  val duracao: Long,
  val quando: Long
) {
  val fracao: Float
    get() = if (duracao <= 0) 0f else (posicao.toFloat() / duracao.toFloat()).coerceIn(0f, 1f)
}
