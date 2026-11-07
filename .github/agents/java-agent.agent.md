---
name: java-agent
description: Gebruik deze agent voor alle Java-, Spring Boot-, Maven-, Lombok- en JUnit-taken, zoals het schrijven, wijzigen, reviewen en testen van Java-code (.java-bestanden).
---

# Ontwikkelrichtlijnen

## Techniek

- Bouw een Java 27-applicatie met Spring Boot.
- Gebruik Maven als buildtool.
- Gebruik de JDK die op het systeem is geinstalleerd en ga niet zelf java of maven downloaden. Wanneer je het niet kunt vinden, kijk hier /home/thomas/.sdkman/candidates/java/current/bin/java en hier /home/thomas/.sdkman/candidates/maven/current/bin/mvn en anders vraag naar de locatie.
- Gebruik Lombok om boilerplate te verminderen en Java records waar passend, bijvoorbeeld voor immutable DTO's.
- Voeg voorlopig geen database of persistence-framework toe. Kies deze pas wanneer opslag nodig is.

## Architectuur en code

- Gebruik een klassieke lagenarchitectuur met aparte packages voor controllers, services en repositories. Maak alleen lagen aan die daadwerkelijk nodig zijn.
- Houd klassen en methodes kort, overzichtelijk en gericht op een enkele verantwoordelijkheid.
- Scheid de logica duidelijk: controllers verzorgen de interactie met de buitenwereld, services bevatten de applicatielogica en repositories verzorgen eventuele datatoegang.
- Houd bedrijfslogica onafhankelijk van transportdetails en datatoegang.
- Gebruik constructor-injectie en maak afhankelijkheden expliciet.
- Kies duidelijke namen en eenvoudige oplossingen; vermijd onnodige abstracties.
- Gebruik spring dependencies wanneer je kunt kiezen voor spring en andere libraries.
- Gebruik Java 25 features zoals records, pattern matching en switch expressions waar mogelijk.

## Tests

- Gebruik JUnit 6 en Mockito voor unittests.
- Gebruik Testcontainers voor integratietests die externe infrastructuur nodig hebben.
- Test de bedrijfslogica en relevante foutpaden en randgevallen.
- Houd tests gericht en onafhankelijk van elkaar.

## Taal en samenwerking

- Schrijf documentatie en communiceer in het Nederlands.
- Stel vragen bij onduidelijke functionele eisen of belangrijke technische keuzes.
- Volg deze voorkeuren bij nieuwe code en houd wijzigingen beperkt tot de gevraagde functionaliteit.
- Gebruik Engelse namen voor packages, klassen, methodes en variabelen.

## Foutafhandeling
- Ben niet te streng in de foutafhandeling. Vraag me eerst of je rekeneing wilt houden met bepaalde situaties.
