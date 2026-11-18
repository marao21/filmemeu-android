# MovFlix — aplicativo Android nativo

O site, feito de novo em Android nativo. Mesmas telas, mesmas cores, mesmos
botões — e nada passando pela Vercel: o que era função serverless agora é
código Kotlin rodando no aparelho.

Celular, tablet e Android TV no mesmo APK. Na TV ele aparece na tela inicial
(entrada *leanback*, com banner) e tudo é alcançável pelo controle remoto: o
foco é desenhado em cada botão, cartaz, ficha e no trilho do player.

## O que deixou de existir

| Site | Aplicativo |
| --- | --- |
| `/api/catalog` numa função da Vercel | `data/Tmdb.kt`, falando direto com a ponte do TMDB |
| `/api/proxy` para contornar CORS e hotlink | nada: não existe CORS em app nativo, e os cabeçalhos vão no próprio pedido |
| `/api/probe` medindo duração no servidor | `media/Medida.kt`, lendo o `mvhd` e os `#EXTINF` no aparelho |
| `hls.js` carregado de um CDN | ExoPlayer (Media3), que é do próprio Android |
| SDK do Supabase vindo de um servidor de fora | `data/Conta.kt`, quatro chamadas HTTP |
| Download de HLS montado na memória do navegador | `download/DownloadService.kt`, escrevendo direto no arquivo |

Não há servidor nosso em lugar nenhum do caminho. Só duas coisas são pedidas
pela rede, as duas direto do aparelho: o catálogo, à ponte do TMDB que já vive
no Supabase, e a conta, ao Supabase. A credencial publicável é a mesma que já
estava no navegador — ela não é segredo.

## De onde vem o vídeo

O site pegava o endereço do filme em `/api/fonte`, que varria o
`playerflix.ink` e tirava de lá o `.m3u8`. **Isso não foi portado**, e não vai
ser: é pegar o catálogo de um serviço que não tem direito de distribuí-lo.

No aplicativo a fonte é informada por quem assiste. Na página do título há
**Fonte do vídeo**: o endereço do `.mp4` ou `.m3u8` que você tem direito de ver
e, se o servidor exigir, o `Referer`. Fica guardado no aparelho, por título,
temporada e episódio, e não é pedido de novo. Serve para o seu próprio
servidor, para um arquivo numa rede local ou para um provedor que te dê a URL
legitimamente.

O player, a medida de duração, o download, o catálogo e as contas funcionam
igual ao site. O que falta é só essa peça — e ela é a que cabe a você apontar.

## As telas

- **Porta de entrada** — entrar, criar conta e esqueci minha senha, com as abas
  do site.
- **Quem vai assistir?** — os perfis da conta, com as capas do catálogo se
  revezando ao fundo e o nome do que está na tela. Criar, editar e excluir;
  figura e cor; o perfil infantil com o mesmo recorte de gênero do site.
- **A casa** — destaque no alto, uma fileira por categoria, as categorias no
  meio do caminho, Minha MovFlix com *continuar assistindo*. Abas Início,
  Filmes, Séries, Animações.
- **Página do título** — imagem larga ao fundo, ficha, sinopse, temporada e
  episódio, Assistir / Continuar assistindo / Assistir do início, Minha lista e
  os parecidos.
- **Player** — ExoPlayer com os controles do site: play, tempo, qualidade que
  está tocando, trilho com o quanto já está guardado adiante, mudo, volume,
  abertura da marca e o nome do episódio. No controle remoto: OK toca e pausa,
  as setas andam dez segundos, Voltar sai.

## Rodar e gerar o APK

Precisa do JDK 17 ou mais novo e do SDK do Android (API 36). O `local.properties`
aponta para o SDK desta máquina; em outra, troque o caminho.

```bash
./gradlew assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`. Para instalar num
aparelho pela rede:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Numa TV box sem `adb`, copie o APK para um pendrive e abra pelo gerenciador de
arquivos, com "fontes desconhecidas" liberado.

Para a versão de release, assine com a sua chave:

```bash
./gradlew assembleRelease
```

## Como está montado

```
app/src/main/java/com/filmemeu/app/
  MainActivity.kt          as telas, na ordem em que a casa abre
  data/
    Modelos.kt             filme, série e anime com o mesmo formato
    Tmdb.kt                o catálogo, igual ao api/catalog.js
    Conta.kt               conta e perfis no Supabase
    Guardados.kt           onde parou, Minha MovFlix e as fontes
  ui/
    Tema.kt                as cores do :root e os botões do site
    Login.kt               a porta de entrada
    Perfis.kt              quem vai assistir, e as figuras desenhadas
    Catalogo.kt            barra, destaque, fileiras e grade
    Detalhe.kt             a página do título
  player/PlayerActivity.kt o player e os controles
  media/Medida.kt          duração de HLS e MP4 sem baixar o vídeo
  download/DownloadService.kt  o download, pelo próprio aparelho
  net/Http.kt              o único caminho de rede
```

As cores em `ui/Tema.kt` são as mesmas do `:root` do `assets/style.css`.
Mudando lá, mude aqui: é o que faz o site e o aplicativo continuarem parecendo
a mesma casa.

## Aviso

Use apenas com conteúdo que você tem direito de assistir e baixar. O
aplicativo não hospeda, não armazena e não distribui vídeos: ele toca o
endereço que você informa.
