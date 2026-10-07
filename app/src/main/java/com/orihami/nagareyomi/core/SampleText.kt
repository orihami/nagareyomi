package com.orihami.nagareyomi.core

/** The document shown on first launch: a short tour that is also a realistic study text. */
object SampleText {
    const val TITLE = "はじめに：ながれよみの使い方"

    val TEXT = """
        # はじめに
        ながれよみは、文章を短いまとまりごとに同じ場所へ表示するリーダーです。視線を動かさずに、長い資料を少しずつ読み進められます。
        画面の中央をタップすると、流れが止まります。止まっているあいだは、前後の文が下に表示されます。
        意味が分からなくなったら、上の「本文」に切り替えてください。普通の文章として確認でき、読みたい文をタップすれば、そこから続きを流せます。

        # 電磁波とは
        電磁波は、電場と磁場が相互に変化しながら空間を伝わる現象である。真空中での伝搬速度は約3.0×10^8 m/sであり、これを光速と呼ぶ。
        マクスウェル方程式によれば、時間変化する磁場は電場を生み、時間変化する電場は磁場を生む。この連鎖が、波として遠くまで伝わっていく。
        波長λと周波数fのあいだには、次の関係がある。
        c = fλ
        例えば、周波数2.4 GHzの電波の波長は、約12.5 cmになる。数式のように一度止まって見るべき部分では、ながれよみは自動で止まります。

        # 回路の例
        抵抗Rに電流Iが流れるとき、両端の電圧VはV = IRで表される。これをオームの法則という。
        In English text, words are shown two or three at a time, so that each phrase can be read at a glance.
    """.trimIndent()
}
