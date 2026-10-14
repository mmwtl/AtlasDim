#!/bin/sh
# Команды на QNX ГУ через telnet из shell Android (busybox telnet 192.168.118.2).
# Пароль root — штатный общедоступный ГУ G636; другой задать: QNX_PASS=... qnx/qnx.sh ...
#
#   qnx/qnx.sh SERIAL run 'команда' [сек]  — выполнить команду (ждать N сек, по умолчанию 3)
#   qnx/qnx.sh SERIAL push                 — демон в /tmp через NFS-шару
#   qnx/qnx.sh SERIAL start [z]            — запустить демон (z по умолчанию 5)
#   qnx/qnx.sh SERIAL stop                 — остановить только наш демон
#   qnx/qnx.sh SERIAL status               — процесс, порт, хвост лога
#
# В QNX ничего не меняется: только свой файл в /tmp и свой процесс.
# /tmp очищается при перезагрузке — автозапуска нет.
set -eu

SERIAL=${1:?serial}
CMD=${2:?command}
: "${QNX_PASS:=fGh4dalvHp4ubmb2}"

DIR=$(cd "$(dirname "$0")" && pwd)
BIN=ghudbridgelited-dim
SHARED_ANDROID=/data/vendor/nfs/shared
QNX_HOST=192.168.118.2

qnx() {
    # $1 — команды для ksh на QNX, $2 — сколько ждать вывода
    adb -s "$SERIAL" shell "(sleep 1; echo root; sleep 1; echo '$QNX_PASS'; sleep 1;
        echo '$1'; sleep ${2:-3}; echo exit) | busybox telnet $QNX_HOST" \
        | tr -d '\r' | sed -e '1,/^Logging in with home/d' -e '/^# exit$/,$d'
}

case "$CMD" in
run)
    qnx "${3:?command}" "${4:-3}"
    ;;
push)
    adb -s "$SERIAL" push "$DIR/$BIN" "$SHARED_ANDROID/$BIN"
    qnx "cp /shared/$BIN /tmp/$BIN && chmod 755 /tmp/$BIN && ls -l /tmp/$BIN && /tmp/$BIN --version"
    adb -s "$SERIAL" shell rm -f "$SHARED_ANDROID/$BIN"
    ;;
start)
    Z=${3:-5}
    qnx "/tmp/$BIN --z $Z > /tmp/$BIN.log 2>&1 & sleep 1; pidin -p $BIN; cat /tmp/$BIN.log"
    ;;
stop)
    qnx "slay -f $BIN; sleep 1; pidin -p $BIN; tail -5 /tmp/$BIN.log"
    ;;
status)
    qnx "pidin -p $BIN; tail -20 /tmp/$BIN.log"
    ;;
*)
    echo "unknown command: $CMD" >&2
    exit 2
    ;;
esac
