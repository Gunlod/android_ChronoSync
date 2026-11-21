# Web配布用APK

配布するファイルは `dist/ChronoSync-1.0.0.apk` です。Google Play用のAABではなく、端末に直接インストールできる署名付きリリースAPKを生成します。Android 8.0以上が対象です。

作成済みの1.0.0はリリースビルドと署名検証に成功しました。単体テスト27件は成功状態、Release Lintも完了しています。APKのZIPアラインメントと64bitネイティブライブラリ4本の16KBアラインメントも確認しました。新しい署名のリリース版を接続端末へインストールするための、既存デバッグ版の削除は行っていません。

- APK SHA-256：`1529a7b7a3a303cf78be251132977184ea1d88fc5b59828fdc6f474da547d940`
- 署名証明書 SHA-256：`a3878fcce60923d9609e9893579f073474fb37230783b201090c5872bed63f69`

## 作成

```powershell
$env:JAVA_HOME = 'E:\Eclipse\java\21'
# 初回のみ。既存の鍵や設定は上書きしません。
.\scripts\Initialize-ReleaseSigning.ps1
.\scripts\Build-WebRelease.ps1
```

`Build-WebRelease.ps1` はリリースAPK・単体テスト・Release Lintを実行し、APKとSHA-256を `dist` に保存します。署名設定がない場合はビルドを停止します。

## 公開するもの

- `ChronoSync-1.0.0.apk`
- `ChronoSync-1.0.0.apk.sha256`
- アプリの説明・対応Androidバージョン・インストール方法
- プライバシーポリシー（掲載用HTML：`store/privacy/index.html`）

APKをWebサーバーへアップロードし、そのHTTPS URLへダウンロードリンクを設定してください。可能であればAPKには `application/vnd.android.package-archive` のContent-Typeを設定します。Webサーバーへのアップロードはこのスクリプトでは行いません。

## 署名鍵の保管

`private-keys/chronosync-release.p12` と `keystore.properties` は、今後の更新でも同じ鍵で署名するために必要です。両方を安全な場所へバックアップしてください。これらはGit管理対象から除外しており、配布ファイルには含めません。

今後の更新は同じapplicationIdと同じ署名鍵を使い、`app/build.gradle.kts` の `versionCode` を増やして作成します。

## インストール

ダウンロードしたAPKを開き、Androidの案内に従って、そのブラウザーまたはファイルアプリの「不明なアプリのインストール」を許可します。

開発中のデバッグ版 `jp.gunlod.chronosync` は署名鍵が異なるため、このリリース版で上書きできません。初回の移行時は必要な設定を控えてからデバッグ版を削除し、リリース版を新規インストールしてください。アプリを削除すると端末内の設定も削除されます。作業中に接続端末のデバッグ版を自動削除することはありません。
