# Play Console申告・審査用原稿

これは現在のコードに基づく入力準備資料です。Consoleの実際の設問と、提出するAABの挙動に合わせて確認してください。

## Foreground Service

種類：`specialUse` / `FOREGROUND_SERVICE_SPECIAL_USE`

用途（英語原稿）：

> ChronoSync displays a small, user-enabled NTP-synchronized floating clock above other apps. The foreground service maintains the visible clock window and its interactive time-offset controls while the user switches to another app. The service starts from the visible app after the user enables the overlay, or restores that saved preference when the user opens the app. The ongoing notification and the in-app switch let the user stop the clock immediately. The service is not used for periodic synchronization or hidden background work. Scheduled jobs cannot provide a continuously visible, interactive overlay window. No other foreground service type describes this core clock-overlay use case.

ユーザーへの影響：サービスが停止すると他アプリ上の時計も終了するため、時計表示を有効にしている間だけ継続します。オーバーレイOFF時はサービスを停止し、ウィンドウも削除します。

デモ動画に含める内容：

1. ChronoSyncを起動し、同期状態を表示。
2. アプリ内でオーバーレイをONにするユーザー操作。
3. 別アプリへ切り替えても小型時計が継続して見えること。
4. 時計をタップし、横並びの「－」「＋」と補正ステップで表示時刻を補正すること。
5. 通知またはアプリ内スイッチから停止でき、時計が消えること。

個人情報が写らない状態で録画し、審査担当者が視聴できるURLを登録します。動画の公開・アップロードは未実施です。

公式：[Foreground Service申告](https://support.google.com/googleplay/android-developer/answer/13392821?hl=ja)。

## アプリへのアクセス

ログイン・有料機能・地域限定のアカウントはありません。全機能にアクセスできます。オーバーレイの表示には端末設定で「他のアプリの上に表示」の許可が必要です。NTP同期にはインターネット接続とUDP 123の通信許可が必要です。未同期でも設定画面は操作でき、時計は `--:--:--.---` と表示します。

## データセーフティの確認材料

| 実装の確認事項 | 状態 |
| --- | --- |
| 広告SDK・解析SDK・開発者の収集API | なし |
| 端末位置・連絡先・写真・広告IDへのアクセス | なし |
| 他アプリの画面内容や操作内容の読取り | なし |
| 設定データ | 端末内DataStoreに保存。開発者サーバーへの送信機能なし |
| ネットワーク通信 | 選択したNTPサーバーへの時刻リクエスト。DNSは端末の仕組みを使用 |
| NTP通信先に伝わる情報 | 送信元IPアドレス、NTPのリクエスト時刻 |
| NTP通信の暗号化 | なし |
| アカウント作成・削除 | アプリ内アカウント機能なし |
| 端末内設定の削除 | アプリのストレージ消去またはアンインストール |

NTPの通信先がネットワーク情報をどう保持・利用するかは本アプリだけでは確認できません。「データを一切送信しない」「すべての通信が暗号化される」とは申告しないでください。データセーフティの収集・共有・一時処理・例外の定義に照らし、通信先事業者の取扱いを確認して最終回答を決めます。IPアドレスから位置を推定する機能は本アプリに実装していません。

公式：[データセーフティの定義と入力方法](https://support.google.com/googleplay/android-developer/answer/10787469?hl=ja)。

## レーティング・対象年齢・広告

広告はありません。課金、ユーザー投稿、チャット、アカウント作成もありません。対象年齢はアプリの機能から自動で決めず、開発者が対象ユーザーを決めてConsoleで回答します。コンテンツレーティングはIARCの質問票へ回答して取得します。
