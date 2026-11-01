# Raspberry Pi bediening

Een Java 27/Spring Boot-applicatie met een eenvoudige mobiele HTML-pagina.
Geen database en voorlopig geen authenticatie. Keycloak-integratie volgt later.
Spring Boot 4.1.2 wordt gebruikt met de standaard JUnit 6-testdependencies.

## Vereisten

- Java 27 (op de Pi een JDK/JRE passend bij de architectuur, bijvoorbeeld ARM64).
- Linux met `/bin/sh` en `touch` en `date` in het `PATH`.
- Een geinstalleerde Maven voor het bouwen. Gebruik de JDK en Maven van het systeem.

## Starten

```sh
mvn spring-boot:run
```

Open op je telefoon `http://<ip-adres-van-de-pi>:8080`.
De telefoon moet de Pi kunnen bereiken, bijvoorbeeld via dezelfde wifi.
De server luistert standaard op alle netwerkinterfaces.

Een uitvoerbare jar bouwen en op de Pi starten:

```sh
mvn clean package
java -jar target/pi-frontend-0.0.1-SNAPSHOT.jar
```

Voer de applicatie uit als een gewone gebruiker, niet als root. Bestanden komen
standaard in `./data`, relatief aan de werkmap waarin je de applicatie start.
De map moet vooraf bestaan en schrijfbaar zijn voor die gebruiker.
De applicatie maakt geen map aan. Dit geldt ook voor een map die je via
`PI_COMMANDS_DIRECTORY` instelt. Maak de standaardmap zo nodig zelf aan
voordat je de applicatie gebruikt:

```sh
mkdir -p data
```

Configuratie via omgevingsvariabelen:

```sh
PI_COMMANDS_DIRECTORY=/home/pi/poc-files SERVER_PORT=8080 \
  java -jar target/pi-frontend-0.0.1-SNAPSHOT.jar
```

Met `SERVER_ADDRESS=127.0.0.1` beperk je de server tot de Pi zelf.

## Actie en API

De knop verstuurt `POST /api/commands/touch`. De backend voert alleen de vaste
actie uit via `ProcessBuilder` en `/bin/sh -c`, zonder commando-invoer van de client.
`CommandService.execute(String command)` verzorgt de generieke uitvoering.
De controller bepaalt het vaste commando; toekomstige acties kunnen dezelfde
service gebruiken. Er is geen endpoint voor willekeurige commando's.
De shell voert exact dit commando uit:

```sh
touch $(date "+%F %T")
```

Omdat de command substitution niet gequote is, maakt dit commando twee
bestanden: een met de datum en een met de tijd als naam. Bestaande bestanden
worden bijgewerkt. Datum en tijd worden volledig door het Linux-commando
bepaald, niet door Java.

```sh
curl -X POST http://<ip-adres-van-de-pi>:8080/api/commands/touch \
  -H 'Content-Type: application/json' -d '{}'
```

Een afgerond commando geeft HTTP 200 met de gecombineerde standaardoutput
en foutoutput en de exitcode:

```json
{"output":"","exitCode":0}
```

`touch` geeft bij succes normaal geen output. Ook een niet-nul exitcode wordt
met output teruggegeven; de pagina toont dan dat het commando is mislukt.
De output wordt tijdens uitvoering naar een tijdelijk bestand geschreven
om blokkeren op een volle procespipe te voorkomen. Dit bestand wordt na
afloop verwijderd; de ingestelde werkmap wordt nog steeds niet aangemaakt.
Het proces wordt via try-with-resources gesloten. Bij een time-out of
onderbreking wordt een nog draaiend proces eerst expliciet gestopt, omdat
`Process.close()` op het einde van het proces wacht.

Een startfout, leesfout of time-out geeft HTTP 500 met een Problem Detail; details staan in de
serverlogs. Het commando wordt na maximaal vijf seconden gestopt.
De HTML-pagina toont het resultaat of een foutmelding en blokkeert de knop
tijdens het request. Er is geen CORS-configuratie: pagina en API draaien op
dezelfde origin. Een aparte frontend kan later bewust worden aangesloten.

**Let op:** iedereen die de server kan bereiken, kan deze actie uitvoeren.
Stel poort 8080 niet bloot aan internet en gebruik de POC alleen op een
vertrouwd lokaal netwerk. Er zijn nu geen beveiligingsintegraties of
Keycloak-dependencies toegevoegd.

## Tests

```sh
mvn test
```

De tests gebruiken JUnit 6, Mockito en tijdelijke mappen. Testcontainers is
niet nodig: de applicatie gebruikt geen externe infrastructuur.
