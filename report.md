# M1 報告

## 仮定(冒頭に列挙)

1. 足場の package / namespace / applicationId は `dev.sunfaded.schedule_manager` だった。指定の `io.github.sh_sunfaded.schedulemanager` に改め、ソースのディレクトリも移した。足場のサンプルテスト(`ExampleUnitTest` / `ExampleInstrumentedTest`)は旧 package 前提のため外した。M1 では instrumented テストを置かないので `androidTestImplementation` 系の依存も外した。
2. 時刻列は epoch ミリ秒(UTC)で持つ。入力・表示の解釈だけ `Asia/Tokyo` 固定(`JstTime`)。`due_at` は保存時に分へ切り捨てる。
3. 一覧の並びの細目(SPEC に無いので決めた):
   - 未完了は `due_at` 昇順、期限なしは末尾、同順位は `created_at` 昇順、さらに `id` 昇順。
   - 完了は `done_at` の新しい順。
4. 完了の取り消し(完了欄のチェックを外すと `active` に戻り `done_at` を消す)を許した。状態は `active` / `done` の2つのまま。
5. 日付だけ選んだ場合の既定の時刻は 09:00。時刻ピッカーで分まで変えられる。
6. `title` は前後の空白を除いて必須。`note` は空白のみなら `null`。
7. DAO の一覧取得に並びは持たせない(`ORDER BY id` のみ)。並びの正本は純関数 `buildSections`。
8. ライブラリ版はこの環境の Gradle キャッシュにあるものを使った(Room 2.8.5 / KSP 2.3.14 / Robolectric 4.18.1)。lint が「新版あり」を警告している依存があるが、更新は M1 の範囲外とした。
9. Room の `CRUD` の単体テストは、JVM で動く Robolectric + in-memory Room で行う(`testDebugUnitTest` だけで完結させるため。instrumented は使わない)。
10. `data_extraction_rules.xml` / `backup_rules.xml` は足場のまま残した(中身は雛形コメントのみ)。`allowBackup=false` を優先する。
11. CI は `.github/workflows/ci.yml`(`./gradlew lint testDebugUnitTest`)のみ。簡体字・機密語の検査(SPEC §7)は検査語の定義が無いので M1 では載せていない。
12. git のコミットはしていない(`repo` にコミットがまだ無く、指示も無いため)。

## 実施日

2026-11-27

## 完了条件の照合

1. `./gradlew clean assembleDebug lint testDebugUnitTest` → `BUILD SUCCESSFUL`、終了コード 0(最終実行 9s)。lint は 13 件、すべて警告でエラー 0(下記「申し送り」参照)。
2. `Room` の `CRUD` と一覧の並びの単体テスト → 31 件、失敗 0・エラー 0・スキップ 0(内訳は次節)。
3. `UI` が SPEC §3 を満たす → 実装した(下記)。**実機・エミュレータでの操作確認はしていない**。確認できたのはコンパイルと lint までで、`UI` のテストも無い。
   - 追加: `title` 必須、`note` 任意、`due_at` 任意(日付・時刻のピッカー、「期限を消す」)。
   - 一覧: 未完了が上・期限の近い順。完了は「完了(N)」の見出しで折り畳み(既定は閉じる)。
   - 編集: 行をタップ。完了: 行のチェック。
   - 削除: 行の「削除」→ 確認ダイアログ。
4. `reminder` 表は作成済みで、`UI` からは触っていない。`ReminderDao` はテスト用に insert と取得だけを持つ。
5. 本書。

### 検査ポイント

- 並び順の実装: `domain/TaskOrdering.kt` の `buildSections(List<TaskEntity>): TaskSections`。副作用の無い純関数で、入力の並びに依存しない(テスト済み)。
- schema エクスポート: `app/schemas/io.github.sh_sunfaded.schedulemanager.data.AppDatabase/1.json`(`task` / `reminder` の2表。`reminder.task_id` は `ON DELETE CASCADE`)。`room { schemaDirectory(...) }` と `exportSchema = true` で出力する。
- application id と `allowBackup`: マージ後マニフェストで `package="io.github.sh_sunfaded.schedulemanager"`、`android:allowBackup="false"` を確認した。
- 通知・発火: 入れていない。`AlarmManager` / `WorkManager` / `POST_NOTIFICATIONS` には触れていない。

## テスト数

総数 31(`testDebugUnitTest`、全件成功)。

| クラス | 件数 | 内容 |
|---|---|---|
| `TaskOrderingTest` | 8 | 空入力、期限の近い順、期限なしは末尾、同順位の決定性、完了の分離、完了の並び、入力順に依存しない、入力を破壊しない |
| `TaskDaoTest` | 9 | insert→取得、任意列が null、update、delete は対象行のみ、存在しない id、observeAll、status の保存形式(`active` / `done`)、reminder の insert と取得、task 削除で reminder が消える(他 task の reminder は残る) |
| `TaskRepositoryTest` | 10 | 追加(title の trim と時刻)、空 title の拒否、空 note は null、`due_at` の分切り捨て、編集(`updated_at` のみ更新)、完了(`done_at` 記録)、完了の取り消し、削除で reminder も消える、存在しない id は何もしない、DB から読んだ一覧の並び(未完了が上・期限順・完了は別) |
| `JstTimeTest` | 4 | zone が `Asia/Tokyo`、端末の既定 TZ が違っても JST 表示、入力を JST で解釈、分への切り捨て |

## 仮定

冒頭の 1〜12 を参照。

## 申し送り

- `UI` は未操作。M1 の検収前に、実機で「追加→期限設定→完了→折り畳みを開く→取り消し→削除(確認)」を一度通すのを勧める。
- lint の警告 13 件(エラー 0)。内訳は、依存・Gradle の更新通知 4 件(AGP 付属の Gradle、`compose-bom`、Compose の Kotlin プラグイン、`kotlinx-coroutines-test`)、足場の未使用色リソース 7 件、足場の `RedundantLabel` 1 件(マニフェスト)、足場の `ObsoleteSdkInt` 1 件(`Theme.kt`)。M1 では直していない。
- M2 向け: `reminder` は `fired_at` で発火済みを記録する(SPEC §5)。`TaskRepository.delete` と `edit` は、M2 で発火予約の追随(取消・再予約)を足す場所。
- M2 向け: 通知タップで該当 `todo` を開くには、`MainActivity` に task id を受ける経路が要る(現状は無い)。
- CI の簡体字・機密語の検査は未導入。検査語の定義が決まったら足す。
- `.idea/`、`local.properties` は `.gitignore` の既存設定に従う(秘密値は `repo` に置いていない)。

---

# M2 報告

## 仮定(冒頭に列挙)

1. 通知の文言は指示の仮定どおり、期限「{title}の期限です(HH:MM)。」、リマインダ「{title}のリマインダです(HH:MM)。」。括弧は半角 `()` とした(全角にする場合は `NotificationText.kt` と golden の2か所だけを直す)。`title` の前後の空白は除き、改行・連続空白は1つの空白にして1行に保つ。HH:MM は JST の発火時刻。承認は検収で受ける。
2. 発火の対象は active の `task` だけ。done の `task` の期限・リマインダは発火しない(完了の取り消しで active に戻れば、未来の分は復活する)。
3. SPEC §5 の `task` には発火済みの記録が無いので、期限通知の「1回だけ」は「過ぎたものは発火しない(now 以前は除外)」で保つ。リマインダは `reminder.fired_at` で記録する。「過ぎた」は `at <= now` とし、`at > now` だけを次回候補にする。
4. 予約は常に1本(次回発火だけ)。発火時は、その時刻ちょうどの全件(期限が先、リマインダは id 昇順)を通知し、リマインダの `fired_at` を記録して、次回を再予約する。時刻はすべて分に揃えて保存するので、同時刻の判定は等号で行う。
5. 再起動後・アプリ更新後に過ぎてしまったものは、遡って通知しない(§4「過ぎたものは発火しない」の解釈)。
6. リマインダを付けられるのは「保存済みの `todo` の編集ダイアログ」から。追加ダイアログでは付けられない(案内の1行を出す)。リマインダは追加と削除のみで、時刻の変更は削除→追加。現在以前の時刻は受け付けない(その旨を1行で出す)。
7. `SCHEDULE_EXACT_ALARM` は API 33 以降で既定が拒否のことがある。許可が無い間は `setAndAllowWhileIdle`(非 exact)で代替し、一覧の上に「許可の設定を開く」の案内を1行出す。許可の変更を受けて予約を復帰する。これは「`UI` への権限の要求の追加」の一部として入れた。
8. 通知タップは `MainActivity`(`singleTop`)へ task id を渡し、該当 `todo` の編集ダイアログを開く。完了済みでも開ける。
9. 通知チャネルは1本(id は `todo`、重要度は標準=標準の音)。通知は `contentText` のみで、タイトル・装飾・絵文字なし。小さいアイコンは Android 標準の `ic_popup_reminder`。
10. `POST_NOTIFICATIONS` は API 33 以上で未許可なら、起動のたびに要求する(OS 側が表示を止めることがある)。拒否されても一覧等は動き、通知の表示だけを行わない(`fired_at` は記録する)。
11. 復帰の契機は `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / exact alarm 許可の変更、`WorkManager` の日次、アプリ起動時。端末の時刻変更(`TIME_SET`)では復帰しない(上記の日次と起動時で拾う)。
12. マージ後 manifest には、指定の4つに加えて `WorkManager` が足す権限(`ACCESS_NETWORK_STATE`、`FOREGROUND_SERVICE` など)が入る。

## 実施日

2026-11-27

## 完了条件の照合

1. `./gradlew clean assembleDebug lint testDebugUnitTest` → `BUILD SUCCESSFUL`、終了コード 0。lint は 16 件、すべて警告でエラー 0。
2. golden との差分 0。文言生成は純関数 `NotificationText`(`domain/NotificationText.kt`)。典型例は `app/src/test/resources/golden/notification_text.golden`(7件)で、`NotificationTextGoldenTest` が全件の差分 0 を検査する。
3. 本書。

### 実装したもの(SPEC §4 との対応)

- 次回発火の計算(純関数): `domain/NextFire.kt` の `fireCandidates` / `nextFireTime` / `firesAt`。
- 予約と発火: `notify/Rescheduler.kt`(`Rescheduler`: 全件から次回を求めて予約し直す。`FireHandler`: 発火・`fired_at`・再予約)、`AndroidAlarmPort`(`AlarmManager` の exact)。
- 受信: `AlarmReceiver`(発火)、`RescheduleReceiver`(`BOOT_COMPLETED` と `MY_PACKAGE_REPLACED` と許可変更)、`DailyRescheduleWorker`(日次の保険)。
- 追随: `TaskRepository` の追加・編集・完了・削除・リマインダの追加と削除のたびに `onChanged` で再予約する。
- `UI`: 編集ダイアログにリマインダ一覧・追加・削除。通知許可の要求。exact alarm の案内。通知タップで該当 `todo` を開く。
- manifest: `SCHEDULE_EXACT_ALARM`、`RECEIVE_BOOT_COMPLETED`、`WAKE_LOCK`、`POST_NOTIFICATIONS`。`allowBackup="false"`、application id は M1 のまま。Room の schema は変更なし(version 1 のまま)。

## テスト数

総数 73(M1 の 31 から +42)。全件成功、失敗 0・エラー 0・スキップ 0。

| クラス | M1 | M2 | 内容 |
|---|---|---|---|
| `TaskOrderingTest` | 8 | 8 | 変更なし |
| `TaskDaoTest` | 9 | 9 | 変更なし |
| `TaskRepositoryTest` | 10 | 10 | コンストラクタの変更に追随しただけ |
| `JstTimeTest` | 4 | 4 | 変更なし |
| `NextFireTest` | - | 17 | 発火計算の行列(下記) |
| `NotificationTextGoldenTest` | - | 4 | golden 差分 0、1行・句点終わり、JST 固定表示、件数 |
| `NotificationPermissionTest` | - | 3 | API 33 以上かつ未許可でのみ要求、許可済みは要求しない、API 32 以下は要求しない |
| `NotifyFlowTest` | - | 18 | 予約の追随 13 件と発火処理 5 件(下記) |

`NextFireTest` の行列(17): 空、期限も reminder も無い、期限(未来)、リマインダ(未来)、`task` をまたぐ最近接、1つの `todo` に複数のリマインダ、過去の期限、過去のリマインダ、now ちょうど(発火しない)、過去を飛ばして未来を選ぶ、発火済みの除外、done の除外、存在しない `task` の reminder、日をまたぐ(23:59 の次の 00:01)、翌日のリマインダ、同時刻の全件と並び(`firesAt`)、`firesAt` が発火済み・done を除く。

`NotifyFlowTest`(実 Room + 偽の `AlarmPort` / `NotifierPort`)の追随: 期限付き追加で予約、期限なしは予約なし、期限の編集で移動、期限を消すと取消、削除で取消、完了で取消・取り消しで復活、リマインダ追加で最近接が勝つ、リマインダ削除で次点に戻る、`todo` 削除でリマインダごと取消、過去のリマインダは拒否、存在しない `todo` は拒否、分への切り捨て、DB から予約を復元。発火処理: 期限の文言と次回の再予約、リマインダの `fired_at` 記録と二重発火なし、同時刻は期限が先、削除済み・完了済みは古い予約で通知しない、復元時に過去分は発火しない。

## 仮定

冒頭の 1〜12 を参照。

## 申し送り

- **実機・エミュレータでは未確認**。確認できたのは、純関数と、実 Room + 偽の窓口によるロジック、コンパイルと lint まで。次は JVM のテストが届かない実体で、本人検収で見る必要がある: `AlarmManager` への実際の予約と発火、`AlarmReceiver` / `RescheduleReceiver` の受信、`WorkManager` の日次、通知の表示と標準の音、通知タップで該当 `todo` が開くこと、権限ダイアログ。
- 本人検収で通らないときの観察のしどころ: (a) exact alarm の許可が無いと非 exact になり、時刻がずれうる(一覧の上に案内が出る)。(b) 通知の許可を拒否すると通知は出ない。(c) メーカー独自の省電力でアプリが止められると、発火が遅れうる。
- lint の警告 16 件(エラー 0)。M1 の 13 件に加え、M2 で `InlinedApi`(`POST_NOTIFICATIONS` の定数。定数はコンパイル時に埋め込まれるので実害なし)2 件と `UseKtx` 1 件が増えた。
- 将来(SPEC §9): 期限切れの扱い、繰り返し、夜間の音なし設定は未実装のまま。
