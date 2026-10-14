# AtlasDim — правила проекта

Тестовое приложение для проверки вывода на приборку (DIM) ГУ G636 / OneOS
из Android. Это исследовательский проект: минимальный код, максимум проверок.

## Структура

- `app/` — приложение (план — TASK.md).
- `qnx/` — пропатченный демон ghudbridgelited-dim и скрипт qnx.sh (telnet на QNX).
- `notes/` — RESEARCH.md (все выводы по приборке), commands.md (команды на ГУ).
- `reference/` — только для чтения, только локально (в .gitignore, в публичный
  репозиторий не входит: декомпилированный закрытый код, VIN и серийники ГУ):
  - `dumps/` — dumpsys display, getprop, packages с ГУ;
  - `qnx/` — pidin, exports, NFS/fdbus init-скрипты, mtouch;
  - `diminteraction-java/` — декомпилированные исходники com.autolink.diminteraction
    (binder-интерфейсы, ipc.fdbus, InteractionProto);
  - `anhud/` — протокол gcp/1 и клиент из ANHUD + бинарник ghudbridgelited;
  - `precedents/` — MiscService (скрытие приборки), DrakeStone (display 4), qa-отчёты;
  - `apk/` — DIMInteraction.apk (для jadx).
- `qa/` — отчёты проверок по фазам: `qa/<фаза>/report.md`.

## Сборка и запуск

JDK 17, Android SDK API 35, Gradle Wrapper. `minSdk=26`, `targetSdk=30` — не менять.

```sh
./gradlew :app:assembleDebug
adb -s SERIAL install -r app/build/outputs/apk/debug/*.apk
```

Релиз подписывается общим ключом Atlas: `secure.signing.gradle` и
`atlascodecfix-release.jks` в корне (оба в .gitignore). Без них
`:app:assembleRelease` падает — не обходить и не подписывать release debug-ключом.
Подпись проверять `apksigner verify --print-certs`.

## Стиль

Java, отступ 4 пробела, программный UI без тяжёлых зависимостей. Референсы в
`reference/` не редактировать. Копируемые в `app/` классы (ipc.fdbus, протокол)
сопровождать комментарием об источнике.

## Проверки

Каждая фаза TASK.md завершается отчётом в `qa/<фаза>/report.md`: устройство,
версия APK, шаги, результат, ограничения. Перед коммитом `git diff --check`.

## Безопасность на ГУ

- Скрытие штатной приборки (фаза 3) — только на стоянке; гарантированный возврат
  при закрытии/краше приложения.
- На QNX — только запуск своих процессов из /tmp; ничего не останавливать и
  не менять в системе.
- Не коммитить ключи, пароли и крупные бинарники (*.apk в .gitignore;
  ghudbridgelited — открытый бинарник из ANHUD, его можно). Исключение —
  штатный общедоступный telnet-пароль root QNX ГУ G636 (`app/build.gradle.kts`,
  `qnx/qnx.sh`); любые другие пароли — решать отдельно.
- Репозиторий публичный: не коммитить VIN, серийники, дампы ГУ и декомпилированный
  чужой код; из `reference/` в комментарии и отчёты — только выводы и ссылки.

## Коммиты

Короткий английский заголовок в повелительном наклонении, без префиксов.
После каждой завершённой фазы — коммит. Работать в `main`.
