# CLAUDE.md — ながれよみ（nagareyomi）の開発ルール

このリポジトリは、文章を短い「意味のまとまり」ごとに同じ場所へ次々表示するRSVP方式の文章リーダー**「ながれよみ」**のAndroidアプリです。
テンプレート `orihami/Claudee` から作成した（2026-10-07）。開発ルール・検証/納品フローはテンプレートと同じです。
Claude(このリポジトリで作業するAIエージェント)は、以降のすべての開発依頼で、このファイルに書かれた手順を標準として従ってください。

### アプリの方針（ユーザーの要望、2026-10-07）

- 目的は速読の競争ではなく、大学資料・PDF・技術文書を「とりあえず流して読んでみるか」と思える程度に読む負担を減らすこと
- 機械的な1語ずつではなく、意味のまとまり（「電磁波は」「電場と磁場が」…）で表示し、句読点・文末・段落・見出しで自然な間を置く
- 「今どこか」「文の区切り」「少し戻る」「普通の文章で確認→続きから再開」を見失わせない。流し読みと通常読書の往復を重視
- 数式・記号・単位・英数字を極端に読みづらくしない。数式やコードのような部分は無理に流さず、止まって見せる
- 紙・スクリーンショット・PDFからの文章も読めること
- UIは速読トレーニング風ではなく、落ち着いて長時間使えるReader。設定を毎回いじらなくても使えること

**言語: ユーザーへの返答・報告は常に日本語で行うこと。**(ユーザーの指示、2026-09-28)

**リンク: リリースや成果物を報告するときは、タップしてすぐ開けるURLを必ず載せること。** 特にAPKは、リリースページのURLに加えて `app-release.apk` の直接ダウンロードURL（`https://github.com/<owner>/<repo>/releases/download/vX.Y.Z/app-release.apk`）を書く。（ユーザーの指示、2026-10-07）

**時刻: 待ち時間や予定を伝えるときは、必ず日本時間の時刻を併記すること。** 例:「約20分後（日本時間 17:56ごろ）に確認します」。`send_later` などで確認を予約したら、その予定時刻（日本時間）もユーザーに伝える。（ユーザーの指示、2026-09-28）

## 0. 引き継ぎ（最優先の習慣）

作業場所は **GitHub（クラウドのClaude Code）** と **NAS（自宅のNAS上での作業）** の2つがあり、どちらで作業することもある。**いつ・どこで作業が途切れても、次のセッションがすぐ再開できるように、引き継ぎを常にきれいに保つこと。**（ユーザーの指示、2026-09-28）

- 引き継ぎ先はリポジトリ直下の **`HANDOFF.md`** に一本化する。チャットの会話だけに頼らない（別の場所・別セッションからは見えないため）。
- **作業を始めるとき**: `git pull` で最新にしてから `HANDOFF.md` を読み、書かれている「次にやること」「進行中のこと」から再開する。
- **作業の区切りごと（pushのたび）と、作業を終えるとき**: `HANDOFF.md` を更新して commit・push する。未コミット・未pushの変更を作業場所に残さない。
- `HANDOFF.md` に書くこと:
  - 最終更新（日本時間）と作業場所（GitHub / NAS）
  - 現在の状態（最新バージョン、公開済みリリース、CIの結果）
  - 進行中のこと（実行中のCI、予約した確認の日本時間など）
  - 次にやること（優先順）
  - 未検証のこと（実機未確認など）
  - ユーザーに頼んでいること・ユーザーの判断待ち
- NASでpushできなかった場合は、その理由と未pushの内容を `HANDOFF.md` に書き、次にGitHub側で作業するときに最初に反映する。
- `HANDOFF.md` に秘密情報（APIキー、パスワード、署名鍵、NASのIPアドレスやホスト名・共有名などの内部情報）は書かない。

## 1. 技術スタック

- 言語: Kotlin
- UI: Jetpack Compose (Material 3)
- ビルド: Gradle (Kotlin DSL, Version Catalog `gradle/libs.versions.toml`)
- 最小/ターゲットSDK: minSdk 24 / targetSdk・compileSdk 34
- JDK: 17 (Temurin)

新しい依存関係を追加するときは `gradle/libs.versions.toml` にバージョンを追加し、各モジュールの `build.gradle.kts` からは `libs.xxx` 経由で参照すること(バージョン直書き禁止)。

## 2. 開発〜納品の標準フロー(必須)

**「実際に検証していない作業を成功したと報告してはいけない。」** これは最優先ルールです。

新しい機能追加・修正を行うときは、必ず次の順序で進めてください:

1. **要件の実装**
   - Composable / ロジックを実装する。
   - UIの状態やロジックは可能な限り純粋なKotlinオブジェクト/クラス(`core/`)に分離し、Androidフレームワークに依存しないユニットテストを書けるようにする。

2. **テストを書く/更新する**
   - ロジックに対する**ユニットテスト** (`app/src/test/...`, JVM上で実行、高速)
   - 画面操作を伴う**Compose UIテスト** (`app/src/androidTest/...`, エミュレータ上で実行)
   - 新機能や修正には、少なくとも1つ以上の対応するテストを追加すること。

3. **ローカルでの静的確認(可能な範囲で)**
   - Kotlinの構文・依存関係の整合性を確認する。
   - このAIエージェントの実行環境にAndroid SDK/ネットワークアクセスがない場合、ローカルでの実ビルドはできない。その場合は次のステップ(GitHub Actions)が唯一の実検証手段になるため、必ず最後まで確認する。

4. **コミット & プッシュ**
   - 変更内容が分かるコミットメッセージを書く。
   - `main` へ直接プッシュするか、PRを作成する(タスクの大きさに応じて判断してよいが、破壊的な変更や大きな変更はPR経由を推奨)。

5. **GitHub Actions (`Android CI`) の結果を必ず確認する**
   - プッシュ後、`gh run watch` 相当の方法で実行中のワークフローを追跡し、**完了するまで待つ**こと。
   - 3つのジョブすべてが成功していることを確認する:
     - `unit-test` (ユニットテスト)
     - `build` (デバッグAPKのビルド)
     - `instrumented-test` (エミュレータ上のUIテスト、スクリーンショット・ログ取得)
   - 失敗した場合は、アップロードされたartifact(テストレポート、ログ、スクリーンショット)とジョブログを取得して原因を調査し、**成功するまで修正を繰り返す**こと。
   - 原因が特定できない、または自動修正できない場合は、ユーザーに「何を試して」「どこまで分かって」「何が不明か」を具体的に報告すること。曖昧に「直しました」と報告しない。

6. **リリース(必要な場合)**
   - **標準経路(タグの手動push不要・追加課金なし)**: `app/build.gradle.kts` の `versionName`(通常 `versionCode` も)を上げて `main` にコミット・プッシュするだけでよい。`Android CI` が成功すると `.github/workflows/auto-release.yml` が自動的に新バージョンを検知し、GitHub Actions自身の `GITHUB_TOKEN` でタグを作成・push → リリースAPKをビルド → GitHub Releasesに公開、までを1本のワークフロー内で完結させる。このAIエージェントは新機能・修正をリリースとして納品したい場合、この方法でバージョンを上げて `main` に反映すればよい。
   - 署名鍵(`RELEASE_KEYSTORE_BASE64` 等のSecrets、4.2節参照)が設定されていない場合、公開されるAPKはデバッグ鍵で署名される(インストール確認用。Playストア配布には使えない)。
   - 公開後は `should_release` の判定( `gh api` やActionsログで確認可)と、実際に `https://github.com/orihami/nagareyomi/releases` にAPKが付いたリリースが現れたことを必ず確認してから「リリース完了」と報告すること。
   - **補足(手動経路、通常は不要)**: `vX.Y.Z` 形式のタグを人間が自分の端末やGitHub上からpushすると、従来通り `.github/workflows/release.yml` が単独でも動作する(`Android CI` を再実行→ビルド→公開)。ただしこのAIエージェントのセッション自身の資格情報では `git push origin vX.Y.Z` のようなタグのpushが権限上拒否される(HTTP 403)ことを確認済み(通常のブランチへのpushは問題なく行える)。そのため上記の自動経路(`auto-release.yml`)を標準とし、`release.yml` へのタグpushは人間が手動でやりたい場合の代替手段として残してある。

7. **ユーザーへの報告**
   - 何を実装したか、どのテストを追加/実行したか、CI結果(成功/失敗)、成果物へのリンクを簡潔に報告する。
   - 「ユニットテスト成功」「エミュレータでのUIテスト成功」「実機での動作確認」は別物として区別して報告する。エミュレータで確認できない事項(バックグラウンド位置情報、メーカー独自の省電力制御、GPS等)は未検証と明記し、必要な場合のみ簡潔な実機確認手順を示す。
   - 自動化できなかった設定(GitHub上での操作、Secretsの追加など)は具体的に、何を・どこで・なぜ必要かを説明する。

## 3. GitHub Actions ワークフロー一覧

| ファイル | 役割 |
|---|---|
| `.github/workflows/android-ci.yml` | `push`(main) / `pull_request` / 手動実行で、ユニットテスト → デバッグビルド → エミュレータUIテストを実行。スクリーンショット・ログ・テスト結果をartifactとして3日間保存(APKは保存しない。4.3節)。他のワークフローから `workflow_call` で再利用可能。 |
| `.github/workflows/auto-release.yml` | **標準のリリース経路。** `Android CI` がmainで成功するたびに発火し、`app/build.gradle.kts` の `versionName` が既存タグと比べて新しければ、`GITHUB_TOKEN` でタグを作成・push → リリースAPKビルド → GitHub Releases公開、までを自動で行う。タグの手動pushもPATも不要。 |
| `.github/workflows/release.yml` | 補足的な手動経路。人間が自分の資格情報で `vX.Y.Z` タグを直接pushした場合に発火し、`android-ci.yml` を再実行して全チェック成功を確認した後、リリースAPKをビルドしてGitHub Releasesに公開。 |
| `.github/workflows/claude-autofix.yml` | `Android CI` がPRで失敗したときに、Claude Code GitHub Actionでログを解析し、修正PRを自動作成する(**オプトイン**、要設定・要課金。4章参照)。 |

### NASへの自動納品

NAS自動納品エージェントはテンプレート `orihami/Claudee` の `nas-delivery/` にある(このリポジトリには含めない)。このアプリのリリースをNASへ納品するには、NAS側の設定にこのリポジトリ(`orihami/nagareyomi`)を加える必要がある(設定方法はテンプレートの `nas-delivery/README.md`)。

## 4. オプション設定(自動化できない/ユーザー操作が必要な項目)

以下はGitHub上の設定画面からユーザー自身が行う必要があります(このAIエージェントのセッションからは権限上/仕様上、直接変更・確認ができません)。

### 4.1 Claude Code PR Auto-fix (テスト失敗時の自動修正PR)

CI失敗時にClaude Codeが自動でログを解析し、修正PRを作成する仕組み(`.github/workflows/claude-autofix.yml`)を用意済みですが、**デフォルトでは無効**です。有効化するとAnthropic APIの従量課金が発生するため、必ず内容を理解した上で以下を設定してください。

1. Anthropic Consoleで課金設定をしたAPIキーを発行する。
2. リポジトリの `Settings → Secrets and variables → Actions → Secrets` に `ANTHROPIC_API_KEY` を追加する。
3. 同じ画面の `Variables` タブで `ENABLE_CLAUDE_AUTOFIX` を `true` に設定する(このスイッチをオンにしない限り、APIキーを追加してもワークフローは動作しません)。
4. 動作条件: 同一リポジトリ内のPRでの `Android CI` 失敗時のみ発火します(フォークからのPRでは動作しません)。
5. コスト目安: 失敗1回あたりの解析・修正はログの量とやり取りの回数に依存します。心配な場合は`claude_args`の`--max-turns`を小さくするか、`ENABLE_CLAUDE_AUTOFIX`を必要な時だけ`true`にしてください。

参考実装: https://github.com/anthropics/claude-code-action/blob/main/examples/ci-failure-auto-fix.yml (このリポジトリの `claude-autofix.yml` はAndroid/Gradle向けに調整したものです)

### 4.2 本番用の署名鍵(GitHub Releases用)

現在の `release.yml` は、署名用Secretsが無い場合は自動的にデバッグ鍵で署名します(動作確認用途では問題ありませんが、Playストア配布などの本番用途には使えません)。本番用の署名付きAPKが必要になったら:

1. ローカルで `keytool -genkeypair -v -keystore release.keystore -alias <alias> -keyalg RSA -keysize 2048 -validity 10000` 等でキーストアを作成する(**このキーストアは絶対にリポジトリにコミットしない**)。
2. `base64 -w0 release.keystore` でBase64エンコードする。
3. リポジトリの `Settings → Secrets and variables → Actions → Secrets` に以下を追加する:
   - `RELEASE_KEYSTORE_BASE64`
   - `RELEASE_KEYSTORE_PASSWORD`
   - `RELEASE_KEY_ALIAS`
   - `RELEASE_KEY_PASSWORD`

### 4.3 GitHub Actions の課金について

このリポジトリは**Private**(2026-09-27にユーザーが変更)。PrivateリポジトリのGitHub-hosted runner利用は、アカウントの月間無料枠(GitHub Freeで2,000分/月)を消費する。支払い方法や使用上限を設定していなければ、枠を超えると実行が止まるだけで課金はされないが、**上限の引き上げや有料プラン化はユーザーの確認なしに行わない**こと。

- 分数節約のため、`android-ci.yml` はMarkdownだけの変更では走らない(`paths-ignore`)。ただしPRの場合はPR全体の差分で判定されるため、コードを含むPRにMarkdownだけのコミットを足しても再実行される。HANDOFF更新はまとめて行う。
- **artifactの保存容量も無料枠は500MB(アカウント全体・月平均)**。超えるとartifactを保存できずCIが失敗する。そのため `android-ci.yml` の artifact は `retention-days: 3` とし、デバッグAPK(約20MB/回)はアップロードしない(2026-09-29、ユーザー承認)。新しいアプリでもこの設定を引き継ぐこと。
- エミュレータを使うUIテストが最も時間を使う。無駄な再実行(同じ失敗の繰り返し)を避け、原因を調べてから再プッシュする。

## 5. アプリのパッケージ構成

```
app/src/main/java/com/orihami/nagareyomi/
├── MainActivity.kt          # エントリーポイント、画面遷移、他アプリからの共有・文字選択(PROCESS_TEXT)の受け取り
├── MainViewModel.kt         # 画面状態・読書位置・再生・読み込み
├── core/                    # Android非依存のロジック(ユニットテスト対象)
│   ├── Chunker.kt           # 文 → 意味のまとまり(ひらがな→漢字境界の文節化+助詞・読点で区切る)
│   ├── SentenceSplitter.kt  # 段落 → 文
│   ├── LineClassifier.kt    # 見出し・数式・コード・箇条書きの行判定
│   ├── TextNormalizer.kt    # PDF/OCRの改行結合・ページ番号除去・全角英数の半角化
│   ├── DocumentParser.kt    # テキスト → ブロック/文/まとまり(元テキストのオフセット付き)
│   ├── Pacing.kt            # 表示時間(字/分、間、再開直後のゆっくり表示)
│   ├── ReaderNavigator.kt   # 前の文/次の文/再開位置
│   ├── Layout.kt            # PDFの行・画像の位置から〔式〕〔図〕の印と切り抜き範囲を作る(LayoutAssembler)
│   ├── SymbolFix.kt         # Word製PDFのSymbolフォント私用文字(U+F0xx)・数式斜体文字を通常の文字へ
│   ├── DocMeta.kt, Document.kt, CharClass.kt, SampleText.kt
├── data/                    # 保存(DocumentStore/SettingsStore、元PDFと切り抜き範囲も保存)、取り込み(Importers: PDF=pdfbox-android, OCR=ML Kit日本語)
│                            #   PdfLayout.kt(位置付きの文字抽出・画像検出)、RegionRenderer.kt(PDFの一部を画像に)
└── ui/                      # 一覧・読み込み・リーダー画面、テーマ、TestTags

app/src/test/.../core/       # ユニットテスト
app/src/androidTest/.../     # ReaderFlowUiTest(画面操作)、ImportersTest(端末上で作ったPDFからの抽出)、
                             # PdfLayoutTest(〔式〕〔図〕と切り抜き)、PdfFigureUiTest(数式・図の画像表示)
```

## 6. 運用上のルール

- このリポジトリは「ながれよみ」専用。別のアプリはテンプレート `orihami/Claudee` から別リポジトリで作る。
- 既存の成功している設定・ワークフローを無断で作り直したり上書きしたりしない。変更は差分として行い、理由と検証結果を報告する。
- 追加料金・従量課金が発生する操作(Anthropic APIキーの設定、有料ランナー、リポジトリのPrivate化によるActions課金など)は、必ず事前にユーザーに確認する。
- **公開リポジトリには秘密や個人環境の情報(NASの内部構成、IPアドレス、ホスト名、共有名、認証情報、鍵など)をコミットしない。** それらは非公開の場所かGitHub Secretsに置く。

## 7. 検証済みの状態

`HANDOFF.md` を参照(最新の状態はそこに一本化する)。

## 8. 次の段階

`HANDOFF.md` の「次にやること」を参照。
