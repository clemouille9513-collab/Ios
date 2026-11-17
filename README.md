# Écran de verrouillage iOS pour Samsung

Projet Android (Kotlin). L'APK se compile tout seul sur GitHub (onglet Actions > Build APK > artefact `ioslock-apk`).

## Comment ça marche
Un service d'accessibilité pose l'écran dans une fenêtre système qui passe au-dessus de tout (applis, barre d'état, barre de navigation) et absorbe tous les touchers. La fenêtre est créée une fois et préparée quand l'écran s'éteint : au rallumage, elle est déjà là et seules les animations se jouent.

## Réglages sur le téléphone
1. Ouvre l'appli « Écran iOS », active le service d'accessibilité (si Android refuse : infos de l'appli > ⋮ > « Autoriser les paramètres restreints »), autorise la caméra.
2. Choisis un code, enregistre ton visage, règle le fond et l'heure.
3. Active l'interrupteur. Éteins puis rallume l'écran.

## Verrouillage Samsung
- Sans verrouillage Samsung (« Aucun ») : code + visage. C'est ton écran qui protège le téléphone.
- Avec un verrouillage Samsung (code + empreinte) : l'empreinte déverrouille aussi (le système Samsung la lit et l'écran iOS s'ouvre).
  Android efface les empreintes si on supprime le verrouillage : les deux ne vont pas ensemble.

## Limites
- Le bouton d'éteignement, le redémarrage et le mode sans échec restent possibles : aucune appli ne peut les bloquer.
- Si tu oublies le code : mode sans échec (maintiens le bouton d'éteignement, appui long sur « Éteindre ») puis désinstalle l'appli.
- Reconnaissance du visage simple (points du visage via ML Kit, sans détection de photo ou de vidéo) : moins sûre que le Face ID.
- Écran prévu pour le mode portrait.
