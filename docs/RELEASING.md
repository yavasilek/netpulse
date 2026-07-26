# Выпуск NetPulse

## Один раз

Создайте release keystore и сохраните независимую резервную копию. Потеря
закрытого ключа делает обновление существующих установок невозможным.

В GitHub Actions добавьте секреты:

- `NETPULSE_KEYSTORE_B64`;
- `NETPULSE_KEYSTORE_PASSWORD`;
- `NETPULSE_KEY_ALIAS`;
- `NETPULSE_KEY_PASSWORD`.

## Новый выпуск

1. Обновите `CHANGELOG.md`.
2. Убедитесь, что рабочее дерево чистое.
3. Запустите `./gradlew testDebugUnitTest lintDebug assembleDebug`.
4. Создайте аннотированный тег, например `v1.0.0`.
5. Отправьте тег в GitHub.

Workflow `release.yml` соберёт подписанный APK, проверит проект, рассчитает
SHA-256 и создаст GitHub Release.
