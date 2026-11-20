package com.filmemeu.app.ui

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as itensDaGrade
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmemeu.app.data.Item
import com.filmemeu.app.data.Tipo
import com.filmemeu.app.data.Tmdb
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

private val TECLAS_BUSCA = listOf(
  listOf("a", "b", "c", "d", "e", "f"),
  listOf("g", "h", "i", "j", "k", "l"),
  listOf("m", "n", "o", "p", "q", "r"),
  listOf("s", "t", "u", "v", "w", "x"),
  listOf("y", "z", "0", "1", "2", "3"),
  listOf("4", "5", "6", "7", "8", "9")
)

/**
 * Tela de busca igual a versao web:
 * - A esquerda: campo "Buscar pelo nome" + botoes espaco/apagar + grade a-z / 0-9
 *   para funcionar tanto no controle remoto da TV/Stick quanto no toque do celular.
 * - A direita: "EM ALTA AGORA" (quando vazio) ou os resultados da busca em grade.
 */
@Composable
fun TelaBusca(
  infantil: Boolean,
  termoInicial: String,
  aoMudarTermo: (String) -> Unit,
  aoAbrirTitulo: (Item) -> Unit
) {
  var termo by remember(termoInicial) { mutableStateOf(termoInicial) }
  var emAlta by remember { mutableStateOf<List<Item>>(emptyList()) }
  var resultados by remember { mutableStateOf<List<Item>>(emptyList()) }
  var carregando by remember { mutableStateOf(true) }

  fun atualizar(novo: String) {
    termo = novo
    aoMudarTermo(novo)
  }

  // Carrega "EM ALTA AGORA" (filmes e series misturados, como no web).
  LaunchedEffect(infantil) {
    try {
      emAlta = coroutineScope {
        val f = async { Tmdb.emAlta(Tipo.FILME, 1, infantil) }
        val s = async { Tmdb.emAlta(Tipo.SERIE, 1, infantil) }
        intercalar(f.await(), s.await())
      }
    } catch (_: Exception) {
      emAlta = emptyList()
    }
    if (termo.isBlank()) carregando = false
  }

  // Busca com pequeno debounce enquanto a pessoa digita ou aperta letras no controle.
  LaunchedEffect(termo, infantil) {
    val q = termo.trim()
    if (q.isEmpty()) {
      resultados = emptyList()
      carregando = emAlta.isEmpty()
      return@LaunchedEffect
    }
    carregando = true
    delay(260)
    resultados = try {
      coroutineScope {
        val f = async { Tmdb.buscar(Tipo.FILME, q, 1, infantil) }
        val s = async { Tmdb.buscar(Tipo.SERIE, q, 1, infantil) }
        intercalar(f.await(), s.await())
      }
    } catch (_: Exception) {
      emptyList()
    }
    carregando = false
  }

  val listaVisivel = if (termo.isBlank()) emAlta else resultados
  val tituloGrade = if (termo.isBlank()) {
    "EM ALTA AGORA"
  } else {
    "RESULTADOS PARA \"${termo.trim().uppercase()}\""
  }

  BoxWithConstraints(Modifier.fillMaxSize().background(Cor.bg)) {
    val compacto = maxHeight < 460.dp || maxWidth < 740.dp
    val larguraTeclado = if (compacto) 216.dp else 258.dp
    val larguraCard = if (compacto) 108 else 136

    Row(
      Modifier
        .fillMaxSize()
        .padding(
          horizontal = if (compacto) 12.dp else 22.dp,
          vertical = if (compacto) 8.dp else 14.dp
        ),
      horizontalArrangement = Arrangement.spacedBy(if (compacto) 14.dp else 22.dp)
    ) {
      // Painel esquerdo: campo de busca + teclado na tela (TV/Stick/Toque)
      Column(
        Modifier
          .width(larguraTeclado)
          .fillMaxHeight()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(if (compacto) 6.dp else 8.dp)
      ) {
        // Campo de busca com icone de lupa
        Row(
          Modifier
            .fillMaxWidth()
            .clip(raioPilula)
            .background(Color(0xFF141E33))
            .border(1.dp, Cor.lineSoft, raioPilula)
            .padding(horizontal = 12.dp, vertical = if (compacto) 8.dp else 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = Cor.muted,
            modifier = Modifier.size(16.dp)
          )
          Box(Modifier.weight(1f)) {
            if (termo.isEmpty()) {
              Text(
                "Buscar pelo nome",
                color = Cor.placeholder,
                fontSize = if (compacto) 12.sp else 13.sp,
                maxLines = 1
              )
            }
            BasicTextField(
              value = termo,
              onValueChange = { atualizar(it) },
              singleLine = true,
              textStyle = LocalTextStyle.current.copy(
                color = Cor.text,
                fontSize = if (compacto) 12.5.sp else 13.5.sp
              ),
              cursorBrush = SolidColor(Cor.accent),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("search_input_field")
            )
          }
        }

        // Botoes espaco e apagar
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          TeclaBusca(
            rotulo = "espaço",
            compacto = compacto,
            modifier = Modifier.weight(1f)
          ) {
            if (termo.isNotEmpty() && !termo.endsWith(" ")) atualizar("$termo ")
          }
          TeclaBusca(
            rotulo = "apagar",
            compacto = compacto,
            modifier = Modifier.weight(1f)
          ) {
            if (termo.isNotEmpty()) atualizar(termo.dropLast(1))
          }
        }

        // Grade 6x6 a-z e 0-9
        TECLAS_BUSCA.forEach { linha ->
          Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compacto) 4.dp else 6.dp)
          ) {
            linha.forEach { tecla ->
              TeclaBusca(
                rotulo = tecla,
                compacto = compacto,
                modifier = Modifier.weight(1f)
              ) {
                atualizar(termo + tecla)
              }
            }
          }
        }
      }

      // Painel direito: EM ALTA AGORA ou resultados
      Column(Modifier.weight(1f).fillMaxHeight()) {
        Text(
          tituloGrade,
          color = Cor.text,
          fontSize = if (compacto) 13.sp else 15.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 0.8.sp,
          modifier = Modifier.padding(bottom = if (compacto) 8.dp else 12.dp)
        )

        when {
          carregando && listaVisivel.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Rodinha()
            }
          }

          listaVisivel.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  "Nada encontrado",
                  color = Cor.text,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold
                )
                Vao(6)
                Text(
                  "Tente outro nome na busca ao lado.",
                  color = Cor.muted,
                  fontSize = 13.sp
                )
              }
            }
          }

          else -> {
            LazyVerticalGrid(
              columns = GridCells.Adaptive(larguraCard.dp),
              contentPadding = PaddingValues(bottom = 20.dp),
              horizontalArrangement = Arrangement.spacedBy(if (compacto) 10.dp else 14.dp),
              verticalArrangement = Arrangement.spacedBy(if (compacto) 12.dp else 16.dp),
              modifier = Modifier.fillMaxSize()
            ) {
              itensDaGrade(listaVisivel) { item ->
                Cartaz(item, largura = larguraCard) { aoAbrirTitulo(item) }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TeclaBusca(
  rotulo: String,
  compacto: Boolean,
  modifier: Modifier = Modifier,
  aoTocar: () -> Unit
) {
  val fonte = remember { MutableInteractionSource() }
  val focada by fonte.collectIsFocusedAsState()

  Box(
    modifier
      .height(if (compacto) 30.dp else 36.dp)
      .clip(RoundedCornerShape(7.dp))
      .background(if (focada) Cor.accentStrong else Color(0xFF162136))
      .border(
        width = 1.dp,
        color = if (focada) Color.White else Cor.lineSoft,
        shape = RoundedCornerShape(7.dp)
      )
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoTocar() },
    contentAlignment = Alignment.Center
  ) {
    Text(
      rotulo,
      color = if (focada) Cor.sobreAccent else Cor.text,
      fontSize = if (compacto) 12.sp else 13.sp,
      fontWeight = if (focada) FontWeight.Bold else FontWeight.Medium,
      maxLines = 1
    )
  }
}

private fun intercalar(a: List<Item>, b: List<Item>): List<Item> {
  val saida = ArrayList<Item>(a.size + b.size)
  val max = maxOf(a.size, b.size)
  for (i in 0 until max) {
    if (i < a.size) saida.add(a[i])
    if (i < b.size) saida.add(b[i])
  }
  return saida.distinctBy { "${it.tipo.chave}:${it.id}" }
}
