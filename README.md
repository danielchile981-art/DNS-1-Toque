# DNS 1 Toque

Aplicativo Android para alternar o DNS Privado entre:

- `dns.adguard.com`
- Automático
- Desligado

## Como funciona

O Android protege `WRITE_SECURE_SETTINGS`, então um app comum não pode conceder essa permissão a si mesmo silenciosamente.

O **DNS 1 Toque** faz a ativação inicial sem computador e sem Termux usando a própria **Depuração sem fio do Android 11+**. O app pareia localmente com o ADB do aparelho e envia o `pm grant` para si mesmo. Depois que a permissão for liberada, a Depuração sem fio pode ser desligada e os três botões continuam funcionando.

O app usa um ícone próprio no estilo escudo + rede DNS + botão de energia, baseado na capa aprovada para o projeto.

## Ativação inicial

1. Ative **Opções do desenvolvedor > Depuração sem fio**.
2. Abra o app e toque em **Abrir depuração sem fio**.
3. No Android, toque em **Parear dispositivo com código**.
4. Mantenha Configurações e o app em tela dividida/pop-up.
5. Digite a porta e o código de 6 números no app.
6. Toque em **Parear e liberar permissão**.
7. Quando aparecer **Permissão liberada**, a configuração terminou.

## Xiaomi, POCO, MIUI e HyperOS

O bloco **DNS 1 Toque** é declarado como um bloco de Configurações rápidas do Android. Em MIUI/HyperOS, a confirmação automática pode não ser exibida pelo painel do fabricante; nesse caso, abra a Central de controle, toque em **Editar** (lápis), encontre **DNS 1 Toque** nos blocos disponíveis e arraste-o para os ativos.

A configuração da permissão pelo próprio app usa Depuração sem fio, disponível no Android 11 ou superior. O projeto mantém Android 11 como versão mínima. Em aparelhos com Android 13 ou superior, o app também tenta pedir ao sistema para adicionar o bloco e mostra instruções manuais se a tentativa não funcionar.

## APK automático

O workflow `.github/workflows/build-apk.yml` compila o APK em cada push para `main` e também pode ser iniciado manualmente. O arquivo final aparece nos **Artifacts** da execução com o nome **DNS-1-Toque-APK**.

## Segurança

O pareamento acontece localmente pelo recurso de Depuração sem fio do Android. Depois da ativação, desligue a Depuração sem fio se não precisar mais dela.

## Terceiros

Usa `libadb-android` de MuntashirAkon para ADB local/pairing e dependências relacionadas. Consulte `THIRD_PARTY_NOTICES.md`.


## Versão 1.3.0: alternativa quando o MIUI/HyperOS nega pm grant

A ativação tenta conceder WRITE_SECURE_SETTINGS uma vez. Se o sistema negar,
testa a escrita direta das configurações de DNS como usuário ADB shell,
reaplicando apenas os valores existentes. Este teste não seleciona outro DNS.
O modo ADB só é salvo depois do teste e da leitura dos valores.

- Com a permissão concedida ao app, a depuração pode ser desligada.
- No modo ADB, Wi-Fi e Depuração sem fio devem permanecer ligados.
  Cada ação no app ou no bloco do painel reconecta usando a chave de pareamento salva.
  Depois de reiniciar, pode ser necessário reativar a depuração.
- Os dois caminhos conferem os valores após a troca. Em falhas parciais, tentam
  restaurar a configuração anterior e avisam se a restauração também falhar.
- O app exibe DNS **configurado**; isso não comprova que o servidor DNS está acessível.
- Se o fabricante bloquear também a escrita pelo shell, o app não pode garantir
  a troca em um toque. Use o botão para abrir o DNS privado do sistema.

Testes automatizados cobrem recusa de concessão com escrita shell permitida,
recusa dos dois caminhos, valores ausentes, falha silenciosa, alteração parcial,
restauração e resposta ADB incompleta. A compilação e os testes de lógica
não substituem testes físicos em diferentes versões do MIUI/HyperOS.
