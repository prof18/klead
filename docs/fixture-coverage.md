# Fixture Coverage

Last updated: 2026-06-15

Upstream Defuddle SHA: `9db72600a0cfc568eafb31e85ef68ba16add072e`

## Current Gates

- Total upstream HTML fixtures: 190
- Strict metadata fixtures: 3
- Relaxed removal fixtures: 2
- MVP relaxed allowlist: 16
- Full diagnostic fixtures: 190
- Unknown diagnostic failures: 0
- Strict whole-corpus Markdown parity: deferred as a documented known difference
- FeedFlow reader-dump regression fixtures: 16

## Active Strict Fixtures

- `metadata--h1-sibling-byline`
- `metadata--placeholder-values`
- `metadata--rel-author-in-bio-container`

## Active Relaxed Fixtures

- MVP allowlist from `docs/09-fixture-coverage-expansion.md`
- `hidden--nodes`
- `hidden--visibility`

## FeedFlow Reader-Dump Regressions

- `general--www.ilpost.it-2026-06-15-ufc-casa-bianca`: verifies that broad body selection, breadcrumbs, and bottom recommendation sections do not leak into reader Markdown.
- `general--www.ilpost.it-2026-06-15-lisbona-funicolare-gloria-ferme`: verifies that WordPress-style captioned image wrappers keep body images in Markdown.
- `general--www.ilpost.it-2026-06-15-cooling-break-mondiali-calcio-pause`: verifies that embedded audio player placeholders do not leak into reader Markdown.
- `general--www.ilpost.it-2026-06-15-marius-borg-hoiby-figlio-principessa-ereditaria-norvegia-condannato-stupro`: verifies that trailing article tag lists do not leak into reader Markdown.
- `general--www.ilpost.it-2026-06-15-sorelle-sparite-minturno`: verifies that emphasized link text with boundary whitespace produces tight Markdown delimiters.
- `general--www.macrumors.com-2026-06-15-uk-ban-social-media-under-16s`: verifies that broad main selection, article tags, comment counts, popular-story modules, and comment modules do not leak into reader Markdown.
- `general--www.androidcentral.com-phones-honor-phones-honor-magic-v6-review`: verifies that video carousels, affiliate/deal widgets, gallery controls, trailing comment prompts, back-to-top controls, and read-more recirculation modules do not leak into reader Markdown, while no-op-spanned specs tables render as Markdown tables.
- `general--www.androidpolice.com-replaced-samsung-home-screen-with-custom-launcher-never-going-back`: verifies that Valnet-style author header/bio chrome, inline newsletter signup widgets, article tag links, and follow controls do not leak into reader Markdown.
- `general--www.axios.com-2026-06-14-anthropic-white-house-mythos-fable`: verifies that timestamp/byline/share/source-preference/read-next chrome and empty list artifacts do not leak into reader Markdown while prose wrappers with utility-class clutter keywords are preserved.
- `general--www.veneziatoday.it-cronaca-contratto-scaduto-sciopero-farmacie-comunali`: verifies that Citynews-style mobile app promos, entry footers, story-card recirculation, most-read sidebars, native footer sections, and Outbrain placeholders do not leak into reader Markdown.
- `general--www.pianetabasket.com-legabasket-serie-a-virtus-bologna-casting-continua-sekulic-profili-panchina-363560`: verifies that body-level site chrome, repeated dates, section navigation, latest-news modules, popular lists, and footer text do not leak when a semantic `role="main"` article container is available.
- `general--www.pianetabasket.com-euroleague-l-anadolu-efes-conferma-l-uscita-rolands-smits-stagioni-363578`: verifies that short semantic-main articles still exclude body-level navigation, author-profile boxes, latest-news modules, popular lists, and footer text.
- `general--m.pianetabasket.com-euroleague-partizan-belgrado-interessato-all-ex-brindisi-venezia-derek-willis-363565`: verifies that mobile-template opening byline/date/read-count metadata does not leak into reader Markdown.
- `general--www.basketuniverso.it-nba-piu-di-una-semplice-lega-un-viaggio-tra-stori`: verifies that WordPress category chips and Author Bio Box latest-posts modules do not leak into reader Markdown.
- `general--www.20percent.berlin-p-500-uber-bvg-nius-raves-podcast`: verifies that Substack captioned images with comma-bearing transformed `srcset` URLs render as Markdown images while Substack discussion/footer chrome is excluded.
- `general--www.20percent.berlin-p-493-easy-burgeramt-appts-gun-raid`: verifies that Substack discussion, comments, top-posts archive, and ready-for-more footer modules do not leak into reader Markdown.

## Known Differences

- Non-empty Kotlin Markdown output is normalized to one final newline. Empty content remains an empty string.
- Math rendering/conversion fidelity remains out of scope; source data is preserved where practical.
- Full exact Markdown parity is not yet expected for the diagnostic corpus.

## Top Classified Gaps

- Full exact Markdown parity across every upstream fixture is staged behind classified known differences.
- Production network-backed site extractor coverage beyond the injected-client hook remains staged.
- Math rendering/conversion fidelity is excluded by scope.
