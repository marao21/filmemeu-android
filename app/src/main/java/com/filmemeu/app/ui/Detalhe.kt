package com.filmemeu.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmemeu.app.data.Conta
import com.filmemeu.app.data.Fonte
import com.filmemeu.app.data.Guardados
import com.filmemeu.app.data.Item
import com.filmemeu.app.data.Temporada
import com.filmemeu.app.data.Tipo
import com.filmemeu.app.data.Tmdb

/**
 * A pagina do titulo: e o que o toque no cartaz abre.
 *
 * O player so comeca quando a pessoa aperta Assistir, como no site. Ao
 * fundo, a imagem larga do titulo; por cima, o nome, a ficha, a sinopse e,
 * em serie, a escolha de temporada e episodio.
 */
@Composable
fun TelaDetalhe(
  item: Item,
  aoVoltar: () -> Unit,
  aoAssistir: (Fonte, Long) -> Unit,
  aoAbrirOutro: (Item) -> Unit
) {
  val ctx = LocalContext.current
  val infantil = Conta.perfil?.infantil == true

  var temporadas by remember { mutableStateOf<List<Temporada>>(emptyList()) }
  var temporada by remember { mutableStateOf(1) }
  var episodio by remember { mutableStateOf(1) }
  var parecidos by remember { mutableStateOf<List<Item>>(emptyList()) }
  var naMinha by remember { mutableStateOf(Guardados.estaNaMinha(ctx, item.tipo, item.id)) }

  var pedindoFonte by remember { mutableStateOf(false) }
  var urlDigitada by remember { mutableStateOf("") }
  var refererDigitado by remember { mutableStateOf("") }

  LaunchedEffect(item.id) {
    if (item.tipo.ehSerie) {
      temporadas = try {
        Tmdb.temporadas(item.id)
      } catch (_: Exception) {
        emptyList()
      }
    }
    parecidos = try {
      Tmdb.parecidos(item.tipo, item.id, infantil)
    } catch (_: Exception) {
      emptyList()
    }
  }

  val guardada = Guardados.fonte(ctx, item.tipo, item.id, temporada, episodio)
  val onde = Guardados.posicaoDe(ctx, item.tipo, item.id, temporada, episodio)

  fun assistir(doComeco: Boolean) {
    val fonte = Guardados.fonte(ctx, item.tipo, item.id, temporada, episodio)
    if (fonte == null) {
      pedindoFonte = true
      return
    }
    aoAssistir(fonte, if (doComeco) 0L else onde)
  }

  Column(
    Modifier
      .fillMaxSize()
      .background(Cor.bg)
      .verticalScroll(rememberScrollState())
  ) {
    Box(Modifier.fillMaxWidth().height(380.dp)) {
      AsyncImage(
        model = item.fundo ?: item.cartaz,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
      )
      Box(
        Modifier.fillMaxSize().background(
          Brush.verticalGradient(listOf(Color(0x880F172A), Color(0x660F172A), Cor.bg))
        )
      )

      Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
        Botao("Voltar ao catalogo", jeito = Jeito.FANTASMA, pequeno = true) { aoVoltar() }
        Vao(10)

        Text(
          item.titulo,
          color = Cor.text, fontSize = 30.sp, fontWeight = FontWeight.Bold
        )
        Vao(6)

        val ficha = buildList {
          if (item.ano != null) add("" + item.ano)
          if (item.nota != null) add("Nota " + item.nota)
          add(
            when (item.tipo) {
              Tipo.FILME -> "Filme"
              Tipo.SERIE -> "Serie"
              Tipo.ANIME -> "Animacao"
            }
          )
        }.joinToString("  ·  ")
        Text(ficha, color = Cor.muted, fontSize = 13.sp)

        if (item.sinopse.isNotEmpty()) {
          Vao(10)
          Text(
            item.sinopse,
            color = Cor.muted, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(0.8f)
          )
        }

        if (item.tipo.ehSerie && temporadas.isNotEmpty()) {
          Vao(14)
          Text("Temporada", color = Cor.muted, fontSize = 12.sp)
          Vao(6)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(temporadas) { t ->
              Ficha("T" + t.numero, t.numero == temporada) {
                temporada = t.numero
                episodio = 1
              }
            }
          }

          val quantos = temporadas.firstOrNull { it.numero == temporada }?.episodios ?: 1
          Vao(10)
          Text("Episodio", color = Cor.muted, fontSize = 12.sp)
          Vao(6)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items((1..quantos).toList()) { e ->
              Ficha("" + e, e == episodio) { episodio = e }
            }
          }
        }

        Vao(16)
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Botao(
            if (onde > 0) "Continuar assistindo" else "Assistir",
            jeito = Jeito.PRIMARIO
          ) { assistir(false) }

          // So aparece depois que o titulo foi comecado: ai o botao de cima
          // vira "Continuar assistindo" e este da a outra saida, para quem
          // quer ver de novo desde o comeco.
          if (onde > 0) {
            Botao("Assistir do inicio") { assistir(true) }
          }

          Botao(if (naMinha) "Na minha lista" else "+ Minha lista", pequeno = true) {
            naMinha = Guardados.virarNaMinha(ctx, item)
          }

          if (guardada != null) {
            Botao("Trocar a fonte", jeito = Jeito.FANTASMA, pequeno = true) {
              urlDigitada = guardada.url
              refererDigitado = guardada.referer ?: ""
              pedindoFonte = true
            }
          }
        }

        if (guardada == null) {
          Vao(8)
          Text(
            "Este titulo ainda nao tem fonte de video neste aparelho.",
            color = Cor.muted, fontSize = 12.sp
          )
        }
      }
    }

    /*
     * De onde vem o video.
     *
     * O site pedia o endereco a uma funcao que varria sites de terceiros.
     * Aqui quem informa e quem assiste: o endereco do arquivo que a pessoa
     * tem direito de ver. Fica guardado por titulo, temporada e episodio,
     * para nao ser pedido de novo.
     */
    if (pedindoFonte) {
      Column(
        Modifier
          .padding(16.dp)
          .fillMaxWidth()
          .border(1.dp, Cor.lineSoft, raio)
          .background(Cor.panel, raio)
          .padding(18.dp)
      ) {
        Text("Fonte do video", color = Cor.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Vao(6)
        Text(
          "Informe o endereco do arquivo .mp4 ou .m3u8 que voce tem direito de assistir. " +
            "Ele fica guardado neste aparelho, so para este titulo.",
          color = Cor.muted, fontSize = 12.5.sp
        )
        Vao(14)

        Campo(
          "Endereco do video", urlDigitada, { urlDigitada = it },
          Modifier.fillMaxWidth(), dica = "https://..."
        )
        Vao(10)
        Campo(
          "Referer (so se o servidor exigir)", refererDigitado, { refererDigitado = it },
          Modifier.fillMaxWidth(), dica = "https://..."
        )

        Vao(16)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Botao("Salvar e assistir", jeito = Jeito.PRIMARIO) {
            val url = urlDigitada.trim()
            if (url.startsWith("http")) {
              val fonte = Fonte(
                tipo = item.tipo,
                id = item.id,
                temporada = temporada,
                episodio = episodio,
                url = url,
                referer = refererDigitado.trim().takeIf { it.isNotEmpty() },
                nome = item.titulo
              )
              Guardados.guardarFonte(ctx, fonte)
              pedindoFonte = false
              aoAssistir(fonte, onde)
            }
          }
          Botao("Cancelar", jeito = Jeito.FANTASMA) { pedindoFonte = false }
          if (guardada != null) {
            Botao("Apagar a fonte", jeito = Jeito.PERIGO, pequeno = true) {
              Guardados.apagarFonte(ctx, item.tipo, item.id, temporada, episodio)
              urlDigitada = ""
              pedindoFonte = false
            }
          }
        }
      }
    }

    // Parecidos com este titulo, para continuar escolhendo sem voltar.
    if (parecidos.isNotEmpty()) {
      Column(Modifier.padding(top = 10.dp, bottom = 30.dp)) {
        Text(
          "Parecidos com este",
          color = Cor.text, fontSize = 17.sp, fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
        )
        LazyRow(
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(parecidos) { p -> Cartaz(p) { aoAbrirOutro(p) } }
        }
      }
    }
  }
}
