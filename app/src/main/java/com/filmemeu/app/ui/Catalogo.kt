package com.filmemeu.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
// A grade tem o `items` dela, que nao e o da fileira: um conta itens de
// lista, o outro de grade, e trocados um pelo outro nem compilam.
import androidx.compose.foundation.lazy.grid.items as itensDaGrade
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmemeu.app.data.Conta
import com.filmemeu.app.data.Fileira
import com.filmemeu.app.data.Genero
import com.filmemeu.app.data.Guardados
import com.filmemeu.app.data.Item
import com.filmemeu.app.data.Marca
import com.filmemeu.app.data.Progresso
import com.filmemeu.app.data.Tipo
import com.filmemeu.app.data.Tmdb
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Quantas fileiras de genero a casa monta, como no site. */
private const val QUANTAS_FILEIRAS = 8

/** As abas do catalogo, na ordem do site. */
private val ABAS = listOf("Inicio", "Filmes", "Series", "Animacoes", "Minha MovFlix")

/**
 * A casa.
 *
 * Uma fileira por categoria, cada uma com os seus cartazes, e o destaque no
 * alto. Escolhida uma categoria ou feita uma busca, as fileiras dao lugar a
 * grade — e e assim que o site tambem faz.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaCatalogo(
  aoAbrirTitulo: (Item) -> Unit,
  aoTrocarPerfil: () -> Unit,
  aoSair: () -> Unit
) {
  val ctx = LocalContext.current
  val escopo = rememberCoroutineScope()
  val infantil = Conta.perfil?.infantil == true

  var aba by remember { mutableStateOf(0) }
  var busca by remember { mutableStateOf("") }
  var buscaAberta by remember { mutableStateOf(false) }
  var generoEscolhido by remember { mutableStateOf<Genero?>(null) }
  var mostrarCategorias by remember { mutableStateOf(false) }
  var menuAberto by remember { mutableStateOf(false) }

  var generos by remember { mutableStateOf<List<Genero>>(emptyList()) }
  var fileiras by remember { mutableStateOf<List<Fileira>>(emptyList()) }
  var destaques by remember { mutableStateOf<List<Item>>(emptyList()) }
  var grade by remember { mutableStateOf<List<Item>>(emptyList()) }
  var pagina by remember { mutableStateOf(1) }
  var carregando by remember { mutableStateOf(false) }
  var recado by remember { mutableStateOf<String?>(null) }
  var continuar by remember { mutableStateOf<List<Marca>>(emptyList()) }

  /** Inicio mistura os tres; cada uma das outras mostra so o seu. */
  val tipoDaAba = when (aba) {
    2 -> Tipo.SERIE
    3 -> Tipo.ANIME
    else -> Tipo.FILME
  }
  val inicio = aba == 0
  val naMinha = aba == 4
  val naGrade = generoEscolhido != null || busca.isNotBlank()

  /* ----- a casa aparece por partes ----- */

  LaunchedEffect(aba, infantil) {
    if (naMinha) {
      // Primeiro o que ja esta no aparelho, para a fileira aparecer sem
      // esperar a rede; depois o que a conta sabe, que pode trazer o que
      // foi assistido em outro aparelho.
      continuar = Guardados.continuar(ctx)
      try {
        Guardados.absorver(ctx, Progresso.listar())
        continuar = Guardados.continuar(ctx)
      } catch (_: Exception) {
        // Sem internet, vale o que esta guardado aqui.
      }
      return@LaunchedEffect
    }
    recado = null
    carregando = true
    fileiras = emptyList()
    destaques = emptyList()

    try {
      generos = Tmdb.generos(tipoDaAba)

      /*
       * Os pedidos partem todos juntos.
       *
       * Antes tudo esperava por tudo: umas onze consultas ao TMDB e a tela
       * vazia ate a ultima responder, e a ultima e sempre a mais lenta.
       * Numa caixa de TV, com rede pior, era isso que fazia a casa demorar.
       */
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
          val nome = if (tipoDaAba == Tipo.FILME) "Lancamentos" else "No ar agora"
          montadas.add(Fileira(nome, novidades, tipoDaAba))
        }
        fileiras = montadas.toList()

        // O destaque do topo entra quando a sua consulta chegar, sem
        // segurar o resto.
        val consagrados = vemConsagrados.await()
        destaques = (novidades.filter { it.fundo != null } + consagrados.filter { it.fundo != null })
          .distinctBy { it.id }
          .take(3)

        if (inicio) {
          val series = vemSeries.await()
          if (series.isNotEmpty()) {
            montadas.add(Fileira("Series no ar agora", series, Tipo.SERIE))
            fileiras = montadas.toList()
          }
        }

        if (consagrados.isNotEmpty()) {
          montadas.add(Fileira("Consagrados", consagrados, tipoDaAba))
          fileiras = montadas.toList()
        }

        vemListas.forEachIndexed { i, vem ->
          val itens = vem.await()
          if (itens.isNotEmpty()) {
            montadas.add(Fileira(planos[i].nome, itens, tipoDaAba, planos[i].id))
            fileiras = montadas.toList()
          }
        }
      }
    } catch (_: Exception) {
      recado = "O catalogo esta fora do ar por um instante. Tente de novo daqui a pouco."
    }
    carregando = false
  }

  /* ----- a grade: busca ou categoria ----- */

  LaunchedEffect(generoEscolhido, busca, aba) {
    if (!naGrade || naMinha) return@LaunchedEffect
    carregando = true
    pagina = 1
    grade = try {
      if (busca.isNotBlank()) {
        Tmdb.buscar(tipoDaAba, busca, 1, infantil)
      } else {
        Tmdb.porGenero(tipoDaAba, generoEscolhido?.id, 1, infantil)
      }
    } catch (_: Exception) {
      recado = "Nao deu para carregar a lista."
      emptyList()
    }
    carregando = false
  }

  fun maisUmaPagina() {
    if (carregando) return
    escopo.launch {
      carregando = true
      val proxima = pagina + 1
      val novos = try {
        if (busca.isNotBlank()) {
          Tmdb.buscar(tipoDaAba, busca, proxima, infantil)
        } else {
          Tmdb.porGenero(tipoDaAba, generoEscolhido?.id, proxima, infantil)
        }
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

  Column(Modifier.fillMaxSize().background(Cor.bg)) {

    /* ----- a barra do alto ----- */
    BarraDoCatalogo(
      aba = aba,
      busca = busca,
      buscaAberta = buscaAberta,
      aoAbrirBusca = { buscaAberta = !buscaAberta },
      aoBuscar = { busca = it },
      aoTrocarAba = {
        aba = it
        busca = ""
        buscaAberta = false
        generoEscolhido = null
        mostrarCategorias = false
      },
      aoCategorias = { mostrarCategorias = !mostrarCategorias },
      menuAberto = menuAberto,
      aoMenu = { menuAberto = !menuAberto },
      aoTrocarPerfil = aoTrocarPerfil,
      aoSair = aoSair
    )

    if (mostrarCategorias && generos.isNotEmpty()) {
      FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Ficha("Todos", generoEscolhido == null) {
          generoEscolhido = null
          mostrarCategorias = false
        }
        generos.forEach { g ->
          Ficha(g.nome, generoEscolhido?.id == g.id) {
            generoEscolhido = if (generoEscolhido?.id == g.id) null else g
          }
        }
      }
    }

    if (recado != null) {
      Text(
        recado!!,
        color = Cor.danger,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
      )
    }

    when {
      naMinha -> MinhaMovFlix(continuar, Guardados.minha(ctx), aoAbrirTitulo) { tipo, id ->
        Guardados.esquecer(ctx, tipo, id)
        continuar = Guardados.continuar(ctx)
      }

      naGrade -> Grade(
        itens = grade,
        titulo = if (busca.isNotBlank()) {
          "Resultados para \"" + busca + "\""
        } else {
          generoEscolhido?.nome ?: "Catalogo"
        },
        carregando = carregando,
        aoAbrir = aoAbrirTitulo,
        aoCarregarMais = { maisUmaPagina() }
      )

      else -> Fileiras(
        fileiras = fileiras,
        destaques = destaques,
        generos = generos,
        carregando = carregando,
        aoAbrir = aoAbrirTitulo,
        aoGenero = { generoEscolhido = it }
      )
    }
  }
}

/**
 * A barra do alto, com a busca recolhida numa lupa.
 *
 * Aberta, a busca e a peca mais larga da barra e no celular empurrava tudo
 * para a linha de baixo — por isso ela comeca recolhida, como no site.
 */
@Composable
private fun BarraDoCatalogo(
  aba: Int,
  busca: String,
  buscaAberta: Boolean,
  aoAbrirBusca: () -> Unit,
  aoBuscar: (String) -> Unit,
  aoTrocarAba: (Int) -> Unit,
  aoCategorias: () -> Unit,
  menuAberto: Boolean,
  aoMenu: () -> Unit,
  aoTrocarPerfil: () -> Unit,
  aoSair: () -> Unit
) {
  Column(
    Modifier
      .fillMaxWidth()
      .background(Brush.verticalGradient(listOf(Color(0xFF16213A), Cor.bg)))
      .padding(horizontal = 16.dp, vertical = 10.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      MarcaMovFlix(tamanho = 30)
      VaoH(10)

      Box(Modifier.weight(1f)) {
        if (buscaAberta) {
          Campo(null, busca, aoBuscar, Modifier.fillMaxWidth(), dica = "Buscar pelo nome")
        } else {
          Abas(ABAS, aba, aoEscolher = aoTrocarAba)
        }
      }

      VaoH(8)
      Botao(if (buscaAberta) "Fechar" else "Buscar", pequeno = true) { aoAbrirBusca() }
      VaoH(6)
      Botao("Categorias", pequeno = true) { aoCategorias() }
      VaoH(8)

      // A carinha e o perfil: e ela que guarda a conta, as opcoes e a saida.
      val p = Conta.perfil
      Box {
        Box(Modifier.focusable().clickable { aoMenu() }) {
          Cara(p?.figura ?: "sorriso", p?.cor ?: "#38bdf8", 34)
        }
      }
    }

    if (buscaAberta) {
      Vao(8)
      Abas(ABAS, aba, aoEscolher = aoTrocarAba)
    }

    if (menuAberto) {
      Vao(10)
      Column(
        Modifier
          .border(1.dp, Cor.lineSoft, raio)
          .background(Cor.panel, raio)
          .padding(8.dp)
      ) {
        Text(
          Conta.perfil?.nome ?: (Conta.email ?: ""),
          color = Cor.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        BotaoTexto("Trocar de perfil", tamanho = 14) { aoTrocarPerfil() }
        BotaoTexto("Sair", tamanho = 14) { aoSair() }
      }
    }
  }
}

/**
 * O destaque e as fileiras.
 *
 * E a primeira coisa que se ve ao abrir: a imagem larga ao fundo, o nome, a
 * sinopse e os dois botoes.
 */
@Composable
private fun Fileiras(
  fileiras: List<Fileira>,
  destaques: List<Item>,
  generos: List<Genero>,
  carregando: Boolean,
  aoAbrir: (Item) -> Unit,
  aoGenero: (Genero) -> Unit
) {
  var qualDestaque by remember { mutableStateOf(0) }

  LazyColumn(Modifier.fillMaxSize()) {
    if (destaques.isNotEmpty()) {
      item {
        Destaque(
          item = destaques[qualDestaque.coerceIn(0, destaques.lastIndex)],
          quantos = destaques.size,
          qual = qualDestaque,
          aoTrocar = { qualDestaque = it },
          aoAssistir = { aoAbrir(destaques[qualDestaque]) },
          aoInfo = { aoAbrir(destaques[qualDestaque]) }
        )
      }
    }

    if (carregando && fileiras.isEmpty()) {
      item {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
          Rodinha()
        }
      }
    }

    itemsIndexed(fileiras) { i, f ->
      // No meio do caminho entram as categorias, como uma parada para quem
      // ja desceu um pouco e ainda nao achou o que queria.
      if (i == fileiras.size / 2 && generos.isNotEmpty()) {
        FileiraDeCategorias(generos, aoGenero)
      }
      UmaFileira(f, aoAbrir)
    }
  }
}

@Composable
private fun Destaque(
  item: Item,
  quantos: Int,
  qual: Int,
  aoTrocar: (Int) -> Unit,
  aoAssistir: () -> Unit,
  aoInfo: () -> Unit
) {
  val alta = LocalConfiguration.current.screenHeightDp
  Box(Modifier.fillMaxWidth().height((alta * 0.72f).coerceIn(270f, 420f).dp)) {
    AsyncImage(
      model = item.fundo ?: item.cartaz,
      contentDescription = null,
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.Crop
    )
    // A sombra, para o texto ler por cima da imagem.
    Box(
      Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(Color(0x660F172A), Color(0x330F172A), Cor.bg)
          )
        )
    )

    Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
      Text(
        if (item.tipo == Tipo.FILME) "FILME" else "SERIE",
        color = Cor.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold
      )
      Vao(6)
      Text(
        item.titulo,
        color = Cor.text, fontSize = 30.sp, fontWeight = FontWeight.Bold,
        maxLines = 2, overflow = TextOverflow.Ellipsis
      )
      Vao(8)
      Text(
        item.sinopse,
        color = Cor.muted, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(0.7f)
      )
      Vao(14)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Botao("Assistir", jeito = Jeito.PRIMARIO) { aoAssistir() }
        Botao("Mais informacoes") { aoInfo() }
      }
      if (quantos > 1) {
        Vao(12)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          repeat(quantos) { i ->
            Box(
              Modifier
                .size(if (i == qual) 10.dp else 8.dp)
                .background(if (i == qual) Cor.accent else Cor.lineSoft, RoundedCornerShape(50))
                .focusable()
                .clickable { aoTrocar(i) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun UmaFileira(f: Fileira, aoAbrir: (Item) -> Unit) {
  Column(Modifier.padding(top = 18.dp)) {
    Text(
      f.nome,
      color = Cor.text, fontSize = 17.sp, fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
    )
    LazyRow(
      contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(f.itens) { item -> Cartaz(item) { aoAbrir(item) } }
    }
  }
}

@Composable
private fun FileiraDeCategorias(generos: List<Genero>, aoGenero: (Genero) -> Unit) {
  Column(Modifier.padding(top = 20.dp)) {
    Text(
      "Categorias",
      color = Cor.text, fontSize = 17.sp, fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
    )
    LazyRow(
      contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(generos) { g -> Ficha(g.nome, false) { aoGenero(g) } }
    }
  }
}

/**
 * O cartaz.
 *
 * Com o foco, ele cresce um pouco e o sinal de play aparece no meio — e o
 * `.cat-card:hover` do site, traduzido para quem navega com o controle.
 */
@Composable
fun Cartaz(item: Item, largura: Int = 132, aoTocar: () -> Unit) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()

  Column(
    Modifier
      .width(largura.dp)
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoTocar() }
  ) {
    Box(
      Modifier
        .fillMaxWidth()
        .aspectRatio(2f / 3f)
        .border(if (focado) 3.dp else 0.dp, if (focado) Cor.accent else Color.Transparent, raio8)
        .background(Cor.bgSoft, raio8)
    ) {
      if (item.cartaz != null) {
        AsyncImage(
          model = item.cartaz,
          contentDescription = item.titulo,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop
        )
      } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("sem capa", color = Cor.muted, fontSize = 11.sp)
        }
      }

      Selo(
        when (item.tipo) {
          Tipo.FILME -> "FILME"
          Tipo.SERIE -> "SERIE"
          Tipo.ANIME -> "ANIME"
        },
        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
      )

      if (focado) {
        Box(
          Modifier
            .align(Alignment.Center)
            .size(44.dp)
            .background(Color(0xCC0EA5E9), RoundedCornerShape(50)),
          contentAlignment = Alignment.Center
        ) {
          Text("▶", color = Cor.text, fontSize = 18.sp)
        }
      }
    }

    Vao(6)
    Text(
      item.titulo,
      color = if (focado) Cor.text else Cor.muted,
      fontSize = 13.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Row {
      if (item.ano != null) Text("" + item.ano, color = Cor.muted, fontSize = 12.sp)
      if (item.nota != null) {
        if (item.ano != null) Text(" · ", color = Cor.muted, fontSize = 12.sp)
        Text("" + item.nota, color = Cor.warn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}

/** A grade: busca e categoria. */
@Composable
private fun Grade(
  itens: List<Item>,
  titulo: String,
  carregando: Boolean,
  aoAbrir: (Item) -> Unit,
  aoCarregarMais: () -> Unit
) {
  val estado = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

  // Ficando a vista o pe da lista, outra pagina e buscada: e o que faz o
  // catalogo nao ter fundo.
  LaunchedEffect(estado, itens.size) {
    snapshotFlow { estado.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
      .collect { ultimo ->
        if (itens.isNotEmpty() && ultimo >= itens.size - 4) aoCarregarMais()
      }
  }

  Column(Modifier.fillMaxSize()) {
    Text(
      titulo,
      color = Cor.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp)
    )

    if (itens.isEmpty() && !carregando) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("Nada encontrado", color = Cor.text, fontSize = 15.sp,
            fontWeight = FontWeight.Bold)
          Vao(6)
          Text("Tente outro nome ou outra categoria.", color = Cor.muted, fontSize = 13.sp)
        }
      }
      return@Column
    }

    LazyVerticalGrid(
      columns = GridCells.Adaptive(132.dp),
      state = estado,
      contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.fillMaxSize()
    ) {
      itensDaGrade(itens) { item -> Cartaz(item, largura = 132) { aoAbrir(item) } }
    }
  }
}

/**
 * Minha MovFlix.
 *
 * "Continuar assistindo" e a primeira coisa daqui, e so daqui: repetida em
 * toda aba, a mesma fileira abria Inicio, Filmes, Series e Animacoes —
 * quatro lugares mostrando o mesmo, e nenhum deles sendo o lugar dela.
 */
@Composable
private fun MinhaMovFlix(
  continuar: List<Marca>,
  salvos: List<Item>,
  aoAbrir: (Item) -> Unit,
  aoEsquecer: (Tipo, Int) -> Unit
) {
  LazyColumn(Modifier.fillMaxSize()) {
    if (continuar.isNotEmpty()) {
      item {
        Column(Modifier.padding(top = 16.dp)) {
          Text(
            "Continuar assistindo",
            color = Cor.text, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
          )
          LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(continuar) { m ->
              Column(Modifier.width(132.dp)) {
                Cartaz(
                  Item(m.id, m.tipo, m.titulo, null, null, m.cartaz, null, "")
                ) {
                  aoAbrir(Item(m.id, m.tipo, m.titulo, null, null, m.cartaz, null, ""))
                }
                // A barrinha do quanto ja foi visto.
                Box(
                  Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Cor.line)
                ) {
                  Box(
                    Modifier
                      .fillMaxWidth(m.fracao)
                      .height(3.dp)
                      .background(Cor.accent)
                  )
                }
                if (m.tipo.ehSerie) {
                  Text(
                    "T" + m.temporada + " E" + m.episodio,
                    color = Cor.muted, fontSize = 11.sp
                  )
                }
                BotaoTexto("remover", tamanho = 11) { aoEsquecer(m.tipo, m.id) }
              }
            }
          }
        }
      }
    }

    if (salvos.isNotEmpty()) {
      item {
        Column(Modifier.padding(top = 20.dp)) {
          Text(
            "Minha lista",
            color = Cor.text, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
          )
          LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(salvos) { item -> Cartaz(item) { aoAbrir(item) } }
          }
        }
      }
    }

    if (continuar.isEmpty() && salvos.isEmpty()) {
      item {
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("A sua MovFlix esta vazia", color = Cor.text, fontSize = 16.sp,
              fontWeight = FontWeight.Bold)
            Vao(6)
            Text(
              "O que voce comecar a assistir aparece aqui.",
              color = Cor.muted, fontSize = 13.sp
            )
          }
        }
      }
    }
  }
}
