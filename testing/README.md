# Bot tests

End-to-end tests of the author: headless bots (`bot/QuestBot.java`, built on MCProtocolLib) join a throwaway Paper
instance, place machines, click through the screens and compare inventories and ledgers.

They drive a test harness that is not part of this repository (`ptest.sh`, which starts and stops the instance, sends
console commands and talks to the bots). The scripts look for it in `../../PostMarkt/testing` or in `$PTEST_DIR`.
Without it only the two Python scripts run on their own:

- `gameport-test.py <port> <sha1>`: the pack delivery on the game port from outside the JVM (GET, HEAD, 404, 405,
  status ping, legacy ping, garbage, a silent client).
- `pack-slowreader-test.py <port> <casino_sha1> <music_sha1>`: the download pools and deadlines against clients that
  read too slowly.

The unit tests (`./gradlew test`) need none of this.
