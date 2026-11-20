package com.filmemeu.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as itensIndexadosDaGrade
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmemeu.app.data.Conta
import com.filmemeu.app.data.Fileira
import com.filmemeu.app.data.Genero
import com.filmemeu.app.data.Guardados
import com.filmemeu.app.data.Item
import com.filmemeu.app.data.Marca
import com.filmemeu.app.data.OrigemFileira
import com.filmemeu.app.data.Tipo
import com.filmemeu.app.data.Tmdb
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Quantas fileiras de genero a casa monta, como no site. */
private const val QUANTAS_FILEIRAS = 8

/** Cor especial para a categoria Lancamentos. */
private val COR_CATEGORIA_LANCAMENTOS = Pair(Color(0xFF0EA5E9), Color(0xFF0369A1))

/** Paleta de gradientes dos cartoes de categoria (igual ao web). */
private val CORES_CATEGORIAS = listOf(
  Pair(Color(0xFF7A2D3E), Color(0xFF4F1C2A)), // Acao (vinho)
  Pair(Color(0xFF244D85), Color(0xFF173259)), // Aventura (azul real)
  Pair(Color(0xFF156E5B), Color(0xFF0E493D)), // Animacao (esmeralda)
  Pair(Color(0xFF6B28C4), Color(0xFF471A87)), // Comedia (roxo)
  Pair(Color(0xFF8C5A23), Color(0xFF5D3A14)), // Crime (ambar)
  Pair(Color(0xFF822943), Color(0xFF54182B)), // Documentario (bordo)
  Pair(Color(0xFF1C5785), Color(0xFF113757)), // Drama (azul oceano)
  Pair(Color(0xFF2E6B47), Color(0xFF1B452D))  // Familia / outros (verde)
)

/** Blocos verticais da tela principal (fileiras de cards + faixa de Categorias). */
private sealed interface BlocoCatalogo {
  val chave: String
  data class DeFileira(val fileira: Fileira) : BlocoCatalogo {
    override val chave: String = "f_${fileira.tipo.chave}_${fileira.nome}"
  }
  data object DeCategorias : BlocoCatalogo {
    override val chave: String = "bloco_categorias"
  }
}

/**
 * A casa do MovFlix — alinhada visualmente a versao web / Smart TV:
 * - Banner de destaque sorteado a cada entrada (sem bolinhas de slide)
 * - Carrossel de foco fixo (Netflix TV) em TODAS as linhas de cartazes:
 *   foco parado na vaga fixa com escala 1.15 e fileira deslizando em 250 ms
 * - Carrossel centralizado na linha de Categorias
 * - Descer do cabecalho cai direto no primeiro card; subir da primeira fileira
 *   passa pelo botao Assistir do slide antes de voltar ao cabecalho
 */
@Composable
fun TelaCatalogo(
  aba: Int,
  buscaAberta: Boolean,
  aoTrocarAba: (Int) -> Unit,
  aoAbrirBusca: () -> Unit,
  aoAbrirTitulo: (Item) -> Unit,
  aoTrocarPerfil: () -> Unit,
  aoSair: () -> Unit
) {
  val ctx = LocalContext.current
  val escopo = rememberCoroutineScope()
  val infantil = Conta.perfil?.infantil == true

  var busca by remember { mutableStateOf("") }
  var generoEscolhido by remember { mutableStateOf<Genero?>(null) }
  var verTodosTitulo by remember { mutableStateOf<String?>(null) }
  var verTodosLista by remember { mutableStateOf<List<Item>>(emptyList()) }

  var generos by remember { mutableStateOf<List<Genero>>(emptyList()) }
  var fileiras by remember { mutableStateOf<List<Fileira>>(emptyList()) }
  var destaques by remember { mutableStateOf<List<Item>>(emptyList()) }
  var grade by remember { mutableStateOf<List<Item>>(emptyList()) }
  var pagina by remember { mutableStateOf(1) }
  var carregando by remember { mutableStateOf(false) }
  var recado by remember { mutableStateOf<String?>(null) }
  val carregandoFileiras = remember { mutableStateMapOf<String, Boolean>() }
  var continuar by remember { mutableStateOf<List<Marca>>(emptyList()) }
  var salvos by remember { mutableStateOf<List<Item>>(emptyList()) }
  var porqueViuTitulo by remember { mutableStateOf<String?>(null) }
  var porqueViuItens by remember { mutableStateOf<List<Item>>(emptyList()) }

  val estadoFileiras = rememberLazyListState()
  val estadoMinha = rememberLazyListState()
  val estadoGrade = rememberLazyGridState()

  val focoCabecalho = remember { FocusRequester() }
  val focoAssistirDestaque = remember { FocusRequester() }
  val focoPrimeiroCardGrade = remember { FocusRequester() }

  var focarPrimeiraFileira by remember { mutableStateOf<(() -> Unit)?>(null) }
  var focarPrimeiraLinhaMinha by remember { mutableStateOf<(() -> Unit)?>(null) }

  val tipoDaAba = when (aba) {
    2 -> Tipo.SERIE
    3 -> Tipo.ANIME
    else -> Tipo.FILME
  }
  val inicio = aba == 0
  val naMinha = aba == ABA_MINHA
  val naGrade = !buscaAberta && (generoEscolhido != null || verTodosTitulo != null)

  LaunchedEffect(aba, buscaAberta) {
    generoEscolhido = null
    verTodosTitulo = null
    verTodosLista = emptyList()
    estadoFileiras.scrollToItem(0)
    estadoMinha.scrollToItem(0)
  }

  fun subirParaCabecalho() {
    escopo.launch {
      when {
        naGrade -> estadoGrade.animateScrollToItem(0)
        naMinha -> estadoMinha.animateScrollToItem(0)
        !buscaAberta -> estadoFileiras.animateScrollToItem(0)
      }
      runCatching { focoCabecalho.requestFocus() }
    }
  }

  fun subirParaAssistirDoSlide() {
    escopo.launch {
      estadoFileiras.animateScrollToItem(0)
      var tentou = false
      repeat(4) {
        if (!tentou) {
          tentou = runCatching { focoAssistirDestaque.requestFocus(); true }.getOrDefault(false)
          if (!tentou) delay(30)
        }
      }
      if (!tentou) runCatching { focoCabecalho.requestFocus() }
    }
  }

  /* ----- carregamento das fileiras e Minha MovFlix ----- */

  LaunchedEffect(aba, infantil) {
    if (naMinha) {
      continuar = Guardados.continuar(ctx)
      salvos = Guardados.minha(ctx)
      val base = continuar.firstOrNull()?.let { Triple(it.tipo, it.id, it.titulo) }
        ?: salvos.firstOrNull()?.let { Triple(it.tipo, it.id, it.titulo) }
      if (base != null) {
        porqueViuTitulo = base.third
        porqueViuItens = try {
          Tmdb.parecidos(base.first, base.second, infantil)
        } catch (_: Exception) {
          emptyList()
        }
      } else {
        porqueViuTitulo = null
        porqueViuItens = emptyList()
      }
      return@LaunchedEffect
    }

    recado = null
    carregando = true
    carregandoFileiras.clear()
    fileiras = emptyList()
    destaques = emptyList()

    try {
      generos = Tmdb.generos(tipoDaAba)

      coroutineScope {
        val vemNovidades = async { Tmdb.lancamentos(tipoDaAba, 1, infantil) }
        val vemSeries = async {
          if (inicio) Tmdb.lancamentos(Tipo.SERIE, 1, infantil) else emptyList()
        }
        val vemConsagrados = async { Tmdb.consagrados(tipoDaAba, 1, infantil) }

        val planos = generos.take(QUANTAS_FILEIRAS)
        val vemListas = planos.map { g ->
          async { Tmdb.porGenero(tipoDaAba, g.id, 1, infantil) }
        }

        val montadas = ArrayList<Fileira>()

        val novidades = vemNovidades.await()
        if (novidades.isNotEmpty()) {
          val nome = if (tipoDaAba == Tipo.FILME) "Lançamentos" else "No ar agora"
          montadas.add(
            Fileira(
              nome = nome,
              itens = novidades,
              tipo = tipoDaAba,
              generoId = Tmdb.GENERO_LANCAMENTOS,
              origem = OrigemFileira.LANCAMENTOS
            )
          )
        }
        fileiras = montadas.toList()

        val consagrados = vemConsagrados.await()
        // Sorteia novos slides a cada entrada/troca de aba.
        destaques = (novidades.filter { it.fundo != null } + consagrados.filter { it.fundo != null })
          .distinctBy { it.id }
          .shuffled()
          .take(8)

        if (inicio) {
          val series = vemSeries.await()
          if (series.isNotEmpty()) {
            montadas.add(
              Fileira(
                nome = "Séries no ar agora",
                itens = series,
                tipo = Tipo.SERIE,
                generoId = Tmdb.GENERO_LANCAMENTOS,
                origem = OrigemFileira.LANCAMENTOS
              )
            )
            fileiras = montadas.toList()
          }
        }

        if (consagrados.isNotEmpty()) {
          montadas.add(
            Fileira(
              nome = "Consagrados",
              itens = consagrados,
              tipo = tipoDaAba,
              origem = OrigemFileira.CONSAGRADOS
            )
          )
          fileiras = montadas.toList()
        }

        vemListas.forEachIndexed { i, vem ->
          val itens = vem.await()
          if (itens.isNotEmpty()) {
            montadas.add(
              Fileira(
                nome = planos[i].nome,
                itens = itens,
                tipo = tipoDaAba,
                generoId = planos[i].id,
                origem = OrigemFileira.GENERO
              )
            )
            fileiras = montadas.toList()
          }
        }
      }
    } catch (_: Exception) {
      recado = "O catálogo está fora do ar por um instante. Tente de novo daqui a pouco."
    }
    carregando = false
  }

  /**
   * Pre-carrega automaticamente a proxima pagina de uma fileira horizontal
   * antes que ela chegue ao fim, evitando qualquer espaco vazio no final.
   */
  fun carregarMaisNaFileira(alvo: Fileira) {
    val chave = "${alvo.tipo.chave}_${alvo.nome}"
    if (carregandoFileiras[chave] == true) return
    val atual = fileiras.firstOrNull { "${it.tipo.chave}_${it.nome}" == chave } ?: return
    if (!atual.temMais) return

    carregandoFileiras[chave] = true
    escopo.launch {
      try {
        val proxima = atual.paginaAtual + 1
        var recebidos = when (atual.origem) {
          OrigemFileira.LANCAMENTOS -> {
            val viaPadrao = Tmdb.lancamentos(atual.tipo, proxima, infantil)
            if (viaPadrao.isNotEmpty()) {
              viaPadrao
            } else {
              Tmdb.lancamentosDoAno(atual.tipo, proxima, infantil)
            }
          }
          OrigemFileira.CONSAGRADOS -> Tmdb.consagrados(atual.tipo, proxima, infantil)
          OrigemFileira.GENERO -> Tmdb.porGenero(atual.tipo, atual.generoId, proxima, infantil)
        }

        val existentes = atual.itens.map { "${it.tipo.chave}:${it.id}" }.toHashSet()
        var novos = recebidos.filter { existentes.add("${it.tipo.chave}:${it.id}") }

        // Se a pagina de lancamentos de cinema repetiu tudo, continua pelos lancamentos do ano.
        if (novos.isEmpty() && atual.origem == OrigemFileira.LANCAMENTOS) {
          recebidos = Tmdb.lancamentosDoAno(atual.tipo, proxima, infantil)
          novos = recebidos.filter { existentes.add("${it.tipo.chave}:${it.id}") }
        }

        fileiras = fileiras.map { f ->
          if ("${f.tipo.chave}_${f.nome}" == chave) {
            if (novos.isNotEmpty()) {
              f.copy(itens = f.itens + novos, paginaAtual = proxima, temMais = true)
            } else if (recebidos.isEmpty()) {
              f.copy(paginaAtual = proxima, temMais = false)
            } else {
              f.copy(paginaAtual = proxima, temMais = proxima < 25)
            }
          } else {
            f
          }
        }
      } catch (_: Exception) {
        // Silencioso: tenta novamente no proximo avanco se houver falha momentanea de rede.
      } finally {
        carregandoFileiras[chave] = false
      }
    }
  }

  /* ----- a grade: categoria selecionada ----- */

  LaunchedEffect(generoEscolhido, aba) {
    val gen = generoEscolhido ?: return@LaunchedEffect
    if (naMinha || buscaAberta) return@LaunchedEffect
    carregando = true
    pagina = 1
    grade = try {
      Tmdb.porGenero(tipoDaAba, gen.id, 1, infantil)
    } catch (_: Exception) {
      recado = "Não deu para carregar a lista."
      emptyList()
    }
    carregando = false
  }

  fun maisUmaPagina() {
    val gen = generoEscolhido ?: return
    if (carregando) return
    escopo.launch {
      carregando = true
      val proxima = pagina + 1
      val novos = try {
        Tmdb.porGenero(tipoDaAba, gen.id, proxima, infantil)
      } catch (_: Exception) {
        emptyList()
      }
      if (novos.isNotEmpty()) {
        grade = grade + novos
        pagina = proxima
      }
      carregando = false
    }
  }

  BoxWithConstraints(Modifier.fillMaxSize().background(Cor.bg)) {
    val compacto = maxHeight < 460.dp || maxWidth < 740.dp
    val larguraCardGrade = if (compacto) 110 else 138
    val alturaHero = (maxHeight * if (compacto) 0.56f else 0.52f).coerceIn(200.dp, 380.dp)

    Column(Modifier.fillMaxSize().background(Cor.bg)) {
      CabecalhoMovFlix(
        abaSelecionada = aba,
        buscaAberta = buscaAberta,
        compacto = compacto,
        focoAbaAtiva = focoCabecalho,
        trocarAbaAoFocar = true,
        aoFocarCabecalho = {
          escopo.launch {
            when {
              naGrade -> estadoGrade.animateScrollToItem(0)
              naMinha -> estadoMinha.animateScrollToItem(0)
              !buscaAberta -> estadoFileiras.animateScrollToItem(0)
            }
          }
        },
        aoDescerDoCabecalho = {
          // Descida de qualquer botao do cabecalho cai direto no primeiro card da 1a linha.
          when {
            buscaAberta -> false
            naGrade -> {
              val lista = if (verTodosTitulo != null) verTodosLista else grade
              if (lista.isNotEmpty()) {
                escopo.launch {
                  estadoGrade.scrollToItem(0)
                  delay(24)
                  runCatching { focoPrimeiroCardGrade.requestFocus() }
                }
                true
              } else {
                false
              }
            }
            naMinha -> {
              if (continuar.isNotEmpty() || porqueViuItens.isNotEmpty() || salvos.isNotEmpty()) {
                focarPrimeiraLinhaMinha?.invoke()
                true
              } else {
                false
              }
            }
            fileiras.isNotEmpty() -> {
              focarPrimeiraFileira?.invoke()
              true
            }
            else -> false
          }
        },
        aoAbrirBusca = aoAbrirBusca,
        aoTrocarAba = { novaAba ->
          busca = ""
          generoEscolhido = null
          verTodosTitulo = null
          verTodosLista = emptyList()
          aoTrocarAba(novaAba)
        },
        aoTrocarPerfil = aoTrocarPerfil,
        aoSair = aoSair
      )

      if (recado != null && !buscaAberta) {
        Text(
          recado!!,
          color = Cor.danger,
          fontSize = 13.sp,
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
      }

      when {
        buscaAberta -> TelaBusca(
          infantil = infantil,
          termoInicial = busca,
          aoMudarTermo = { busca = it },
          aoAbrirTitulo = aoAbrirTitulo
        )

        naGrade -> Grade(
          itens = if (verTodosTitulo != null) verTodosLista else grade,
          titulo = verTodosTitulo ?: generoEscolhido?.nome ?: "Catálogo",
          subtitulo = if (generoEscolhido?.id == Tmdb.GENERO_LANCAMENTOS) "Últimos 12 meses" else null,
          larguraCard = larguraCardGrade,
          compacto = compacto,
          carregando = carregando && verTodosTitulo == null,
          estado = estadoGrade,
          focoPrimeiroCard = focoPrimeiroCardGrade,
          aoVoltar = {
            generoEscolhido = null
            verTodosTitulo = null
            verTodosLista = emptyList()
          },
          aoAbrir = aoAbrirTitulo,
          aoCarregarMais = { maisUmaPagina() }
        )

        naMinha -> MinhaMovFlix(
          continuar = continuar,
          salvos = salvos,
          porqueViuTitulo = porqueViuTitulo,
          porqueViuItens = porqueViuItens,
          compacto = compacto,
          estado = estadoMinha,
          aoRegistrarFocoPrimeiraLinha = { focarPrimeiraLinhaMinha = it },
          aoSubirParaCabecalho = { subirParaCabecalho() },
          aoAbrir = aoAbrirTitulo,
          aoVerTodosRecomendados = {
            if (porqueViuTitulo != null && porqueViuItens.isNotEmpty()) {
              verTodosTitulo = "Porque você viu $porqueViuTitulo"
              verTodosLista = porqueViuItens
            }
          },
          aoEsquecer = { tipo, id ->
            Guardados.esquecer(ctx, tipo, id)
            continuar = Guardados.continuar(ctx)
          }
        )

        else -> Fileiras(
          fileiras = fileiras,
          destaques = destaques,
          generos = remember(generos) {
            listOf(Genero(Tmdb.GENERO_LANCAMENTOS, "Lançamentos")) + generos
          },
          alturaHero = alturaHero,
          compacto = compacto,
          carregando = carregando,
          estado = estadoFileiras,
          focoAssistirDestaque = focoAssistirDestaque,
          aoRegistrarFocoPrimeiraFileira = { focarPrimeiraFileira = it },
          aoSubirParaCabecalho = { subirParaCabecalho() },
          aoSubirParaAssistir = {
            if (destaques.isNotEmpty()) subirParaAssistirDoSlide() else subirParaCabecalho()
          },
          aoAbrir = aoAbrirTitulo,
          aoCarregarMaisFileira = { f -> carregarMaisNaFileira(f) },
          aoGenero = { generoEscolhido = it },
          aoVerTodosFileira = { f ->
            if (f.generoId != null) {
              generoEscolhido = Genero(f.generoId, f.nome)
            } else {
              verTodosTitulo = f.nome
              verTodosLista = f.itens
            }
          }
        )
      }
    }
  }
}

@Composable
private fun Fileiras(
  fileiras: List<Fileira>,
  destaques: List<Item>,
  generos: List<Genero>,
  alturaHero: Dp,
  compacto: Boolean,
  carregando: Boolean,
  estado: LazyListState,
  focoAssistirDestaque: FocusRequester,
  aoRegistrarFocoPrimeiraFileira: (() -> Unit) -> Unit,
  aoSubirParaCabecalho: () -> Unit,
  aoSubirParaAssistir: () -> Unit,
  aoAbrir: (Item) -> Unit,
  aoCarregarMaisFileira: (Fileira) -> Unit,
  aoGenero: (Genero) -> Unit,
  aoVerTodosFileira: (Fileira) -> Unit
) {
  val escopo = rememberCoroutineScope()
  var qualDestaque by remember(destaques) { mutableIntStateOf(0) }

  LaunchedEffect(destaques) {
    if (destaques.size <= 1) return@LaunchedEffect
    while (true) {
      delay(7000)
      qualDestaque = (qualDestaque + 1) % destaques.size
    }
  }

  val blocos = remember(fileiras, generos) {
    val lista = ArrayList<BlocoCatalogo>()
    val posCategorias = if (fileiras.size > 1) (fileiras.size / 2).coerceAtLeast(1) else fileiras.size
    fileiras.forEachIndexed { idx, f ->
      if (idx == posCategorias && generos.isNotEmpty()) {
        lista.add(BlocoCatalogo.DeCategorias)
      }
      lista.add(BlocoCatalogo.DeFileira(f))
    }
    if (fileiras.size <= 1 && generos.isNotEmpty()) {
      lista.add(BlocoCatalogo.DeCategorias)
    }
    lista
  }

  val focadoresDeBloco = remember { mutableStateMapOf<String, () -> Boolean>() }
  val temHero = destaques.isNotEmpty()
  val deslocamentoHero = if (temHero) 1 else 0

  fun focarBloco(indiceBloco: Int) {
    val bloco = blocos.getOrNull(indiceBloco) ?: return
    val indiceLista = deslocamentoHero + indiceBloco
    escopo.launch {
      if (indiceBloco == 0 && temHero) {
        if (estado.firstVisibleItemIndex > 1) {
          estado.animateScrollToItem(0)
        }
      } else {
        val visivel = estado.layoutInfo.visibleItemsInfo.firstOrNull { it.index == indiceLista }
        val fora = visivel == null ||
          visivel.offset < 0 ||
          (visivel.offset + visivel.size) > estado.layoutInfo.viewportEndOffset
        if (fora) {
          estado.animateScrollToItem(indiceLista)
        }
      }

      var focou = false
      repeat(5) {
        if (!focou) {
          focou = focadoresDeBloco[bloco.chave]?.invoke() == true
          if (!focou) delay(30)
        }
      }
    }
  }

  LaunchedEffect(blocos, temHero) {
    aoRegistrarFocoPrimeiraFileira {
      if (blocos.isNotEmpty()) focarBloco(0)
    }
  }

  LazyColumn(
    state = estado,
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 36.dp)
  ) {
    if (temHero) {
      item(key = "hero_destaque") {
        val seguro = qualDestaque.coerceIn(0, destaques.lastIndex)
        Destaque(
          item = destaques[seguro],
          altura = alturaHero,
          compacto = compacto,
          focoAssistir = focoAssistirDestaque,
          aoSubir = aoSubirParaCabecalho,
          aoDescer = { if (blocos.isNotEmpty()) focarBloco(0) },
          aoAssistir = { aoAbrir(destaques[seguro]) },
          aoInfo = { aoAbrir(destaques[seguro]) }
        )
      }
    }

    if (carregando && fileiras.isEmpty()) {
      item(key = "carregando") {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
          Rodinha()
        }
      }
    }

    itemsIndexed(blocos, key = { _, b -> b.chave }) { idxBloco, bloco ->
      when (bloco) {
        is BlocoCatalogo.DeFileira -> {
          UmaFileira(
            f = bloco.fileira,
            compacto = compacto,
            aoRegistrarFocador = { focadoresDeBloco[bloco.chave] = it },
            aoSubirDaLinha = {
              // Na subida da 1a fileira, passa pelo botao Assistir do slide antes do cabecalho.
              if (idxBloco == 0) {
                aoSubirParaAssistir()
              } else {
                focarBloco(idxBloco - 1)
              }
            },
            aoDescerDaLinha = {
              if (idxBloco + 1 < blocos.size) focarBloco(idxBloco + 1)
            },
            aoAbrir = aoAbrir,
            aoPrecisarDeMais = { aoCarregarMaisFileira(bloco.fileira) },
            aoVerTodos = { aoVerTodosFileira(bloco.fileira) }
          )
        }

        BlocoCatalogo.DeCategorias -> {
          FileiraDeCategorias(
            generos = generos,
            compacto = compacto,
            aoRegistrarFocador = { focadoresDeBloco[bloco.chave] = it },
            aoSubirDaLinha = {
              if (idxBloco == 0) {
                aoSubirParaAssistir()
              } else {
                focarBloco(idxBloco - 1)
              }
            },
            aoDescerDaLinha = {
              if (idxBloco + 1 < blocos.size) focarBloco(idxBloco + 1)
            },
            aoGenero = aoGenero
          )
        }
      }
    }
  }
}

@Composable
private fun Destaque(
  item: Item,
  altura: Dp,
  compacto: Boolean,
  focoAssistir: FocusRequester,
  aoSubir: () -> Unit,
  aoDescer: () -> Unit,
  aoAssistir: () -> Unit,
  aoInfo: () -> Unit
) {
  Box(Modifier.fillMaxWidth().height(altura)) {
    Crossfade(targetState = item, label = "destaque_banner") { atual ->
      Box(Modifier.fillMaxSize()) {
        AsyncImage(
          model = atual.fundo ?: atual.cartaz,
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop
        )
        Box(
          Modifier
            .fillMaxSize()
            .background(
              Brush.horizontalGradient(
                listOf(Color(0xE60F172A), Color(0x990F172A), Color(0x220F172A))
              )
            )
        )
        Box(
          Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                listOf(Color(0x330F172A), Color.Transparent, Cor.bg)
              )
            )
        )
      }
    }

    Column(
      Modifier
        .align(Alignment.BottomStart)
        .padding(
          horizontal = if (compacto) 16.dp else 28.dp,
          vertical = if (compacto) 14.dp else 24.dp
        )
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (item.nota != null) {
          Text(
            "★ ${item.nota}",
            color = Cor.accent,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
          )
          Text("·", color = Cor.accent, fontSize = 11.5.sp)
        }
        Text(
          "LANÇAMENTO",
          color = Cor.accent,
          fontSize = 11.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 0.8.sp
        )
      }

      Vao(4)
      Text(
        item.titulo,
        color = Cor.text,
        fontSize = if (compacto) 22.sp else 32.sp,
        fontWeight = FontWeight.Black,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )

      if (item.sinopse.isNotEmpty()) {
        Vao(if (compacto) 4 else 8)
        Text(
          item.sinopse,
          color = Color(0xFFCBD5E1),
          fontSize = if (compacto) 12.sp else 14.sp,
          maxLines = if (compacto) 2 else 3,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.fillMaxWidth(if (compacto) 0.68f else 0.54f)
        )
      }

      Vao(if (compacto) 10 else 16)
      Row(
        Modifier.onPreviewKeyEvent { evento ->
          if (evento.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
          when (evento.key) {
            Key.DirectionUp -> {
              aoSubir(); true
            }
            Key.DirectionDown -> {
              aoDescer(); true
            }
            else -> false
          }
        },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Botao(
          "▶ Assistir",
          modifier = Modifier.focusRequester(focoAssistir),
          jeito = Jeito.PRIMARIO,
          pequeno = compacto
        ) { aoAssistir() }
        Botao("Mais informações", pequeno = compacto) { aoInfo() }
      }
    }
  }
}

@Composable
private fun CabecalhoFileira(
  titulo: String,
  compacto: Boolean,
  aoVerTodos: (() -> Unit)? = null
) {
  Row(
    Modifier
      .fillMaxWidth()
      .padding(
        start = if (compacto) 14.dp else 24.dp,
        end = if (compacto) 14.dp else 24.dp,
        bottom = if (compacto) 4.dp else 6.dp
      ),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      titulo,
      color = Cor.text,
      fontSize = if (compacto) 15.sp else 18.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.weight(1f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    if (aoVerTodos != null) {
      BotaoVerTodos(compacto = compacto, aoTocar = aoVerTodos)
    }
  }
}

@Composable
private fun BotaoVerTodos(compacto: Boolean, aoTocar: () -> Unit) {
  Box(
    Modifier
      .focusProperties { canFocus = false }
      .clip(RoundedCornerShape(8.dp))
      .background(Color(0xFF141E33))
      .border(1.dp, Cor.lineSoft, RoundedCornerShape(8.dp))
      .clickable { aoTocar() }
      .padding(
        horizontal = if (compacto) 10.dp else 12.dp,
        vertical = if (compacto) 5.dp else 6.dp
      )
  ) {
    Text(
      "Ver todos ›",
      color = Cor.muted,
      fontSize = if (compacto) 11.5.sp else 12.5.sp,
      fontWeight = FontWeight.SemiBold
    )
  }
}

@Composable
private fun UmaFileira(
  f: Fileira,
  compacto: Boolean,
  aoRegistrarFocador: (() -> Boolean) -> Unit,
  aoSubirDaLinha: () -> Unit,
  aoDescerDaLinha: () -> Unit,
  aoAbrir: (Item) -> Unit,
  aoPrecisarDeMais: () -> Unit,
  aoVerTodos: () -> Unit
) {
  val chaveLinha = "${f.tipo.chave}_${f.nome}"
  val focoLinha = remember(chaveLinha) { FocusRequester() }

  LaunchedEffect(chaveLinha) {
    aoRegistrarFocador {
      runCatching { focoLinha.requestFocus(); true }.getOrDefault(false)
    }
  }

  Column(Modifier.padding(top = if (compacto) 10.dp else 16.dp)) {
    CabecalhoFileira(f.nome, compacto, aoVerTodos = aoVerTodos)
    CarrosselFocoFixo(
      chaveLinha = chaveLinha,
      itens = f.itens,
      compacto = compacto,
      focoLinha = focoLinha,
      aoSubirDaLinha = aoSubirDaLinha,
      aoDescerDaLinha = aoDescerDaLinha,
      aoAbrir = aoAbrir,
      aoPrecisarDeMais = aoPrecisarDeMais
    )
  }
}

/**
 * Fileira de Categorias com cursor CENTRALIZADO no meio da tela.
 */
@Composable
private fun FileiraDeCategorias(
  generos: List<Genero>,
  compacto: Boolean,
  aoRegistrarFocador: (() -> Boolean) -> Unit,
  aoSubirDaLinha: () -> Unit,
  aoDescerDaLinha: () -> Unit,
  aoGenero: (Genero) -> Unit
) {
  val escopo = rememberCoroutineScope()
  val densidade = LocalDensity.current
  val estadoCat = rememberLazyListState()
  val focos = remember(generos) { List(generos.size) { FocusRequester() } }
  var indiceAtivo by remember(generos) { mutableIntStateOf(0) }

  val padH = if (compacto) 14.dp else 24.dp
  val larguraCardCat = if (compacto) 142.dp else 192.dp
  val larguraCardPx = with(densidade) { larguraCardCat.roundToPx() }

  suspend fun centralizarCategoria(idx: Int) {
    val viewportW = estadoCat.layoutInfo.viewportSize.width
    val itemW = estadoCat.layoutInfo.visibleItemsInfo
      .firstOrNull { it.index == idx }?.size ?: larguraCardPx
    val offsetCentro = if (viewportW > itemW) -((viewportW - itemW) / 2) else 0
    estadoCat.animateScrollToItem(idx, scrollOffset = offsetCentro)
  }

  fun moverParaCategoria(destino: Int) {
    if (generos.isEmpty()) return
    val idx = destino.coerceIn(0, generos.lastIndex)
    indiceAtivo = idx
    escopo.launch {
      centralizarCategoria(idx)
      var focou = false
      repeat(4) {
        if (!focou) {
          focou = runCatching { focos[idx].requestFocus(); true }.getOrDefault(false)
          if (!focou) delay(24)
        }
      }
    }
  }

  LaunchedEffect(generos) {
    aoRegistrarFocador {
      if (generos.isEmpty()) {
        false
      } else {
        val alvo = indiceAtivo.coerceIn(0, generos.lastIndex)
        val ok = runCatching { focos[alvo].requestFocus(); true }.getOrDefault(false)
        if (!ok) {
          moverParaCategoria(alvo)
          true
        } else {
          escopo.launch { centralizarCategoria(alvo) }
          true
        }
      }
    }
  }

  Column(Modifier.padding(top = if (compacto) 12.dp else 18.dp)) {
    CabecalhoFileira("Categorias", compacto)
    LazyRow(
      state = estadoCat,
      contentPadding = PaddingValues(horizontal = padH),
      horizontalArrangement = Arrangement.spacedBy(if (compacto) 10.dp else 14.dp),
      modifier = Modifier.onPreviewKeyEvent { evento ->
        if (evento.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (evento.key) {
          Key.DirectionUp -> {
            aoSubirDaLinha(); true
          }
          Key.DirectionDown -> {
            aoDescerDaLinha(); true
          }
          Key.DirectionRight -> {
            if (indiceAtivo + 1 < generos.size) {
              moverParaCategoria(indiceAtivo + 1)
            }
            true
          }
          Key.DirectionLeft -> {
            if (indiceAtivo > 0) {
              moverParaCategoria(indiceAtivo - 1)
            }
            true
          }
          else -> false
        }
      }
    ) {
      itemsIndexed(generos) { idx, g ->
        val cores = if (g.id == Tmdb.GENERO_LANCAMENTOS) {
          COR_CATEGORIA_LANCAMENTOS
        } else {
          val offsetCor = (idx - 1).coerceAtLeast(0)
          CORES_CATEGORIAS[offsetCor % CORES_CATEGORIAS.size]
        }
        CartaoCategoria(
          nome = g.nome,
          cores = cores,
          largura = larguraCardCat,
          compacto = compacto,
          modifier = Modifier.focusRequester(focos[idx]),
          aoMudarFoco = { focado ->
            if (focado) {
              indiceAtivo = idx
              escopo.launch { centralizarCategoria(idx) }
            }
          }
        ) {
          aoGenero(g)
        }
      }
    }
  }
}

@Composable
private fun CartaoCategoria(
  nome: String,
  cores: Pair<Color, Color>,
  largura: Dp,
  compacto: Boolean,
  modifier: Modifier = Modifier,
  aoMudarFoco: (Boolean) -> Unit = {},
  aoTocar: () -> Unit
) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()
  val forma = RoundedCornerShape(10.dp)

  LaunchedEffect(focado) {
    aoMudarFoco(focado)
  }

  Box(
    modifier
      .padding(vertical = 4.dp)
      .graphicsLayer {
        if (focado) {
          scaleX = 1.04f
          scaleY = 1.04f
        }
      }
      .width(largura)
      .height(if (compacto) 66.dp else 90.dp)
      .clip(forma)
      .background(Brush.linearGradient(listOf(cores.first, cores.second)))
      .border(
        width = if (focado) 2.5.dp else 1.dp,
        color = if (focado) Color.White else Color(0x26FFFFFF),
        shape = forma
      )
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoTocar() }
      .padding(horizontal = 12.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      nome.uppercase(),
      color = Color.White,
      fontSize = if (compacto) 12.5.sp else 15.sp,
      fontWeight = FontWeight.ExtraBold,
      letterSpacing = 0.9.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

/** A grade de uma categoria ou "Ver todos". */
@Composable
private fun Grade(
  itens: List<Item>,
  titulo: String,
  subtitulo: String? = null,
  larguraCard: Int,
  compacto: Boolean,
  carregando: Boolean,
  estado: LazyGridState,
  focoPrimeiroCard: FocusRequester,
  aoVoltar: () -> Unit,
  aoAbrir: (Item) -> Unit,
  aoCarregarMais: () -> Unit
) {
  LaunchedEffect(estado, itens.size) {
    snapshotFlow { estado.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
      .collect { ultimo ->
        if (itens.isNotEmpty() && ultimo >= itens.size - 4) aoCarregarMais()
      }
  }

  Column(Modifier.fillMaxSize()) {
    Row(
      Modifier
        .fillMaxWidth()
        .padding(
          horizontal = if (compacto) 14.dp else 24.dp,
          vertical = if (compacto) 8.dp else 12.dp
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Botao("← Voltar", jeito = Jeito.FANTASMA, pequeno = true) { aoVoltar() }
      Text(
        titulo,
        color = Cor.text,
        fontSize = if (compacto) 16.sp else 19.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      if (!subtitulo.isNullOrBlank()) {
        Box(
          Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF14243D))
            .border(1.dp, Cor.accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            subtitulo,
            color = Cor.accent,
            fontSize = if (compacto) 10.5.sp else 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
          )
        }
      }
    }

    if (itens.isEmpty() && carregando) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Rodinha() }
      return@Column
    }

    if (itens.isEmpty()) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("Nada encontrado", color = Cor.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
          Vao(6)
          Text("Tente outra categoria.", color = Cor.muted, fontSize = 13.sp)
        }
      }
      return@Column
    }

    LazyVerticalGrid(
      columns = GridCells.Adaptive(larguraCard.dp),
      state = estado,
      contentPadding = PaddingValues(
        horizontal = if (compacto) 14.dp else 24.dp,
        vertical = 10.dp
      ),
      horizontalArrangement = Arrangement.spacedBy(if (compacto) 10.dp else 14.dp),
      verticalArrangement = Arrangement.spacedBy(if (compacto) 14.dp else 18.dp),
      modifier = Modifier.fillMaxSize()
    ) {
      itensIndexadosDaGrade(itens) { idx, item ->
        Cartaz(
          item = item,
          largura = larguraCard,
          modifier = if (idx == 0) Modifier.focusRequester(focoPrimeiroCard) else Modifier
        ) { aoAbrir(item) }
      }
    }
  }
}

/**
 * Minha MovFlix usando o mesmo carrossel de foco fixo em todas as fileiras:
 * 1. Continuar assistindo
 * 2. Porque voce viu [ultimo titulo]
 * 3. Minha lista
 */
@Composable
private fun MinhaMovFlix(
  continuar: List<Marca>,
  salvos: List<Item>,
  porqueViuTitulo: String?,
  porqueViuItens: List<Item>,
  compacto: Boolean,
  estado: LazyListState,
  aoRegistrarFocoPrimeiraLinha: (() -> Unit) -> Unit,
  aoSubirParaCabecalho: () -> Unit,
  aoAbrir: (Item) -> Unit,
  aoVerTodosRecomendados: () -> Unit,
  aoEsquecer: (Tipo, Int) -> Unit
) {
  val escopo = rememberCoroutineScope()
  val itensContinuar = remember(continuar) {
    continuar.map { m -> Item(m.id, m.tipo, m.titulo, null, null, m.cartaz, null, "") }
  }
  val mapaMarcas = remember(continuar) {
    continuar.associateBy { "${it.tipo.chave}:${it.id}" }
  }

  val focoContinuar = remember { FocusRequester() }
  val focoPorqueViu = remember { FocusRequester() }
  val focoSalvos = remember { FocusRequester() }

  val ordemFocos = remember(itensContinuar, porqueViuTitulo, porqueViuItens, salvos) {
    buildList {
      if (itensContinuar.isNotEmpty()) add(focoContinuar)
      if (porqueViuTitulo != null && porqueViuItens.isNotEmpty()) add(focoPorqueViu)
      if (salvos.isNotEmpty()) add(focoSalvos)
    }
  }

  fun focarLinhaMinha(idx: Int) {
    val alvo = ordemFocos.getOrNull(idx) ?: return
    escopo.launch {
      estado.animateScrollToItem(idx)
      var ok = false
      repeat(4) {
        if (!ok) {
          ok = runCatching { alvo.requestFocus(); true }.getOrDefault(false)
          if (!ok) delay(30)
        }
      }
    }
  }

  LaunchedEffect(ordemFocos) {
    aoRegistrarFocoPrimeiraLinha {
      if (ordemFocos.isNotEmpty()) focarLinhaMinha(0)
    }
  }

  LazyColumn(
    state = estado,
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 32.dp)
  ) {
    if (itensContinuar.isNotEmpty()) {
      item(key = "minha_continuar") {
        val minhaPos = ordemFocos.indexOf(focoContinuar)
        Column(Modifier.padding(top = if (compacto) 10.dp else 16.dp)) {
          CabecalhoFileira("Continuar assistindo", compacto)
          CarrosselFocoFixo(
            chaveLinha = "minha_continuar",
            itens = itensContinuar,
            compacto = compacto,
            focoLinha = focoContinuar,
            aoSubirDaLinha = {
              if (minhaPos <= 0) aoSubirParaCabecalho() else focarLinhaMinha(minhaPos - 1)
            },
            aoDescerDaLinha = {
              if (minhaPos + 1 < ordemFocos.size) focarLinhaMinha(minhaPos + 1)
            },
            aoAbrir = aoAbrir,
            etiquetaEpisodioDe = { item ->
              val m = mapaMarcas["${item.tipo.chave}:${item.id}"]
              if (m != null && m.tipo.ehSerie) "T${m.temporada} · E${m.episodio}" else null
            },
            progressoDe = { item ->
              mapaMarcas["${item.tipo.chave}:${item.id}"]?.fracao
            },
            aoRemover = { item -> aoEsquecer(item.tipo, item.id) }
          )
        }
      }
    }

    if (porqueViuTitulo != null && porqueViuItens.isNotEmpty()) {
      item(key = "minha_porque_viu") {
        val minhaPos = ordemFocos.indexOf(focoPorqueViu)
        Column(Modifier.padding(top = if (compacto) 12.dp else 18.dp)) {
          CabecalhoFileira(
            titulo = "Porque você viu $porqueViuTitulo",
            compacto = compacto,
            aoVerTodos = aoVerTodosRecomendados
          )
          CarrosselFocoFixo(
            chaveLinha = "minha_porque_viu_${porqueViuTitulo}",
            itens = porqueViuItens,
            compacto = compacto,
            focoLinha = focoPorqueViu,
            aoSubirDaLinha = {
              if (minhaPos <= 0) aoSubirParaCabecalho() else focarLinhaMinha(minhaPos - 1)
            },
            aoDescerDaLinha = {
              if (minhaPos + 1 < ordemFocos.size) focarLinhaMinha(minhaPos + 1)
            },
            aoAbrir = aoAbrir
          )
        }
      }
    }

    if (salvos.isNotEmpty()) {
      item(key = "minha_lista") {
        val minhaPos = ordemFocos.indexOf(focoSalvos)
        Column(Modifier.padding(top = if (compacto) 12.dp else 18.dp)) {
          CabecalhoFileira("Minha lista", compacto)
          CarrosselFocoFixo(
            chaveLinha = "minha_lista",
            itens = salvos,
            compacto = compacto,
            focoLinha = focoSalvos,
            aoSubirDaLinha = {
              if (minhaPos <= 0) aoSubirParaCabecalho() else focarLinhaMinha(minhaPos - 1)
            },
            aoDescerDaLinha = {
              if (minhaPos + 1 < ordemFocos.size) focarLinhaMinha(minhaPos + 1)
            },
            aoAbrir = aoAbrir
          )
        }
      }
    }

    if (continuar.isEmpty() && salvos.isEmpty()) {
      item(key = "minha_vazia") {
        Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              "A sua MovFlix está vazia",
              color = Cor.text,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold
            )
            Vao(6)
            Text(
              "O que você começar a assistir ou salvar aparece aqui.",
              color = Cor.muted,
              fontSize = 13.sp
            )
          }
        }
      }
    }
  }
}
