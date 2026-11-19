package com.filmemeu.app.data

import android.content.Context
import com.filmemeu.app.net.Http
import com.filmemeu.app.net.HttpErro
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * A conta e os perfis, falando direto com o Supabase.
 *
 * No site isto era o SDK do Supabase carregado de um servidor de fora. Aqui
 * sao quatro chamadas HTTP: entrar, criar, recuperar e ler os perfis. A
 * credencial publicavel e a mesma que ja vivia no navegador, e nao e
 * segredo. A senha nunca e guardada no aparelho, so a sessao que o Supabase
 * devolve.
 */
object Conta {

  private const val URL = "https://xedlipdgeuzcmmenuxvq.supabase.co"
  private const val PUBLICA = "sb_publishable_AkeqeZYZwdkNweRaBeKNmQ_PGPdUlRJ"

  private val JSON = "application/json; charset=utf-8".toMediaType()

  /** Quem entrou, nesta sessao do aplicativo. */
  var acesso: String? = null
    private set
  var renovacao: String? = null
    private set
  var usuarioId: String? = null
    private set
  var email: String? = null
    private set

  /** O perfil escolhido na tela "Quem vai assistir?". */
  var perfil: Perfil? = null

  val entrou: Boolean get() = acesso != null

  private fun prefs(ctx: Context) =
    ctx.getSharedPreferences("conta", Context.MODE_PRIVATE)

  /**
   * O endereco e os cabecalhos, para quem mais precisar falar com a conta.
   *
   * O progresso mora na mesma base e usa a mesma sessao; repetir a chave e o
   * Bearer noutro arquivo seria ter dois lugares para errar.
   */
  internal val base: String get() = URL

  internal fun cabecalhosDaConta(): Map<String, String> = cabecalhos()

  private fun cabecalhos(comSessao: Boolean = true): Map<String, String> {
    val m = HashMap<String, String>()
    m["apikey"] = PUBLICA
    m["Authorization"] = "Bearer " + (if (comSessao) (acesso ?: PUBLICA) else PUBLICA)
    m["content-type"] = "application/json"
    return m
  }

  /** Recupera a sessao guardada, para quem ja entrou nao entrar de novo. */
  fun recuperar(ctx: Context) {
    val p = prefs(ctx)
    acesso = p.getString("acesso", null)
    renovacao = p.getString("renovacao", null)
    usuarioId = p.getString("usuario", null)
    email = p.getString("email", null)
  }

  private fun guardar(ctx: Context) {
    prefs(ctx).edit()
      .putString("acesso", acesso)
      .putString("renovacao", renovacao)
      .putString("usuario", usuarioId)
      .putString("email", email)
      .apply()
  }

  private fun aplicar(ctx: Context, resposta: JSONObject) {
    acesso = resposta.optString("access_token").takeIf { it.isNotEmpty() }
    renovacao = resposta.optString("refresh_token").takeIf { it.isNotEmpty() }
    val u = resposta.optJSONObject("user")
    usuarioId = u?.optString("id")
    email = u?.optString("email")
    guardar(ctx)
  }

  /**
   * O recado que a pessoa le quando algo da errado.
   *
   * O Supabase responde em ingles e com o jargao dele. Repassar isso cru
   * seria entregar a nossa falha de encanamento para quem so quer assistir.
   */
  private fun recado(e: Exception): String {
    if (e is HttpErro) {
      val texto = try {
        val j = JSONObject(e.corpo)
        var m = j.optString("error_description")
        if (m.isEmpty()) m = j.optString("msg")
        if (m.isEmpty()) m = j.optString("message")
        m
      } catch (_: Exception) {
        ""
      }
      if (texto.contains("Invalid login", true)) return "E-mail ou senha nao conferem."
      if (texto.contains("already registered", true)) return "Esse e-mail ja tem conta."
      if (texto.contains("Email not confirmed", true)) {
        return "Falta confirmar o e-mail. Procure a mensagem que enviamos."
      }
      if (texto.contains("Password should be", true)) {
        return "A senha precisa de pelo menos 6 caracteres."
      }
      if (texto.isNotEmpty()) return texto
      return "Nao deu para falar com o servidor (" + e.codigo + ")."
    }
    return "Sem conexao. Confira a internet do aparelho."
  }

  suspend fun entrar(ctx: Context, email: String, senha: String): String? {
    return try {
      val corpo = JSONObject()
        .put("email", email.trim())
        .put("password", senha)
        .toString().toRequestBody(JSON)
      val resposta = JSONObject(
        Http.enviar(URL + "/auth/v1/token?grant_type=password", corpo, cabecalhos(false))
      )
      aplicar(ctx, resposta)
      null
    } catch (e: Exception) {
      recado(e)
    }
  }

  suspend fun criar(ctx: Context, nome: String, email: String, senha: String): String? {
    return try {
      val corpo = JSONObject()
        .put("email", email.trim())
        .put("password", senha)
        .put("data", JSONObject().put("nome", nome.trim()))
        .toString().toRequestBody(JSON)
      val resposta = JSONObject(Http.enviar(URL + "/auth/v1/signup", corpo, cabecalhos(false)))

      // Com confirmacao de e-mail ligada, o cadastro volta sem sessao: a
      // sessao so nasce depois do clique no link da mensagem.
      if (resposta.optString("access_token").isNotEmpty()) {
        aplicar(ctx, resposta)
        null
      } else {
        "Conta criada. Confirme o e-mail e entre em seguida."
      }
    } catch (e: Exception) {
      recado(e)
    }
  }

  suspend fun recuperarSenha(email: String): String {
    return try {
      val corpo = JSONObject().put("email", email.trim()).toString().toRequestBody(JSON)
      Http.enviar(URL + "/auth/v1/recover", corpo, cabecalhos(false))
      "Enviamos um link para " + email.trim() + "."
    } catch (e: Exception) {
      recado(e)
    }
  }

  /**
   * Troca a sessao vencida por uma nova.
   *
   * O acesso do Supabase dura cerca de uma hora. Numa TV o aplicativo fica
   * dias aberto, e sem isto a casa parava de carregar sozinha.
   */
  suspend fun renovar(ctx: Context): Boolean {
    val r = renovacao ?: return false
    return try {
      val corpo = JSONObject().put("refresh_token", r).toString().toRequestBody(JSON)
      val resposta = JSONObject(
        Http.enviar(URL + "/auth/v1/token?grant_type=refresh_token", corpo, cabecalhos(false))
      )
      aplicar(ctx, resposta)
      true
    } catch (_: Exception) {
      false
    }
  }

  fun sair(ctx: Context) {
    acesso = null
    renovacao = null
    usuarioId = null
    email = null
    perfil = null
    prefs(ctx).edit().clear().apply()
  }

  /* ----- os perfis ----- */

  private fun perfilDe(o: JSONObject) = Perfil(
    id = o.optString("id"),
    nome = o.optString("nome"),
    infantil = o.optBoolean("infantil"),
    cor = o.optString("cor").ifEmpty { if (o.optBoolean("infantil")) "#f59e0b" else "#38bdf8" },
    figura = o.optString("figura").ifEmpty { "sorriso" },
    sessao = o.optString("sessao").takeIf { it.isNotEmpty() && it != "null" },
    vistoEm = o.optString("visto_em").takeIf { it.isNotEmpty() && it != "null" }
  )

  suspend fun listarPerfis(): List<Perfil> {
    val url = URL + "/rest/v1/perfis?select=id,nome,infantil,cor,figura,sessao,visto_em" +
      "&order=infantil.asc&order=criado_em.asc"
    val arr: JSONArray = Http.lista(url, cabecalhos())
    val saida = ArrayList<Perfil>(arr.length())
    for (i in 0 until arr.length()) {
      val o = arr.optJSONObject(i) ?: continue
      saida.add(perfilDe(o))
    }
    return saida
  }

  /**
   * Toda conta tem o perfil do dono e o infantil.
   *
   * Criados aqui, e nao no cadastro, porque com confirmacao de e-mail a
   * sessao so existe depois do clique no link, e sem sessao nao ha a quem
   * pertencer o perfil. Aqui ja ha.
   */
  suspend fun garantirPerfis(): List<Perfil> {
    val lista = listarPerfis()
    if (lista.isNotEmpty()) return lista

    val dono = usuarioId ?: return emptyList()
    val nome = (email ?: "Eu").substringBefore("@")

    /*
     * As duas linhas vao com TODAS as colunas preenchidas, de proposito.
     *
     * Mandando duas linhas de uma vez com campos diferentes, o servidor
     * junta as colunas das duas e preenche com nulo o que faltar numa
     * delas, e nao com o valor padrao da coluna.
     */
    val corpo = JSONArray()
      .put(
        JSONObject().put("user_id", dono).put("nome", nome)
          .put("infantil", false).put("cor", "#38bdf8").put("figura", "sorriso")
      )
      .put(
        JSONObject().put("user_id", dono).put("nome", "Infantil")
          .put("infantil", true).put("cor", "#f59e0b").put("figura", "sorriso")
      )
      .toString().toRequestBody(JSON)

    Http.enviar(URL + "/rest/v1/perfis", corpo, cabecalhos() + ("Prefer" to "return=minimal"))
    return listarPerfis()
  }

  suspend fun salvarPerfil(id: String?, nome: String, cor: String, figura: String) {
    val campos = JSONObject().put("nome", nome).put("cor", cor).put("figura", figura)
    if (id == null) {
      val dono = usuarioId ?: return
      campos.put("user_id", dono).put("infantil", false)
      Http.enviar(
        URL + "/rest/v1/perfis",
        campos.toString().toRequestBody(JSON),
        cabecalhos() + ("Prefer" to "return=minimal")
      )
    } else {
      Http.enviar(
        URL + "/rest/v1/perfis?id=eq." + id,
        campos.toString().toRequestBody(JSON),
        cabecalhos() + ("Prefer" to "return=minimal"),
        "PATCH"
      )
    }
  }

  suspend fun apagarPerfil(id: String) {
    Http.enviar(
      URL + "/rest/v1/perfis?id=eq." + id,
      "".toRequestBody(JSON),
      cabecalhos(),
      "DELETE"
    )
  }
}
