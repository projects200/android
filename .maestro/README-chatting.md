# 채팅 송수신 2기기 E2E

`chatting-*.yaml` 중 `requires-two-devices` 태그가 붙은 네 개(`chatting-send`, `chatting-receive`,
`chatting-history`, `chatting-list-refresh`)는 계정 2개와 기기(또는 에뮬레이터) 2대가 동시에 있어야
완결됩니다. 단일 기기로 전체를 돌리는 `maestro test .maestro/`에는 섞여 있지 않습니다. 반드시
`--exclude-tags=requires-two-devices`로 빼고 돌리거나, 아래 절차로 따로 실행합니다.

## 전제

- 계정 A·B는 같은 서버 환경(dev 등)을 보는 서로 다른 테스트 계정입니다. #609(무인 로그인)가 아직
  없어 각 기기에 수동으로 한 번씩 로그인해 둬야 합니다. `launch-logged-in.yaml`은 세션을 유지한 채
  실행하며 로그인 자체는 하지 않습니다
- 기기 2대를 `adb devices`로 구분합니다. 에뮬레이터 2개를 띄우면 보통 `emulator-5554`,
  `emulator-5556`처럼 잡힙니다. 실기기를 섞어 써도 됩니다
- A·B가 공유하는 전용 대화방이 미리 있어야 합니다. 이 저장소의 채팅 목록에는 새 방 생성 버튼이
  없고(#613 범위), 매칭 화면에서 방에 들어가는 경로는 매칭 E2E(#613) 담당입니다. 그래서 전용 방은
  매칭 플로우나 수동 조작으로 **한 번만** 만들어 두고, 이후 실행에서는 재사용합니다. 방을 새로
  만들지 않는 것이 테스트 설계의 일부입니다
- 차단·퇴장 상태가 아닌지 확인합니다. `chatting-send.yaml`/`chatting-receive.yaml`은 방에 들어가서
  `chatting_message_et`가 `enabled: true`인지 가볍게 확인합니다. 차단되었거나 상대가 나간 방이면
  입력창이 비활성화되고 힌트가 `차단한 회원과는 대화할 수 없습니다` 또는
  `상대방이 채팅방을 나갔습니다`로 바뀌어(`ChatInputState`) 이 단언에서 실패합니다. 실패하면
  Maestro가 아니라 앱을 직접 열어 차단을 풀거나 다른 전용 방을 준비합니다

## 실행 순서와 환경 변수

메시지 문구는 두 기기가 같은 값을 봐야 비교가 성립합니다. 한쪽이 `evalScript`로 타임스탬프를
만들면 다른 쪽은 그 값을 알 수 없으므로, 셸에서 변수를 만들어 양쪽 명령에 그대로 넘깁니다.
ASCII만 씁니다(한글은 `inputText`로 입력되지 않습니다).

```bash
UDID_A=emulator-5554
UDID_B=emulator-5556
NICK_A="계정A 닉네임"   # B 기기 목록/방 제목에 뜨는 표시명
NICK_B="계정B 닉네임"   # A 기기 목록/방 제목에 뜨는 표시명

# 1. A → B 송수신
TAG1="e2e614-$(date +%s)"
maestro --device "$UDID_A" test .maestro/flows/chatting-send.yaml \
  -e PEER_NICKNAME="$NICK_B" -e MESSAGE="$TAG1" \
  --debug-output build/maestro/debug-A --test-output-dir build/maestro/A-send

maestro --device "$UDID_B" test .maestro/flows/chatting-receive.yaml \
  -e PEER_NICKNAME="$NICK_A" -e MESSAGE="$TAG1" \
  --debug-output build/maestro/debug-B --test-output-dir build/maestro/B-receive

# 2. B → A 답장 (완료 기준의 "B의 답장 → A에서 수신 확인")
TAG2="e2e614-$(date +%s)-reply"
maestro --device "$UDID_B" test .maestro/flows/chatting-send.yaml \
  -e PEER_NICKNAME="$NICK_A" -e MESSAGE="$TAG2" \
  --debug-output build/maestro/debug-B --test-output-dir build/maestro/B-send

maestro --device "$UDID_A" test .maestro/flows/chatting-receive.yaml \
  -e PEER_NICKNAME="$NICK_B" -e MESSAGE="$TAG2" \
  --debug-output build/maestro/debug-A --test-output-dir build/maestro/A-receive

# 3. 목록 반영 확인 (각 기기에서 독립적으로, 방금 받은 문구가 최근 메시지로 보이는지)
maestro --device "$UDID_A" test .maestro/flows/chatting-list-refresh.yaml \
  -e PEER_NICKNAME="$NICK_B" -e MESSAGE="$TAG2" \
  --debug-output build/maestro/debug-A --test-output-dir build/maestro/A-list

maestro --device "$UDID_B" test .maestro/flows/chatting-list-refresh.yaml \
  -e PEER_NICKNAME="$NICK_A" -e MESSAGE="$TAG1" \
  --debug-output build/maestro/debug-B --test-output-dir build/maestro/B-list

# 4. 재진입 이력 유지 + 비운 동안 도착한 메시지 확인 (B 기준 예시)
# chatting-history.yaml은 방을 비우고 8초 대기하는 구간이 있습니다. 그 구간 안에
# A가 AWAY_MESSAGE를 보내도록 백그라운드로 겹쳐 실행합니다
AWAY_TAG="e2e614-$(date +%s)-away"
maestro --device "$UDID_B" test .maestro/flows/chatting-history.yaml \
  -e PEER_NICKNAME="$NICK_A" -e EXISTING_MESSAGE="$TAG2" -e AWAY_MESSAGE="$AWAY_TAG" \
  --debug-output build/maestro/debug-B --test-output-dir build/maestro/B-history &
HISTORY_PID=$!

sleep 3
maestro --device "$UDID_A" test .maestro/flows/chatting-send.yaml \
  -e PEER_NICKNAME="$NICK_B" -e MESSAGE="$AWAY_TAG" \
  --debug-output build/maestro/debug-A --test-output-dir build/maestro/A-away-send

wait "$HISTORY_PID"
```

`sleep 3`은 `chatting-history.yaml`이 방을 나가 목록으로 돌아올 시간을 벌어 주는 값입니다. 기기가
느리면 `chatting-send.yaml` 쪽 전송이 history 플로우의 8초 대기 구간보다 늦게 끝날 수 있으니,
콜드 스타트가 느린 기기에서는 `sleep` 값을 늘립니다.

## 실패 자료를 계정·기기별로 구분하기

위 예시처럼 `--debug-output`과 `--test-output-dir`을 실행마다(기기·역할 조합마다) 다른 경로로
지정합니다. 한쪽 기기의 실패가 다른 쪽 성공 리포트에 묻히지 않도록, 묶어서 보지 말고 각
`build/maestro/<기기>-<역할>/` 디렉터리를 따로 확인합니다. `maestro.log`와 그 시점의 접근성 트리
JSON, 스크린샷이 함께 남습니다.

## 데이터 수명

- 메시지는 건별 삭제 UI가 없어 Maestro로 지울 수 없습니다. 전용 방과 그 안의 e2e 태그 메시지는
  반복 실행마다 쌓이는 장기 픽스처로 남겨 둡니다
- 방 자체도 메뉴의 '나가기'로 탈퇴하지 않습니다. 탈퇴하면 다음 실행에서 쓸 전용 방이 사라져
  다시 수동으로 만들어야 합니다
- 다른 사용자의 방이나 메시지는 건드리지 않습니다
