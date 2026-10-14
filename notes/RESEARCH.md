# AtlasDim — исследование вывода на приборку (DIM) с Android, ГУ G636 / OneOS

Собрано 2026-10-04 из выгрузки ГУ (`AtlasLauncher/reference-apks/g636-20250623G312/`)
и анализа проекта ANHUD (https://github.com/g992/ANHUD, цель — HUD на N155).
Исходники-прецеденты декомпилированы и лежат в локальном `reference/`; в публичный
репозиторий он не входит (закрытый код и данные конкретного ГУ).

## 1. Архитектура ГУ

- Qualcomm SA8155. QNX 7.0 — хост; Android 11 (API 30) — гость в `qvm`
  (`qvm @/vm/images/linux-la.config @... @/vm/images/la_dp_disabled.config ...`).
- Приборка (DIM) — QNX-приложение Kanzi (Rightware): `/apps/cluster/FX11_J1`
  (`cluster-app`, `cluster-hmi` — основной слой, `cluster-bg-hmi` — фон,
  `cluster-anim`, `welcomeshow-anim`; HUD — `hud-hmi`; DMS — `dms2-app`).
- Android ↔ QNX:
  - **FDBus поверх TCP**. На Android init-сервис `name-server`
    (`reference/qnx/fdbus-name-server.rc`): `-u tcp://192.168.118.2:60000 -n android
    -s 60000 -x 65000`. QNX-сервисы видны как `svc://com.autolink.cluster_ivi.*`.
  - **NFS-шара**: QNX `/shared` ↔ Android `/data/vendor/nfs/shared`
    (`reference/qnx/init.bosch.nfs.sh`, `reference/qnx/exports`). Через неё
    передают картинки (обложки альбомов: JPEG-файл + имя файла в protobuf).
- QNX доступен по telnet root: `busybox telnet 192.168.118.2` из shell ГУ
  (логин root; использовался для снятия дампов).

## 2. Дисплеи Android (из `reference/dumps/display.txt`, dumpsys display)

| Android display | Разрешение | uniqueId / port | Что это |
|---|---|---|---|
| 0 | 1440×1920 | local:1 / port 1 | IVI — основной экран (наш) |
| 1 | 800×480 | local:132 / port 132 | «TEST_HA1» (в DrakeStone); на N155 так подключён HUD |
| 2 | 2880×1080 | local:5 / port 5 | PSD — пассажирский; в софте есть («Psd dock» в SystemUI), **в машине пользователя физически нет** |
| 3 | 1920×532 | local:130 / port 130 | кандидат: приборка или HUD |
| 4 | 1920×532 | local:131 / port 131 | «DIM» по классификации DrakeStone (`DisplayUtils.DIM_DISPLAY_ID = 4`) |

Ключевое: на QNX потребители (vmwindow) есть только для IVI и PSD —
`vmwindow_csd -display=3 -size=1440*1920` и `vmwindow_psd -display=5 -size=2880*1080`
(см. `reference/qnx/pidin-arg.txt`). **Для дисплеев 1/3/4 vmwindow нет — framebuffer
Android туда никто не выводит.**

QNX Screen-дисплеи (`/tmp/graphics.conf` + `/dev/screen/*/win-*`, снято 2026-10-03):

| QNX dpy | Размер | Окна (order) | Что это |
|---|---|---|---|
| 1 | 1920×532 | `cluster-hmi` (3, Kanzi, основной слой), `EarlyTT` (10, контрольные лампы) | **приборка, верхний слой** |
| 2 | 1920×532 | `cluster-bg-hmi` (0, fullscreen) | **приборка, фон** |
| 3 | 1440×1920 | `vmwindow_csd` (Android display 0), `hvac-hmi`, `apa-arc-app` | IVI (CSD) |
| 4 | 800×480 | `hud-hmi` (1) | HUD (WHUD) — **в этой машине физически нет** |
| 5 | 2880×1080 | `vmwindow_psd` (Android display 2) | PSD — физически нет |

Физически в машине пользователя только два экрана: центральный Android (CSD,
Android display 0 / QNX dpy-3) и приборка. PSD и HUD прошивка заводит, но панелей
нет (прошивка общая для комплектаций).

Приборка — это два QNX-дисплея 1920×532 (dpy-1 поверх dpy-2). Android-дисплеи
3/4 (1920×532) и 1 (800×480) — лишь «зеркальные» по размеру, связи с dpy-1/2/4 нет.

**Живой тест (2026-10-03)**: `am start --display N -f 0x18000000
-n com.android.settings/.Settings` на Android-дисплеях 4, 3, 1 (3 и 4 — дважды):
SurfaceFlinger рисует слой во весь экран, но **картинки нет нигде** (ни на приборке,
ни на других экранах), приборка остаётся обычной. Вывод: путь «Activity/Presentation
на Android display 1/3/4» на приборку **не работает** — фаза 1 закрыта отрицательно.

Эксперимент пользователя: запуск `com.kanzi.skim` (DrakeStone) — игра на CSD,
**на приборке остался только фон** (dpy-2), основной слой спрятан. Причина — не
пиксели игры, а MiscService: `FloatService` видит окно `com.kanzi.skim` на
display 0 и шлёт `sendDIMHideSwt(true)` (MSG_TYPE_VEHICLE, subtype 56), при
появлении другого окна — `false` (п. 4). С «Настройками» на 3/4 этого нет —
сходится. Собственный `FdbusClientInstance.sendDIMHideSwt` игры (subtype 62
`INTERNAL_SCENEMODSELD`, value 3/0) нигде не вызывается. Запуск PSD/DIM-активити
отдельно — падение в Kanzi `ResourceAcquireTaskDirector`, оба экрана перерисовываются.

## 3. Канал данных (текст/иконки в штатные слоты приборки) — работает

Мост — `com.autolink.diminteraction` (`/system/priv-app/DIMInteraction`, uid 1000
= android.uid.system). APK и декомпилированные исходники: `reference/apk/`,
`reference/diminteraction-java/`.

Внутри: чистая Java-обёртка FDBus (`ipc.fdbus.*`, нативный `libfdbus-jni.so`)
+ protobuf `com.autolink.interaction.InteractionProto` + экспортированные
**binder-сервисы без permission** (манифест: `reference/manifests/DIMInteraction-AndroidManifest.xml`):

- `.DIMNaviInteractioncService` — навигация;
- `.DIMMediaInteractioncService` — медиа (трек, обложка файлом через NFS);
- `.DIMPhoneCallInteractioncService` — телефон;
- `.DIMAppDockService` — док приборки (в т.ч. `setDockCustomAppIcon(position, byte[])`);
- `.PDCService`, `.DIMKeyService` (boot-complete, см. AtlasLauncher qa/boot-logo).

Цепочка: любое приложение → binder → DIMInteraction → protobuf →
`svc://com.autolink.cluster_ivi.interaction` → Kanzi-приборка.

### Что реально работает в этой сборке (не заглушки)

`IDimNaviInteractioncService` (`reference/diminteraction-java/com/autolink/adapterbinder/`):
- `notifyNavigationStatus(0|1|3)` — шлётся сразу (0=UNNAVI, 1=START, 3=END);
- `updateNavigationTurnId(int)`, `updateNextGuidancePointName(String)`,
  `updateDistanceToNextGuidancePoint(int)`,
  `updateDistanceToNextGuidancePointMsgInt(String, int)`,
  `updateDistanceToDestination(int)`, `updateDriveDirection(int)`,
  `updateETA(int)` (отправка с задержкой ~400 мс, дедупликация);
- `updateExtensionInfo(Bundle)` с ключами `KEY_NAVI_INFO_ROAD_TYPE` (шоссе),
  `KEY_NAVI_SPEED_LIMIT_INFO_*` (JCTWAY/ROAD/LIMI — съезды, дороги, ограничения ЭД) — шлётся сразу;
- `updateLaneInfo(String[] "laneNumber,laneBackIconId", Bitmap[])` (bitmap игнорируется, важны ID),
  `updateNaviInfo("lat,lng,dist,time,name,?")`, `updateHighwayExitInfo`,
  `updateRoadCameraInfo`, `updateServiceAreaInfo` (накапливаются, отправка по триггеру).

**Заглушки в этой сборке**: `updateTurnByTurnArrowBitmap/Int/Str`,
`notifyDestinationReached`, `updateNavigationTurnSVG`, `updateAddresses` и др. — пустые.

Схема накопления: отдельные `update*` заполняют общий builder
`InteractionProto.DimNaviInteraction`; реальная отправка — по триггерам
(`notifyNavigationStatus`, `updateETA`, `updateExtensionInfo`) через
`invokeAsync(MSG_TYPE_ENTERTAINMENT=1, protobuf)`.

`DIMMediaInteractioncService`: `updateMediaSourceTypeList`, `updateCurrentSourceType`,
`updatePlaylist`, `updatePlaybackInfo(PlayMediaInfo)` — обложка: bitmap сжимается
в JPEG 80 и пишется в `/data/vendor/nfs/shared/<file>`, приборке уходит имя файла
(`reference/diminteraction-java/com/autolink/diminteraction/DIMMediaInteractioncService.java`).

### Вышестоящий API (adaptapi, как в штатных приложениях)

`com.ecarx.xui.adaptapi.diminteraction.DimInteraction.create(context)` — классы в
BOOTCLASSPATH (`/system/framework/ecarx.adaptapi.jar`, снят). ANHUD на FX11-семействе
использует его через reflection из **обычного приложения** — без подписи и permission
(`reference/anhud/NativeNavigationController.kt`). Внутри цепочки
`ECarXAPI.creator(...)` ищет классы `ecarx.fw.api.diminteraction.*$$Creator` —
их jar есть в `/system/framework`, но **в снятые 10 jar не попал** — надо докачать
(см. `notes/commands.md`).

`com.geely.dimservice` (oneOS_DIMService) — штатный потребитель этого API для
медиа/BT-телефона; AtlasLauncher уже запускает его при старте HOME
(`qa/dim-service`, `reference/precedents/qa-dim-service.md`).

## 4. Управление приборкой (скрытие штатного UI) — работает

`com.autolink.miscservice` (MiscService, `/system/priv-app`) —
`reference/precedents/FdbusClientInstance.java`:

```
InteractionMessage{ msgType = MSG_TYPE_VEHICLE (ordinal 3), msgSubtype = 56,
                    InteractionData{ vehicleValue = 1 } }   // 1 = скрыть, 0 = вернуть
→ FdbusClient.invokeAsync(3, bytes) на svc://com.autolink.cluster_ivi.interaction
```

MiscService посылает это, когда видит окно DrakeStone на дисплее 0
(`FloatService.java`, `reference/precedents/FloatService.java`): скрыть при открытии,
вернуть при закрытии. Условия авто-запуска DrakeStone: carMode==0,
usageMode ∈ {2,11,13}, P, скорость < 5 км/ч, vehicleType G636.
Сообщение по binder **не экспортировано** — только напрямую fdbus.

## 5. Пиксели: прецедент DrakeStone и решение ANHUD

- **DrakeStone_G636** (`com.kanzi.skim`, `/system/app`, есть в лаунчере, exported):
  Kanzi-рантайм внутри Android. `MainActivity` (display 0) сам стартует
  `PSDActivity` (display 2) и `DIMActivity` (`setLaunchDisplayId(4)`) — т.е.
  рисует произвольный UI «на дисплее 4». Но по факту (см. п. 2) framebuffer
  дисплея 4 никто на QNX не потребляет — на приборке не видно. Демка при этом
  падает при старте активити на вторичных дисплеях.
- **ANHUD** (N155, HUD = QNX Screen display 4, 800×480): проблема та же, решение —
  **свой QNX-процесс с окном Screen**:
  - `ghudbridgelited` (`reference/anhud/ghudbridgelited`, QNX7 aarch64, ~30 КБ,
    ELF с debug_info): создаёт Screen-окно на дисплее с ID 4, z по умолчанию −3
    (под штатным hud-hmi, у которого z=1; опция `--z -100..100`), слушает TCP
    (по умолчанию 49210; `--bind`, `--heartbeat-ms`, `--pixel-order`).
  - Протокол **gcp/1** (`reference/anhud/Gcp.kt`, `HudLink.kt`): 24-байтный
    little-endian заголовок (magic 0x31504347, type, flags, seq, length,
    crc32 payload, crc32 заголовка) + payload. Типы: HELLO(0x01)/HELLO_ACK(0x02)/
    PING(0x03)/PONG(0x04)/BYE(0x05)/ERROR(0x06)/FRAME(0x10)/FRAME_ACK(0x11).
    HELLO: имя приложения + окно (w,h ≤ 800×480, x,y). FRAME: raw RGBA (или RLE32,
    ENC=1), прямоугольник, флаг FRAME_LAST. Демон сам убирает окно при потере связи.
  - Android-сторона: приватный VirtualDisplay 800×480 + ImageReader(RGBA_8888),
    диффы (изменившийся rect), ≤30 fps (`HudBridgeFrameSink.kt`).
  - Установка демона: telnet root на QNX (дефолт хост 192.168.118.2 — тот же!),
    бинарник копируется через NFS-шару `/data/vendor/nfs/shared` (или «набивается»
    `print -n '…' >> file` по telnet, если шары нет), `chmod 755`, запуск из `/tmp`
    (`DaemonInstaller.kt`, `QnxShell.kt`).

### Патч ghudbridgelited под приборку (1920×532) — сделан: `qnx/ghudbridgelited-dim`

Сверено llvm-objdump (VMA = смещение в файле), изменены ровно 4 инструкции:
`0x4964`/`0x5090` `cmp w8,#4 → #1` (SCREEN_PROPERTY_ID 87 дисплея),
`0x3210` `cmp x10,#0x320 → #0x780`, `0x322C` `cmp x8,#0x1e1 → #0x215`.
HELLO (payload): u16 ver=1, u16 mode=1, i32 x @4, i32 y @8, u32 w @12, u32 h @16,
u32 z @20 (игнорируется), u16 transparency @24 (3 или 4), u16 fps @26.
`window_create(x, y, w, h, transparency)`: формат RGBA8888, z из `--z`,
прозрачность — свойство окна, позиция/размер — из HELLO.

Исходное описание (ANHUD):

В бинарнике жёстко (по смещениям файла):
- выбор дисплея: свойство дисплея == 4 — `cmp w8,#4` в **0x4964** (подбор) и
  **0x5090** (проверка window_on_hud); заменить на ID Screen-дисплея приборки;
- лимиты окна 800×480 в HELLO-обработчике: `cmp x10,#0x320` в **0x3210**
  (x+w ≤ 800 → 1920 = 0x780) и `cmp x8,#0x1e1` в **0x322C** (y+h ≤ 481 → 533 = 0x215).

ID дисплея приборки известен (п. 2): **QNX dpy-1** (поверх `cluster-hmi`, order 3,
и под `EarlyTT`, order 10 — контрольные лампы не перекрывать) или dpy-2 (фон).
Т.е. патч `#4 → #1`, окно — `--z` между 3 и 10 (или ниже 3 при скрытом основном
слое, п. 4). Непатченный демон рисует на dpy-4 = HUD, которого в машине нет, —
визуально не проверить. Запуск своих процессов на QNX — только с явного
согласия пользователя (сейчас на QNX работаем только на чтение).

Нюанс ГУ: в getprop есть `vendor.display.disable_virtual_display=1` —
проверить, создаётся ли VirtualDisplay из приложения; если нет — рендерить
софтом в Bitmap (для панели часов/медиа достаточно).

## 6. FDBus Java-клиент из стороннего приложения

- Классы `ipc.fdbus.*` (~1500 строк) копируются из DIMInteraction
  (`reference/diminteraction-java/ipc/fdbus/`).
- `libfdbus-jni.so` лежит в `/system/lib64` ГУ, **не** в public.libraries.txt —
  стороннее приложение не может `System.loadLibrary("fdbus-jni")`. Решение:
  снять `.so` с ГУ и упаковать в APK как свою нативную библиотеку (тогда
  loadLibrary сработает из app namespace). Нужен permission INTERNET
  (fdbus ходит по TCP на localhost:60000+).
- Прямой fdbus нужен для сообщения скрытия приборки (п. 4) и для будущих
  экспериментов; для дата-канала (п. 3) он не нужен — есть binder.

## 7. Открытые вопросы

1. ~~ID приборки~~ — закрыт: dpy-1 (основной слой) + dpy-2 (фон), п. 2.
2. ~~Куда идут Android-дисплеи 1/3/4~~ — закрыт: никуда (живой тест, п. 2).
3. Совместимость ghudbridgelited с QNX ГУ (QNX 7.0, тот же SA8155 — вероятно ок).
4. Работает ли `createVirtualDisplay` из приложения при
   `vendor.display.disable_virtual_display=1`.
5. Где jar с `ecarx.fw.api.*$$Creator` (докачать `/system/framework`).
6. SELinux для app-домена на коннект к localhost:60000 (системные ходят из
   system-домена).
7. Что в QNX меняется при скрытии (sendDIMHideSwt 56): `cluster-hmi` invisible /
   alpha 0 / смена order — снять `win-*` до и во время игры DrakeStone.

## 8. Два независимых слоя — AtlasDim v0.8.0

Бинарник имеет одну клиентскую сессию и одно окно; второй клиент получает
`refused a second client: the HUD is taken`. z берётся из `--z`, поле HELLO
его не меняет. Поэтому без переписывания QNX-демона используются два процесса
с разными портами и именами исполняемых файлов:

| Роль | Файл в /tmp | TCP | По умолчанию |
|---|---|---|---|
| Часы | `ghudbridgelited-dim-clock1` | 50210 | dpy-1, z=9, 100×50 @0,482 |
| Фон | `ghudbridgelited-dim-background2` | 50211 | выключен; dpy-2, z=0, 1920×532 @0,0 |
| Топливо (v0.14.0) | `ghudbridgelited-dim-fuel1` | 50212 | выключено; dpy-1, z=8, 300×50 @1620,482 |

Последняя цифра имени соответствует выбранному display (1…3). При установке
Android вписывает display в те же две проверенные инструкции 0x4964/0x5090.
Сам файл `qnx/ghudbridgelited-dim` не изменён. По дизассемблированию буфер
кадров вычисляется из размеров окна (`main`, 0x3264…0x32c8), отдельного
фиксированного буфера только под 800×480 в этом участке нет.

Применение настроек к уже работающей композиции идёт по наименьшему пути:
- меняется только рисунок (картинка, цвет, прозрачность, сетка, секунды) — новый
  FRAME в открытые окна, без telnet и переподключения;
- меняются положение/размер окна — окна переоткрываются с новым HELLO к тем же
  процессам, без telnet. Демон после закрытия сокета убирает окно и ждёт
  следующего клиента (строки бинарника `session ended`, `window torn down`,
  `client_drop`, цикл `select(listen)`/`accept`; так же переподключается ANHUD).
  Пока демон не заметил закрытие, он отвечает ERR_BUSY — HELLO повторяется до 10 с;
- меняются display или z — по telnet сверяются процессы из `pidin ar`: демон с той
  же командной строкой (файл с display, `--port`, `--z`) и файлом нужного размера
  остаётся, лишние останавливаются по pid, недостающие запускаются. Связь, счётчик кадров и heartbeat у окон отдельные:
подключение второго окна не должно задерживать heartbeat первого (watchdog
демона по умолчанию 2000 мс). Статичный фон отправляется один раз, часы — при
смене текста. Ошибка одного соединения закрывает оба; при наличии пароля
приложение затем останавливает свои демоны. После краша Android демон должен
убрать окно по закрытию сокета/watchdog; процесс может остаться до следующего
запуска или перезагрузки. Живой тест этого поведения ещё требуется.

Предпросмотр использует тот же `SceneRenderer`, масштабирует координаты,
учитывает z внутри дисплея и dpy-1 поверх dpy-2. Он показывает только наши
пиксели: непрозрачность областей Kanzi и аппаратная композиция штатного UI
неизвестны, поэтому видимость фона через верхний слой проверяется на ГУ.
Окна dpy-3 показаны отдельно. Локальные подтверждения и границы —
`qa/composition/report.md`; это не доказательство успешного вывода на QNX.

## 9. Смежная модель G426_J1 — приборка одним слоем на dpy-2

Диагностика AtlasDim 2026-10-14 на смежной модели (та, с которой брали второй
набор приложений для лаунчера): `G426_J1`, QNX 7.0
`SA8155_ecarx_oversea_D0128G_v6.0.53`. Дисплеи те же (dpy-1/dpy-2 1920×532,
dpy-3 1440×1920, dpy-4 800×480, dpy-5 2880×1080), но окна другие:

| QNX dpy | Окна (order, статус) |
|---|---|
| 1 | `DPY_CLUSTER_EARLYTT` (10, VISIBLE), `DPY_CLUSTER_EARLYTT` (9, REALIZED) |
| 2 | `DPY_CLUSTER_FUNCTION` (3, FULLSCREEN) — окно процесса `cluster-hmi` |

Процесса и окна `cluster-bg-hmi` нет. `DPY_CLUSTER_FUNCTION` рисует и фон, и все
приборы одним слоем на dpy-2. **Подтверждено на машине 2026-10-14**: наш фон на
dpy-2 с z=4 закрывает всю приборку целиком. Модель различается по
`ro.product.model` (`Build.MODEL`): `G636` у основной ГУ, `G426_J1` у смежной.
Следствия:

- фона «под приборами» нет: на dpy-2 z<3 закрыт штатным слоем, z>3 закрывает
  приборы; фон возможен только поверх с прозрачностью;
- часы и топливо на dpy-1 — над всей приборкой; z не выше 8 (второе окно ламп — 9);
- выбор раскладки придётся делать по модели (или по окнам из диагностики).
