# WeeLife Line Art para Android

O app transforma uma imagem escolhida pelo usuário em traços coloridos e os reproduz na lousa do WeeLife (`com.newlang.weelife.sa`). As imagens podem ser escolhidas pelo seletor de fotos do Android ou pesquisadas no Google Imagens por um navegador integrado ao app; a imagem escolhida é baixada para o aparelho e tem limite de 15 MB. A busca e o download precisam de internet. A opção de IA usa o modelo MediaPipe DeepLab-v3 incluído no APK para tentar separar o assunto do fundo; depois, o detector local de bordas cria os traços e estima até dezesseis cores da imagem. O processamento não precisa de Google Play services. O modelo reconhece 21 categorias comuns e pode não identificar desenhos ou assuntos fora dessas categorias; se falhar, o app avisa e usa o traçado local tradicional. A lousa, a paleta e os controles de lápis do WeeLife são verificados por captura local da tela. O app seleciona o lápis e recomenda a espessura conforme o detalhe da imagem; durante o desenho, escolhe as cores detectadas.

## Atualizar pelo GitHub

O botão **Verificar atualização** consulta o último GitHub Release em `deusasuprema/Traco-Automatico`. Para cada nova publicação, use uma tag que corresponda ao `versionName` do APK e anexe `WeeLife Line Art.apk`. Para permitir a atualização de instalações antigas, anexe também uma cópia com o nome `traco-automatico.apk`. O atualizador aceita os dois nomes.

O APK publicado deve usar a mesma chave de assinatura das versões instaladas. Não troque a chave de depuração usada para os APKs atuais; o Android não aceita atualizar um app com um APK assinado por outra chave. Depois do download, o Android exige que o usuário permita a instalação dessa fonte e confirme a instalação; apps comuns não podem instalar atualizações silenciosamente.

O modelo DeepLab-v3 tem cerca de 2,8 MB e está sob Apache License 2.0. A atribuição, licença e checksum estão em `app/src/main/assets/`.

## Gerar o APK

Com JDK 17 e Android SDK 35 instalados, abra esta pasta (`android`) no terminal e execute:

```powershell
.\gradlew.bat assembleDebug
```

Também é possível abrir a pasta no Android Studio e usar **Build > Build APK(s)**. O APK de depuração será salvo em `app/build/outputs/apk/debug/app-debug.apk`.

Para gerar o APK **release** para publicar no GitHub:

```powershell
.\gradlew.bat prepareReleaseApk
```

O arquivo será `app/build/outputs/weelife/WeeLife Line Art.apk`. Esta versão release usa a mesma chave debug local para permitir atualizar as instalações atuais; mantenha essa chave e gere futuras atualizações neste mesmo ambiente. A chave debug não é indicada para distribuição comercial ou para builds em outras máquinas.

## Usar

1. Toque em **Escolher da galeria** ou em **Pesquisar imagens no Google**. No navegador integrado, pesquise, toque numa imagem para ampliar e mantenha-a pressionada para selecioná-la. Use apenas imagens que você tem direito de utilizar; a busca e o download precisam de internet. Use **Remover imagem** para limpar a seleção. O app sugere automaticamente um nível de detalhe com base nas bordas encontradas, que você pode ajustar; a espessura recomendada acompanha esse nível. Depois de preparar, toque em **Ver contornos** para conferir os traços e as cores estimadas. Deixe **Separar o assunto com IA local** ativado para tentar separar o assunto, ou desative para usar apenas a detecção de bordas tradicional.
2. Ative **WeeLife Line Art** nas configurações de Acessibilidade do Android.
3. Volte ao app e toque em **Preparar desenho**.
4. Abra o WeeLife.
5. Toque em **Reconhecer lousa**, confira a prévia da área e escolha **Usar esta área**. Se não estiver correta, marque manualmente os cantos, confira a prévia e confirme.
6. Toque em **Iniciar desenho colorido**. O app seleciona o lápis e uma das seis espessuras conforme o detalhe, localiza e abre o seletor de cores, arrasta os controles de matiz e tom para aplicar até dezesseis cores detectadas e fecha a paleta entre as trocas; não é necessário deixá-la aberta.
7. Para atualizar, toque em **Verificar atualização** na tela principal e confirme no instalador do Android.

O serviço analisa uma captura da tela para reconhecer a lousa do WeeLife. Só envia gestos após a confirmação da área. A imagem e a captura da lousa são processadas localmente. O app de destino precisa aceitar gestos de toque; alguns apps podem limitar ou bloquear esse recurso.

O painel de seleção aparece centralizado sobre o WeeLife; escolha **Reconhecer lousa** ou **Marcar área manualmente**. Ao iniciar, o WeeLife Line Art identifica os controles, seleciona o lápis e a espessura recomendada, procura o botão colorido, abre o seletor e escolhe até dezesseis cores antes de reproduzir os traços. No WeeLife, o atalho flutuante **WL** abre controles sem sair dele: pausar, continuar ou parar o desenho e voltar às opções da área. **Trocar imagem / opções** abre a tela principal para selecionar outra imagem e preparar o plano; depois, volte ao app de desenho para iniciar. Durante o desenho, um painel mostra o progresso e mantém a tela ligada. Se o Android cancelar um gesto ou o app de desenho perder o foco, volte ao app de destino e toque em **Continuar**. O plano aceita até 1.600 traços por imagem; um nível de detalhe maior também pode gerar mais ruído e deixar o desenho mais demorado.
