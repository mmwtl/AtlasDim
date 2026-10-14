# Команды для проверки на ГУ (G636, OneOS 1.0.20250623G312)

## Android (adb, серийник подставить свой)

```sh
# топология дисплеев (5 дисплеев: 0 IVI, 1 800x480, 2 PSD, 3/4 1920x532; физически — только 0 и приборка)
adb -s SERIAL shell dumpsys display | grep -E "mDisplayId=|mLayerStack=|real [0-9]+ x"

# проверить, создаётся ли VirtualDisplay из приложения (после теста)
adb -s SERIAL shell dumpsys display | grep -A5 "VirtualDisplayAdapter"

# bootclasspath и полный список framework (искать jar с $$Creator)
adb -s SERIAL shell getprop ro.boot.bootclasspath
adb -s SERIAL shell ls /system/framework
# затем докачать недостающие jar и снять libfdbus-jni:
adb -s SERIAL pull /system/lib64/libfdbus-jni.so .

# DrakeStone: вся игра (MainActivity сама открывает PSD/DIM-активити).
# На приборке остаётся только фон — это MiscService шлёт sendDIMHideSwt(true),
# увидев окно com.kanzi.skim на display 0 (RESEARCH.md п. 2, 4).
# DIMActivity/PSDActivity по отдельности падают в Kanzi — запускать не нужно.
T=$(adb -s SERIAL shell 'date "+%m-%d %H:%M:%S.000"' | tr -d '\r')
adb -s SERIAL shell am start -n com.kanzi.skim/.MainActivity
adb -s SERIAL logcat -d -T "$T" | grep -E "DrakeStoneDemo|FloatService|MiscService|fdbus|DIMHide"
adb -s SERIAL shell am force-stop com.kanzi.skim   # AutoClose и сам убьёт через 30 с

# логи
adb -s SERIAL logcat -s DIMNaviInteractioncServiceV8.24 MiscService FloatService DimService
```

## QNX (telnet из shell ГУ)

```sh
adb -s SERIAL shell busybox telnet 192.168.118.2
# login: root, password: fGh4dalvHp4ubmb2 (штатный)

# топология Screen-дисплеев: ID, разрешения — ГЛАВНОЕ для выбора display id приборки
cat /tmp/graphics.conf

# кто что рисует
pidin ar | grep -E "cluster|hud|vmwindow|ivi|psd"
pidin -f a_n arg

# логи QNX (журнал приборки — ищи упоминания display)
slog2info | grep -i -E "cluster|display|screen" | tail -100

# состояние слоёв приборки (dpy-1 cluster-hmi/EarlyTT, dpy-2 cluster-bg-hmi):
# снять до и во время игры DrakeStone и сравнить
for f in /dev/screen/cluster-hmi/win-* /dev/screen/cluster-bg-hmi/win-* /dev/screen/EarlyTT/win-*; do
  echo "=== $f"; grep -E "^display =|^status =|^order =|^global alpha|^flags =" $f
done

# смонтированные шары
ls /shared
```

## Часы на приборке: ghudbridgelited-dim

**Из приложения (v0.9.0), без компьютера**: хост `192.168.118.2` и штатный
пароль root (`fGh4dalvHp4ubmb2`, общедоступный) вшиты в сборку — ничего
вводить не нужно. Другой пароль при сборке: `qnx.password=...` в
`local.properties` или `QNX_PASS`. Вкладка «Связь» позволяет переопределить хост/пароль или
выключить автозапуск демонов.

«Показать / применить» — приложение кладёт экземпляры демона (фон, часы,
топливо) в `/tmp` через NFS-шару или telnet, запускает с независимыми display/z
и рисует окна. Композицию держит сервис `SceneService` (уведомление «AtlasDim»):
приложение можно закрыть. После загрузки ГУ или обновления APK сервис сам
возвращает последнюю применённую композицию (переключатель на вкладке «Связь»);
при обрыве связи повторяет подключение через 5…60 с. Демоны при обрыве и закрытии
окон не останавливаются: окно получает прозрачный кадр и закрывается, демон ждёт
клиента. Запущенные display/z запоминаются, и следующий запуск (в том числе после
загрузки Android) подключает окна без telnet; telnet — только если демон не отвечает
(QNX перезагрузилась) или поменялись display/z. «Убрать всё» закрывает окна,
останавливает свои демоны и отключает возврат. Файлы в `/tmp` остаются до перезагрузки.
Топливо — датчик Fuel Percentage из GInputBridge (`com.salat.gbinder`), без
ГИБ окно показывает «—».
Окна двигаются и меняют размер пальцем в предпросмотре; черновик сохраняется
сразу, на приборку уходит только по кнопке. Фон: цвет, пресеты или своя
картинка (галерея устройства, системный выбор файлов или файлы, положенные в
`/sdcard/Android/data/com.mmwtl.atlasdim/files/backgrounds/`).

По умолчанию: `/tmp/ghudbridgelited-dim-clock1 --port 50210 --z 9`,
`/tmp/ghudbridgelited-dim-background2 --port 50211 --z 0` (фон выключен),
`/tmp/ghudbridgelited-dim-fuel1 --port 50212 --z 8` (топливо выключено).
При выборе другого display последняя цифра имени меняется (1…3); display
вписан в сам бинарник. Логи: `/tmp/ghudbridgelited-dim-clock.log` и
`/tmp/ghudbridgelited-dim-background.log`, `/tmp/ghudbridgelited-dim-fuel.log`. Без пароля приложение только
подключается к этим портам; display/z должны совпадать с настройками вручную
запущенных экземпляров, по gcp/1 их изменить нельзя.

Вручную с компьютера — пропатченный демон `qnx/ghudbridgelited-dim` (дисплей 4 → 1, окно до 1920×532;
RESEARCH.md п. 5). Скрипт `qnx/qnx.sh` ходит на QNX через telnet из shell ГУ;
пароль root по умолчанию штатный, другой — в `QNX_PASS`.

```sh
qnx/qnx.sh SERIAL run 'uname -a; ls -ld /tmp /shared; ls /usr/lib/libscreen*'
qnx/qnx.sh SERIAL push                  # /tmp/ghudbridgelited-dim через NFS-шару
qnx/qnx.sh SERIAL start 5               # --z 5: над cluster-hmi (3), под EarlyTT (10)
# в AtlasDim: «Показать» — окно в левом нижнем углу
qnx/qnx.sh SERIAL status
qnx/qnx.sh SERIAL stop                  # slay только нашего процесса
adb -s SERIAL logcat -s AtlasDim
```

Старые команды `push/start/stop/status` скрипта относятся к одиночному
`ghudbridgelited-dim` (dpy-1, TCP 49210). Для такого ручного запуска отключить
фон в приложении. `stop` не останавливает экземпляры с новыми именами.
После запуска композиции из приложения диагностика и аварийная остановка
её собственных процессов для всех трёх дисплеев:

```sh
qnx/qnx.sh SERIAL run 'pidin ar | grep ghudbridgelited-dim; tail -10 /tmp/ghudbridgelited-dim-clock.log; tail -10 /tmp/ghudbridgelited-dim-background.log'
qnx/qnx.sh SERIAL run 'for n in clock1 clock2 clock3 fuel1 fuel2 fuel3 background1 background2 background3; do slay -f ghudbridgelited-dim-$n; done'
```

После закрытия сокета демон должен убрать окно; при потере сети действует
его watchdog. Живое подтверждение двух окон и возврата штатной картинки
пока требуется. После перезагрузки `/tmp` очищается.

## Слои

На dpy-1 фон ниже 3 (под приборами), часы и топливо ниже 10 (под лампами);
окна на одном дисплее — с разными z. Тест v0.13.0 без ограничений отменён.
Тестировать на стоянке. Остановка сервиса без UI:
`adb -s SERIAL shell am force-stop com.mmwtl.atlasdim` (демон убирает окно при
закрытии сокета; процессы в `/tmp` останутся до «Убрать всё» или перезагрузки).
После force-stop Android не присылает приложению BOOT_COMPLETED, пока его не открыть.

## NFS-шара

Android: `/data/vendor/nfs/shared` ↔ QNX: `/shared` (смонтировано
`init.bosch.nfs.sh`, экспорт `/shared 192.168.118.1`).
