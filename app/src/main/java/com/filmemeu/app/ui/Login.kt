package com.filmemeu.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmemeu.app.data.Conta
import kotlinx.coroutines.launch

/**
 * A porta de entrada, igual a do site.
 *
 * Fica por cima de tudo ate haver uma sessao valida, e o resto do sistema
 * nem aparece antes disso. As abas Entrar e Criar conta pedem as mesmas
 * coisas, e por isso sao a mesma tela com um campo a mais.
 */
@Composable
fun TelaLogin(aoEntrar: () -> Unit) {
  val ctx = LocalContext.current
  val escopo = rememberCoroutineScope()

  var criando by remember { mutableStateOf(false) }
  var esqueci by remember { mutableStateOf(false) }

  var nome by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var senha by remember { mutableStateOf("") }
  var recado by remember { mutableStateOf<String?>(null) }
  var erro by remember { mutableStateOf(false) }
  var trabalhando by remember { mutableStateOf(false) }

  fun enviar() {
    if (trabalhando) return
    recado = null
    erro = false

    if (esqueci) {
      if (email.isBlank()) {
        recado = "Escreva o e-mail da conta."
        erro = true
        return
      }
      trabalhando = true
      escopo.launch {
        recado = Conta.recuperarSenha(email)
        trabalhando = false
      }
      return
    }

    if (email.isBlank() || senha.length < 6) {
      recado = "Informe o e-mail e uma senha de pelo menos 6 caracteres."
      erro = true
      return
    }
    if (criando && nome.isBlank()) {
      recado = "Diga como quer ser chamado."
      erro = true
      return
    }

    trabalhando = true
    escopo.launch {
      val falha = if (criando) {
        Conta.criar(ctx, nome, email, senha)
      } else {
        Conta.entrar(ctx, email, senha)
      }
      trabalhando = false
      if (falha == null) {
        aoEntrar()
      } else {
        recado = falha
        erro = !falha.startsWith("Conta criada")
      }
    }
  }

  Box(
    Modifier
      .fillMaxSize()
      .background(Cor.bg)
      .verticalScroll(rememberScrollState()),
    contentAlignment = Alignment.Center
  ) {
    Column(
      Modifier
        .widthIn(max = 460.dp)
        .padding(24.dp)
        .border(1.dp, Cor.lineSoft, raio)
        .background(Cor.panel, raio)
        .padding(26.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      MarcaMovFlix(tamanho = 56)

      Spacer(Modifier.height(14.dp))
      Text("MovFlix", color = Cor.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)

      Spacer(Modifier.height(6.dp))
      Text(
        if (esqueci) {
          "Escreva o e-mail da conta e enviamos um link para criar outra senha."
        } else {
          "Entre com a sua conta para continuar."
        },
        color = Cor.muted,
        fontSize = 14.sp,
        textAlign = TextAlign.Center
      )

      Spacer(Modifier.height(18.dp))

      if (!esqueci) {
        Row(
          Modifier
            .fillMaxWidth()
            .background(Cor.bg, raio8)
            .padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          AbaDaConta("Entrar", !criando, Modifier.weight(1f)) { criando = false; recado = null }
          AbaDaConta("Criar conta", criando, Modifier.weight(1f)) { criando = true; recado = null }
        }
        Spacer(Modifier.height(16.dp))
      }

      if (criando && !esqueci) {
        Campo(
          "Nome", nome, { nome = it },
          Modifier.fillMaxWidth(), dica = "Como quer ser chamado"
        )
        Spacer(Modifier.height(12.dp))
      }

      Campo(
        "E-mail", email, { email = it },
        Modifier.fillMaxWidth(), dica = "voce@exemplo.com", email = true
      )

      if (!esqueci) {
        Spacer(Modifier.height(12.dp))
        Campo(
          "Senha", senha, { senha = it },
          Modifier.fillMaxWidth(),
          dica = "Pelo menos 6 caracteres",
          senha = true,
          acaoDoTeclado = ImeAction.Done
        )
      }

      if (recado != null) {
        Spacer(Modifier.height(14.dp))
        Text(
          recado!!,
          color = if (erro) Cor.danger else Cor.muted,
          fontSize = 13.sp,
          textAlign = TextAlign.Center
        )
      }

      Spacer(Modifier.height(18.dp))
      Botao(
        when {
          trabalhando -> "Um instante..."
          esqueci -> "Enviar o link"
          criando -> "Criar conta"
          else -> "Entrar"
        },
        Modifier.fillMaxWidth(),
        Jeito.PRIMARIO,
        ligado = !trabalhando
      ) { enviar() }

      Spacer(Modifier.height(14.dp))
      if (esqueci) {
        BotaoTexto("Voltar para entrar") { esqueci = false; recado = null }
      } else {
        BotaoTexto("Esqueci minha senha") { esqueci = true; recado = null }
      }
    }
  }
}

@Composable
private fun AbaDaConta(
  texto: String,
  ligada: Boolean,
  modifier: Modifier = Modifier,
  aoTocar: () -> Unit
) {
  val fonte = remember { MutableInteractionSource() }
  val focada by fonte.collectIsFocusedAsState()
  Box(
    modifier
      .background(if (ligada) Cor.panel else Color.Transparent, raio8)
      .border(1.dp, if (focada) Cor.accentStrong else Color.Transparent, raio8)
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoTocar() }
      .padding(vertical = 10.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      texto,
      color = if (ligada) Cor.accent else Cor.muted,
      fontSize = 14.sp,
      fontWeight = FontWeight.SemiBold
    )
  }
}

/**
 * A marca da casa: o M em destaque, como no site.
 *
 * Desenho nosso, feito de formas simples para ler bem pequeno na barra e de
 * longe na TV.
 */
@Composable
fun MarcaMovFlix(tamanho: Int = 40, modifier: Modifier = Modifier) {
  Box(
    modifier
      .size(tamanho.dp)
      .background(Cor.accent, RoundedCornerShape((tamanho / 4).dp)),
    contentAlignment = Alignment.Center
  ) {
    Text(
      "M",
      color = Cor.bg,
      fontSize = (tamanho * 0.58f).sp,
      fontWeight = FontWeight.Black
    )
  }
}

/** O nome escrito como no site: o M aceso, o resto claro. */
@Composable
fun NomeMovFlix(tamanho: Int = 20, modifier: Modifier = Modifier) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Text("M", color = Cor.accent, fontSize = tamanho.sp, fontWeight = FontWeight.Black)
    Text("ovFlix", color = Cor.text, fontSize = tamanho.sp, fontWeight = FontWeight.Bold)
  }
}

/** Um espaco em branco com a altura pedida, para o ritmo do site. */
@Composable
fun Vao(altura: Int) {
  Spacer(Modifier.height(altura.dp))
}

/** O mesmo, na horizontal. */
@Composable
fun VaoH(largura: Int) {
  Spacer(Modifier.width(largura.dp))
}
