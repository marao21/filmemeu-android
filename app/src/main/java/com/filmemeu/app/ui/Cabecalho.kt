package com.filmemeu.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmemeu.app.data.Conta

/** As abas do cabecalho, na mesma ordem e grafia da versao web. */
val ABAS_PRINCIPAIS = listOf("Início", "Filmes", "Séries", "Animações")
const val ABA_MINHA = 4

/**
 * O cabecalho unificado do MovFlix, igual ao da versao web.
 *
 * Avatar do perfil a esquerda; lupa de busca + Início, Filmes, Séries,
 * Animações | Minha MovFlix centralizados; responsivo para TV box, sticks,
 * tablets e celulares na horizontal.
 *
 * Quando o foco sobe para qualquer botao do cabecalho, chama [aoFocarCabecalho]
 * para rolar a tela toda para o topo. Quando o usuario aperta para baixo em
 * qualquer botao do cabecalho, chama [aoDescerDoCabecalho] para cair no botao
 * Assistir do destaque (ou no primeiro card quando nao ha destaque).
 */
@Composable
fun CabecalhoMovFlix(
  abaSelecionada: Int,
  buscaAberta: Boolean,
  compacto: Boolean,
  aoAbrirBusca: () -> Unit,
  aoTrocarAba: (Int) -> Unit,
  aoTrocarPerfil: () -> Unit,
  aoSair: () -> Unit,
  focoAbaAtiva: FocusRequester? = null,
  trocarAbaAoFocar: Boolean = false,
  aoFocarCabecalho: () -> Unit = {},
  aoDescerDoCabecalho: (() -> Boolean)? = null
) {
  var menuAberto by remember { mutableStateOf(false) }
  val p = Conta.perfil
  val tamAvatar = if (compacto) 34 else 40

  // FocusRequesters estaveis para nunca perder foco no cabecalho quando a aba ativa muda.
  val focoLupaInterno = remember { FocusRequester() }
  val focosAbasInternos = remember { List(ABAS_PRINCIPAIS.size) { FocusRequester() } }
  val focoMinhaInterno = remember { FocusRequester() }

  Column(
    Modifier
      .fillMaxWidth()
      .onFocusChanged { estado ->
        if (estado.hasFocus) aoFocarCabecalho()
      }
      .onPreviewKeyEvent { evento ->
        if (!menuAberto && evento.type == KeyEventType.KeyDown && evento.key == Key.DirectionDown) {
          aoDescerDoCabecalho?.invoke() == true
        } else {
          false
        }
      }
      .background(
        Brush.verticalGradient(listOf(Color(0xFF0A1120), Color(0xEE0F172A)))
      )
      .padding(
        horizontal = if (compacto) 14.dp else 24.dp,
        vertical = if (compacto) 8.dp else 12.dp
      )
  ) {
    Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Avatar do perfil no canto esquerdo, igual ao web.
      val fonteAvatar = remember { MutableInteractionSource() }
      val focadoAvatar by fonteAvatar.collectIsFocusedAsState()
      Box(
        Modifier
          .testTag("profile_menu_button")
          .border(
            width = if (focadoAvatar || menuAberto) 2.dp else 1.dp,
            color = if (focadoAvatar || menuAberto) Cor.accent else Cor.lineSoft,
            shape = RoundedCornerShape(10.dp)
          )
          .clip(RoundedCornerShape(10.dp))
          .focusable(interactionSource = fonteAvatar)
          .clickable(interactionSource = fonteAvatar, indication = null) {
            menuAberto = !menuAberto
          }
          .padding(2.dp)
      ) {
        Cara(p?.figura ?: "sorriso", p?.cor ?: "#38bdf8", tamAvatar)
      }

      // Centro: lupa + abas principais + divisor + Minha MovFlix
      Box(
        Modifier
          .weight(1f)
          .padding(horizontal = if (compacto) 8.dp else 16.dp),
        contentAlignment = Alignment.Center
      ) {
        Row(
          Modifier.horizontalScroll(rememberScrollState()),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(if (compacto) 4.dp else 8.dp)
        ) {
          BotaoLupa(
            ativa = buscaAberta,
            compacto = compacto,
            modifier = Modifier.focusRequester(
              if (buscaAberta && focoAbaAtiva != null) focoAbaAtiva else focoLupaInterno
            ),
            aoTocar = {
              menuAberto = false
              aoAbrirBusca()
            }
          )

          ABAS_PRINCIPAIS.forEachIndexed { indice, nome ->
            val selecionada = !buscaAberta && abaSelecionada == indice
            BotaoAbaWeb(
              texto = nome,
              selecionada = selecionada,
              compacto = compacto,
              destaquePilula = false,
              trocarAoFocar = trocarAbaAoFocar,
              modifier = Modifier.focusRequester(
                if (selecionada && focoAbaAtiva != null) focoAbaAtiva else focosAbasInternos[indice]
              ),
              aoTocar = {
                menuAberto = false
                aoTrocarAba(indice)
              }
            )
          }

          // Divisor vertical fino antes de "Minha MovFlix", igual ao web.
          Box(
            Modifier
              .padding(horizontal = if (compacto) 4.dp else 8.dp)
              .width(1.dp)
              .height(if (compacto) 18.dp else 22.dp)
              .background(Cor.lineSoft)
          )

          val minhaAtiva = !buscaAberta && abaSelecionada == ABA_MINHA
          BotaoAbaWeb(
            texto = "Minha MovFlix",
            selecionada = minhaAtiva,
            compacto = compacto,
            destaquePilula = true,
            trocarAoFocar = trocarAbaAoFocar,
            modifier = Modifier.focusRequester(
              if (minhaAtiva && focoAbaAtiva != null) focoAbaAtiva else focoMinhaInterno
            ),
            aoTocar = {
              menuAberto = false
              aoTrocarAba(ABA_MINHA)
            }
          )
        }
      }

      if (!compacto) {
        Spacer(Modifier.width((tamAvatar + 6).dp))
      }
    }

    if (menuAberto) {
      Vao(8)
      Row(
        Modifier
          .border(1.dp, Cor.lineSoft, raio)
          .background(Cor.panel, raio)
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Text(
          Conta.perfil?.nome ?: (Conta.email ?: ""),
          color = Cor.accent,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold
        )
        BotaoTexto("Trocar de perfil", tamanho = 13) {
          menuAberto = false
          aoTrocarPerfil()
        }
        BotaoTexto("Sair", tamanho = 13) {
          menuAberto = false
          aoSair()
        }
      }
    }
  }
}

@Composable
private fun BotaoLupa(
  ativa: Boolean,
  compacto: Boolean,
  modifier: Modifier = Modifier,
  aoTocar: () -> Unit
) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()
  val tam = if (compacto) 34.dp else 38.dp
  Box(
    modifier
      .testTag("header_search_button")
      .size(tam)
      .clip(CircleShape)
      .background(
        when {
          ativa -> Cor.line
          focado -> Color(0xFF1A2942)
          else -> Color.Transparent
        }
      )
      .border(
        width = if (focado || ativa) 1.5.dp else 0.dp,
        color = if (focado || ativa) Cor.accent else Color.Transparent,
        shape = CircleShape
      )
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoTocar() },
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = Icons.Rounded.Search,
      contentDescription = "Buscar",
      tint = if (ativa || focado) Cor.accent else Cor.text,
      modifier = Modifier.size(if (compacto) 18.dp else 20.dp)
    )
  }
}

@Composable
private fun BotaoAbaWeb(
  texto: String,
  selecionada: Boolean,
  compacto: Boolean,
  destaquePilula: Boolean,
  trocarAoFocar: Boolean = false,
  modifier: Modifier = Modifier,
  aoTocar: () -> Unit
) {
  val fonte = remember { MutableInteractionSource() }
  val focada by fonte.collectIsFocusedAsState()

  // Atualiza a pagina automaticamente ao passar o foco pelos botoes do cabecalho.
  LaunchedEffect(focada, trocarAoFocar, selecionada) {
    if (focada && trocarAoFocar && !selecionada) {
      aoTocar()
    }
  }

  val mostrarBordaAccent = focada || (destaquePilula && selecionada)
  val mostrarFundoPilula = selecionada || focada

  Box(
    modifier
      .testTag("header_tab_${texto.lowercase()}")
      .clip(raioPilula)
      .background(
        when {
          destaquePilula && selecionada -> Color(0xFF14243D)
          mostrarFundoPilula -> Color(0xFF1C2942)
          else -> Color.Transparent
        }
      )
      .border(
        width = if (mostrarBordaAccent) 1.5.dp else 0.dp,
        color = if (mostrarBordaAccent) Cor.accent else Color.Transparent,
        shape = raioPilula
      )
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoTocar() }
      .padding(
        horizontal = if (compacto) 12.dp else 16.dp,
        vertical = if (compacto) 6.dp else 8.dp
      ),
    contentAlignment = Alignment.Center
  ) {
    Text(
      texto,
      color = if (selecionada || focada) Cor.text else Cor.muted,
      fontSize = if (compacto) 13.sp else 14.sp,
      fontWeight = if (selecionada || focada) FontWeight.Bold else FontWeight.Medium,
      maxLines = 1
    )
  }
}
