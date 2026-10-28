# TCLTV-Mirakurun

対応するTCL G06 Android TVの内蔵地デジ2チューナーを、TV本体で動く
Mirakurunから利用するセットアップアプリです。PCにMirakurunを常駐させる必要はありません。

**機種・ファームウェア限定の実験版です。** Android TV全般に対応するアプリではありません。
root化・Magisk導入は事前に必要です。本アプリはboot・vbmeta・vendorパーティションを
書き換えません。対応ライブラリーのSHA-256が一致しなければ導入を中止します。

## ダウンロード・導入

[ReleasesからTCLTV-Mirakurun.apkをダウンロード](https://github.com/Long0823-1/TCLTV-Mirakurun/releases/latest)

1. 対応TVをLANへ接続し、TV本体の地デジチャンネルスキャンを完了します。
2. APKをTVへインストールします。ADBの場合は `adb install -r TCLTV-Mirakurun.apk`。
3. TVのアプリ一覧から **TCL TV Mirakurun** を開き、**1. セットアップ / 局一覧を更新** を選択します。
4. Magiskのroot要求を許可します。実行環境を展開するので数分待ちます。
5. チューナー配信を初めて導入した場合は、TVを再起動します。
6. アプリで **2. 起動・自動起動を有効化** を選択します。
7. 同じLANのブラウザーで、画面に表示された `http://TVのIP:40772/` を開きます。

APKにはARMv7 Linux実行環境、Node.js、Python、Mirakurun 4.1.5を同梱します。
セットアップ時にnpm等の追加ダウンロードは行いません。
TV用のTCLTV-Bondriver APKを別途入れる必要はありません。
既にネイティブ配信環境が動作している場合は、それを検出して利用します。

## 対応条件

- TCL G06 / MT5867、日本向けAndroid 11、32bit ARM。TCL 32S5400系の検証ファームウェア。
- Magisk root、`su`、Android標準のchroot / mount。
- 地デジの受信環境とTV本体のスキャン済み局情報。
- `/data` の空き領域1GB以上を推奨。インストール用コピーと展開済み実行環境を保持します。
- PC・視聴端末とTVが同じLANにあること。有線LAN・Wi-Fiの両方で利用できます。

TVに登録された地デジ多重ごとに設定を作成します。地域固定のプリセットは使いません。
サブチャンネル・番組情報はMirakurunが受信ストリームから検出します。
BS・CS・4Kは本アプリの対象外です。

## 操作と構成

- **セットアップ / 局一覧を更新**: 同梱実行環境の初回展開、局設定、起動フックの配置。
  実行中のMirakurunは停止します。完了後に「起動」を選択してください。
- **起動**: Mirakurunと通信維持サービスを起動し、次回起動時の自動起動を有効化します。
- **停止**: Mirakurunを止め、自動起動・このアプリの通信維持を無効化します。
  このアプリが追加したTCL常駐許可と電源設定は停止時に復元します。
- **状態 / ログ**: 起動状態、チューナー状態、ネットワーク情報、直近のサーバーログを表示します。

実行環境: `/data/local/tcl-mirakurun/rootfs`。
設定: 同ディレクトリ内の `/usr/local/etc/mirakurun/{server,tuners,channels}.yml`。
サービス・番組DB: `/usr/local/var/db/mirakurun/`。
ログ: `/data/local/tcl-mirakurun/server.log`。
Magiskフック: `/data/adb/service.d/99-tcl-mirakurun.sh`。

既存のTV配信プロトコルをPython製の小さなブリッジでMirakurunのチューナーコマンドへ
接続します。TV内の通常の復号処理を使用し、Mirakurun側の追加デコーダーは設定しません。
機器固有の接続キーはTV内だけに保存し、APKやリポジトリには含めません。

## 注意・制限

- 各物理チューナーのTS接続は1つです。BonDriver・既存IPTVと同じチューナーを同時利用しないでください。
- 消灯中の通信維持は通常待機より電力を使います。完全な電源断では動作しません。
- 初回のサービス検出・番組表収集には数分以上かかります。受信できない登録局は取得に失敗します。
- LAN内向けです。40772番ポートをインターネットへ直接公開しないでください。
- Android標準のDHCP設定を利用します。固定IPやネットワーク全体の探索は行いません。
  直結LANの設定は別途必要です。既存環境で設定済みのアドレスはそのまま利用できます。
- 起動後にWi-Fi等のIPが付く場合にも接続できるようIPv4の待受け処理を調整しています。
  MirakurunのLAN内IP・Origin制限は維持しています。
- APKのアンインストールだけではroot領域の実行環境・起動フックは削除されません。
  先にアプリで停止してください。完全撤去は [運用手順](docs/OPERATIONS.md) を参照してください。
- 初回導入と既存環境への追加は分岐します。実機で確認した範囲は [検証記録](docs/VALIDATION.md) を参照してください。

## ビルド

Android SDK Platform 35 / Build-Tools 36.0.0、JDK、NDK r29、PowerShellを使用します。
実行環境の作成にはDockerとARMv7のbinfmt/QEMUを使います。

```sh
./scripts/build-runtime.sh
```

```powershell
./scripts/build-native.ps1 -NdkRoot <Android-NDK-folder>
./android/build.ps1 -SdkRoot <Android-SDK-folder> -JdkRoot <JDK-folder>
```

出力は `android/build/TCLTV-Mirakurun.apk`。
署名鍵は `.local/` に生成します。更新版を同じアプリとして導入するには鍵を保持してください。
署名鍵・パスワードはコミットしないでください。
GitHub Actionsの手動実行でもビルドできます。Actionsで生成するAPKは毎回一時的な
署名鍵を使う検証用です。公開リリースAPKとの上書き互換性はありません。
一部環境でのAAPT2/zipalignのZIP終端書込みクラッシュに限り、
`-RecoverAaptZip` を指定できます。修復後はAPK署名検証を行います。

実行環境のnpm依存は同梱のpackage-lockに記録します。Alpineパッケージの更新により
ビルド結果は変わるため、各リリースの実データはSHA-256で識別します。

## 関連プロジェクト・ライセンス

[TCLTV-Bondriver](https://github.com/Long0823-1/TCLTV-Bondriver) のMITライセンスの
ネイティブ配信実装を利用しています。本リポジトリはMirakurunの導入・運用に特化しています。
Mirakurun本体は [Chinachu/Mirakurun](https://github.com/Chinachu/Mirakurun) の
Apache-2.0ライセンスです。自作部分はMIT。
詳細は [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) を参照してください。

## Support / 開発支援

開発・保守活動をKo-fiで支援できます。

[Ko-fiで支援する](https://ko-fi.com/long0823)
