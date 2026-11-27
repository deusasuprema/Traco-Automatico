# Traço Automático para Android

O app transforma uma imagem escolhida pelo usuário em traços de contorno e os reproduz no app de desenho selecionado. As imagens podem ser escolhidas pelo seletor de fotos do Android ou importadas por um link HTTPS direto; imagens baixadas ficam no aparelho e têm limite de 15 MB. A opção de IA usa o modelo MediaPipe DeepLab-v3 incluído no APK para tentar separar o assunto do fundo; depois, o detector local de bordas cria os traços. Não precisa de Google Play services. O modelo reconhece 21 categorias comuns e pode não identificar desenhos ou assuntos fora dessas categorias; se falhar, o app avisa e usa o traçado local tradicional. Para o WeLife (`com.newlang.weelife.sa`), o app reconhece a lousa e seleciona uma das seis espessuras do lápis. O traçado usa a cor selecionada no app de destino; não reproduz automaticamente cores, sombras ou áreas preenchidas.

## Atualizar pelo GitHub

O botão **Verificar atualização** consulta o último GitHub Release em `deusasuprema/Traco-Automatico`. Para publicar esta versão, crie um release com a tag `v1.26` e anexe `app-release.apk` renomeado para `traco-automatico.apk`. O `versionName` do APK precisa corresponder à tag. A versão 1.26 corrige também o endereço usado pelo atualizador.

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
.\gradlew.bat assembleRelease
```

O arquivo será `app/build/outputs/apk/release/app-release.apk`. Esta primeira versão release usa a mesma chave debug local para permitir atualizar as instalações atuais; mantenha essa chave e gere futuras atualizações neste mesmo ambiente. A chave debug não é indicada para distribuição comercial ou para builds em outras máquinas.

## Usar

1. Toque em **Escolher da galeria** para abrir o seletor de fotos do Android, ou em **Importar imagem da internet** e cole um link HTTPS direto para um arquivo de imagem (links de páginas de sites não funcionam). Use **Remover imagem** para limpar a seleção. O app sugere automaticamente um nível de detalhe com base nas bordas encontradas, que você pode ajustar. Depois de preparar, toque em **Ver contornos** para conferir o traçado e a quantidade de linhas detectadas. Deixe **Separar o assunto com IA local** ativado para tentar separar o assunto, ou desative para usar apenas a detecção de bordas tradicional. Escolha também a espessura do lápis.
2. Ative **Traço Automático** nas configurações de Acessibilidade do Android.
3. Volte ao app e toque em **Preparar desenho**.
4. Abra o app de destino.
5. No WeLife, toque em **Reconhecer lousa**, confira a prévia da área e escolha **Usar esta área**. Se não estiver correta, marque manualmente os cantos, confira a prévia e confirme.
6. O app seleciona o lápis e a espessura escolhida antes de desenhar. Para outros apps, marque manualmente os dois cantos do papel.
7. Para atualizar, toque em **Verificar atualização** na tela principal e confirme no instalador do Android.

O serviço analisa uma captura da tela para reconhecer a lousa do WeLife. Só envia gestos após a confirmação da área. A imagem e a captura da lousa são processadas localmente. O app de destino precisa aceitar gestos de toque; alguns apps podem limitar ou bloquear esse recurso.

O painel de seleção aparece centralizado sobre o app de desenho; no WeLife, escolha **Reconhecer lousa** ou **Marcar área manualmente**. No app de destino, o atalho flutuante **TA** abre controles sem sair dele: pausar, continuar ou parar o desenho e voltar às opções da área. **Trocar imagem / opções** abre a tela principal para selecionar outra imagem e preparar o plano; depois, volte ao app de desenho para iniciar. Durante o desenho, um painel mostra o progresso e mantém a tela ligada. Se o Android cancelar um gesto ou o app de desenho perder o foco, volte ao app de destino e toque em **Continuar**. O plano aceita até 1.600 traços por imagem; um nível de detalhe maior também pode gerar mais ruído e deixar o desenho mais demorado.
