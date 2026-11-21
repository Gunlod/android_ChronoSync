# ChronoSync Google Play公開手順

準備日：2026年11月21日。開発者：Gunlod。サポート：gunlod.0122+playstore@gmail.com。

## 現在の状態

- アプリID：`jp.gunlod.chronosync`。公開後のアプリID変更は別アプリになります。
- 初回バージョン：1.0.0 / versionCode 1。アップロード済みのversionCodeは再利用せず、次の更新時に増やします。
- compileSdk / targetSdk 36。現時点の新規公開向けAPI要件に対応する設定です。
- リリース用AABのビルド、単体テスト、Release Lintを実行済み。
- 掲載文、リリースノート、512×512アイコン、1024×500フィーチャー画像、実機スクリーンショット、公開用プライバシーポリシーHTMLを用意しました。
- アプリ内「アプリ情報」に開発者・連絡先・プライバシーポリシー全文を追加しました。
- **Play Consoleへの登録・本人確認・支払い・アップロード・審査申請は未実施です。**
- **現在のAABは署名鍵が未設定の場合は未署名です。そのままPlay Consoleへ提出するファイルではありません。**

## 1. 開発者アカウントを登録

[Google Play Console](https://play.google.com/console/signup)で登録します。アカウントの種類は実態に合わせて個人／組織を選択してください。登録には一度限りのUS$25の料金が必要です。Googleの画面で求められる本人情報・連絡先・本人確認・実機確認を完了してください。本人確認書類や支払い情報をチャットに送る必要はありません。

個人で新規登録する場合、公開アクセス申請前に、12人以上が14日間連続でオプトインしているクローズドテストが必要です。内部テストだけではこの要件を満たしません。テスターには実際に操作してもらい、感想・不具合・改善内容を記録してください。条件達成後も製品版公開アクセスの申請・審査があります。

公式：[登録方法](https://support.google.com/googleplay/android-developer/answer/6112435?hl=ja)、[新規個人アカウントのテスト要件](https://support.google.com/googleplay/android-developer/answer/14151465?hl=ja)。

## 2. アップロード鍵を作成・保管

既存のアップロード鍵がある場合はそれを使用できます。新しく作る場合は、ローカルのPowerShellで実行します。秘密鍵の作成はまだ実行していません。

```powershell
$env:JAVA_HOME = 'E:\Eclipse\java\21'
.\scripts\New-UploadKey.ps1
Copy-Item keystore.properties.example keystore.properties
```

`keytool`のパスワード入力は手元で行います。作成した鍵とパスワードは別々の安全な場所へバックアップしてください。`keystore.properties` のパスワードを手元で編集し、PKCS12の場合は `storePassword` と `keyPassword` に同じパスワードを設定します。実際の鍵・パスワードはGit管理対象から除外しています。

Play App Signingでは配信用の署名鍵とアップロード鍵を区別します。新規公開時はPlay Consoleの案内に従ってPlay App Signingを設定します。GoogleへアップロードするAABにはアップロード鍵で署名します。debug用の鍵は使用しません。

公式：[アプリへの署名](https://developer.android.com/studio/publish/app-signing)。

## 3. 署名付きAABを作成

```powershell
.\scripts\Build-PlayRelease.ps1
```

成果物：`app/build/outputs/bundle/release/app-release.aab`

このスクリプトは鍵の設定がない場合に停止します。単に `bundleRelease` を実行すると、鍵が未設定でも検証用の未署名AABを生成できるため、提出用には上記スクリプトを使用してください。

CIなどでは `CHRONOSYNC_STORE_FILE`、`CHRONOSYNC_STORE_PASSWORD`、`CHRONOSYNC_KEY_ALIAS`、`CHRONOSYNC_KEY_PASSWORD` の4環境変数でも設定できます。パスワードをコマンド引数・ログに出さないでください。

確認例：

```powershell
& "$env:JAVA_HOME\bin\jarsigner.exe" -verify app/build/outputs/bundle/release/app-release.aab
python scripts/check-native-alignment.py app/build/outputs/bundle/release/app-release.aab
```

16KBのELFアラインメント確認と、実際の16KB端末での動作確認は別です。Play ConsoleのApp Bundle Explorerと事前公開レポートも確認してください。

公式：[target API要件](https://developer.android.com/google/play/requirements/target-sdk)、[16KB対応](https://developer.android.com/guide/practices/page-sizes)。

## 4. プライバシーポリシーを公開

`store/privacy/index.html` を静的Webホスティングへ配置してください。公開用HTMLにはGunlodと連絡先を反映済みです。公開サイトはまだ作成していません。

既存サイトがない場合は、例えばGitHub Pagesに専用の公開リポジトリを用意し、HTMLを `index.html` として配置できます。アプリのソースや署名鍵を同じ公開リポジトリへ置く必要はありません。

GitHub Pagesを使う場合の流れ：

1. GitHubで `chronosync-privacy` などの公開リポジトリを作成。
2. `store/privacy/index.html` をリポジトリ直下の `index.html` としてアップロード。
3. リポジトリのSettings → Pagesで、公開元のブランチとルートフォルダーを選択。
4. 公開されたURLをログアウト状態でも読めることを確認して、Play Consoleへ登録。

公式：[GitHub Pagesの作成手順](https://docs.github.com/en/pages/getting-started-with-github-pages/creating-a-github-pages-site)。GitHub Freeでは公開リポジトリで利用できます。

ログイン不要で読める公開URLをPlay Consoleへ登録します。ローカルファイルのパスやPDFは提出用URLとして使いません。アプリ内には同じ内容の全文を表示できるようにしています。

ポリシーを変更した場合は `app/src/main/res/values/privacy.xml` を修正し、`python scripts/prepare-play-assets.py` でHTMLを再生成して、アプリと公開ページの内容を揃えてください。

公式：[ユーザーデータ・プライバシーポリシー要件](https://support.google.com/googleplay/android-developer/answer/10144311?hl=ja)。

## 5. Play Consoleのアプリを作成

候補入力：

| 項目 | 内容 |
| --- | --- |
| アプリ名 | ChronoSync |
| デフォルト言語 | 日本語 |
| 種別 | アプリ |
| カテゴリ候補 | ツール |
| 開発者名 | Gunlod |
| サポートメール | gunlod.0122+playstore@gmail.com |
| 広告 | なし |
| アプリ内課金 | なし |
| ログイン | 不要 |
| 価格・配信地域・対象年齢 | 未設定。本人がConsoleで選択 |

掲載文は `store/listing-ja-JP.txt`、リリースノートは `store/release-notes-ja-JP.txt`、画像は `store/assets` にあります。同期画面、他アプリ上の時計、補正パネル、ポリシー画面の実機キャプチャを用意しました。掲載するものを選び、最新の仕様と一致することを最終確認してください。

コンテンツレーティング、対象年齢、広告、データセーフティ、アプリへのアクセスなどの申告も完了させます。詳しくは `store/review-notes.md` を参照してください。

公式：[掲載用画像の要件](https://support.google.com/googleplay/android-developer/answer/9866151?hl=ja)、[審査の準備](https://support.google.com/googleplay/android-developer/answer/9859455?hl=ja)。

## 6. 内部テスト → クローズドテスト → 製品版

1. 署名付きAABを内部テストへアップロードし、Google Play経由でインストールして確認。
2. Foreground Service申告と、確認できるデモ動画のURLを登録。
3. 新規個人アカウントの場合はクローズドテストを実施し、12人・14日間の条件とテスト内容を満たして公開アクセスを申請。
4. 事前公開レポートのクラッシュ・互換性・表示の指摘を修正。
5. 掲載情報、価格、地域、署名、申告を確認してから製品版の審査へ提出。

オーバーレイと `specialUse` Foreground Serviceの採用についてはGoogleの審査があります。この準備だけで承認されることを保証するものではありません。
