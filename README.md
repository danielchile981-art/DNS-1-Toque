# DNS 1 Toque

Aplicativo Android para alternar o DNS Privado entre `dns.adguard.com`, Automático e Desligado com poucos toques.

O Android protege `WRITE_SECURE_SETTINGS`. Por isso, a ativação inicial usa a própria **Depuração sem fio do Android 11+** para parear localmente com o ADB do aparelho e conceder a permissão ao app. Depois da ativação, a Depuração sem fio pode ser desligada.

O projeto inclui GitHub Actions para gerar automaticamente o APK `DNS-1-Toque.apk`.
