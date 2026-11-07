# OpenGotha corpus

Real tournament files, each with the reference [OpenGothaCorpusTest] checks pairgoth against:
`<name>.og.txt.gz` — the per-pair cost breakdown of every round, then, after a `Pairings` line, the
pairing OpenGotha itself produces for it.

References are generated once, by the instrumented build of the OpenGotha version that saved the
file — `instrumented-3.52` branch of the opengotha repository. A file saved by another version needs
its own build: the pairing algorithm changes between versions, and the test refuses to compare a
file with a reference from a different one.

    ~/git/opengotha/instrumented-3.52/build.sh <file>.xml <file>.og.txt <adjustments...>
    gzip -9 <file>.og.txt

Whatever the operator did by hand — a pairing made outside the algorithm, a bye player chosen rather
than computed — is passed to the generator as `--handmade` / `--bye` flags *and* declared in the
test's `adjustments()`, or the two sides pair different fields. The exact command used for each file
is in the comment above its entry there.

| file          | tournament                      | opengotha |
|---------------|---------------------------------|-----------|
| open-2026.xml | Open 2026 Lille, 41 players, 7R | 3.52.03   |
