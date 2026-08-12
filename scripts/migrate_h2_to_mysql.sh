#!/usr/bin/env bash
# H2(파일 모드)에 쌓여있는 기존 데모/테스트 데이터를 MySQL 8.4로 옮긴다.
#
# 실행 전제:
#   - 지금 배포된(H2 시절) ROOT.war가 아직 서버에 있다 (h2 드라이버 jar를 여기서 꺼낸다)
#   - 대상 MySQL에는 이미 Flyway로 스키마(V1__init.sql)가 적용돼 있다
#     (새 앱을 MySQL에 한 번 띄웠다 내리면 자동으로 만들어진다)
#   - 이 스크립트는 H2 "원본 파일의 복사본"만 읽으므로, 지금 뜬 목 서버(H2)는 내릴 필요 없다
#
# 사용법:
#   ./migrate_h2_to_mysql.sh /실제/경로/mockbroker.mv.db <MYSQL_HOST> <MYSQL_PORT> <MYSQL_DB> <MYSQL_USER> <MYSQL_PASSWORD>
#
# 주의: 각 테이블 import 전에 TRUNCATE한다 — 대상 MySQL 테이블이 비어있는 상태(혹은 덮어써도 되는
# 상태)인지 반드시 먼저 확인할 것.

set -euo pipefail

H2_FILE="$1"           # 예: /home/kb_investory/tomcat9/data/mockbroker.mv.db
MYSQL_HOST="$2"
MYSQL_PORT="$3"
MYSQL_DB="$4"
MYSQL_USER="$5"
MYSQL_PASSWORD="$6"

WORK=$(mktemp -d)
echo "작업 디렉터리: $WORK"

# 1) H2 파일을 복사해서 원본은 건드리지 않는다 (다운타임 없음)
H2_BASENAME=$(basename "$H2_FILE" .mv.db)
cp "$H2_FILE" "$WORK/$H2_BASENAME.mv.db"

# 2) 배포된 WAR에서 h2 드라이버 jar를 꺼낸다
CATALINA_HOME="${CATALINA_HOME:-$HOME/tomcat9}"
unzip -j -o "$CATALINA_HOME/webapps/ROOT.war" 'WEB-INF/lib/h2-*.jar' -d "$WORK" >/dev/null
H2JAR=$(ls "$WORK"/h2-*.jar | head -1)
echo "h2 드라이버: $H2JAR"

TABLES="mock_user mock_client mock_account mock_price mock_holding mock_transaction mock_connection"

# 3) 테이블별로 CSV로 내보낸다 (NULL은 MySQL이 이해하는 \N으로 표시)
mkdir -p "$WORK/csv"
for t in $TABLES; do
  echo "내보내는 중: $t"
  java -cp "$H2JAR" org.h2.tools.Shell \
    -url "jdbc:h2:file:$WORK/$H2_BASENAME;MODE=MySQL" \
    -user sa -password "" \
    -sql "CALL CSVWRITE('$WORK/csv/$t.csv', 'SELECT * FROM $t', 'charset=UTF-8 nullString=\\N');" \
    >/dev/null
done

# 4) MySQL로 LOAD DATA (테이블마다 먼저 비우고 적재)
MYSQL="mysql --local-infile=1 -h$MYSQL_HOST -P$MYSQL_PORT -u$MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DB"

for t in $TABLES; do
  rows=$(($(wc -l < "$WORK/csv/$t.csv") - 1))
  echo "적재 중: $t ($rows행)"
  $MYSQL -e "SET SESSION local_infile = 1;"
  $MYSQL -e "
    TRUNCATE TABLE $t;
    LOAD DATA LOCAL INFILE '$WORK/csv/$t.csv'
    INTO TABLE $t
    CHARACTER SET utf8mb4
    FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\"'
    LINES TERMINATED BY '\n'
    IGNORE 1 LINES;
  "
  loaded=$($MYSQL -N -e "SELECT COUNT(*) FROM $t;")
  echo "  -> MySQL에 $loaded 행 (H2 CSV는 $rows 행)"
  if [ "$loaded" != "$rows" ]; then
    echo "  !! 행 수가 다릅니다. $t 테이블을 확인하세요." >&2
  fi
done

echo "완료. 임시 파일은 $WORK 에 남겨뒀습니다 (문제 있으면 CSV 확인 후 직접 지우세요)."
