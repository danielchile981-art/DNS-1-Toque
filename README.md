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

## APK automático

O workflow `.github/workflows/build-apk.yml` compila o APK em cada push para `main` e também pode ser iniciado manualmente. O arquivo final aparece nos **Artifacts** da execução com o nome **DNS-1-Toque-APK**.

## Segurança

O pareamento acontece localmente pelo recurso de Depuração sem fio do Android. Depois da ativação, desligue a Depuração sem fio se não precisar mais dela.

## Terceiros

Usa `libadb-android` de MuntashirAkon para ADB local/pairing e dependências relacionadas. Consulte `THIRD_PARTY_NOTICES.md`.
