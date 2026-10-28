# 運用・復元

通常の停止はアプリから実行してください。自動起動フラグの削除、Mirakurun停止、
通信維持サービス停止、TCL常駐許可・電源設定の復元を行います。

ADBでの緊急停止（root）:

```sh
su -c 'rm -f /data/local/tcl-mirakurun/enabled; sh /data/local/tcl-mirakurun/control.sh stop'
```

これはサーバーだけを停止します。アプリが変更した常駐許可・電源設定の復元は
TVアプリの停止ボタンから行ってください。

## 完全撤去

1. アプリの停止ボタンで停止します。
2. `/data/adb/service.d/99-tcl-mirakurun.sh` を削除します。
3. `/data/local/tcl-mirakurun/rootfs` 内の `run/tcl/tsd_token`、`proc`、`dev` の
   マウントを解除します。動作中のプロセスがある場合は先に停止してください。
4. `/data/local/tcl-mirakurun` を削除し、APKをアンインストールします。

既存のBonDriver環境を利用した場合、ネイティブ配信環境は削除しないでください。
このアプリが初めてネイティブ配信を導入した場合は `tuner-owned` が保存されます。
同梱 `restore.sh` で `/data/adb/tcl-mirakurun-backup` のバックアップを復元してから
TVを再起動すると、導入前のチューナー起動設定へ戻せます。
他の配信アプリを後から導入した場合は共有ファイルの利用状況を先に確認してください。

## 更新

署名が同じAPKを上書きインストールできます。セットアップボタンは局一覧と
ブリッジ・起動スクリプトを更新します。既存のLinux実行環境は再展開しません。
Mirakurun/Node.js本体の更新は現版では自動化していません。
実行環境を変更する際は、停止・バックアップ・マウント解除後に再構築してください。
