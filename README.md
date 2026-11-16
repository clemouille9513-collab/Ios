# Écran de verrouillage iOS pour Samsung

Projet Android (Kotlin). L'APK se compile tout seul sur GitHub (onglet Actions > Build APK > artefact `ioslock-apk`).

## Réglages sur le téléphone
1. Ouvre l'appli « Écran iOS » et accorde les autorisations (affichage par-dessus les autres applis, notifications, batterie).
2. Choisis un code (4 à 6 chiffres).
3. Visage : « Enregistrer mon visage », puis active « Déverrouiller avec le visage ».
4. Apparence : fond d'écran au choix, hauteur et taille de l'heure.
5. Sécurité : dans les réglages Samsung, mets le verrouillage sur « Balayer » ou « Aucun », et laisse « Verrouillage strict » activé.
6. Active l'interrupteur. Éteins puis rallume l'écran.

## Limites
- Android ne permet pas de remplacer l'écran de verrouillage du système : l'appli s'affiche par-dessus et revient aussitôt si on essaie de la quitter (mode strict). Le bouton d'éteignement, le redémarrage ou le mode sans échec restent possibles.
- La reconnaissance du visage est simple (points du visage via ML Kit, sans détection de photo ou de vidéo). Elle est moins sûre que le Face ID : garde un code solide.
- Le fond d'écran officiel de l'iPhone n'est pas inclus : choisis l'image dans l'appli.
- Pas encore : notifications sur l'écran, widgets.
