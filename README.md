# ChronoSync

Android向けの小型NTP同期オーバーレイ時計です。パッケージは `jp.gunlod.chronosync`。時刻を `HH:mm:ss.SSS` / `Asia/Tokyo` / 24時間で表示し、表示時刻だけを−9999～+9999ms、1ms単位で補正できます。Rootやアクセシビリティサービスは使用しません。

## ビルド

- JDK 17 または21（`JAVA_HOME` を設定）
- Android SDK Platform 36 / Build Tools 35.0.0以上
- インターネット接続（初回のGradle・ライブラリ取得）
- Android 8.0（API 26）以上の端末

Android Studioでこのフォルダーを開くか、VS Codeのターミナルから実行してください。

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
.\gradlew.bat installDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

単体テスト結果: `app/build/reports/tests/testDebugUnitTest/index.html`

Lint結果: `app/build/reports/lint-results-debug.html`

SDKはAndroid StudioのSDK Managerで導入し、`local.properties` にパスを設定します（このファイルはGit管理対象外）。例:

```properties
sdk.dir=C\:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk
```

この作業環境には `.tools/android-sdk` にSDKを導入し、`local.properties` を設定しています。JDK 21は `E:\Eclipse\java\21` を使用しました。

```powershell
$env:JAVA_HOME = 'E:\Eclipse\java\21'
.\gradlew.bat assembleDebug
```

Gradle Wrapper 8.13（JARを含む）、AGP 8.13.2、Kotlin / Compose Compiler 2.2.21、Compose BOM 2025.11.00を固定しています。VS Codeにはビルド、テスト、インストールのタスクを同梱しています。

## 使い方

1. アプリを開くと `ntp.nict.jp` に最大5回問い合わせます。測定中は完了を待ってください。
2. 「他のアプリの上に表示」をONにし、オーバーレイ表示権限を許可します。Android 13以降では通知権限も要求します。通知を拒否しても時計は利用できます。
3. 時計をドラッグすると移動し、指を離すと位置を保存します。短いタップで補正操作を開閉します。
4. ＋ / －で選択した1 / 10 / 100ms分を補正します。リセットは0msに戻します。設定画面から数値の直接入力もできます。
5. 停止は設定画面のスイッチ、または表示中の通知の「停止」から行います。

通常時に表示するのは時計だけです。補正用の各ウィンドウは時計と別に配置し、時計やボタンの外側には全画面タッチ面を作りません。端にある場合は操作パネルを空き領域へ移します。狭い領域では操作パネルをスクロールできます。画面回転・サイズ変更で時計を可視領域へ収め、その位置を保存します。

保存対象はNTPサーバー、補正値、補正ステップ、文字サイズ・色、背景不透明度、表示位置、オーバーレイのON/OFFです。オーバーレイを有効にしていた場合、アプリ画面を次に開いたときに表示を復元します。端末再起動時やバックグラウンドからサービスを自動起動する処理はありません。

## 時刻と精度

- UDP 123のNTPv3/v4応答を検証します。バージョン、モード、LI、stratum、要求の送信時刻との一致、受信・送信時刻、処理時間、ルート誤差を確認します。
- 各測定の受信時刻は `t4 = t1 + (受信elapsedRealtimeNanos − 送信elapsedRealtimeNanos)` で求めます。`offset = ((t2 − t1) + (t3 − t4)) / 2`、`RTT = (t4 − t1) − (t3 − t2)`。RTTと誤差見積もりが小さい測定を優先します。
- 初回測定でUTCの基準を作るためだけに端末時計を読み、同期後は `T_base + (M_now − M_base)` で計算します。描画ループで端末のシステム時刻は読みません。内部時刻はLongのナノ秒です。
- 表示補正は `同期UTCナノ秒 + 補正ミリ秒 × 1,000,000`。同期値やRTTには影響しません。
- 初回同期が失敗した場合、`UNSYNCED` とエラーを表示し、時計は `--:--:--.---` のままです。再同期失敗時は最後の同期基準を維持し、`DEGRADED` とエラーを表示します。
- 同期情報はプロセス内だけに保持します。プロセス再生成後の起動時には新しく同期し、古い単調増加時計の基準を復元しません。同一プロセスでの画面再作成では追加の自動同期を行いません。手動再同期はいつでも可能です。
- ネットワーク測定は最大5回、各UDP応答は1.5秒でタイムアウトし、試行の間隔は1秒です。起動時の測定以外に定期通信はありません。DNS解決時間はOS・ネットワーク環境に依存します。
- 概算誤差はRTT/2、サーバーのroot delay/2・dispersion、ローカル計測余裕1ms、同期後20ppmの仮定から求めます。1時間経過または概算誤差100ms超で `DEGRADED`。これは精度保証・統計的な信頼区間ではありません。
- ミリ秒表示と1msの実精度は異なります。通信経路の非対称、画面リフレッシュ、描画遅延で誤差が生じます。NTP通信は認証されません。NTP eraの判定は端末の日付を基準にするため、端末の日付が実際から約68年以上ずれている場合は正しく推定できません。
- オーバーレイはChoreographerで画面のvsyncに合わせて更新します。画面OFF時は描画を停止し、内部の時計はスリープ時間も含めて進みます。

## Androidサービス

`TYPE_APPLICATION_OVERLAY` と、ユーザーが明示的に開始・停止する `specialUse` Foreground Serviceを使用します。Android 14以降の必要権限と用途プロパティをManifestに宣言しています。サービス開始は可視Activityでの操作または保存済みのON設定の復元に限定し、バックグラウンド起動の例外には依存しません。ウィンドウ全体のalphaを0.8に抑え、背景不透明度の設定とは別にAndroidのタッチ遮蔽制約を考慮しています。機種やAndroidの設定によって表示が制限される場合があります。

公式資料: [AGP 8.13互換性](https://developer.android.com/build/releases/agp-8-13-0-release-notes)、[Foreground Serviceの種別](https://developer.android.com/develop/background-work/services/fgs/service-types)、[バックグラウンド起動制約](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)、[WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)。

## 構成

| ファイル | 役割 |
| --- | --- |
| MainActivity / MainViewModel | Compose設定画面、権限導線、起動時同期 |
| NtpClient / NtpPacket | UDP測定、応答検証、オフセット・RTT算出 |
| ClockSyncManager / SynchronizedClock | 同期状態と単調増加時計の基準 |
| ClockOffsetManager | 表示補正と上下限 |
| SettingsRepository | DataStore保存、順序を保った書き込み |
| OverlayClockService / OverlayClockView | 常駐通知、ウィンドウ配置、vsync描画、ドラッグ |

## テスト

単体テストではNTP応答検証・RTT・オフセット・2036年のera境界、未同期時の表示、単調増加時計、正負補正と秒・分・日またぎ、再同期、失敗時の状態遷移、補正上限・下限、実際のDataStoreファイルでの保存・再読み込みを確認します。

端末での実行結果と、追加確認が必要な項目は `docs/TEST_RESULTS.md` に記載します。
