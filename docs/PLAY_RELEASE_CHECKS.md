# 公開準備の検証記録

2026年11月21日（JST）

- `bundleRelease assembleDebug testDebugUnitTest lintRelease --offline`：BUILD SUCCESSFUL。
- 単体テスト27件：成功（時計・同期8件、NTP16件、設定3件）。
- Release Lint：`No issues found.`
- 未設定の署名鍵を要求する `-Pchronosync.requireReleaseSigning=true`：意図どおり設定不足を検出して停止。
- AABの署名を調査：署名エントリーなし。検証用の未署名AABであることを確認。
- AAB内の64bitネイティブライブラリ4本：PT_LOADのアラインメントが16KB以上であることを確認。
- Debug APKの `zipalign -c -P 16 -v 4`：成功。
- Pixel 7aのDebug版で、アプリ内プライバシーポリシーの全文表示とスクロール・閉じる操作を確認。
- 掲載素材：512×512アイコン、1024×500フィーチャー画像を画像として確認。実機スクリーンショット4枚の内容を確認。

未実施：秘密鍵の作成、署名付きAAB、16KB端末での実行、Play ConsoleでのApp Bundle Explorer検証・テスト配信・申告・公開、ポリシーの外部ホスティング、審査用デモ動画の録画・アップロード。
