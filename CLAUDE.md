# schedule_manager(CLAUDE.md)

本人用の「ただの `todo` アプリ」。v0.0=`todo`(一覧・登録・編集・完了・削除・期限)、v0.1=+通知(期限通知・任意時刻リマインダ)。規範は docs/SPEC.md(todoアプリ 仕様書 v0.1)。それ以上の機能(睡眠帯・中継・`MCP`)は範囲外。

## 構成

- app/: `Android` アプリ(`Kotlin` + Jetpack `Compose` + `Room`)。他モジュールは無い。
- docs/SPEC.md: 規範。文言は変えない。

## 規約(破ったものは検収で差し戻す)

- app は `Kotlin`。公式スタイルガイドと lint(gradle タスク)に従う。
- 秘密値は `repo` に置かない。署名鍵は local.properties 経由で読む。
- 文言: ドキュメント・コメント・コミット文言で簡体字(中国語簡体字)の混入を 0 にする。変換ミスの家規。
- application id は io.github.sh_sunfaded.schedulemanager。`allowBackup` は `false`。タイムゾーンは `Asia/Tokyo` 固定。
- `UI` は素朴に作る。装飾を作り込まない。

## 作業の原則

- マイルストーンごとに完了条件(機械可読)を全て満たしてから止まる。
- 仮定を設けたら作業冒頭の報告に列挙する(後で検収で照合する)。
- 作業は report.md(下の鋳型)を必ず残す。

## report.md の鋳型

```
# M<N> 報告
- 実施日:
- 完了条件の照合:(条件ごとに実行結果を貼る)
- テスト数:(総数と内訳)
- 仮定:(列挙。無ければ「なし」)
- 申し送り:(次マイルストーン以降へ伝えること)
```
