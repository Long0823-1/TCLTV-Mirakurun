# Mirakurun本体の更新（プレリリース）

## 操作

1. TVで「TCL TV Mirakurun」を開き、「Mirakurun本体の更新を確認」を選びます。
2. インストール済み版・公式npmの最新安定版・Node.js・戻せる版・Apache-2.0表示を確認します。
3. 視聴・録画を終了し、「本体を更新する」を選びます。更新中はTVの電源を切らないでください。
4. 数分待ち、成功表示を確認します。配信は自動で再開します。TVの再起動は不要です。

「前の本体に戻す」では、本体と、対応する設定・番組DBのバックアップを復元します。
更新情報の取得に失敗しても、保存済み本体への復元は利用できます。
本体・依存パッケージのライセンスと更新ログはそれぞれ専用ボタンで表示します。

初回APKの同梱版はMirakurun 4.3.0です（0.3.0-pre.5以降。それ以前は4.1.5）。本機能は**npmのMirakurunパッケージ**を更新します。
APK、Node.js、Alpine Linux、ネイティブチューナー本体、ネットワーク設定の更新ではありません。
自動的な無人更新は行いません。

## 取得・検証・切り替え

- 更新候補は `https://registry.npmjs.org/mirakurun/latest` の安定版です。
- パッケージ名・版・Apache-2.0表示・配布URL・SHA-512 integrityを検査します。
- 安定版4.xだけを対象にし、Node.js enginesを検査します。別メジャーは案内だけを表示します。
- TVの有効な接続のDNSをchroot環境へ反映して取得します。Android側のネットワーク設定は変更しません。
- 現在の視聴・録画ユーザー（priority>=0）がいれば実行を拒否します。バックグラウンドEPG（負のpriority）は停止できます。
- 低メモリー機のため、準備前にMirakurunを停止します。npmのヒープを128MBに制限し、取得は7分を上限にします。
- `npm --ignore-scripts` でライフサイクルスクリプトを実行せず、別prefixへ導入します。
- `package-lock.json` の版・integrityを照合し、ARMv7/muslで必要なCRCのJS代替を配置・検証します。
- IPv4の待受、管理下の.local Origin、専用Ethernet /30の修正を候補に適用します。非対応の上流実装では適用を止めます。
- 新しい本体は `/opt/mirakurun.update-new` で準備し、旧本体は `/opt/mirakurun.update-previous` に残します。
- 停止完了後に `/usr/local/etc/mirakurun` と `/usr/local/var/db/mirakurun` を保存します。
- 起動後にAPIの版とチューナー設定を確認します。失敗時は本体と保存した設定・DBへ戻して再起動します。
- 失敗した候補は復元候補として提示しません。途中の取得ファイルを削除します。

ロック・取得・退避・復元は固定のアプリ専用パスを使用し、呼び出し元から任意のURLやシェルコマンドを受け付けません。
更新処理を終了できなかった場合に備え、開始時に実行プロセスを確認して古いロックを解除します。
電源断やストレージ故障の全条件は未検証です。更新中の電源断を避けてください。
BonDriver等がネイティブ配信へ直接接続している利用状況はMirakurunのAPIに現れないため、更新前に終了してください。

## 記録とライセンス

Android側（root専用）：

- `/data/local/tcl-mirakurun/core-update.log`
- `/data/local/tcl-mirakurun/core-update-backup/{config,db}`

chroot環境内：

- `/opt/mirakurun/package-lock.json`：取得した依存関係と整合性
- `/opt/mirakurun/tcl-update.json`：更新版、integrity、日時
- `/opt/mirakurun/tcl-dependency-licenses.json`：依存パッケージの版・ライセンス
- `node_modules/**/LICENSE*`：各パッケージに同梱されるライセンス

## 検証

- TV実機: Mirakurun 4.1.5→4.2.1の取得・起動・地デジHTTP TS配信。
- 手動で4.1.5へ戻し、本体の版とAPI起動を確認。
- 視聴ストリームを開いた状態で更新を試行し、拒否され、サーバーPID・版が変わらないことを確認。
- テスト用の起動確認を意図的に失敗させ、4.1.5と設定・DBへの自動復元を確認。失敗注入コードは配布APKに含めません。
- Nodeテスト13件: 不正な版・別メジャー・パッケージ名/ライセンス/配布元/integrity不一致・Node要件・旧版・容量不足・切り替え・復元・rename失敗・未検証候補の拒否。
- Javaの版比較8件、既存のネットワーク修正と更新候補への適用、無関係なパスの拒否を確認。

```sh
node tests/test-core-update.cjs
node tests/test-network-runtime.cjs
```

今後の上流4.xすべての互換性を保証するものではありません。対応条件に合わない更新は中止して旧本体を起動します。
BS/CSの実受信はアンテナがないため未検証です。
