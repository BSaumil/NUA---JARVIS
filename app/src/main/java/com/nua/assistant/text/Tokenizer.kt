package com.nua.assistant.text

/**
 * The one tokenizer every lexical-overlap search in NUA uses: [SecondBrainSearch],
 * [FactRelevance], and the command palette's memory ranking.
 *
 * Before this, all three defined their own copy. Two ([SecondBrainSearch] and
 * [FactRelevance]) were byte-identical (`\W+`, drop tokens of length ≤ 2). The third
 * (the command palette's) used a different regex (`[^a-z0-9]+`, no length filter),
 * so the same words tokenized differently depending on which search box you typed them
 * into — `"AI"` matched in the palette and silently never in Second Brain. Found in an
 * architecture review, called out explicitly in the accompanying engineering directive:
 * "do not create separate tokenisation rules for different sources."
 *
 * Returns a [Set] rather than a [List] — every call site immediately used the result for
 * overlap counting (`count { it in other }`), where a duplicate token should not count
 * twice. [com.nua.assistant.ui.palette.CommandPalette] fixed exactly that inflation bug
 * for its own tokenizer already; returning a set here keeps it fixed by construction
 * rather than by every caller remembering to `.toSet()`.
 */
fun tokenize(text: String): Set<String> =
    text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 }.toSet()
