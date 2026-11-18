package com.filmemeu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.filmemeu.app.data.Conta
import com.filmemeu.app.data.Guardados
import com.filmemeu.app.data.Item
import com.filmemeu.app.player.PlayerActivity
import com.filmemeu.app.ui.Cor
import com.filmemeu.app.ui.Rodinha
import com.filmemeu.app.ui.TelaCatalogo
import com.filmemeu.app.ui.TelaDetalhe
import com.filmemeu.app.ui.TelaLogin
import com.filmemeu.app.ui.TelaPerfis
import kotlinx.coroutines.launch

/**
 * As telas, na ordem em que a casa abre.
 *
 * E o mesmo caminho do site: a porta de entrada, depois quem vai assistir, e
 * so entao o catalogo. O player e uma tela separada, porque ela fica em
 * paisagem e sem nada em volta.
 */
private enum class Onde { ABRINDO, LOGIN, PERFIS, CATALOGO, DETALHE }

class MainActivity : ComponentActivity() {

  override fun onCreate(estado: Bundle?) {
    super.onCreate(estado)
    setContent { Casa() }
  }
}

@Composable
private fun Casa() {
  val ctx = LocalContext.current
  val escopo = rememberCoroutineScope()

  var onde by remember { mutableStateOf(Onde.ABRINDO) }
  var titulo by remember { mutableStateOf<Item?>(null) }

  /*
   * Quem ja entrou nao entra de novo.
   *
   * A sessao fica guardada no aparelho e e renovada ao abrir: numa TV o
   * aplicativo fica dias no ar, e sem renovar a casa parava de carregar
   * sozinha depois de uma hora.
   */
  LaunchedEffect(Unit) {
    Conta.recuperar(ctx)
    if (Conta.entrou) {
      Conta.renovar(ctx)
      onde = if (Conta.entrou) Onde.PERFIS else Onde.LOGIN
    } else {
      onde = Onde.LOGIN
    }
  }

  BackHandler(enabled = onde == Onde.DETALHE || onde == Onde.CATALOGO) {
    when (onde) {
      Onde.DETALHE -> {
        titulo = null
        onde = Onde.CATALOGO
      }
      // Do catalogo, o Voltar devolve a escolha de perfil, como o site faz.
      Onde.CATALOGO -> onde = Onde.PERFIS
      else -> Unit
    }
  }

  Box(Modifier.fillMaxSize().background(Cor.bg)) {
    when (onde) {
      Onde.ABRINDO -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Rodinha(tamanho = 44)
      }

      Onde.LOGIN -> TelaLogin(aoEntrar = { onde = Onde.PERFIS })

      Onde.PERFIS -> TelaPerfis(
        aoEscolher = { perfil ->
          Conta.perfil = perfil
          onde = Onde.CATALOGO
        },
        aoSair = { onde = Onde.LOGIN }
      )

      Onde.CATALOGO -> TelaCatalogo(
        aoAbrirTitulo = { item ->
          titulo = item
          onde = Onde.DETALHE
        },
        aoTrocarPerfil = {
          Conta.perfil = null
          onde = Onde.PERFIS
        },
        aoSair = {
          Conta.sair(ctx)
          onde = Onde.LOGIN
        }
      )

      Onde.DETALHE -> {
        val item = titulo
        if (item == null) {
          onde = Onde.CATALOGO
        } else {
          TelaDetalhe(
            item = item,
            aoVoltar = {
              titulo = null
              onde = Onde.CATALOGO
            },
            aoAssistir = { fonte, posicao ->
              PlayerActivity.abrir(
                ctx = ctx,
                url = fonte.url,
                referer = fonte.referer,
                titulo = item.titulo,
                cartaz = item.cartaz,
                tipo = item.tipo,
                id = item.id,
                temporada = fonte.temporada,
                episodio = fonte.episodio,
                posicao = posicao
              )
            },
            aoAbrirOutro = { outro -> titulo = outro }
          )
        }
      }
    }
  }
}
