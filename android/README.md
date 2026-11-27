# Traço Automático para Android

O app transforma uma imagem escolhida pelo usuário em traços de contorno e os reproduz no app de desenho selecionado. A opção de IA usa o modelo MediaPipe DeepLab-v3 incluído no APK para tentar separar o assunto do fundo; depois, o detector local de bordas cria os traços. Não precisa de Google Play services nem de internet, e a imagem não sai do aparelho. O modelo reconhece 21 categorias comuns e pode não identificar desenhos ou assuntos fora dessas categorias; se falhar, o app avisa e usa o traçado local tradicional. Para o WeLife (`com.newlang.weelife.sa`), o app reconhece a lousa e seleciona uma das seis espessuras do lápis. O traçado usa a cor selecionada no app de destino; não reproduz automaticamente cores, sombras ou áreas preenchidas.

O modelo DeepLab-v3 tem cerca de 2,8 MB e está sob Apache License 2.0. A atribuição, licença e checksum estão em `app/src/main/assets/`.

## Gerar o APK

Com JDK 17 e Android SDK 35 instalados, abra esta pasta (`android`) no terminal e execute:

```powershell
.\gradlew.bat assembleDebug
```

Também é possível abrir a pasta no Android Studio e usar **Build > Build APK(s)**. O APK de depuração será salvo em `app/build/outputs/apk/debug/app-debug.apk`.

## Usar

1. Escolha uma imagem; use **Remover imagem** na prévia para limpá-la e selecionar outra. O app sugere automaticamente um nível de detalhe com base nas bordas encontradas, que você pode ajustar. Depois de preparar, toque em **Ver contornos** para conferir o traçado e a quantidade de linhas detectadas. Também é possível criar desenhos vetoriais offline a partir de formas prontas (sol, coração, estrela, casa, flor ou gato) e adicionar um nome curto, sem selecionar uma imagem. Deixe **Separar o assunto com IA local** ativado para tentar separar o assunto, ou desative para usar apenas a detecção de bordas tradicional. Escolha também a espessura do lápis.
2. Ative **Traço Automático** nas configurações de Acessibilidade do Android.
3. Volte ao app e toque em **Preparar desenho**.
4. Abra o app de destino.
5. No WeLife, toque em **Reconhecer lousa**, confira a prévia da área e escolha **Usar esta área**. Se não estiver correta, marque manualmente os cantos, confira a prévia e confirme.
6. O app seleciona o lápis e a espessura escolhida antes de desenhar. Para outros apps, marque manualmente os dois cantos do papel.

O serviço analisa uma captura da tela para reconhecer a lousa do WeLife. Só envia gestos após a confirmação da área. A imagem e a captura da lousa são processadas localmente. O app de destino precisa aceitar gestos de toque; alguns apps podem limitar ou bloquear esse recurso.

O painel de seleção aparece centralizado sobre o app de desenho; no WeLife, escolha **Reconhecer lousa** ou **Marcar área manualmente**. Se voltar ao app de desenho com um plano ainda pronto, as opções aparecem novamente. Durante o desenho, um painel mostra o progresso e mantém a tela ligada. Use **Pausar** para interromper entre traços e **Continuar** para retomar; o botão **Parar** só aparece depois da pausa para evitar cancelamento acidental. Se o Android cancelar um gesto ou o app de desenho perder o foco, volte ao app de destino e toque em **Continuar**. O plano aceita até 1.600 traços por imagem; um nível de detalhe maior também pode gerar mais ruído e deixar o desenho mais demorado.
